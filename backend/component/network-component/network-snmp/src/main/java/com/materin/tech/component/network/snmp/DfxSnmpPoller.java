package com.materin.tech.component.network.snmp;

import com.materin.tech.common.spi.DfxSink;
import com.materin.tech.common.spi.DfxSnmpTargetLookup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.snmp4j.CommunityTarget;
import org.snmp4j.Snmp;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.PDU;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.*;
// TimeTicks 由 smi 包通配导入
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

/**
 * SNMP v2c 轮询器（docs/dfx-monitoring-design.md §4.3）：
 * 每 15s 扫描到期目标，GET UCD-SNMP-MIB 的 CPU/内存/负载与 sysUpTime，
 * 映射为统一指标后交 DfxSink 管道。snmp4j 全程兜底异常。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "materin.dfx.snmp", name = "enabled", havingValue = "true")
public class DfxSnmpPoller {

    /** UCD-SNMP-MIB OID 映射（docs/dfx-monitoring-design.md §4.3） */
    private static final Map<String, String> OIDS = Map.of(
            "ssCpuIdle", "1.3.6.1.4.1.2021.11.11.0",
            "memTotalReal", "1.3.6.1.4.1.2021.4.5.0",
            "memAvailReal", "1.3.6.1.4.1.2021.4.6.0",
            "laLoad.1", "1.3.6.1.4.1.2021.10.1.3.1",
            "sysUpTime", "1.3.6.1.2.1.1.3.0");

    private final ObjectProvider<DfxSnmpTargetLookup> targetLookup;
    private final ObjectProvider<DfxSink> dfxSink;
    /** 目标下次轮询时间（deviceId -> epoch ms） */
    private final Map<Long, Long> nextDueAt = new ConcurrentHashMap<>();
    private Snmp snmp;

    public DfxSnmpPoller(ObjectProvider<DfxSnmpTargetLookup> targetLookup,
                         ObjectProvider<DfxSink> dfxSink) {
        this.targetLookup = targetLookup;
        this.dfxSink = dfxSink;
    }

    /** 每 15s 扫描一次到期目标（各目标按自身 interval 节流）。 */
    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void pollTick() {
        DfxSnmpTargetLookup lookup = targetLookup.getIfAvailable();
        DfxSink sink = dfxSink.getIfAvailable();
        if (lookup == null || sink == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (DfxSnmpTargetLookup.SnmpTarget t : lookup.findEnabled()) {
            if (t.deviceKey() == null) {
                continue;
            }
            Long due = nextDueAt.get(t.deviceId());
            if (due != null && now < due) {
                continue;
            }
            nextDueAt.put(t.deviceId(), now + t.intervalSeconds() * 1000L);
            try {
                poll(t, sink);
            } catch (Exception e) {
                log.warn("SNMP 轮询失败: deviceKey={}, host={}, {}",
                        t.deviceKey(), t.host(), e.getMessage());
            }
        }
    }

    private void poll(DfxSnmpTargetLookup.SnmpTarget t, DfxSink sink) throws Exception {
        if (snmp == null) {
            snmp = new Snmp(new DefaultUdpTransportMapping());
            snmp.listen();
        }
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(t.community()));
        target.setAddress(new UdpAddress(t.host() + "/" + t.port()));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(1500);
        target.setRetries(1);

        PDU pdu = new PDU();
        pdu.setType(PDU.GET);
        OIDS.values().forEach(oid -> pdu.add(new VariableBinding(new OID(oid))));

        ResponseEvent resp = snmp.get(pdu, target);
        if (resp == null || resp.getResponse() == null) {
            log.debug("SNMP 无响应: {}", t.host());
            return;
        }
        Map<String, Object> metrics = mapMetrics(resp.getResponse());
        if (!metrics.isEmpty()) {
            sink.saveDfx(t.deviceKey(), System.currentTimeMillis(), "snmp", metrics);
        }
    }

    /** OID → 统一指标映射（数值解析失败即跳过该项）。 */
    private Map<String, Object> mapMetrics(PDU resp) {
        Map<String, String> values = new HashMap<>();
        for (VariableBinding vb : resp.toArray()) {
            // TimeTicks（sysUpTime）toString 是时间格式串，统一转原始厘秒
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

    /** 0 值多数为 OID 缺失的占位；cpu/load 的 0 是合法值，保留。 */
    private void tryPut(Map<String, Object> m, String k, double v) {
        if (!Double.isNaN(v)) {
            m.put(k, v);
        }
    }
}
