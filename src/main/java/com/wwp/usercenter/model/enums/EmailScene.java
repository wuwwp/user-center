package com.wwp.usercenter.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 邮箱验证码场景。不同场景使用不同 Redis 键，禁止跨场景复用验证码。 */
public enum EmailScene {
  REGISTER("register", "注册"),
  RESET("reset", "重置密码"),
  RECOVER("recover", "恢复账号");

  private final String code;
  private final String description;

  EmailScene(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  @JsonCreator
  public static EmailScene fromCode(String code) {
    return Arrays.stream(values())
        .filter(scene -> scene.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知的验证码场景: " + code));
  }
}
