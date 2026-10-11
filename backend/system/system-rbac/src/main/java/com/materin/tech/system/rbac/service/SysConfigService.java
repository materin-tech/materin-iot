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
package com.materin.tech.system.rbac.service;

import com.materin.tech.system.rbac.entity.SysConfig;
import com.materin.tech.system.rbac.mapper.SysConfigMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 系统参数读取（等保安全策略）：内存缓存 + 代码内默认值兜底，
 * 管理端更新后调用 {@link #refreshCache()} 立即生效。
 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String key, String defaultValue) {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SysConfig row = sysConfigMapper.selectOneByQuery(
                QueryWrapper.create().eq("config_key", key));
        String value = row == null || row.getConfigValue() == null
                ? defaultValue : row.getConfigValue();
        cache.put(key, value);
        return value;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public Map<String, String> upsert(Map<String, String> values) {
        values.forEach((key, value) -> {
            SysConfig row = sysConfigMapper.selectOneByQuery(
                    QueryWrapper.create().eq("config_key", key));
            if (row == null) {
                row = new SysConfig();
                row.setConfigKey(key);
                row.setConfigValue(value);
                sysConfigMapper.insert(row);
            } else {
                row.setConfigValue(value);
                sysConfigMapper.update(row);
            }
            cache.put(key, value);
        });
        return Map.copyOf(cache);
    }

    public void refreshCache() {
        cache.clear();
    }
}
