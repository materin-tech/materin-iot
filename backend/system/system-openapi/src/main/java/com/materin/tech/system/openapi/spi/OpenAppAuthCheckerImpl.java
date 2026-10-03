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
