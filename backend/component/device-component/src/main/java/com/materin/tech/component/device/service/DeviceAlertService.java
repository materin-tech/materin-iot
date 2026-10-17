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
package com.materin.tech.component.device.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 设备告警（列表筛选 / 统计 / 生命周期操作）。 */
@Service
@RequiredArgsConstructor
public class DeviceAlertService {

    private final DeviceAlertMapper alertMapper;

    public PageResult<DeviceAlert> list(long page, long pageSize, String keyword,
                                        String level, Integer status, Long deviceId,
                                        String startTime, String endTime) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword + "%";
            wrapper.and(new QueryColumn("content").like(like)
                    .or(new QueryColumn("device_name").like(like)));
        }
        if (StringUtils.hasText(level)) {
            wrapper.eq("level", level);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (deviceId != null) {
            wrapper.eq("device_id", deviceId);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge("create_time", startTime);
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le("create_time", endTime + " 23:59:59");
        }
        Page<DeviceAlert> result = alertMapper.paginate(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotalRow());
    }

    /** 告警统计（单次返回，替代前端多次凑数请求）：总量/生命周期/级别分布。 */
    public java.util.Map<String, Long> stats() {
        java.util.Map<String, Long> stats = new java.util.LinkedHashMap<>();
        stats.put("total", count(null));
        stats.put("active", count(0));
        stats.put("acked", count(1));
        stats.put("cleared", count(2));
        stats.put("info", countByLevel("info"));
        stats.put("warn", countByLevel("warn"));
        stats.put("error", countByLevel("error"));
        return stats;
    }

    public DeviceAlert update(Long id, DeviceAlert patch) {
        DeviceAlert alert = alertMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (alert == null) {
            throw BizException.notFound("告警不存在: " + id);
        }
        if (patch.getStatus() != null) {
            alert.setStatus(patch.getStatus());
            // 确认动作记录时间；恢复动作记录时间与原因
            if (patch.getStatus() == 1) {
                alert.setAckTime(java.time.LocalDateTime.now());
            } else if (patch.getStatus() == 2 && alert.getClearTime() == null) {
                alert.setClearTime(java.time.LocalDateTime.now());
                alert.setClearReason("人工处理");
            }
        }
        if (patch.getContent() != null) {
            alert.setContent(patch.getContent());
        }
        alertMapper.update(alert);
        return alert;
    }

    private Long count(Integer status) {
        QueryWrapper qw = QueryWrapper.create();
        if (status != null) {
            qw.eq("status", status);
        }
        Long c = alertMapper.selectCountByQuery(qw);
        return c == null ? 0L : c;
    }

    private Long countByLevel(String level) {
        Long c = alertMapper.selectCountByQuery(QueryWrapper.create().eq("level", level));
        return c == null ? 0L : c;
    }
}
