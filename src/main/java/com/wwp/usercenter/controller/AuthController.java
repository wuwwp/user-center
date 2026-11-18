package com.wwp.usercenter.controller;

import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.dto.RegisterResult;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.model.param.RegisterParam;
import com.wwp.usercenter.model.param.ResetPasswordParam;
import com.wwp.usercenter.model.param.SendEmailCodeParam;
import com.wwp.usercenter.security.ClientIpResolver;
import com.wwp.usercenter.security.SecurityUtils;
import com.wwp.usercenter.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
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
  private final ClientIpResolver clientIpResolver;

  @PostMapping("/register")
  public RegisterResult register(@Valid @RequestBody RegisterParam param) {
    return authService.register(param);
  }

  @PostMapping("/login")
  public LoginResult login(@Valid @RequestBody LoginParam param, HttpServletRequest request) {
    return authService.login(param, clientIpResolver.resolve(request));
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/logout")
  public void logout() {
    authService.logout(SecurityUtils.currentUserId());
  }

  @PostMapping("/email/code")
  public void sendEmailCode(
      @Valid @RequestBody SendEmailCodeParam param, HttpServletRequest request) {
    authService.sendEmailCode(param, clientIpResolver.resolve(request));
  }

  @PostMapping("/password/reset")
  public void resetPassword(@Valid @RequestBody ResetPasswordParam param) {
    authService.resetPassword(param);
  }
}
