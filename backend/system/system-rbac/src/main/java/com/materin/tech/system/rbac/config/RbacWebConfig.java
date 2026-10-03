package com.materin.tech.system.rbac.config;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.system.rbac.security.AuthInterceptor;
import com.materin.tech.system.rbac.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** RBAC 模块 Web 配置：注册登录态拦截器。 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class RbacWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 当前仅 RBAC 面需要登录态；component 板块（device 等）待其接入认证后再纳入
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        ApiVersions.V1_PREFIX + "/system/**",
                        ApiVersions.V1_PREFIX + "/user/**",
                        ApiVersions.V1_PREFIX + "/auth/codes",
                        ApiVersions.V1_PREFIX + "/open/**");
    }
}
