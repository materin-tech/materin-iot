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
 * AUTHORS OR COPY holders BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.gateway.connector.modbus;

import java.nio.ByteBuffer;

/**
 * Modbus 寄存器字节 → 遥测值 编解码（纯函数，可单测）。
 * 支持数据类型 int16 / uint16 / int32 / float32，可选 scale 缩放。
 */
public final class ModbusValueCodec {

    private ModbusValueCodec() {
    }

    /** 是否占两个寄存器（32 位类型） */
    public static boolean isWide(String dataType) {
        return "int32".equalsIgnoreCase(dataType) || "float32".equalsIgnoreCase(dataType);
    }

    /** 把寄存器原始字节（大端）按 dataType 解码并乘 scale */
    public static double decode(byte[] registers, String dataType, double scale) {
        ByteBuffer buffer = ByteBuffer.wrap(registers);
        double raw;
        raw = switch (dataType == null ? "" : dataType.toLowerCase()) {
            case "int16" -> buffer.getShort();
            case "uint16" -> buffer.getShort() & 0xFFFF;
            case "int32" -> buffer.getInt();
            case "float32" -> buffer.getFloat();
            default -> throw new IllegalArgumentException("不支持的 dataType: " + dataType);
        };
        return raw * scale;
    }

    /** 把遥测值编码为单寄存器 16 位值（int16/uint16 用），默认四舍五入 */
    public static int encodeRegister16(double value) {
        return (int) Math.rint(value) & 0xFFFF;
    }

    /** 把遥测值编码为双寄存器字节数组（int32/float32 用，大端） */
    public static byte[] encodeWide(double value, String dataType) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        if ("float32".equalsIgnoreCase(dataType)) {
            buffer.putFloat((float) value);
        } else {
            buffer.putInt((int) Math.rint(value));
        }
        return buffer.array();
    }

    /** 线圈读取响应字节 → boolean（首字节最低位为首个线圈） */
    public static boolean decodeCoil(byte[] coilBytes) {
        return coilBytes != null && coilBytes.length > 0 && (coilBytes[0] & 0x01) != 0;
    }

    /** 线圈写值 */
    public static boolean encodeCoil(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }
}
