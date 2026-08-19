package com.lrj.oa.common.api;

/** 统一响应包装。所有 REST 接口返回它（FHIR 类原样透传的场景除外）。 */
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.code(), ResultCode.SUCCESS.message(), data, null);
    }

    public static Result<Void> ok() { return ok(null); }

    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc.code(), rc.message(), null, null);
    }

    public static <T> Result<T> fail(ResultCode rc, String detail) {
        return new Result<>(rc.code(), detail == null ? rc.message() : detail, null, null);
    }

    public boolean success() { return code == ResultCode.SUCCESS.code(); }
}
