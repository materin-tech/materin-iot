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
package com.materin.tech.component.timeseries;

/**
 * 时序管理器统一入口（对标 jetlinks-community TimeSeriesManager）：
 * 按 TimeSeriesMetadata 获取（并缓存）对应模型的存取服务。
 */
public interface TimeSeriesManager {

    /** 获取指定模型的时序服务（同一模型复用同一实例）。 */
    TimeSeriesService getTimeSeries(TimeSeriesMetadata metadata);

    /** 预注册模型（建库/校验列定义），实现方可为空操作（写入时懒建）。 */
    default void register(TimeSeriesMetadata metadata) {
    }

    /** 删除业务主键下的全部时序数据（如设备删除后清理）。 */
    default void remove(String metric, String modelId, String id) {
    }
}
