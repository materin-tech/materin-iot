package com.materin.tech.component.dfx.access.snmp;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** DFX SNMP 轮询目标（Linux 网关/主机类设备）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("dfx_snmp_target")
public class DfxSnmpTarget extends BaseEntity {

    private Long deviceId;

    private String deviceName;

    private String host;

    private Integer port;

    /** v2c（v3 二期） */
    private String version;

    private String community;

    /** 轮询间隔（秒），最小 10 */
    private Integer intervalSeconds;

    /** 1-启用 0-停用 */
    private Integer enabled;

    private String remark;
}
