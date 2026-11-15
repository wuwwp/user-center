package com.wwp.usercenter.util;

import java.util.Locale;

/** 邮箱规范化与脱敏。规范化保证唯一索引与 Redis 键大小写一致。 */
public final class EmailUtils {

  private EmailUtils() {}

  public static String normalize(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  /** 日志与提示用：`a***@example.com`。 */
  public static String mask(String email) {
    if (email == null || email.isBlank()) {
      return "***";
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(at);
  }
}
