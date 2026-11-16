package com.wwp.usercenter.common.validation;

import com.wwp.usercenter.common.constant.ValidationConstants;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 邮箱入参的公共校验：非空、格式合法、长度不超过数据库列宽。 */
@Documented
@Constraint(validatedBy = {})
@NotBlank(message = ValidationConstants.EMAIL_BLANK_MESSAGE)
@Email(message = ValidationConstants.EMAIL_FORMAT_MESSAGE)
@Size(
    max = ValidationConstants.EMAIL_MAX_LENGTH,
    message = ValidationConstants.EMAIL_MAX_LENGTH_MESSAGE)
@Target({
  ElementType.RECORD_COMPONENT,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

  String message() default ValidationConstants.EMAIL_FORMAT_MESSAGE;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
