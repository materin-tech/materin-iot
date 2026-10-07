package com.materin.tech.component.ota.service;

import com.materin.tech.common.spi.CommandPublisher;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.component.ota.entity.OtaPackage;
import com.materin.tech.component.ota.entity.OtaTask;
import com.materin.tech.component.ota.entity.OtaTaskDevice;
import com.materin.tech.component.ota.mapper.OtaPackageMapper;
import com.materin.tech.component.ota.mapper.OtaTaskDeviceMapper;
import com.materin.tech.component.ota.mapper.OtaTaskMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** OTA 升级任务：批量圈选设备 -> 逐台推送（预签名下载地址） -> 进度状态机跟踪。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaTaskService {

    /** 明细状态：0 待推送 1 已推送 2 下载中 3 升级中 4 成功 5 失败 */
    public static final int ST_PENDING = 0;
    public static final int ST_PUSHED = 1;
    public static final int ST_DOWNLOADING = 2;
    public static final int ST_UPGRADING = 3;
    public static final int ST_SUCCESS = 4;
    public static final int ST_FAILED = 5;

    private final OtaTaskMapper taskMapper;
    private final OtaTaskDeviceMapper taskDeviceMapper;
    private final OtaPackageMapper packageMapper;
    private final OtaPackageService packageService;
    private final DeviceMapper deviceMapper;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<CommandPublisher> publisher;

    /**
     * 创建任务并立即推送。
     * scope=product：按产品统一升级（圈选该产品下全部设备）；
     * scope=device：按设备单独升级（显式 deviceIds）。
     */
    public OtaTask createTask(OtaTask task, List<Long> deviceIds, Long productId) {
        OtaPackage pkg = packageMapper.selectOneById(task.getPackageId());
        if (pkg == null) {
            throw new com.materin.tech.common.exception.BizException(404, "固件包不存在");
        }
        boolean byProduct = productId != null;
        task.setScope(byProduct ? "product" : "device");
        if (byProduct) {
            task.setProductId(productId);
            Device probe = deviceMapper.selectOneByQuery(
                    QueryWrapper.create().eq("product_id", productId).limit(1));
            task.setProductName(probe == null ? "" : probe.getProductName());
            List<Device> devices = deviceMapper.selectListByQuery(
                    QueryWrapper.create().eq("product_id", productId));
            deviceIds = devices.stream().map(Device::getId).toList();
            if (deviceIds.isEmpty()) {
                throw new com.materin.tech.common.exception.BizException(400, "该产品下暂无设备");
            }
        } else {
            if (deviceIds == null || deviceIds.isEmpty()) {
                throw new com.materin.tech.common.exception.BizException(400, "deviceIds 不能为空");
            }
        }
        task.setDeviceCount(deviceIds.size());
        if (task.getStatus() == null) {
            task.setStatus(0);
        }
        taskMapper.insert(task);
        int pushed = 0;
        for (Long deviceId : deviceIds) {
            OtaTaskDevice td = new OtaTaskDevice();
            td.setTaskId(task.getId());
            td.setPackageId(pkg.getId());
            td.setDeviceId(deviceId);
            Device d = deviceMapper.selectOneByQuery(
                    QueryWrapper.create().eq("id", deviceId));
            if (d == null) {
                continue;
            }
            td.setDeviceKey(d.getDeviceKey());
            td.setDeviceName(d.getName());
            td.setStatus(ST_PENDING);
            td.setProgress(0);
            td.setPushCount(0);
            taskDeviceMapper.insert(td);
            pushed += pushOne(td, pkg) ? 1 : 0;
        }
        log.info("OTA 任务创建: id={}, pkg={}, scope={}, 设备 {} 台",
                task.getId(), pkg.getId(), task.getScope(), pushed);
        return task;
    }

    /** 推送单台：组装 OTA 指令（含预签名下载地址）发布到 materin/{pk}/{dk}/ota_push。 */
    public boolean pushOne(OtaTaskDevice td, OtaPackage pkg) {
        CommandPublisher pub = publisher.getIfAvailable();
        if (pub == null) {
            log.warn("无 MQTT 发布通道，OTA 推送暂缓");
            return false;
        }
        String pk = credentialLookup.getIfAvailable() == null ? null
                : credentialLookup.getIfAvailable()
                        .findProductKeyByKey(td.getDeviceKey()).orElse(null);
        if (pk == null) {
            markFailed(td, "产品归属解析失败");
            return false;
        }
        String url = packageService.downloadUrl(pkg.getId(), 3600);
        String payload = "{\"taskId\":" + td.getTaskId()
                + ",\"packageId\":" + pkg.getId()
                + ",\"version\":\"" + pkg.getVersion() + "\""
                + ",\"module\":\"" + (pkg.getModule() == null ? "" : pkg.getModule()) + "\""
                + ",\"size\":" + pkg.getSize()
                + ",\"signMethod\":\"" + pkg.getSignMethod() + "\""
                + ",\"signValue\":\"" + pkg.getSignValue() + "\""
                + ",\"url\":\"" + url + "\"}";
        String topic = "materin/" + pk + "/" + td.getDeviceKey() + "/ota_push";
        boolean ok = pub.publish(topic, payload.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            td.setStatus(ST_PUSHED);
            td.setPushCount(td.getPushCount() == null ? 1 : td.getPushCount() + 1);
            taskDeviceMapper.update(td);
        }
        return ok;
    }

    /** 设备进度上报（mqtt ota 上行通道回调）：推进状态机。 */
    public void onProgress(String deviceKey, Map<String, Object> body) {
        Object taskId = body.get("taskId");
        if (taskId == null) {
            return;
        }
        OtaTaskDevice td = taskDeviceMapper.selectOneByQuery(QueryWrapper.create()
                .eq("task_id", Long.valueOf(String.valueOf(taskId)))
                .eq("device_key", deviceKey));
        if (td == null) {
            log.debug("OTA 进度无匹配明细: deviceKey={}, taskId={}", deviceKey, taskId);
            return;
        }
        int status = body.get("status") instanceof Number n ? n.intValue() : -1;
        int progress = body.get("progress") instanceof Number n ? n.intValue() : td.getProgress();
        String message = body.get("message") == null ? null : String.valueOf(body.get("message"));
        if (status >= ST_PENDING && status <= ST_FAILED) {
            td.setStatus(status);
        }
        td.setProgress(Math.max(0, Math.min(100, progress)));
        if (message != null) {
            td.setMessage(message);
        }
        taskDeviceMapper.update(td);
        if (status == ST_SUCCESS) {
            // 升级成功回写设备当前固件版本
            OtaPackage pkg = packageMapper.selectOneById(td.getPackageId());
            if (pkg != null) {
                Device d = deviceMapper.selectOneByQuery(
                        QueryWrapper.create().eq("id", td.getDeviceId()));
                if (d != null) {
                    d.setFirmware(pkg.getVersion());
                    deviceMapper.update(d);
                }
            }
            log.info("OTA 升级成功: device={}, version={}", deviceKey,
                    pkg == null ? "?" : pkg.getVersion());
        }
        convergeTask(td.getTaskId());
    }

    /** 全部明细到达终态（成功/失败）后收敛任务状态。 */
    private void convergeTask(Long taskId) {
        Long pending = taskDeviceMapper.selectCountByQuery(QueryWrapper.create()
                .eq("task_id", taskId)
                .notIn("status", List.of(ST_SUCCESS, ST_FAILED)));
        if (pending != null && pending == 0) {
            OtaTask task = taskMapper.selectOneById(taskId);
            if (task != null && task.getStatus() == 0) {
                task.setStatus(1);
                taskMapper.update(task);
                log.info("OTA 任务完成: {}", taskId);
            }
        }
    }

    public Page<OtaTaskDevice> taskDevices(long page, long pageSize, Long taskId, Integer status) {
        QueryWrapper qw = QueryWrapper.create().eq("task_id", taskId);
        if (status != null) {
            qw.eq("status", status);
        }
        return taskDeviceMapper.paginate(page, pageSize, qw.orderBy("id", true));
    }

    public Page<OtaTask> list(long page, long pageSize) {
        return taskMapper.paginate(page, pageSize, QueryWrapper.create().orderBy("id", false));
    }

    public void markFailed(OtaTaskDevice td, String reason) {
        td.setStatus(ST_FAILED);
        td.setMessage(reason);
        taskDeviceMapper.update(td);
    }

    /** 重推（失败重试入口）。 */
    public boolean retry(Long taskDeviceId) {
        OtaTaskDevice td = taskDeviceMapper.selectOneById(taskDeviceId);
        if (td == null) {
            throw new com.materin.tech.common.exception.BizException(404, "明细不存在");
        }
        OtaPackage pkg = packageMapper.selectOneById(td.getPackageId());
        return pushOne(td, pkg);
    }
}
