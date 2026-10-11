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
package com.materin.gateway.connector.modbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ModbusValueCodec 编解码纯函数单测（不依赖网络）。
 */
class ModbusValueCodecTest {

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    @Test
    @DisplayName("int16 正负数解码 + scale")
    void decodesInt16WithScale() {
        assertThat(round2(ModbusValueCodec.decode(new byte[]{0x00, 0x7B}, "int16", 0.1))).isEqualTo(12.3);
        assertThat(round2(ModbusValueCodec.decode(new byte[]{(byte) 0xFF, (byte) 0x9C}, "int16", 0.1))).isEqualTo(-10.0);
    }

    @Test
    @DisplayName("uint16 高位字节解码")
    void decodesUint16() {
        assertThat(ModbusValueCodec.decode(new byte[]{(byte) 0xFF, (byte) 0x9C}, "uint16", 1.0)).isEqualTo(65436);
    }

    @Test
    @DisplayName("int32/float32 双寄存器编解码")
    void decodesWideTypes() {
        assertThat(ModbusValueCodec.decode(new byte[]{0x00, 0x00, 0x01, (byte) 0xF4}, "int32", 1.0)).isEqualTo(500);
        assertThat(ModbusValueCodec.decode(new byte[]{0x3F, (byte) 0xC0, 0x00, 0x00}, "float32", 1.0)).isEqualTo(1.5);
        assertThat(round2(ModbusValueCodec.decode(new byte[]{0x3F, (byte) 0xC0, 0x00, 0x00}, "float32", 10.0))).isEqualTo(15.0);
    }

    @Test
    @DisplayName("encodeRegister16 / encodeWide 编码")
    void encodesValues() {
        assertThat(ModbusValueCodec.encodeRegister16(100.6)).isEqualTo(101);
        assertThat(ModbusValueCodec.encodeRegister16(-1) == 0xFFFF).isTrue();
        byte[] wide = ModbusValueCodec.encodeWide(1.5, "float32");
        assertThat(wide).isEqualTo(new byte[]{0x3F, (byte) 0xC0, 0x00, 0x00});
        assertThat(ModbusValueCodec.encodeWide(500, "int32")).isEqualTo(new byte[]{0x00, 0x00, 0x01, (byte) 0xF4});
    }

    @Test
    @DisplayName("不支持的 dataType 抛 IllegalArgumentException")
    void rejectsUnknownDataType() {
        assertThatThrownBy(() -> ModbusValueCodec.decode(new byte[]{0x00, 0x00}, "int64", 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decodeCoil/encodeCoil 线圈编解码")
    void handlesCoils() {
        assertThat(ModbusValueCodec.decodeCoil(new byte[]{0x01})).isTrue();
        assertThat(ModbusValueCodec.decodeCoil(new byte[]{0x00})).isFalse();
        assertThat(ModbusValueCodec.encodeCoil(Boolean.TRUE)).isTrue();
        assertThat(ModbusValueCodec.encodeCoil(0)).isFalse();
        assertThat(ModbusValueCodec.encodeCoil(1)).isTrue();
        assertThat(ModbusValueCodec.encodeCoil("true")).isTrue();
        assertThat(ModbusValueCodec.encodeCoil("false")).isFalse();
    }
}
