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
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivDES;
import com.materin.tech.common.spi.DfxAlertEvent;
import com.materin.tech.common.spi.DfxAlertSink;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SNMP v2c/v3 轮询接入实现（dfx-access-snmp，docs/dfx-monitoring-design.md §4.3）：
 * 每 15s 扫描到期目标，GET UCD-SNMP-MIB 的 CPU/内存/负载与 sysUpTime，
 * 组装 DfxRecord 交统一管道。v3 走 USM（noAuthNoPriv/authNoPriv/authPriv）。
 * 连续失败 ≥3 次产生 snmp_poll 告警（同目标活跃告警合并），成功即自动恢复。
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

    /** 连续失败告警阈值 */
    static final int FAIL_ALERT_THRESHOLD = 3;

    private final DfxSnmpTargetMapper targetMapper;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<DfxIngestService> ingest;
    private final ObjectProvider<DfxAlertSink> alertSink;
    private final Map<Long, Long> nextDueAt = new ConcurrentHashMap<>();
    private final Map<Long, Integer> failStreak = new ConcurrentHashMap<>();
    private Snmp snmp;
    private USM usm;
    private volatile long lastPollAt = 0L;
    private volatile boolean lastPollOk = false;

    public DfxSnmpPoller(DfxSnmpTargetMapper targetMapper,
                         ObjectProvider<DeviceCredentialLookup> credentialLookup,
                         ObjectProvider<DfxIngestService> ingest,
                         ObjectProvider<DfxAlertSink> alertSink) {
        this.targetMapper = targetMapper;
        this.credentialLookup = credentialLookup;
        this.ingest = ingest;
        this.alertSink = alertSink;
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
                onPollSuccess(t);
            } catch (Exception e) {
                lastPollAt = System.currentTimeMillis();
                lastPollOk = false;
                onPollFailure(t, e);
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
        ensureSnmp();
        Target<?> target = buildTarget(t);
        if (target == null) {
            log.debug("SNMP 目标配置不完整（跳过）: {}", t.getHost());
            return;
        }

        // v3 (MPv3) requires ScopedPDU; plain PDU is rejected at send time
        PDU pdu = "v3".equalsIgnoreCase(t.getVersion()) ? new ScopedPDU() : new PDU();
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

    /** 懒初始化 Snmp 传输与 USM 安全模型（仅一次；poll 与 buildTarget 共用，避免 usm 未注册 NPE）。 */
    private synchronized void ensureSnmp() throws Exception {
        if (snmp != null) {
            return;
        }
        SecurityProtocols.getInstance().addDefaultProtocols();
        usm = new USM(SecurityProtocols.getInstance(),
                new OctetString(MPv3.createLocalEngineID()), 0);
        usm.setEngineDiscoveryEnabled(true);
        SecurityModels.getInstance().addSecurityModel(usm);
        snmp = new Snmp(new DefaultUdpTransportMapping());
        snmp.listen();
    }

    /** 按目标版本构造 Target：v3 USM（协议缺省降级）或 v2c 团体字。 */
    private Target<?> buildTarget(DfxSnmpTarget t) throws Exception {
        ensureSnmp();
        boolean v3 = "v3".equalsIgnoreCase(t.getVersion());
        if (!v3) {
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(t.getCommunity() == null ? "public" : t.getCommunity()));
            target.setAddress(new UdpAddress(t.getHost() + "/" + t.getPort()));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(1500);
            target.setRetries(1);
            return target;
        }
        if (t.getV3User() == null || t.getV3User().isBlank()) {
            return null;
        }
        boolean hasAuth = t.getV3AuthProtocol() != null && !t.getV3AuthProtocol().isBlank()
                && t.getV3AuthPassphrase() != null && !t.getV3AuthPassphrase().isBlank();
        boolean hasPriv = t.getV3PrivProtocol() != null && !t.getV3PrivProtocol().isBlank()
                && t.getV3PrivPassphrase() != null && !t.getV3PrivPassphrase().isBlank();
        OID authProto = null;
        if (hasAuth) {
            authProto = "SHA".equalsIgnoreCase(t.getV3AuthProtocol()) ? AuthSHA.ID : AuthMD5.ID;
        }
        OID privProto = null;
        if (hasPriv) {
            privProto = "AES128".equalsIgnoreCase(t.getV3PrivProtocol()) ? PrivAES128.ID : PrivDES.ID;
        }
        // 注册 USM 用户（同用户名不同密钥的多目标场景不适用，v1 已知边界）
        usm.addUser(new UsmUser(new OctetString(t.getV3User()), authProto,
                hasAuth ? new OctetString(t.getV3AuthPassphrase()) : null,
                privProto,
                hasPriv ? new OctetString(t.getV3PrivPassphrase()) : null));
        UserTarget target = new UserTarget();
        target.setAddress(new UdpAddress(t.getHost() + "/" + t.getPort()));
        target.setSecurityModel(SecurityModel.SECURITY_MODEL_USM);
        target.setSecurityName(new OctetString(t.getV3User()));
        target.setSecurityLevel(hasAuth ? (hasPriv ? SecurityLevel.AUTH_PRIV : SecurityLevel.AUTH_NOPRIV)
                : SecurityLevel.NOAUTH_NOPRIV);
        target.setVersion(SnmpConstants.version3);
        target.setTimeout(1500);
        target.setRetries(1);
        return target;
    }

    /** 轮询成功：清零失败连击，恢复在案的 snmp_poll 告警。 */
    private void onPollSuccess(DfxSnmpTarget t) {
        failStreak.remove(t.getDeviceId());
        DfxAlertSink sink = alertSink.getIfAvailable();
        if (sink == null) {
            return;
        }
        try {
            sink.clear(null, "snmp_poll", t.getDeviceId(), "SNMP 轮询恢复");
        } catch (Exception ignore) {
            // 告警清理失败不影响轮询
        }
    }

    /** 轮询失败：连击达阈值产生告警（同目标活跃告警合并累加，不重复通知）。 */
    private void onPollFailure(DfxSnmpTarget t, Exception cause) {
        int streak = failStreak.merge(t.getDeviceId(), 1, Integer::sum);
        if (streak < FAIL_ALERT_THRESHOLD) {
            return;
        }
        DfxAlertSink sink = alertSink.getIfAvailable();
        if (sink == null) {
            return;
        }
        try {
            sink.raise(new DfxAlertEvent(null, t.getDeviceId(), t.getDeviceName(), "warn",
                    "snmp_poll", String.valueOf(streak),
                    String.format("SNMP 轮询连续失败 %d 次（host=%s，最近错误：%s）",
                            streak, t.getHost(), cause.getMessage())));
        } catch (Exception ignore) {
            // 告警落库失败不影响轮询
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
