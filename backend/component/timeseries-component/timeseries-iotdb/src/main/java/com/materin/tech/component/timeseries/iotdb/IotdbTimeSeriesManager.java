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
package com.materin.tech.component.timeseries.iotdb;

import com.materin.tech.component.timeseries.CachedTimeSeriesManager;
import com.materin.tech.component.timeseries.TimeSeriesMetadata;
import com.materin.tech.component.timeseries.TimeSeriesService;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.session.pool.SessionPool;

import java.util.concurrent.atomic.AtomicBoolean;

/** IoTDB 时序管理器：懒建数据库，按模型缓存服务实例。 */
@Slf4j
public class IotdbTimeSeriesManager extends CachedTimeSeriesManager {

    private final SessionPool sessionPool;
    private final String database;
    private final AtomicBoolean databaseReady = new AtomicBoolean(false);

    public IotdbTimeSeriesManager(SessionPool sessionPool, String database) {
        this.sessionPool = sessionPool;
        this.database = database;
    }

    @Override
    protected TimeSeriesService createService(TimeSeriesMetadata metadata) {
        return new IotdbTimeSeriesService(sessionPool, database, metadata, this::ensureDatabase);
    }

    @Override
    public void remove(String metric, String modelId, String id) {
        String path = database + "." + IotdbPathBuilder.modelNode(modelId)
                + "." + IotdbPathBuilder.deviceNode(id);
        try {
            ensureDatabase();
            sessionPool.executeNonQueryStatement("DELETE TIMESERIES " + path + ".*");
        } catch (Exception e) {
            log.warn("清理设备时序失败: path={}", path, e);
        }
    }

    /** 建库（幂等：并发/重复建库的 already-exists 静默吞掉）。 */
    void ensureDatabase() {
        if (databaseReady.get()) {
            return;
        }
        try {
            sessionPool.executeNonQueryStatement("CREATE DATABASE " + database);
            log.info("IoTDB 数据库已创建: {}", database);
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                log.warn("IoTDB 建库失败（将继续尝试）: {}", e.getMessage());
            }
        }
        databaseReady.set(true);
    }

    /** IoTDB 无 CREATE IF NOT EXISTS：靠错误消息识别已存在（2.0.11 实测）。 */
    static boolean isAlreadyExists(Throwable e) {
        String message = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("already");  // "already exist" / "has already been created as database"
    }
}
