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
import com.materin.tech.common.spi.ProductDeviceCounter;
import com.materin.tech.common.spi.ProductLookup;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 设备管理：对齐前端 /device 接口契约。 */
@Service
public class DeviceService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 设备类型常量（与迁移列 device_type 约束一致） */
    static final String TYPE_DIRECT = "DIRECT";
    static final String TYPE_GATEWAY = "GATEWAY";
    static final String TYPE_CHILD = "CHILD";

    private final DeviceMapper deviceMapper;
    private final DeviceAlertMapper alertMapper;
    private final ProductDeviceCounter deviceCounter;
    private final ProductLookup productLookup;

    public DeviceService(DeviceMapper deviceMapper, DeviceAlertMapper alertMapper,
                         ObjectProvider<ProductDeviceCounter> counter,
                         ObjectProvider<ProductLookup> lookup) {
        this.deviceMapper = deviceMapper;
        this.alertMapper = alertMapper;
        this.deviceCounter = counter.getIfAvailable();
        this.productLookup = lookup.getIfAvailable();
    }

    public PageResult<Device> list(long page, long pageSize, String name, String deviceKey,
                                   Long productId, Integer status, String deviceType) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(name)) {
            String like = "%" + name + "%";
            wrapper.and(new QueryColumn("name").like(like)
                    .or(new QueryColumn("device_key").like(like)));
        }
        if (StringUtils.hasText(deviceKey)) {
            wrapper.eq("device_key", deviceKey);
        }
        if (productId != null) {
            wrapper.eq("product_id", productId);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(deviceType)) {
            wrapper.eq("device_type", deviceType);
        }
        Page<Device> result = deviceMapper.paginate(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotalRow());
    }

    /** 设备详情（含端侧自报信息 infoJson）。 */
    public Device requireById(Long id) {
        Device d = deviceMapper.selectOneById(id);
        if (d == null) {
            throw BizException.badRequest("设备不存在: " + id);
        }
        return d;
    }

    public Device create(Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String deviceKey = (String) payload.get("deviceKey");
        if (!StringUtils.hasText(name) || !StringUtils.hasText(deviceKey)) {
            throw BizException.badRequest("设备名与设备标识不能为空");
        }
        if (deviceMapper.selectCountByQuery(
                QueryWrapper.create().eq("device_key", deviceKey)) > 0) {
            throw BizException.badRequest("设备标识已存在: " + deviceKey);
        }
        Device device = new Device();
        device.setName(name);
        device.setDeviceKey(deviceKey);
        device.setSecret(java.util.UUID.randomUUID().toString().replace("-", ""));
        if (payload.get("secret") != null) {
            device.setSecret((String) payload.get("secret"));
        }
        device.setStatus(payload.get("status") == null ? 0
                : ((Number) payload.get("status")).intValue());
        bindProduct(device, payload);
        bindDeviceType(device, payload);
        if (payload.get("firmware") != null) {
            device.setFirmware((String) payload.get("firmware"));
        }
        if (payload.get("lastOnline") != null) {
            device.setLastOnline((String) payload.get("lastOnline"));
        }
        if (payload.get("remark") != null) {
            device.setRemark((String) payload.get("remark"));
        }
        deviceMapper.insert(device);
        notifyCounter(device.getProductId(), true);
        return device;
    }

    public Device update(Long id, Map<String, Object> payload) {
        Device device = requireDevice(id);
        if (payload.get("name") != null) {
            device.setName((String) payload.get("name"));
        }
        if (payload.get("status") != null) {
            device.setStatus(((Number) payload.get("status")).intValue());
        }
        if (payload.get("firmware") != null) {
            device.setFirmware((String) payload.get("firmware"));
        }
        if (payload.get("remark") != null) {
            device.setRemark((String) payload.get("remark"));
        }
        if (payload.get("lastOnline") != null) {
            device.setLastOnline((String) payload.get("lastOnline"));
        }
        Long oldProductId = device.getProductId();
        bindProduct(device, payload);
        deviceMapper.update(device);
        if (payload.containsKey("productId")
                && !String.valueOf(payload.get("productId")).equals(String.valueOf(oldProductId))) {
            notifyCounter(oldProductId, false);
            notifyCounter(device.getProductId(), true);
        }
        return device;
    }

    public void delete(Long id) {
        Device device = requireDevice(id);
        deviceMapper.deleteById(id);
        notifyCounter(device.getProductId(), false);
        alertMapper.deleteByQuery(QueryWrapper.create().eq("device_id", id));
    }

    public Map<String, Integer> importDevices(List<Map<String, Object>> items) {
        int failed = 0;
        int imported = 0;
        for (Map<String, Object> item : items) {
            try {
                create(item);
                imported++;
            } catch (Exception e) {
                failed++;
            }
        }
        return Map.of("imported", imported, "failed", failed);
    }

    /**
     * 设备类型绑定与校验：payload 带 deviceType 时设置（缺省 DIRECT），
     * CHILD 必须携带 gatewayId 且指向存在的 GATEWAY 设备，非法值/非法归属直接拒绝。
     */
    private void bindDeviceType(Device device, Map<String, Object> payload) {
        String deviceType = (String) payload.get("deviceType");
        if (!StringUtils.hasText(deviceType)) {
            deviceType = TYPE_DIRECT;
        }
        if (!TYPE_DIRECT.equals(deviceType) && !TYPE_GATEWAY.equals(deviceType)
                && !TYPE_CHILD.equals(deviceType)) {
            throw BizException.badRequest("非法设备类型: " + deviceType);
        }
        // 产品节点形态约束：设备类型必须匹配产品 node_type（产品-物模型-设备 的形态一致性）
        String nodeType = productNodeType(device.getProductId());
        if (nodeType != null && !nodeType.equals(deviceType)) {
            throw BizException.badRequest("设备类型与产品节点形态不一致: 产品 " + nodeType
                    + "，设备 " + deviceType);
        }
        if (TYPE_CHILD.equals(deviceType)) {
            Long gatewayId = parseLong(payload.get("gatewayId"));
            if (gatewayId == null) {
                throw BizException.badRequest("CHILD 设备必须指定 gatewayId");
            }
            Device gateway = deviceMapper.selectOneByQuery(
                    QueryWrapper.create().eq("id", gatewayId));
            if (gateway == null || !TYPE_GATEWAY.equals(gateway.getDeviceType())) {
                throw BizException.badRequest("gatewayId 指向的设备不存在或不是网关设备: " + gatewayId);
            }
            device.setGatewayId(gatewayId);
        }
        device.setDeviceType(deviceType);
    }

    /** 查产品节点形态（DIRECT/GATEWAY/CHILD），产品必须存在（产品-物模型-设备）。 */
    private String productNodeType(Long productId) {
        if (productId == null) {
            return null;
        }
        return productLookup == null ? null
                : productLookup.findById(String.valueOf(productId))
                        .map(ProductLookup.ProductSummary::nodeType)
                        .orElseThrow(() -> BizException.badRequest("产品不存在: " + productId));
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()
                || "null".equals(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void bindProduct(Device device, Map<String, Object> payload) {
        Object pid = payload.get("productId");
        if (pid == null || String.valueOf(pid).isBlank() || "null".equals(String.valueOf(pid))) {
            throw BizException.badRequest("设备必须归属产品（产品-物模型-设备）");
        }
        device.setProductId(Long.valueOf(String.valueOf(pid)));
        if (productLookup != null) {
            Optional<ProductLookup.ProductSummary> product =
                    productLookup.findById(String.valueOf(pid));
            product.ifPresent(p -> device.setProductName(p.name()));
        }
    }

    private void notifyCounter(String productId, boolean added) {
        if (deviceCounter == null || productId == null) {
            return;
        }
        if (added) {
            deviceCounter.onDeviceAdded(productId);
        } else {
            deviceCounter.onDeviceRemoved(productId);
        }
    }

    private void notifyCounter(Long productId, boolean added) {
        notifyCounter(productId == null ? null : String.valueOf(productId), added);
    }

    private Device requireDevice(Long id) {
        Device device = deviceMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (device == null) {
            throw BizException.notFound("设备不存在: " + id);
        }
        return device;
    }
}
