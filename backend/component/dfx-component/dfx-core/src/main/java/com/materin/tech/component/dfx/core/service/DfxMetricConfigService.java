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
