/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.tech.component.dfx.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.component.dfx.core.entity.DfxNotification;
import com.materin.tech.component.dfx.core.entity.DfxNotifyChannel;
import com.materin.tech.component.dfx.core.mapper.DfxNotificationMapper;
import com.materin.tech.component.dfx.core.mapper.DfxNotifyChannelMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DFX 告警通知服务（dfx-core）：按 dfx_notify_channel 分发告警/恢复通知。
 * - inapp：写 dfx_notification（站内通知铃铛数据源）
 * - webhook：钉钉/飞书/企业微信/通用格式，独立小线程池异步 + 短超时，失败仅告警日志
 * - email：spring-boot-starter-mail（spring.mail.host 未配置时该渠道优雅禁用）
 * 通知失败绝不阻断告警落库链路。
 */
@Slf4j
@Service
public class DfxNotifyService {

    private static final Map<String, Integer> LEVEL_RANK = Map.of("info", 0, "warn", 1, "error", 2);

    private final DfxNotifyChannelMapper channelMapper;
    private final DfxNotificationMapper notificationMapper;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectMapper objectMapper;
    private final RestTemplate http;
    private final java.util.concurrent.ExecutorService pool;

    public DfxNotifyService(DfxNotifyChannelMapper channelMapper,
                            DfxNotificationMapper notificationMapper,
                            ObjectProvider<JavaMailSender> mailSender,
                            ObjectMapper objectMapper) {
        this.channelMapper = channelMapper;
        this.notificationMapper = notificationMapper;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(3000);
        this.http = new RestTemplate(f);
        this.pool = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "dfx-notify");
            t.setDaemon(true);
            return t;
        });
    }

    /** 告警通知（新告警时调用）。 */
    public void notifyAlert(String deviceName, String level, String content, Long alertId) {
        dispatch("告警", level, "[" + level.toUpperCase() + "] " + (deviceName == null ? "" : deviceName),
                content, alertId);
    }

    /** 恢复通知（规则恢复条件满足/设备上线时调用）。 */
    public void notifyRecovery(String deviceName, String metric, String value) {
        dispatch("恢复", "info", "恢复 " + (deviceName == null ? "" : deviceName),
                String.format("%s %s 已恢复（当前值 %s）", deviceName == null ? "" : deviceName, metric, value),
                null);
    }

    private void dispatch(String kind, String level, String title, String content, Long alertId) {
        List<DfxNotifyChannel> channels;
        try {
            channels = channelMapper.selectListByQuery(
                    QueryWrapper.create().eq("enabled", 1));
        } catch (Exception e) {
            log.warn("通知渠道读取失败（跳过通知）: {}", e.getMessage());
            return;
        }
        for (DfxNotifyChannel ch : channels) {
            if (!levelAtLeast(level, ch.getMinLevel())) {
                continue;
            }
            String type = ch.getType() == null ? "" : ch.getType();
            switch (type) {
                case "inapp" -> writeInapp(kind, level, title, content, alertId);
                case "webhook" -> pool.execute(() -> {
                    try {
                        sendWebhook(ch, title, content, level);
                    } catch (Exception e) {
                        log.warn("webhook 通知失败: channel={}, {}", ch.getName(), e.getMessage());
                    }
                });
                case "email" -> pool.execute(() -> {
                    try {
                        sendEmail(ch, title, content);
                    } catch (Exception e) {
                        log.warn("邮件通知失败: channel={}, {}", ch.getName(), e.getMessage());
                    }
                });
                default -> log.warn("未知通知渠道类型: {}", type);
            }
        }
    }

    // ------------------------------------------------------------ inapp

    private void writeInapp(String kind, String level, String title, String content, Long alertId) {
        try {
            DfxNotification n = new DfxNotification();
            n.setTitle(title);
            n.setContent(content);
            n.setLevel(level);
            n.setAlertId(alertId);
            n.setReadFlag(0);
            notificationMapper.insert(n);
            log.debug("站内通知已写入: {}", title);
        } catch (Exception e) {
            log.warn("站内通知写入失败（忽略）: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------ webhook

    private void sendWebhook(DfxNotifyChannel ch, String title, String content, String level) throws Exception {
        Map<String, Object> cfg = parseConfig(ch.getConfigJson());
        String url = (String) cfg.get("url");
        if (url == null || url.isBlank()) {
            log.warn("webhook 渠道缺少 url: {}", ch.getName());
            return;
        }
        String format = cfg.get("format") == null ? "generic" : String.valueOf(cfg.get("format"));
        String text = title + "\n" + content;
        Map<String, Object> payload = switch (format) {
            case "dingtalk" -> Map.of("msgtype", "text",
                    "text", Map.of("content", text));
            case "feishu" -> Map.of("msg_type", "text",
                    "content", Map.of("text", text));
            case "wecom" -> Map.of("msgtype", "text",
                    "text", Map.of("content", text));
            default -> Map.of("title", title, "content", content,
                    "level", level, "timestamp", System.currentTimeMillis());
        };
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Content-Type", "application/json");
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(
                objectMapper.writeValueAsString(payload), headers);
        org.springframework.http.ResponseEntity<String> resp =
                http.postForEntity(java.net.URI.create(url.trim()), entity, String.class);
        log.info("webhook 通知已推送: channel={}, status={}", ch.getName(), resp.getStatusCode());
    }

    // ------------------------------------------------------------ email

    private void sendEmail(DfxNotifyChannel ch, String title, String content) throws Exception {
        JavaMailSender sender = mailSender.getIfAvailable();
        Map<String, Object> cfg = parseConfig(ch.getConfigJson());
        Object toObj = cfg.get("to");
        List<String> to = new java.util.ArrayList<>();
        if (toObj instanceof List<?> l) {
            l.forEach(x -> to.add(String.valueOf(x)));
        } else if (toObj != null) {
            to.add(String.valueOf(toObj));
        }
        if (sender == null || to.isEmpty()) {
            log.warn("邮件渠道不可用（未配置 spring.mail.host 或收件人为空）: {}", ch.getName());
            return;
        }
        String prefix = cfg.get("subjectPrefix") == null ? "[Materin IoT]" : String.valueOf(cfg.get("subjectPrefix"));
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setSubject(prefix + " " + title);
        helper.setText(content, false);
        helper.setTo(to.toArray(new String[0]));
        sender.send(message);
        log.info("邮件通知已发送: channel={}, to={}", ch.getName(), to);
    }

    /** 渠道测试发送（同步执行，失败抛业务异常，前端直接展示结果）。 */
    public void testSend(Long channelId) {
        DfxNotifyChannel ch = channelMapper.selectOneById(channelId);
        if (ch == null) {
            throw com.materin.tech.common.exception.BizException.notFound("通知渠道不存在: " + channelId);
        }
        String type = ch.getType() == null ? "" : ch.getType();
        try {
            switch (type) {
                case "inapp" -> writeInapp("测试", "info", "测试通知", "这是一条渠道测试消息（Materin IoT DFX）", null);
                case "webhook" -> sendWebhook(ch, "测试通知", "这是一条渠道测试消息（Materin IoT DFX）", "info");
                case "email" -> sendEmail(ch, "测试通知", "这是一条渠道测试消息（Materin IoT DFX）");
                default -> throw com.materin.tech.common.exception.BizException.badRequest("未知渠道类型: " + type);
            }
        } catch (com.materin.tech.common.exception.BizException be) {
            throw be;
        } catch (Exception e) {
            throw com.materin.tech.common.exception.BizException.badRequest("测试发送失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ 工具

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
        } catch (Exception e) {
            log.warn("通知渠道配置解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 级别门槛：level >= minLevel 才推送。 */
    static boolean levelAtLeast(String level, String minLevel) {
        int l = LEVEL_RANK.getOrDefault(level == null ? "info" : level, 0);
        int m = LEVEL_RANK.getOrDefault(minLevel == null ? "info" : minLevel, 0);
        return l >= m;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
