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
