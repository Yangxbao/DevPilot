package com.devpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DevPilot 后端入口。
 * Mapper 接口通过 @Mapper 注解由 MyBatis-Plus starter 自动扫描注册。
 */
@SpringBootApplication
public class DevPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevPilotApplication.class, args);
    }
}
