package com.wwp.usercenter.util;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis 计数器：原子自增，并在首次返回 1 时设置 TTL。验证码、锁定与限流共用。 */
public final class RedisCounterUtils {

  private RedisCounterUtils() {}

  /**
   * 自增计数键。
   *
   * @return 自增后的计数；Redis 返回 null 时原样返回，由调用方决定如何处理
   */
  public static Long increment(StringRedisTemplate redisTemplate, String key, Duration ttl) {
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, ttl);
    }
    return count;
  }
}
