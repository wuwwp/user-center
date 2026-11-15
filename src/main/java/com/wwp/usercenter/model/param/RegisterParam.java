package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.constant.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterParam(
    @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,
    @NotBlank(message = "验证码不能为空")
        @Pattern(
            regexp = ValidationConstants.EMAIL_CODE_REGEXP,
            message = ValidationConstants.EMAIL_CODE_MESSAGE)
        String code,
    @NotBlank(message = "密码不能为空")
        @Pattern(
            regexp = ValidationConstants.PASSWORD_REGEXP,
            message = ValidationConstants.PASSWORD_MESSAGE)
        String password) {}
