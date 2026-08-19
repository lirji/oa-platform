package com.lrj.oa.common.exception;

import com.lrj.oa.common.api.ResultCode;

/** 业务异常。由全局异常处理器翻译成 {@code Result}，不打堆栈（非系统错误）。 */
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.message());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String detail) {
        super(detail);
        this.resultCode = resultCode;
    }

    public ResultCode resultCode() { return resultCode; }

    public static BusinessException of(ResultCode rc) { return new BusinessException(rc); }
    public static BusinessException of(ResultCode rc, String detail) { return new BusinessException(rc, detail); }
}
