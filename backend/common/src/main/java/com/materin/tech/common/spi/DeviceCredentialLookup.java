package com.materin.tech.common.spi;

import java.util.Optional;

/** 跨组件协作 SPI：MQTT 鉴权时查设备凭证（由 device-component 实现）。 */
public interface DeviceCredentialLookup {

    Optional<DeviceCredential> findByKey(String deviceKey);

    record DeviceCredential(String deviceKey, Long deviceId, String secret, String deviceName) {
    }

    /** 记录设备最近在线时间（DB 节流落库），默认空实现便于无设备模块时独立运行。 */
    default void touchOnline(Long deviceId, java.time.LocalDateTime time) {
    }

    /** 按设备 ID 查 deviceKey（下行命令时使用）。 */
    default Optional<String> findKeyById(Long deviceId) {
        return Optional.empty();
    }

    /** 按设备 Key 查所属产品的 productKey（MQTT ACL namespace 校验）。 */
    default Optional<String> findProductKeyByKey(String deviceKey) {
        return Optional.empty();
    }

    /** 按设备 Key 查所属产品 ID（DFX 时序模型维度）。 */
    default Optional<Long> findProductIdByKey(String deviceKey) {
        return Optional.empty();
    }
}
