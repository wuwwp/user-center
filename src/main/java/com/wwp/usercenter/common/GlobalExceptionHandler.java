package com.wwp.usercenter.common;

import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把异常转换为 RFC 9457 ProblemDetail，对外不暴露堆栈、SQL 与内部路径。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler}，让 Spring MVC 自带的 405/415/406/参数错误也走同一契约； 业务码放在
 * ProblemDetail 的 {@code code} 扩展字段里。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    if (errorCode.getHttpStatus().is5xxServerError()) {
      log.error("business error, code={}", errorCode.getCode(), exception);
    }
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(errorCode.getHttpStatus())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (errorCode == ErrorCode.TOO_MANY_REQUESTS) {
      builder.header(HttpHeaders.RETRY_AFTER, "60");
    }
    return builder.body(problem(errorCode, exception.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
    return problem(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
    log.warn("data integrity violation", exception);
    return problem(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getMessage());
  }

  @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
  public ProblemDetail handleBadRequest(Exception exception) {
    log.debug("bad request: {}", exception.getMessage());
    return problem(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception) {
    log.error("unexpected error", exception);
    return problem(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        Optional.ofNullable(exception.getBindingResult().getFieldError())
            .map(GlobalExceptionHandler::formatFieldError)
            .orElse(ErrorCode.PARAM_INVALID.getMessage());
    return problemResponse(ErrorCode.PARAM_INVALID, detail);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.debug("malformed request body: {}", exception.getMessage());
    return problemResponse(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(
        ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
      HttpMediaTypeNotAcceptableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(ErrorCode.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
  }

  private static String formatFieldError(FieldError fieldError) {
    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
  }

  private static ProblemDetail problem(ErrorCode errorCode, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getHttpStatus(), detail);
    problem.setTitle(errorCode.getMessage());
    problem.setProperty("code", errorCode.getCode());
    return problem;
  }

  private static ResponseEntity<Object> problemResponse(ErrorCode errorCode, String detail) {
    return ResponseEntity.status(errorCode.getHttpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem(errorCode, detail));
  }
}
