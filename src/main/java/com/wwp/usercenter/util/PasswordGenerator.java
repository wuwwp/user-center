package com.wwp.usercenter.util;

import java.security.SecureRandom;

/**
 * 管理员重置密码用的随机密码生成器。
 *
 * <p>排除易混淆字符（I/O/l/0/1），保证至少一个字母和一个数字，满足 `PASSWORD_REGEXP`； 生成结果只返回一次，不写日志。
 */
public final class PasswordGenerator {

  private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
  private static final String DIGITS = "23456789";
  private static final String SYMBOLS = "!@#$%^&*";
  private static final String ALL = LETTERS + DIGITS + SYMBOLS;
  private static final SecureRandom RANDOM = new SecureRandom();

  private PasswordGenerator() {}

  public static String generate(int length) {
    if (length < 2) {
      throw new IllegalArgumentException("密码长度至少为 2");
    }
    char[] password = new char[length];
    password[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
    password[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
    for (int i = 2; i < length; i++) {
      password[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
    }
    for (int i = length - 1; i > 0; i--) {
      int j = RANDOM.nextInt(i + 1);
      char swap = password[i];
      password[i] = password[j];
      password[j] = swap;
    }
    return new String(password);
  }
}
