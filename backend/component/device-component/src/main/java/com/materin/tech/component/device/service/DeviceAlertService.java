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

/** 设备告警。 */
@Service
@RequiredArgsConstructor
public class DeviceAlertService {

    private final DeviceAlertMapper alertMapper;

    public PageResult<DeviceAlert> list(long page, long pageSize, String keyword,
                                        String level, Integer status) {
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
        Page<DeviceAlert> result = alertMapper.paginate(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotalRow());
    }

    public DeviceAlert update(Long id, DeviceAlert patch) {
        DeviceAlert alert = alertMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (alert == null) {
            throw BizException.notFound("告警不存在: " + id);
        }
        if (patch.getStatus() != null) {
            alert.setStatus(patch.getStatus());
        }
        if (patch.getContent() != null) {
            alert.setContent(patch.getContent());
        }
        alertMapper.update(alert);
        return alert;
    }
}
