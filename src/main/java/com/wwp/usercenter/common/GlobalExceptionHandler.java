package com.wwp.usercenter.common;

import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 把异常转换为统一响应体，对外不暴露堆栈、SQL 与内部路径。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    if (errorCode.getHttpStatus().is5xxServerError()) {
      log.error("business error, code={}", errorCode.getCode(), exception);
    }
    return build(errorCode, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception) {
    String message =
        Optional.ofNullable(exception.getBindingResult().getFieldError())
            .map(GlobalExceptionHandler::formatFieldError)
            .orElse(ErrorCode.PARAM_INVALID.getMessage());
    return build(ErrorCode.PARAM_INVALID, message);
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<BaseResponse<Void>> handleBadRequest(Exception exception) {
    log.debug("bad request: {}", exception.getMessage());
    return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<BaseResponse<Void>> handleNotFound(NoResourceFoundException exception) {
    return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<BaseResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
    return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<BaseResponse<Void>> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    log.warn("data integrity violation", exception);
    return build(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<BaseResponse<Void>> handleUnexpected(Exception exception) {
    log.error("unexpected error", exception);
    return build(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
  }

  private static String formatFieldError(FieldError fieldError) {
    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
  }

  private static ResponseEntity<BaseResponse<Void>> build(ErrorCode errorCode, String message) {
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(BaseResponse.failure(errorCode, message));
  }
}
