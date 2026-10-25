package com.devpilot.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * devpilot.* 配置项集中绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "devpilot")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Ai ai = new Ai();
    private Cors cors = new Cors();
    private Admin admin = new Admin();
    private Login login = new Login();

    @Data
    public static class Jwt {
        private String secret;
        private long expireMinutes = 120;
    }

    @Data
    public static class Storage {
        private String path = "./data/storage";
    }

    @Data
    public static class Ai {
        private String baseUrl;
        private String internalToken;
        private int searchTimeoutSeconds = 15;
        private int agentTimeoutSeconds = 150;
    }

    @Data
    public static class Cors {
        private String origins = "http://localhost:5173";
    }

    @Data
    public static class Admin {
        private String bootstrapUsername = "admin";
        private String bootstrapPassword = "admin123456";
    }

    @Data
    public static class Login {
        private int maxFailAttempts = 5;
        private int lockMinutes = 10;
    }
}
