package com.wwp.usercenter.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一业务时钟：日期类逻辑按配置时区计算，不依赖容器默认时区（镜像通常为 UTC）。 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock(TimeZoneProperties properties) {
    return Clock.system(ZoneId.of(properties.timeZone()));
  }
}
