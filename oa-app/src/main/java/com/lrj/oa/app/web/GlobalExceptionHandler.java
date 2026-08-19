package com.lrj.oa.app.web;

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
