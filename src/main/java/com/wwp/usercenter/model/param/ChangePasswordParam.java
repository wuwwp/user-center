package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.constant.ValidationConstants;
import com.wwp.usercenter.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordParam(
    @NotBlank(message = "原密码不能为空")
        @Size(
            max = ValidationConstants.PASSWORD_MAX_LENGTH,
            message = "原密码长度不能超过 " + ValidationConstants.PASSWORD_MAX_LENGTH)
        String oldPassword,
    @NotBlank(message = "新密码不能为空") @StrongPassword String newPassword) {}
