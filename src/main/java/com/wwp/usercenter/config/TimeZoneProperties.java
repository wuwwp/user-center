package com.wwp.usercenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 业务日期计算使用的时区。 */
@ConfigurationProperties(prefix = "user-center")
public record TimeZoneProperties(@DefaultValue("Asia/Shanghai") String timeZone) {}
