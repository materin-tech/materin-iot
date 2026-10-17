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

import com.materin.tech.common.core.R;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.dfx.core.DfxNotifyService;
import com.materin.tech.component.dfx.core.entity.DfxNotifyChannel;
import com.materin.tech.component.dfx.core.service.DfxNotifyChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DFX 通知渠道管理接口：/api/v1/device/dfx/notify/channel。 */
@Tag(name = "DFX 通知渠道")
@RestController
@RequestMapping("/device/dfx/notify/channel")
@RequiredArgsConstructor
public class DfxNotifyChannelController {

    private final DfxNotifyChannelService channelService;
    private final DfxNotifyService notifyService;

    @Operation(summary = "渠道列表")
    @GetMapping("/list")
    public R<java.util.List<DfxNotifyChannel>> list() {
        return R.ok(channelService.list());
    }

    /** 测试发送（同步执行，失败抛业务异常，前端直接展示结果） */
    @Operation(summary = "测试发送")
    @PostMapping("/{id}/test")
    public R<String> test(@PathVariable Long id) {
        notifyService.testSend(id);
        return R.ok("测试消息已发送");
    }

    @Operation(summary = "创建渠道")
    @PostMapping
    public R<DfxNotifyChannel> create(@RequestBody DfxNotifyChannel channel) {
        return R.ok(channelService.create(channel));
    }

    @Operation(summary = "更新渠道")
    @PutMapping("/{id}")
    public R<DfxNotifyChannel> update(@PathVariable Long id, @RequestBody DfxNotifyChannel patch) {
        return R.ok(channelService.update(id, patch));
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        channelService.delete(id);
        return R.ok(null);
    }
}
