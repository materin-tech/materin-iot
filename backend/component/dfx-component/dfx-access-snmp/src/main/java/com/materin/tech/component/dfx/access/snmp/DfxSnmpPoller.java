package com.materin.tech.component.dfx.access.snmp;

import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.DfxIngestService;
import com.materin.tech.component.dfx.core.DfxRecord;
import com.materin.tech.component.dfx.core.health.DfxAccessHealth;
import com.materin.tech.component.dfx.core.health.DfxAccessHealthProvider;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SNMP v2c 轮询接入实现（dfx-access-snmp，docs/dfx-monitoring-design.md §4.3）：
 * 每 15s 扫描到期目标，GET UCD-SNMP-MIB 的 CPU/内存/负载与 sysUpTime，
 * 组装 DfxRecord 交统一管道。snmp4j 全程兜底异常。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "materin.dfx.access.snmp", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DfxSnmpPoller implements DfxAccessHealthProvider {

    /** UCD-SNMP-MIB OID 映射 */
    private static final Map<String, String> OIDS = Map.of(
            "ssCpuIdle", "1.3.6.1.4.1.2021.11.11.0",
            "memTotalReal", "1.3.6.1.4.1.2021.4.5.0",
            "memAvailReal", "1.3.6.1.4.1.2021.4.6.0",
            "laLoad.1", "1.3.6.1.4.1.2021.10.1.3.1",
            "sysUpTime", "1.3.6.1.2.1.1.3.0");

    private final DfxSnmpTargetMapper targetMapper;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<DfxIngestService> ingest;
    private final Map<Long, Long> nextDueAt = new ConcurrentHashMap<>();
    private Snmp snmp;
    private volatile long lastPollAt = 0L;
    private volatile boolean lastPollOk = false;

    public DfxSnmpPoller(DfxSnmpTargetMapper targetMapper,
                         ObjectProvider<DeviceCredentialLookup> credentialLookup,
                         ObjectProvider<DfxIngestService> ingest) {
        this.targetMapper = targetMapper;
        this.credentialLookup = credentialLookup;
        this.ingest = ingest;
    }

    @Override
    public DfxAccessHealth health() {
        Long targets = targetMapper.selectCountByQuery(
                QueryWrapper.create().eq("enabled", 1));
        boolean enabled = targets != null && targets > 0;
        return new DfxAccessHealth("snmp", enabled,
                !enabled ? "DISABLED" : (lastPollAt == 0 ? "UP" : lastPollOk ? "UP" : "DOWN"),
                String.format("启用目标 %d 个，最近轮询 %s",
                        targets == null ? 0 : targets,
                        lastPollAt == 0 ? "尚未执行" : lastPollOk ? "成功" : "失败"),
                lastPollAt == 0 ? null : lastPollAt);
    }

    /** 每 15s 扫描一次到期目标（各目标按自身 interval 节流）。 */
    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void pollTick() {
        DfxIngestService svc = ingest.getIfAvailable();
        if (svc == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<DfxSnmpTarget> targets = targetMapper.selectListByQuery(
                QueryWrapper.create().eq("enabled", 1));
        for (DfxSnmpTarget t : targets) {
            Long due = nextDueAt.get(t.getDeviceId());
            if (due != null && now < due) {
                continue;
            }
            nextDueAt.put(t.getDeviceId(), now + t.getIntervalSeconds() * 1000L);
            try {
                poll(t, svc);
                lastPollAt = System.currentTimeMillis();
                lastPollOk = true;
            } catch (Exception e) {
                lastPollAt = System.currentTimeMillis();
                lastPollOk = false;
                log.warn("SNMP 轮询失败: deviceId={}, host={}, {}",
                        t.getDeviceId(), t.getHost(), e.getMessage());
            }
        }
    }

    private void poll(DfxSnmpTarget t, DfxIngestService svc) throws Exception {
        String deviceKey = credentialLookup.getIfAvailable() == null
                ? null
                : credentialLookup.getIfAvailable().findKeyById(t.getDeviceId()).orElse(null);
        if (deviceKey == null) {
            return;
        }
        if (snmp == null) {
            snmp = new Snmp(new DefaultUdpTransportMapping());
            snmp.listen();
        }
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(t.getCommunity()));
        target.setAddress(new UdpAddress(t.getHost() + "/" + t.getPort()));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(1500);
        target.setRetries(1);

        PDU pdu = new PDU();
        pdu.setType(PDU.GET);
        OIDS.values().forEach(oid -> pdu.add(new VariableBinding(new OID(oid))));

        ResponseEvent resp = snmp.get(pdu, target);
        if (resp == null || resp.getResponse() == null) {
            log.debug("SNMP 无响应: {}", t.getHost());
            return;
        }
        Map<String, Object> metrics = mapMetrics(resp.getResponse());
        if (!metrics.isEmpty()) {
            svc.ingest(new DfxRecord(deviceKey, System.currentTimeMillis(), "snmp", metrics));
        }
    }

    /** OID → 统一指标映射（OID 缺失时跳过，避免误报 0/100）。 */
    private Map<String, Object> mapMetrics(PDU resp) {
        Map<String, String> values = new HashMap<>();
        for (VariableBinding vb : resp.toArray()) {
            values.put(vb.getOid().toDottedString(),
                    vb.getVariable() instanceof TimeTicks tt
                            ? String.valueOf(tt.getValue()) : vb.getVariable().toString());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        String idleRaw = values.get(OIDS.get("ssCpuIdle"));
        if (idleRaw != null) {
            tryPut(m, "cpu_usage_pct", calc(100 - num(idleRaw)));
        }
        double total = num(values.get(OIDS.get("memTotalReal")));
        double avail = num(values.get(OIDS.get("memAvailReal")));
        if (total > 0) {
            tryPut(m, "mem_usage_pct", calc((total - avail) / total * 100));
        }
        String loadRaw = values.get(OIDS.get("laLoad.1"));
        if (loadRaw != null) {
            tryPut(m, "load_1m", num(loadRaw));
        }
        long uptimeCs = (long) num(values.get(OIDS.get("sysUpTime")));
        if (uptimeCs > 0) {
            tryPut(m, "uptime_s", uptimeCs / 100);
        }
        tryPut(m, "heartbeat", 1L);
        return m;
    }

    private double num(String s) {
        try {
            return Double.parseDouble(s == null ? "0" : s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double calc(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void tryPut(Map<String, Object> m, String k, double v) {
        if (!Double.isNaN(v)) {
            m.put(k, v);
        }
    }
}
