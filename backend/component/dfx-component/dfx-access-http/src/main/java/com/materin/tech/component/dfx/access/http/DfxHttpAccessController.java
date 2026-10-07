package com.materin.tech.component.dfx.access.http;

import com.materin.tech.common.core.R;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.DfxIngestService;
import com.materin.tech.component.dfx.core.DfxRecord;
import com.materin.tech.component.dfx.core.health.DfxAccessHealth;
import com.materin.tech.component.dfx.core.health.DfxAccessHealthProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DFX HTTP 接入实现（dfx-access-http，docs/dfx-developer-guide.md §3.2）：
 * 设备 secret 鉴权（与 MQTT 认证同一凭证源），支持网关代理批量上报。
 */
@Tag(name = "DFX HTTP 接入")
@RestController
@RequiredArgsConstructor
public class DfxHttpAccessController implements DfxAccessHealthProvider {

    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<DfxIngestService> ingest;
    private final AtomicLong lastReportAt = new AtomicLong();

    @Operation(summary = "DFX 指标上报（HTTP 接入标准，设备 secret 鉴权）")
    @PostMapping("/device/dfx/report")
    public R<Map<String, Object>> report(@RequestBody Map<String, Object> body) {
        String deviceKey = str(body.get("deviceKey"));
        String secret = str(body.get("secret"));
        if (deviceKey == null || secret == null) {
            throw new com.materin.tech.common.exception.BizException(400, "deviceKey/secret 不能为空");
        }
        if (!authenticate(deviceKey, secret)) {
            throw new com.materin.tech.common.exception.BizException(401, "设备凭证校验失败");
        }
        long time = body.get("time") instanceof Number n ? n.longValue() : 0L;
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) body.get("metrics");
        ingest.getIfAvailable().ingest(
                new DfxRecord(deviceKey, time, "http", metrics == null ? Map.of() : metrics));
        lastReportAt.set(System.currentTimeMillis());
        return R.ok(Map.of("accepted", metrics == null ? 0 : metrics.size()));
    }

    @Operation(summary = "DFX 指标批量上报（网关代理，gw 凭证鉴权）")
    @PostMapping("/device/dfx/report/batch")
    public R<Map<String, Object>> reportBatch(@RequestBody Map<String, Object> body) {
        String deviceKey = str(body.get("deviceKey"));
        String secret = str(body.get("secret"));
        if (deviceKey == null || secret == null || !authenticate(deviceKey, secret)) {
            throw new com.materin.tech.common.exception.BizException(401, "设备凭证校验失败");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = (List<Map<String, Object>>) body.get("devices");
        if (devices == null || devices.isEmpty()) {
            throw new com.materin.tech.common.exception.BizException(400, "devices 不能为空");
        }
        int accepted = 0;
        for (Map<String, Object> item : devices) {
            String dk = str(item.get("deviceKey"));
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) item.get("metrics");
            if (dk == null || metrics == null || metrics.isEmpty()) {
                continue;
            }
            long time = item.get("time") instanceof Number n ? n.longValue() : 0L;
            ingest.getIfAvailable().ingest(new DfxRecord(dk, time, "http", metrics));
            accepted++;
        }
        lastReportAt.set(System.currentTimeMillis());
        return R.ok(Map.of("accepted", accepted));
    }

    @Override
    public DfxAccessHealth health() {
        boolean enabled = ingest.getIfAvailable() != null;
        return new DfxAccessHealth("http", true, enabled ? "UP" : "DOWN",
                "REST 端点 /device/dfx/report 内嵌于后端进程",
                lastReportAt.get() == 0 ? null : lastReportAt.get());
    }

    private boolean authenticate(String deviceKey, String secret) {
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return false;
        }
        return lookup.findByKey(deviceKey)
                .map(c -> c.secret() != null && c.secret().equals(secret))
                .orElse(false);
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
