package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordParam(
    @NotBlank(message = "原密码不能为空") @Size(max = 64, message = "原密码长度不能超过 64") String oldPassword,
    @NotBlank(message = "新密码不能为空")
        @Pattern(
            regexp = ValidationConstants.PASSWORD_REGEXP,
            message = ValidationConstants.PASSWORD_MESSAGE)
        String newPassword) {}
