package com.materin.tech;

import com.materin.tech.common.spi.NetworkProtocol;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 启动入口：仅负责装配 system 板块与 component 板块的全部模块。
 */
@Slf4j
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
