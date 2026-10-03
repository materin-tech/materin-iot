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
                                   Long productId, Integer status) {
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
        Page<Device> result = deviceMapper.paginate(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotalRow());
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

    private void bindProduct(Device device, Map<String, Object> payload) {
        Object pid = payload.get("productId");
        if (pid == null || String.valueOf(pid).isBlank() || "null".equals(String.valueOf(pid))) {
            return;
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
