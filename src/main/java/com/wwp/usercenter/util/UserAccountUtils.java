package com.wwp.usercenter.util;

import java.security.SecureRandom;

/** 生成对外展示的用户标识：`u` + 8 位小写字母数字，不参与登录。 */
public final class UserAccountUtils {

  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int SUFFIX_LENGTH = 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  private UserAccountUtils() {}

  public static String generate() {
    StringBuilder builder = new StringBuilder(SUFFIX_LENGTH + 1).append('u');
    for (int i = 0; i < SUFFIX_LENGTH; i++) {
      builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return builder.toString();
  }
}
