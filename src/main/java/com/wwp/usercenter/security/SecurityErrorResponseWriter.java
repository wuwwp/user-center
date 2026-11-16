package com.wwp.usercenter.security;

import com.wwp.usercenter.common.BaseResponse;
import com.wwp.usercenter.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/** 安全过滤器链中统一写出 JSON 错误响应，避免 401/403 各自拼装响应体。 */
final class SecurityErrorResponseWriter {

  private SecurityErrorResponseWriter() {}

  static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), BaseResponse.failure(errorCode));
  }
}
