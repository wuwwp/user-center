package com.wwp.usercenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 邮件发件人配置；mode 仍由 @ConditionalOnProperty 读取。 */
@ConfigurationProperties(prefix = "user-center.mail")
public record MailProperties(
    @DefaultValue("") String from, @DefaultValue("用户中心") String fromName) {}
