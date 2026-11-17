package com.xiaoyu.common;

/**
 * 可预期的业务异常。
 * Service 抛出该异常后，Spring 事务会回滚，并由全局异常处理器转换为统一响应。
 */
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String message) {
        this(Code.FAIL, message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
