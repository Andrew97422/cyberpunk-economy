package ru.andrew.mainserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private String jwtSecret;
    private long jwtExpirationSeconds;
    private long pinSessionExpirationSeconds;
    private long adminSessionExpirationSeconds;
    private List<String> publicPaths;
}