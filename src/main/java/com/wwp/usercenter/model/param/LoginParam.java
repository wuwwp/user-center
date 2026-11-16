package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.constant.ValidationConstants;
import com.wwp.usercenter.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginParam(
    @ValidEmail String email,
    @NotBlank(message = "密码不能为空")
        @Size(
            min = ValidationConstants.PASSWORD_MIN_LENGTH,
            max = ValidationConstants.PASSWORD_MAX_LENGTH,
            message = "密码长度必须为 8~64 位")
        String password) {}
