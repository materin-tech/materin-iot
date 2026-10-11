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
package com.materin.tech.system.openapi.spi;

import com.materin.tech.common.spi.OpenAppAuthChecker;
import com.materin.tech.system.openapi.service.OpenAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** AK/SK 校验 SPI 实现。 */
@Component
@RequiredArgsConstructor
public class OpenAppAuthCheckerImpl implements OpenAppAuthChecker {

    private final OpenAppService appService;

    @Override
    public boolean existsByAppKey(String appKey) {
        return appService.existsByAppKey(appKey);
    }

    @Override
    public boolean matches(String appKey, String appSecret) {
        return appService.matches(appKey, appSecret);
    }
}
