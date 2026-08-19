package com.lrj.oa.common.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 → Result 的统一映射。
 *
 * <p>★ 放在 oa-common 而不是 oa-app：原先它只在主应用里，于是
 * notify / file / job 三个独立服务<b>没有任何异常处理器</b> ——
 * 一次权限拒绝（BusinessException PERM_DENIED）在那边表现为 <b>HTTP 500 + 空 body</b>。
 * 拦是拦住了，但调用方看到的是"服务器坏了"而不是"你没权限"，
 * 前端无从区分该提示登录、该提示无权限、还是该重试。
 * 安全语义只有被正确<b>表达</b>出来才算完整。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常不打堆栈——它是预期内的控制流，打堆栈会淹没真正的系统错误。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> onBusiness(BusinessException e) {
        ResultCode rc = e.resultCode();
        log.info("business exception: code={} msg={}", rc.code(), e.getMessage());
        return ResponseEntity.status(httpStatusOf(rc)).body(Result.fail(rc, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse(ResultCode.BAD_REQUEST.message());
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> onUnexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    private static HttpStatus httpStatusOf(ResultCode rc) {
        return switch (rc) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, PERM_DENIED, PERM_ELEVATION_REQUIRED,
                 DATA_SCOPE_DENIED, GRANT_EXPIRED, DELEGATION_INVALID -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, ORG_NOT_FOUND, EMPLOYEE_NOT_FOUND, FLOW_TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, ORG_CYCLE, ORG_HAS_CHILDREN, PRIMARY_ASSIGNMENT_CONFLICT,
                 PUNCH_DUPLICATE -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL_ERROR, DEPENDENCY_UNAVAILABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
