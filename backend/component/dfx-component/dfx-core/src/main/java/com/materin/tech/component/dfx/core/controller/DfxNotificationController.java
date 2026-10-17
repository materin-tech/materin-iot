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
package com.materin.tech.component.dfx.core.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.component.dfx.core.entity.DfxNotification;
import com.materin.tech.component.dfx.core.mapper.DfxNotificationMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** DFX 站内通知接口：/api/v1/device/dfx/notification（通知铃铛数据源）。 */
@Tag(name = "DFX 站内通知")
@RestController
@RequestMapping("/device/dfx/notification")
@RequiredArgsConstructor
public class DfxNotificationController {

    private final DfxNotificationMapper notificationMapper;

    @Operation(summary = "通知列表")
    @GetMapping("/list")
    public R<PageResult<DfxNotification>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Integer unreadOnly) {
        QueryWrapper qw = QueryWrapper.create();
        if (unreadOnly != null && unreadOnly == 1) {
            qw.eq("read_flag", 0);
        }
        Page<DfxNotification> p = notificationMapper.paginate(page, pageSize, qw.orderBy("id", false));
        return R.ok(new PageResult<>(p.getRecords(), p.getTotalRow()));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        Long c = notificationMapper.selectCountByQuery(QueryWrapper.create().eq("read_flag", 0));
        return R.ok(c == null ? 0L : c);
    }

    @Operation(summary = "标记已读")
    @PutMapping("/read/{id}")
    public R<Void> read(@PathVariable Long id) {
        DfxNotification n = notificationMapper.selectOneById(id);
        if (n != null) {
            n.setReadFlag(1);
            notificationMapper.update(n);
        }
        return R.ok(null);
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public R<Void> readAll() {
        DfxNotification patch = new DfxNotification();
        patch.setReadFlag(1);
        notificationMapper.updateByQuery(patch, QueryWrapper.create().eq("read_flag", 0));
        return R.ok(null);
    }
}
