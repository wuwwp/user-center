package com.wwp.usercenter.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS 白名单；支持逗号分隔配置与环境变量。 */
@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(List<String> allowedOrigins) {}
