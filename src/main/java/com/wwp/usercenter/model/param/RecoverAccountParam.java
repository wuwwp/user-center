package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.validation.EmailCode;
import com.wwp.usercenter.common.validation.StrongPassword;
import com.wwp.usercenter.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

public record RecoverAccountParam(
    @ValidEmail String email,
    @NotBlank(message = "验证码不能为空") @EmailCode String code,
    @NotBlank(message = "新密码不能为空") @StrongPassword String newPassword) {}
