package com.wwp.usercenter.common.constant;

import java.time.Duration;

/**
 * 登录失败锁定规则。账号维度挡住针对单个账号的撞库，IP 维度挡住换账号的分布式尝试。
 *
 * <p>IP 阈值明显高于账号阈值，避免同一 NAT/办公网出口的正常用户被少量失败误伤。
 */
public final class LoginGuardConstants {

  public static final String ACCOUNT_FAIL_KEY_PREFIX = "login:fail:account:";
  public static final String IP_FAIL_KEY_PREFIX = "login:fail:ip:";

  /** 同一账号连续失败上限。 */
  public static final int ACCOUNT_FAIL_LIMIT = 5;

  /** 同一 IP 连续失败上限。 */
  public static final int IP_FAIL_LIMIT = 50;

  /** 账号与 IP 维度的锁定时长；计数键的 TTL 与锁定窗口一致。 */
  public static final Duration LOCK_TTL = Duration.ofMinutes(15);

  private LoginGuardConstants() {}

  public static String accountFailKey(String email) {
    return ACCOUNT_FAIL_KEY_PREFIX + email;
  }

  public static String ipFailKey(String ip) {
    return IP_FAIL_KEY_PREFIX + ip;
  }
}
