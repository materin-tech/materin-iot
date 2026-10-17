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
package com.materin.tech.component.dfx.core.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.dfx.core.entity.DfxNotifyChannel;
import com.materin.tech.component.dfx.core.mapper.DfxNotifyChannelMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** DFX 通知渠道管理（webhook/email/inapp）。 */
@Service
@RequiredArgsConstructor
public class DfxNotifyChannelService {

    private final DfxNotifyChannelMapper channelMapper;

    public List<DfxNotifyChannel> list() {
        return channelMapper.selectAll();
    }

    public DfxNotifyChannel create(DfxNotifyChannel channel) {
        validate(channel);
        if (channel.getEnabled() == null) {
            channel.setEnabled(1);
        }
        if (!StringUtils.hasText(channel.getMinLevel())) {
            channel.setMinLevel("info");
        }
        channelMapper.insert(channel);
        return channel;
    }

    public DfxNotifyChannel update(Long id, DfxNotifyChannel patch) {
        DfxNotifyChannel channel = require(id);
        if (StringUtils.hasText(patch.getName())) {
            channel.setName(patch.getName());
        }
        if (StringUtils.hasText(patch.getType())) {
            channel.setType(patch.getType());
        }
        if (patch.getConfigJson() != null) {
            channel.setConfigJson(patch.getConfigJson());
        }
        if (StringUtils.hasText(patch.getMinLevel())) {
            channel.setMinLevel(patch.getMinLevel());
        }
        if (patch.getEnabled() != null) {
            channel.setEnabled(patch.getEnabled());
        }
        if (patch.getRemark() != null) {
            channel.setRemark(patch.getRemark());
        }
        channelMapper.update(channel);
        return channel;
    }

    public void delete(Long id) {
        require(id);
        channelMapper.deleteById(id);
    }

    public DfxNotifyChannel require(Long id) {
        DfxNotifyChannel c = channelMapper.selectOneById(id);
        if (c == null) {
            throw BizException.notFound("通知渠道不存在: " + id);
        }
        return c;
    }

    private void validate(DfxNotifyChannel channel) {
        if (!StringUtils.hasText(channel.getName())) {
            throw BizException.badRequest("渠道名称不能为空");
        }
        String type = channel.getType();
        if (!"webhook".equals(type) && !"email".equals(type) && !"inapp".equals(type)) {
            throw BizException.badRequest("非法渠道类型: " + type + "（webhook/email/inapp）");
        }
        if ("webhook".equals(type)) {
            java.util.Map<String, Object> cfg = parseConfigQuietly(channel.getConfigJson());
            if (cfg.get("url") == null || String.valueOf(cfg.get("url")).isBlank()) {
                throw BizException.badRequest("webhook 渠道必须配置 url");
            }
        }
        if ("email".equals(type)) {
            java.util.Map<String, Object> cfg = parseConfigQuietly(channel.getConfigJson());
            if (cfg.get("to") == null) {
                throw BizException.badRequest("email 渠道必须配置 to 收件人");
            }
        }
    }

    private java.util.Map<String, Object> parseConfigQuietly(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { });
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }
}
