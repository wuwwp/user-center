package com.wwp.usercenter.model.param;

import com.wwp.usercenter.model.enums.EmailScene;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendEmailCodeParam(
    @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,
    @NotNull(message = "场景不能为空") EmailScene scene) {}
