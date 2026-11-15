package com.wwp.usercenter.controller;

import com.wwp.usercenter.common.BaseResponse;
import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.dto.RegisterResult;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.model.param.RegisterParam;
import com.wwp.usercenter.model.param.ResetPasswordParam;
import com.wwp.usercenter.model.param.SendEmailCodeParam;
import com.wwp.usercenter.security.SecurityUtils;
import com.wwp.usercenter.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public BaseResponse<RegisterResult> register(@Valid @RequestBody RegisterParam param) {
    return BaseResponse.success(authService.register(param));
  }

  @PostMapping("/login")
  public BaseResponse<LoginResult> login(@Valid @RequestBody LoginParam param) {
    return BaseResponse.success(authService.login(param));
  }

  @PostMapping("/logout")
  public BaseResponse<Void> logout() {
    authService.logout(SecurityUtils.currentUserId());
    return BaseResponse.success();
  }

  @PostMapping("/email/code")
  public BaseResponse<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeParam param) {
    authService.sendEmailCode(param);
    return BaseResponse.success();
  }

  @PostMapping("/password/reset")
  public BaseResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordParam param) {
    authService.resetPassword(param);
    return BaseResponse.success();
  }
}
