package com.wwp.usercenter.util;

import java.security.SecureRandom;

/**
 * 管理员重置密码用的随机密码生成器。
 *
 * <p>排除易混淆字符（I/O/l/0/1），保证至少一个字母和一个数字，满足 `PASSWORD_REGEXP`； 生成结果只返回一次，不写日志。
 */
public final class PasswordGenerator {

  private static final char[] LETTERS =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
  private static final char[] DIGITS = "23456789".toCharArray();
  private static final char[] SYMBOLS = "!@#$%^&*".toCharArray();
  private static final char[] ALL = merge(LETTERS, DIGITS, SYMBOLS);
  private static final SecureRandom RANDOM = new SecureRandom();

  private PasswordGenerator() {}

  public static String generate(int length) {
    if (length < 2) {
      throw new IllegalArgumentException("密码长度至少为 2");
    }
    char[] password = new char[length];
    password[0] = LETTERS[RANDOM.nextInt(LETTERS.length)];
    password[1] = DIGITS[RANDOM.nextInt(DIGITS.length)];
    for (int i = 2; i < length; i++) {
      password[i] = ALL[RANDOM.nextInt(ALL.length)];
    }
    for (int i = length - 1; i > 0; i--) {
      int j = RANDOM.nextInt(i + 1);
      char swap = password[i];
      password[i] = password[j];
      password[j] = swap;
    }
    return new String(password);
  }

  private static char[] merge(char[]... parts) {
    int size = 0;
    for (char[] part : parts) {
      size += part.length;
    }
    char[] result = new char[size];
    int offset = 0;
    for (char[] part : parts) {
      System.arraycopy(part, 0, result, offset, part.length);
      offset += part.length;
    }
    return result;
  }
}
