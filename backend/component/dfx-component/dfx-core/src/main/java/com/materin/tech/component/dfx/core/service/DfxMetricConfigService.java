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
package com.materin.tech.component.dfx.core.service;

import com.materin.tech.component.dfx.core.entity.DfxMetricConfig;
import com.materin.tech.component.dfx.core.mapper.DfxMetricConfigMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** DFX 指标字典服务（自定义指标即配置即纳管）。 */
@Service
@RequiredArgsConstructor
public class DfxMetricConfigService {

    private final DfxMetricConfigMapper mapper;

    public List<DfxMetricConfig> listAll() {
        return mapper.selectListByQuery(QueryWrapper.create().orderBy("id", true));
    }

    /** 按 metric 唯一键幂等保存（新增或更新汉字表述/单位/类型）。 */
    public DfxMetricConfig save(DfxMetricConfig config) {
        if (config.getMetric() == null || config.getMetric().isBlank()) {
            throw new com.materin.tech.common.exception.BizException(400, "metric 不能为空");
        }
        String key = config.getMetric().startsWith("dfx_")
                ? config.getMetric() : "dfx_" + config.getMetric();
        config.setMetric(key);
        DfxMetricConfig db = mapper.selectOneByQuery(
                QueryWrapper.create().eq("metric", config.getMetric()));
        if (db == null) {
            mapper.insert(config);
            return config;
        }
        db.setName(config.getName());
        db.setUnit(config.getUnit());
        db.setDataType(config.getDataType() == null ? "double" : config.getDataType());
        db.setDescription(config.getDescription());
        mapper.update(db);
        return db;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
