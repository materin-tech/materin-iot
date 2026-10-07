package com.materin.tech.component.dfx.access.snmp;

import com.materin.tech.common.core.R;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** SNMP 轮询目标 CRUD（dfx-access-snmp 协议自有配置）。 */
@Tag(name = "DFX SNMP 轮询目标")
@RestController
@RequestMapping("/device/dfx/target")
@RequiredArgsConstructor
public class DfxSnmpTargetController {

    private final DfxSnmpTargetService targetService;

    @Operation(summary = "SNMP 轮询目标列表")
    @GetMapping("/list")
    public R<Page<DfxSnmpTarget>> list(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) Long deviceId) {
        return R.ok(targetService.list(page, pageSize, deviceId));
    }

    @Operation(summary = "创建 SNMP 轮询目标")
    @PostMapping
    public R<DfxSnmpTarget> create(@RequestBody DfxSnmpTarget target) {
        return R.ok(targetService.create(target));
    }

    @Operation(summary = "更新 SNMP 轮询目标")
    @PutMapping("/{id}")
    public R<DfxSnmpTarget> update(@PathVariable Long id, @RequestBody DfxSnmpTarget patch) {
        return R.ok(targetService.update(id, patch));
    }

    @Operation(summary = "删除 SNMP 轮询目标")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        targetService.delete(id);
        return R.ok(null);
    }
}
