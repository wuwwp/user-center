package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.validation.EmailCode;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountParam(@NotBlank(message = "验证码不能为空") @EmailCode String code) {}
