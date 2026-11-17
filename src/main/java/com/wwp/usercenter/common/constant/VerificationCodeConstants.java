package com.wwp.usercenter.common.constant;

import java.time.Duration;

/** 验证码的 Redis 键、有效期与限发规则。键格式与 docs/plan.md §8.2 保持一致。 */
public final class VerificationCodeConstants {

  public static final String CODE_KEY_PREFIX = "verify:";
  public static final String COOLDOWN_KEY_PREFIX = "verify:cooldown:";
  public static final String DAILY_KEY_PREFIX = "verify:daily:";
  public static final String IP_DAILY_KEY_PREFIX = "verify:ip:daily:";
  public static final String FAIL_KEY_PREFIX = "verify:fail:";

  /** 验证码位数；生成宽度与入参正则都从这里派生。 */
  public static final int CODE_LENGTH = 6;

  /** 验证码取值上界，即 10^CODE_LENGTH。 */
  public static final int CODE_BOUND = (int) Math.pow(10, CODE_LENGTH);

  /** 生成验证码时补零用的格式串。 */
  public static final String CODE_FORMAT = "%0" + CODE_LENGTH + "d";

  /** 验证码有效期。 */
  public static final Duration CODE_TTL = Duration.ofMinutes(5);

  /** 同邮箱发码冷却时间。 */
  public static final Duration COOLDOWN = Duration.ofSeconds(60);

  /** 同邮箱每日发码上限。 */
  public static final int DAILY_LIMIT = 10;

  /** 同 IP 每日发码上限；高于邮箱维度，避免换邮箱刷同一出口。 */
  public static final int IP_DAILY_LIMIT = 50;

  /** 单个验证码允许的最大错误次数；达到后作废验证码，必须重新获取。 */
  public static final int FAIL_LIMIT = 5;

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

  public static String ipDailyKey(String ip, String date) {
    return IP_DAILY_KEY_PREFIX + ip + ":" + date;
  }

  public static String failKey(String sceneCode, String email) {
    return FAIL_KEY_PREFIX + sceneCode + ":" + email;
  }
}
