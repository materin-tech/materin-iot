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

import com.materin.tech.common.spi.DfxAlertEvent;
import com.materin.tech.common.spi.DfxAlertSink;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.mapper.DfxAlertRuleMapper;
import com.materin.tech.component.dfx.core.mapper.DfxMetricConfigMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DFX 规则评估器（dfx-core，规则引擎 v2，docs/dfx-monitoring-design.md §5）：
 * - 比较符 gt/gte/lt/lte/eq/ne/between
 * - durationSeconds 持续越限防毛刺（内存状态机：首个越限样本起计时，中断即重置）
 * - clearComparator/clearThreshold 恢复条件（hysteresis）：满足即恢复活跃告警并推送恢复通知
 * - 设备级 + 产品级规则合并评估（产品级规则对新设备自动生效）
 * - 抑制窗口走 Redis（SETNX+TTL），重启不清零、多实例共享
 * 任何单规则异常兜底跳过，绝不阻断指标管道。
 */
@Slf4j
@Component
public class DfxRuleEvaluator {

    /** 抑制窗口默认值（秒） */
    static final long DEFAULT_SUPPRESS_SECONDS = 600L;

    private final DfxAlertRuleMapper ruleMapper;
    private final DfxMetricConfigMapper metricMapper;
    private final ObjectProvider<DfxAlertSink> alertSink;
    private final DfxNotifyService notifyService;
    private final StringRedisTemplate redis;

    /** duration 状态机：ruleId:deviceId → 首个越限样本时间（中断即清，重启丢状态重算） */
    private final Map<String, Long> violatingSince = new ConcurrentHashMap<>();

    public DfxRuleEvaluator(DfxAlertRuleMapper ruleMapper,
                            DfxMetricConfigMapper metricMapper,
                            ObjectProvider<DfxAlertSink> alertSink,
                            DfxNotifyService notifyService,
                            StringRedisTemplate redis) {
        this.ruleMapper = ruleMapper;
        this.metricMapper = metricMapper;
        this.alertSink = alertSink;
        this.notifyService = notifyService;
        this.redis = redis;
    }

    /** 一次上报的规则评估入口（原始未加前缀 metrics）。 */
    public void evaluate(DeviceCredentialLookup.DeviceCredential cred, Long productId,
                         long time, Map<String, Object> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        for (DfxAlertRule rule : selectRules(cred.deviceId(), productId)) {
            try {
                String name = rule.getMetric();
                if (name != null && name.startsWith("dfx_")) {
                    name = name.substring(4);
                }
                Object raw = metrics.get(name);
                if (raw == null) {
                    raw = metrics.get("dfx_" + name);
                }
                if (raw == null) {
                    continue;
                }
                double value = Double.parseDouble(String.valueOf(raw));
                evaluateOne(cred, rule, value, time);
            } catch (NumberFormatException e) {
                log.debug("非数值指标跳过规则评估: {}", rule.getMetric());
            } catch (Exception e) {
                log.warn("规则评估异常（跳过）: ruleId={}, {}", rule.getId(), e.getMessage());
            }
        }
    }

    /** 设备级 + 产品级规则合并（两条索引查询 + 按 id 去重，规避 OR 嵌套的方言差异）。 */
    private List<DfxAlertRule> selectRules(Long deviceId, Long productId) {
        List<DfxAlertRule> rules = new ArrayList<>(ruleMapper.selectListByQuery(
                QueryWrapper.create().eq("enabled", 1).eq("device_id", deviceId)));
        if (productId != null) {
            for (DfxAlertRule r : ruleMapper.selectListByQuery(
                    QueryWrapper.create().eq("enabled", 1).eq("product_id", productId))) {
                if (rules.stream().noneMatch(x -> x.getId().equals(r.getId()))) {
                    rules.add(r);
                }
            }
        }
        return rules;
    }

    private void evaluateOne(DeviceCredentialLookup.DeviceCredential cred,
                             DfxAlertRule rule, double value, long time) {
        // 1) 恢复判定（优先）：配置了恢复条件且当前值满足 → 清除活跃告警 + 恢复通知
        if (hasClearCondition(rule)
                && matches(rule.getClearComparator(), rule.getClearThreshold(), null, value)) {
            boolean cleared = clearAlert(cred, rule, value);
            violatingSince.remove(stateKey(rule, cred));
            if (cleared) {
                return;
            }
        }

        // 2) 触发判定：比较符 → duration 持续窗口 → 抑制窗口 → 落库 + 通知
        if (!matches(rule.getComparator(), rule.getThreshold(), rule.getThreshold2(), value)) {
            violatingSince.remove(stateKey(rule, cred)); // 越限中断，重置持续计时
            return;
        }
        int durationSec = rule.getDurationSeconds() == null ? 0 : rule.getDurationSeconds();
        String key = stateKey(rule, cred);
        if (durationSec > 0) {
            long sinceTs = violatingSince.getOrDefault(key, time);
            if (time - sinceTs < durationSec * 1000L) {
                violatingSince.put(key, sinceTs);
                return;
            }
        }
        violatingSince.remove(key);
        fire(cred, rule, value);
    }

