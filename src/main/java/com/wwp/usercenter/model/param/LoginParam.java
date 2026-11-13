package com.wwp.usercenter.model.param;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginParam(
    @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,
    @NotBlank(message = "密码不能为空") @Size(min = 8, max = 64, message = "密码长度必须为 8~64 位")
        String password) {}
