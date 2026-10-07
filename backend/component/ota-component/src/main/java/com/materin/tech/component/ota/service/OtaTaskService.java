package com.materin.tech.component.ota.service;

import com.materin.tech.common.spi.CommandPublisher;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.component.ota.entity.OtaPackage;
import com.materin.tech.component.ota.entity.OtaTask;
import com.materin.tech.component.ota.entity.OtaTaskDevice;
import com.materin.tech.component.ota.mapper.OtaCandidateMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * OTA 升级计划（创建与设备圈选解耦，内存恒定防 OOM）：
 * - 创建计划只登记口径，不展开设备；设备经详情分页添加（分批去重插入）
 * - 启动后游标分批推送（每批 BATCH_SIZE 条，内存只有一批），推送中实时感知暂停/终止
 * - 状态机：0 未启动 → 1 进行中 ⇄ 2 暂停；→ 3 已终止(终态,不可再启动)；全部明细终态 → 4 已完成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaTaskService {

    /** 计划状态 */
    public static final int T_PENDING = 0;
    public static final int T_RUNNING = 1;
    public static final int T_PAUSED = 2;
    public static final int T_TERMINATED = 3;
    public static final int T_DONE = 4;

    /** 明细状态：0 待推送 1 已推送 2 下载中 3 升级中 4 成功 5 失败 6 已取消 */
    public static final int ST_PENDING = 0;
    public static final int ST_PUSHED = 1;
    public static final int ST_DOWNLOADING = 2;
    public static final int ST_UPGRADING = 3;
    public static final int ST_SUCCESS = 4;
    public static final int ST_FAILED = 5;
    public static final int ST_CANCELLED = 6;

    /** 推送游标分批大小（内存中同批最多这么多明细） */
    static final int PUSH_BATCH_SIZE = 200;
    /** 候选去重的分批上限 */
    static final int ADD_BATCH_SIZE = 1000;

    private final OtaTaskMapper taskMapper;
    private final OtaTaskDeviceMapper taskDeviceMapper;
    private final OtaPackageMapper packageMapper;
    private final OtaPackageService packageService;
    private final DeviceMapper deviceMapper;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<CommandPublisher> publisher;
    private final OtaCandidateMapper candidateMapper;

    // ------------------------------------------------------------ 计划创建（不展开设备）

    /** 创建升级计划：只登记口径，设备经详情分页添加。 */
    public OtaTask createPlan(OtaTask task, Long productId) {
        OtaPackage pkg = packageMapper.selectOneById(task.getPackageId());
        if (pkg == null) {
            throw new com.materin.tech.common.exception.BizException(404, "固件包不存在");
        }
        task.setStatus(T_PENDING);
        task.setDeviceCount(0); // 创建时未展开设备，添加明细时累加
        if (productId != null) {
            task.setScope("product");
            task.setProductId(productId);
            Device probe = deviceMapper.selectOneByQuery(
                    QueryWrapper.create().eq("product_id", productId).limit(1));
            task.setProductName(probe == null ? "" : probe.getProductName());
        } else {
            task.setScope("device");
        }
        taskMapper.insert(task);
        log.info("OTA 计划创建（未展开设备）: id={}, pkg={}, scope={}",
                task.getId(), pkg.getId(), task.getScope());
        return task;
    }

    // ------------------------------------------------------------ 设备分页添加

    /**
     * 计划详情里分页选择添加设备（去重，分批插入，每批 ADD_BATCH_SIZE 条）。
     * @return 实际新增条数
     */
    public int addDevices(Long taskId, List<Long> deviceIds) {
        OtaTask task = requireById(taskId);
        if (task.getStatus() == T_TERMINATED) {
            throw Biz(409, "计划已终止，不能再添加设备");
        }
        int added = 0;
        for (int i = 0; i < deviceIds.size(); i += ADD_BATCH_SIZE) {
            List<Long> batch = deviceIds.subList(i, Math.min(i + ADD_BATCH_SIZE, deviceIds.size()));
            Set<Long> existing = taskDeviceMapper.selectListByQuery(QueryWrapper.create()
                            .select("device_id")
                            .eq("task_id", taskId)
                            .in("device_id", batch))
                    .stream().map(OtaTaskDevice::getDeviceId).collect(java.util.stream.Collectors.toSet());
            for (Long deviceId : batch) {
                if (existing.contains(deviceId)) {
                    continue;
                }
                Device d = deviceMapper.selectOneByQuery(
                        QueryWrapper.create().eq("id", deviceId));
                if (d == null) {
                    continue;
                }
                OtaTaskDevice td = new OtaTaskDevice();
                td.setTaskId(taskId);
                td.setPackageId(task.getPackageId());
                td.setDeviceId(deviceId);
                td.setDeviceKey(d.getDeviceKey());
                td.setDeviceName(d.getName());
                td.setStatus(ST_PENDING);
                td.setProgress(0);
                td.setPushCount(0);
                taskDeviceMapper.insert(td);
                added++;
            }
        }
        refreshCount(taskId);
        return added;
    }

    /** 分页候选设备（NOT EXISTS 排除已添加；scope=product 限该产品），原生 SQL 防大计划 IN 列表。 */
    public Map<String, Object> candidateDevices(Long taskId, String keyword, long page, long pageSize) {
        OtaTask task = requireById(taskId);
        Long productId = "product".equals(task.getScope()) ? task.getProductId() : null;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword;
        List<Map<String, Object>> rows = candidateMapper.selectCandidates(
                taskId, productId, kw, (int) Math.min(pageSize, 200), (page - 1) * pageSize);
        long total = candidateMapper.countCandidates(taskId, productId, kw);
        return Map.of("items", rows, "total", total);
    }

    // ------------------------------------------------------------ 状态机

    /** 启动（未启动/暂停 → 进行中）并异步分批推送待推送明细。已终止计划拒绝。 */
    public void start(Long taskId) {
        OtaTask task = requireById(taskId);
        if (task.getStatus() == T_TERMINATED) {
            throw Biz(409, "计划已终止，不能再次启动");
        }
        if (task.getStatus() == T_DONE) {
            throw Biz(409, "计划已完成");
        }
        task.setStatus(T_RUNNING);
        taskMapper.update(task);
        CompletableFuture.runAsync(() -> pushLoop(taskId));
    }

    /** 暂停（仅进行中可暂停）。 */
    public void pause(Long taskId) {
        OtaTask task = requireById(taskId);
        if (task.getStatus() != T_RUNNING) {
            throw Biz(409, "仅进行中的计划可暂停");
        }
        task.setStatus(T_PAUSED);
        taskMapper.update(task);
    }

    /** 恢复（暂停 → 进行中，继续推送）。 */
    public void resume(Long taskId) {
        OtaTask task = requireById(taskId);
        if (task.getStatus() != T_PAUSED) {
            throw Biz(409, "仅暂停中的计划可恢复");
        }
        task.setStatus(T_RUNNING);
        taskMapper.update(task);
        CompletableFuture.runAsync(() -> pushLoop(taskId));
    }

    /** 终止（0/1/2 → 3，终态不可逆）：未推送明细全部取消。 */
    public void terminate(Long taskId) {
        OtaTask task = requireById(taskId);
        if (task.getStatus() == T_TERMINATED || task.getStatus() == T_DONE) {
            throw Biz(409, "计划已是终态");
        }
        task.setStatus(T_TERMINATED);
        taskMapper.update(task);
        // 游标分批取消待推送明细（内存恒定，不随计划规模增长）
        long cursor = 0L;
        while (true) {
            List<OtaTaskDevice> batch = taskDeviceMapper.selectListByQuery(
                    QueryWrapper.create().eq("task_id", taskId)
                            .eq("status", ST_PENDING).gt("id", cursor)
                            .orderBy("id", true).limit(ADD_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (OtaTaskDevice td : batch) {
                td.setStatus(ST_CANCELLED);
                taskDeviceMapper.update(td);
                cursor = td.getId();
            }
            if (batch.size() < ADD_BATCH_SIZE) {
                break;
            }
        }
    }

    // ------------------------------------------------------------ 推送（游标分批，内存恒定）

    /** 推送循环：每批 PUSH_BATCH_SIZE 条，批间重读计划状态，暂停/终止即停。 */
    private void pushLoop(Long taskId) {
        OtaPackage pkg = packageMapper.selectOneById(
                requireById(taskId).getPackageId());
        if (pkg == null) {
            return;
        }
        long cursor = 0L;
        while (true) {
            OtaTask task = taskMapper.selectOneById(taskId);
            if (task == null || task.getStatus() != T_RUNNING) {
                log.info("OTA 推送循环退出: taskId={}, status={}",
                        taskId, task == null ? "?" : task.getStatus());
                return;
            }
            List<OtaTaskDevice> batch = taskDeviceMapper.selectListByQuery(
                    QueryWrapper.create().eq("task_id", taskId)
                            .eq("status", ST_PENDING).gt("id", cursor)
                            .orderBy("id", true).limit(PUSH_BATCH_SIZE));
            if (batch.isEmpty()) {
                converge(taskId);
                return;
            }
            for (OtaTaskDevice td : batch) {
                pushOne(td, pkg);
                cursor = td.getId();
            }
        }
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

    // ------------------------------------------------------------ 进度回调与收敛

    /** 设备进度上报（mqtt ota 上行通道回调）：推进状态机。 */
    public void onProgress(String deviceKey, Map<String, Object> body) {
        Object taskIdObj = body.get("taskId");
        if (taskIdObj == null) {
            return;
        }
        long taskId = Long.parseLong(String.valueOf(taskIdObj));
        OtaTaskDevice td = taskDeviceMapper.selectOneByQuery(QueryWrapper.create()
                .eq("task_id", taskId)
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
        converge(taskId);
    }

    /** 全部明细到达终态（成功/失败/已取消）且计划进行中 → 已完成。 */
    private void converge(Long taskId) {
        OtaTask task = taskMapper.selectOneById(taskId);
        if (task == null || task.getStatus() != T_RUNNING) {
            return;
        }
        Long unfinished = taskDeviceMapper.selectCountByQuery(QueryWrapper.create()
                .eq("task_id", taskId)
                .in("status", List.of(ST_PENDING, ST_PUSHED, ST_DOWNLOADING, ST_UPGRADING)));
        if (unfinished != null && unfinished == 0) {
            task.setStatus(T_DONE);
            taskMapper.update(task);
            log.info("OTA 计划完成: {}", taskId);
        }
    }

    // ------------------------------------------------------------ 查询与工具

    public Page<OtaTask> list(long page, long pageSize) {
        return taskMapper.paginate(page, pageSize, QueryWrapper.create().orderBy("id", false));
    }

    public Page<OtaTaskDevice> taskDevices(long page, long pageSize, Long taskId, Integer status) {
        QueryWrapper qw = QueryWrapper.create().eq("task_id", taskId);
        if (status != null) {
            qw.eq("status", status);
        }
        return taskDeviceMapper.paginate(page, pageSize, qw.orderBy("id", true));
    }

    public OtaTask requireById(Long taskId) {
        OtaTask t = taskMapper.selectOneById(taskId);
        if (t == null) {
            throw Biz(404, "升级计划不存在: " + taskId);
        }
        return t;
    }

    /** 重推（失败重试入口）。 */
    public boolean retry(Long taskDeviceId) {
        OtaTaskDevice td = taskDeviceMapper.selectOneById(taskDeviceId);
        if (td == null) {
            throw Biz(404, "明细不存在");
        }
        OtaPackage pkg = packageMapper.selectOneById(td.getPackageId());
        return pushOne(td, pkg);
    }

    public void markFailed(OtaTaskDevice td, String reason) {
        td.setStatus(ST_FAILED);
        td.setMessage(reason);
        taskDeviceMapper.update(td);
    }

    /** 计划设备数（明细计数，供列表展示）。 */
    private void refreshCount(Long taskId) {
        Long c = taskDeviceMapper.selectCountByQuery(
                QueryWrapper.create().eq("task_id", taskId));
        OtaTask task = taskMapper.selectOneById(taskId);
        if (task != null) {
            task.setDeviceCount(c == null ? 0 : c.intValue());
            taskMapper.update(task);
        }
    }

    /** 业务异常简写。 */
    private static com.materin.tech.common.exception.BizException Biz(int code, String msg) {
        return new com.materin.tech.common.exception.BizException(code, msg);
    }
}
