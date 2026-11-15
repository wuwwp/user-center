package com.wwp.usercenter.common.constant;

/** 入参校验规则。集中定义，避免注解里的正则散落各处。 */
public final class ValidationConstants {

  /** 8~64 位可见 ASCII 字符，且至少包含一个字母与一个数字；不允许空格。 */
  public static final String PASSWORD_REGEXP = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$";

  public static final String PASSWORD_MESSAGE = "密码必须为 8~64 位，且至少包含字母与数字";

  public static final String EMAIL_CODE_REGEXP = "^[0-9]{6}$";

  public static final String EMAIL_CODE_MESSAGE = "验证码为 6 位数字";

  private ValidationConstants() {}
}
