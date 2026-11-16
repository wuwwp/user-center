package com.wwp.usercenter.model.param;

import com.wwp.usercenter.common.validation.ValidEmail;
import com.wwp.usercenter.model.enums.EmailScene;
import jakarta.validation.constraints.NotNull;

public record SendEmailCodeParam(
    @ValidEmail String email, @NotNull(message = "场景不能为空") EmailScene scene) {}
