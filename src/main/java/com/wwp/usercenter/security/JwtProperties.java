package com.wwp.usercenter.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT 配置。secret 为 Base64 编码的随机字节，解码后至少 256 位；只允许来自环境变量或本地配置。 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {}
