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
package com.materin.tech;

import com.materin.tech.common.spi.NetworkProtocol;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 启动入口：仅负责装配 system 板块与 component 板块的全部模块。
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class MaterinApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaterinApplication.class, args);
    }

    /**
     * 显式注册 Mapper 扫描：多模块拆分后 @Mapper 接口位于嵌套 jar 中，
     * starter 的自动扫描覆盖不到，这里显式按注解扫描整个 com.materin.tech 包树。
     */
    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer scanner = new MapperScannerConfigurer();
        scanner.setBasePackage("com.materin.tech");
        scanner.setAnnotationClass(Mapper.class);
        return scanner;
    }

    @Bean
    ApplicationRunner protocolListRunner(List<NetworkProtocol> protocols) {
        return args -> protocols.forEach(p -> {
            p.start();
            log.info("已装配协议组件: {}", p.name());
        });
    }
}
