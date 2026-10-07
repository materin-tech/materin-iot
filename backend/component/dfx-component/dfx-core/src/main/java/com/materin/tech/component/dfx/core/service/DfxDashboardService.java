package com.materin.tech.component.dfx.core.service;

import com.materin.tech.component.dfx.core.entity.DfxDashboard;
import com.materin.tech.component.dfx.core.mapper.DfxDashboardMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** DFX 产品 Dashboard 服务（upsert by productId+name，布局由 config_json 驱动）。 */
@Service
@RequiredArgsConstructor
public class DfxDashboardService {

    private final DfxDashboardMapper mapper;

    public List<DfxDashboard> listByProduct(Long productId) {
        return mapper.selectListByQuery(
                QueryWrapper.create().eq("product_id", productId).orderBy("id", true));
    }

    /** 按 productId+name 幂等 upsert。 */
    public DfxDashboard save(DfxDashboard dashboard) {
        if (dashboard.getProductId() == null) {
            throw new com.materin.tech.common.exception.BizException(400, "productId 不能为空");
        }
        if (dashboard.getProductName() == null) {
            dashboard.setProductName("");
        }
                if (dashboard.getName() == null || dashboard.getName().isBlank()) {
            dashboard.setName("默认布局");
        }
        DfxDashboard db = mapper.selectOneByQuery(QueryWrapper.create()
                .eq("product_id", dashboard.getProductId())
                .eq("name", dashboard.getName()));
        if (db == null) {
            mapper.insert(dashboard);
            return dashboard;
        }
        db.setConfigJson(dashboard.getConfigJson());
        db.setRemark(dashboard.getRemark());
        mapper.update(db);
        return db;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
