package com.xiaoyu.common;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 将Spring Validation的400错误转换为统一的JSON响应格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 @RequestBody 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        Result result = new Result(Code.PARAM_ERROR, null, message);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.ok(new Result(Code.PARAM_ERROR, null, message));
    }

    /**
     * 处理 @RequestParam / @PathVariable 参数校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        Result result = new Result(Code.PARAM_ERROR, null, message);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException e) {
        return ResponseEntity.ok(new Result(e.getCode(), null, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleUnexpectedException(Exception e) {
        log.error("未处理的服务异常", e);
        return ResponseEntity.ok(new Result(Code.SERVER_ERROR, null, "系统繁忙，请稍后重试"));
    }
}
