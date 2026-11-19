package com.wwp.usercenter.common.constant;

/** 入参校验规则。集中定义，避免注解里的正则与边界散落各处。 */
public final class ValidationConstants {

  public static final int PASSWORD_MIN_LENGTH = 8;
  public static final int PASSWORD_MAX_LENGTH = 64;

  /** 8~64 位可见 ASCII 字符，且至少包含一个字母与一个数字；不允许空格。 */
  public static final String PASSWORD_REGEXP =
      "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{" + PASSWORD_MIN_LENGTH + "," + PASSWORD_MAX_LENGTH + "}$";

  public static final String PASSWORD_MESSAGE = "密码必须为 8~64 位，且至少包含字母与数字";

  public static final int EMAIL_MAX_LENGTH = 128;

  /** 与 user 表 user_account 列宽一致。 */
  public static final int USER_ACCOUNT_MAX_LENGTH = 32;

  public static final String EMAIL_BLANK_MESSAGE = "邮箱不能为空";
  public static final String EMAIL_FORMAT_MESSAGE = "邮箱格式不正确";
  public static final String EMAIL_MAX_LENGTH_MESSAGE = "邮箱长度不能超过 " + EMAIL_MAX_LENGTH;

  public static final String EMAIL_CODE_REGEXP =
      "^[0-9]{" + VerificationCodeConstants.CODE_LENGTH + "}$";

  public static final String EMAIL_CODE_MESSAGE =
      "验证码为 " + VerificationCodeConstants.CODE_LENGTH + " 位数字";

  private ValidationConstants() {}
}
