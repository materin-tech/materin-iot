package com.materin.tech.component.dfx.core.health;

/** 接入组件健康快照（前端"设备运维-接入组件健康"卡片数据源）。 */
public record DfxAccessHealth(
        String name,
        boolean enabled,
        /** UP / DOWN / DISABLED */
        String status,
        String detail,
        Long lastIngestAt) {
}