    private void fire(DeviceCredentialLookup.DeviceCredential cred,
                      DfxAlertRule rule, double value) {
        DfxAlertSink sink = alertSink.getIfAvailable();
        if (sink == null) {
            return;
        }
        if (!tryAcquireSuppress(cred.deviceId(), rule)) {
            log.debug("抑制窗口内不重复告警: device={}, rule={}", cred.deviceKey(), rule.getId());
            return;
        }
        String content = renderContent(rule, cred.deviceName(), value);
        DfxAlertEvent event = new DfxAlertEvent(rule.getId(), cred.deviceId(), cred.deviceName(),
                rule.getLevel() == null ? "warn" : rule.getLevel(),
                rule.getMetric(), String.valueOf(value), content);
        DfxAlertSink.RaiseResult r = sink.raise(event);
        if (r != null && r.created() && r.alertId() != null) {
            notifyService.notifyAlert(cred.deviceName(), event.level(), content, r.alertId());
        }
        log.info("DFX 阈值告警: device={} {}={} {} rule={}",
                cred.deviceKey(), rule.getMetric(), value, event.level(), rule.getId());
    }

    private boolean clearAlert(DeviceCredentialLookup.DeviceCredential cred,
                               DfxAlertRule rule, double value) {
        DfxAlertSink sink = alertSink.getIfAvailable();
        if (sink == null) {
            return false;
        }
        boolean cleared = sink.clear(rule.getId(), null, cred.deviceId(),
                String.format("指标恢复: %s=%s", rule.getMetric(), trimValue(value)));
        if (cleared) {
            notifyService.notifyRecovery(cred.deviceName(), metricLabel(rule.getMetric()),
                    trimValue(value));
            log.info("DFX 告警恢复: device={} {}={}", cred.deviceKey(), rule.getMetric(), value);
        }
        return cleared;
    }

    // ------------------------------------------------------------ 工具

    /** 比较符判定（null 比较符按旧数据隐式 gt 兼容）。 */
    static boolean matches(String comparator, Double threshold, Double threshold2, double value) {
        String c = comparator == null ? "gt" : comparator;
        double t = threshold == null ? 0 : threshold;
        return switch (c) {
            case "gt" -> value > t;
            case "gte" -> value >= t;
            case "lt" -> value < t;
            case "lte" -> value <= t;
            case "eq" -> value == t;
            case "ne" -> value != t;
            case "between" -> value >= t && threshold2 != null && value <= threshold2;
            default -> value > t;
        };
    }

    static boolean hasClearCondition(DfxAlertRule rule) {
        return rule.getClearComparator() != null && !rule.getClearComparator().isBlank();
    }

    private String stateKey(DfxAlertRule rule, DeviceCredentialLookup.DeviceCredential cred) {
        return rule.getId() + ":" + cred.deviceId();
    }

    /** 抑制窗口：Redis SETNX + TTL（重启不清零、多实例共享）；suppress_seconds<=0 表示不抑制。 */
    private boolean tryAcquireSuppress(Long deviceId, DfxAlertRule rule) {
        if (rule.getSuppressSeconds() != null && rule.getSuppressSeconds() <= 0) {
            return true;
        }
        long window = rule.getSuppressSeconds() == null
                ? DEFAULT_SUPPRESS_SECONDS : rule.getSuppressSeconds();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    com.materin.tech.common.redis.RedisKeys.DFX_SUPPRESS
                            + deviceId + ":" + rule.getId(),
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofSeconds(window));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("抑制窗口 Redis 不可用（退化为不抑制）: {}", e.getMessage());
            return true;
    }
    }

    /** 告警内容：模板占位符 {device}/{metric}/{value}/{threshold}，空模板用默认格式。 */
    private String renderContent(DfxAlertRule rule, String deviceName, double value) {
        String metricName = metricLabel(rule.getMetric());
        String thresholdDesc = formatThreshold(rule);
        if (rule.getMessageTemplate() != null && !rule.getMessageTemplate().isBlank()) {
            return rule.getMessageTemplate()
                    .replace("{device}", deviceName == null ? "" : deviceName)
                    .replace("{metric}", metricName)
                    .replace("{value}", trimValue(value))
                    .replace("{threshold}", thresholdDesc);
        }
        return String.format("%s %s 当前值 %s，触发 %s（重复告警抑制：%s）",
                deviceName == null ? rule.getMetric() : deviceName,
                metricName, trimValue(value), thresholdDesc, suppressDesc(rule));
    }

    /** 抑制窗口描述（文案用）：0/null 差异化展示。 */
    private String suppressDesc(DfxAlertRule rule) {
        if (rule.getSuppressSeconds() != null && rule.getSuppressSeconds() <= 0) {
            return "不抑制";
        }
        return (rule.getSuppressSeconds() == null ? DEFAULT_SUPPRESS_SECONDS : rule.getSuppressSeconds()) + "s";
    }

    /** 指标中文名（指标字典缺失时回退原 key）。 */
    private String metricLabel(String metric) {
        try {
            var cfg = metricMapper.selectOneByQuery(
                    QueryWrapper.create().eq("metric",
                            metric.startsWith("dfx_") ? metric : "dfx_" + metric));
            if (cfg != null && cfg.getName() != null) {
                return cfg.getName();
            }
        } catch (Exception ignore) {
            // 字典缺失不影响告警
        }
        return metric;
    }

    private String formatThreshold(DfxAlertRule rule) {
        String c = rule.getComparator() == null ? "gt" : rule.getComparator();
        String t = trimValue(rule.getThreshold() == null ? 0 : rule.getThreshold());
        return switch (c) {
            case "gt" -> ">" + t;
            case "gte" -> ">=" + t;
            case "lt" -> "<" + t;
            case "lte" -> "<=" + t;
            case "eq" -> "=" + t;
            case "ne" -> "!=" + t;
            case "between" -> "[" + t + "," + trimValue(rule.getThreshold2() == null ? 0 : rule.getThreshold2()) + "]";
            default -> ">" + t;
        };
    }

    private String trimValue(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
