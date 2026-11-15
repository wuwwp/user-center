package com.wwp.usercenter.common.constant;

import java.time.Duration;

/** 验证码的 Redis 键、有效期与限发规则。键格式与 docs/plan.md §8.2 保持一致。 */
public final class VerificationCodeConstants {

  public static final String CODE_KEY_PREFIX = "verify:";
  public static final String COOLDOWN_KEY_PREFIX = "verify:cooldown:";
  public static final String DAILY_KEY_PREFIX = "verify:daily:";

  /** 验证码有效期。 */
  public static final Duration CODE_TTL = Duration.ofMinutes(5);

  /** 同邮箱发码冷却时间。 */
  public static final Duration COOLDOWN = Duration.ofSeconds(60);

  /** 同邮箱每日发码上限。 */
  public static final int DAILY_LIMIT = 10;

  /** 每日限发计数键的过期时间，覆盖跨日边界。 */
  public static final Duration DAILY_COUNTER_TTL = Duration.ofHours(26);

  private VerificationCodeConstants() {}

  public static String codeKey(String sceneCode, String email) {
    return CODE_KEY_PREFIX + sceneCode + ":" + email;
  }

  public static String cooldownKey(String email) {
    return COOLDOWN_KEY_PREFIX + email;
  }

  public static String dailyKey(String email, String date) {
    return DAILY_KEY_PREFIX + email + ":" + date;
  }
}
