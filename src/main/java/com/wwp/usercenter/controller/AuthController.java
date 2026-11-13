package com.wwp.usercenter.controller;

import com.wwp.usercenter.common.BaseResponse;
import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.param.LoginParam;
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

  @PostMapping("/login")
  public BaseResponse<LoginResult> login(@Valid @RequestBody LoginParam param) {
    return BaseResponse.success(authService.login(param));
  }
}
