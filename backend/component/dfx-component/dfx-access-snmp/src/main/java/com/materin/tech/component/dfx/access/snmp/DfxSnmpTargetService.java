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
package com.materin.tech.component.dfx.access.snmp;

import com.materin.tech.component.dfx.access.snmp.DfxSnmpTarget;
import com.materin.tech.component.dfx.access.snmp.DfxSnmpTargetMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** DFX SNMP 轮询目标 CRUD。 */
@Service
@RequiredArgsConstructor
public class DfxSnmpTargetService {

    private final DfxSnmpTargetMapper mapper;

    public Page<DfxSnmpTarget> list(long page, long pageSize, Long deviceId) {
        QueryWrapper qw = QueryWrapper.create();
        if (deviceId != null) {
            qw.eq("device_id", deviceId);
        }
        return mapper.paginate(page, pageSize, qw.orderBy("id", false));
    }

    public DfxSnmpTarget create(DfxSnmpTarget target) {
        if (target.getPort() == null) {
            target.setPort(161);
        }
        if (target.getVersion() == null) {
            target.setVersion("v2c");
        }
        if (target.getCommunity() == null) {
            target.setCommunity("public");
        }
        if (target.getIntervalSeconds() == null) {
            target.setIntervalSeconds(60);
        }
        if (target.getEnabled() == null) {
            target.setEnabled(1);
        }
        mapper.insert(target);
        return target;
    }

    public DfxSnmpTarget update(Long id, DfxSnmpTarget patch) {
        DfxSnmpTarget db = requireById(id);
        if (patch.getHost() != null) {
            db.setHost(patch.getHost());
        }
        if (patch.getPort() != null) {
            db.setPort(patch.getPort());
        }
        if (patch.getVersion() != null) {
            db.setVersion(patch.getVersion());
        }
        if (patch.getCommunity() != null) {
            db.setCommunity(patch.getCommunity());
        }
        if (patch.getIntervalSeconds() != null) {
            db.setIntervalSeconds(patch.getIntervalSeconds());
        }
        if (patch.getEnabled() != null) {
            db.setEnabled(patch.getEnabled());
        }
        if (patch.getRemark() != null) {
            db.setRemark(patch.getRemark());
        }
        mapper.update(db);
        return db;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private DfxSnmpTarget requireById(Long id) {
        DfxSnmpTarget db = mapper.selectOneById(id);
        if (db == null) {
            throw new com.materin.tech.common.exception.BizException(404, "目标不存在: " + id);
        }
        return db;
    }
}
