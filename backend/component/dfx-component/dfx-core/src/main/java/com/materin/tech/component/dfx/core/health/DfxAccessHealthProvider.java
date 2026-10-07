package com.materin.tech.component.dfx.core.health;

/** 接入子模块健康探针：每个协议实现（mqtt/http/snmp）各提供一个 Bean。 */
public interface DfxAccessHealthProvider {

    DfxAccessHealth health();
}
