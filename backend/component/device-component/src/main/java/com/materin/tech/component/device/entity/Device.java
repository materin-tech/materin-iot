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
package com.materin.tech.component.device.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备台账。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("device")
public class Device extends BaseEntity {

    private String name;

    /** 设备唯一标识，用于协议层寻址（MQTT username） */
    private String deviceKey;

    /** 设备密钥（MQTT password），创建时生成 */
    private String secret;

    /** 0 未激活 / 1 在线 / 2 离线 */
    private Integer status;

    /** 所属产品 */
    private Long productId;

    /** 冗余产品名，列表展示用 */
    private String productName;

    /** 设备类型：DIRECT 直连（默认）/ GATEWAY 网关 / CHILD 网关子设备 */
    private String deviceType;

    /** CHILD 类型指向的网关设备 ID，DIRECT/GATEWAY 为空 */
    private Long gatewayId;

    /** 固件版本 */
    private String firmware;

    /** 端侧设备自报信息 JSON（MAC/SN/芯片等 KV，升级上报等链路写入，最新值合并覆盖） */
    private String infoJson;

    /** 最近在线时间 */
    private String lastOnline;

    private String remark;
}
