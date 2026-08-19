package com.lrj.oa.common.api;

/** 全局返回码。区段：0 成功 / 1xxx 通用 / 2xxx 组织 / 3xxx 权限 / 4xxx 流程 / 5xxx 考勤 / 9xxx 系统。 */
public enum ResultCode {
    SUCCESS(0, "成功"),

    BAD_REQUEST(1400, "请求参数不合法"),
    UNAUTHORIZED(1401, "未认证"),
    FORBIDDEN(1403, "无权限"),
    NOT_FOUND(1404, "资源不存在"),
    CONFLICT(1409, "资源状态冲突"),
    TOO_MANY_REQUESTS(1429, "请求过于频繁"),

    ORG_NOT_FOUND(2001, "组织不存在"),
    ORG_CYCLE(2002, "组织移动会形成环"),
    ORG_HAS_CHILDREN(2003, "组织下仍有子组织或成员"),
    EMPLOYEE_NOT_FOUND(2010, "员工不存在"),
    PRIMARY_ASSIGNMENT_CONFLICT(2011, "主岗已存在"),

    PERM_DENIED(3001, "权限不足"),
    PERM_ELEVATION_REQUIRED(3002, "该操作需要临时提权"),
    GRANT_EXPIRED(3003, "授权已过期"),
    DELEGATION_INVALID(3004, "委托关系无效"),
    DATA_SCOPE_DENIED(3005, "超出数据权限范围"),

    FLOW_START_FAILED(4001, "流程发起失败"),
    FLOW_TASK_NOT_FOUND(4002, "待办不存在或已被处理"),
    FORM_TEMPLATE_INVALID(4003, "表单模板不合法"),

    PUNCH_DUPLICATE(5001, "重复打卡"),
    LEAVE_BALANCE_INSUFFICIENT(5002, "假期额度不足"),

    INTERNAL_ERROR(9000, "系统内部错误"),
    DEPENDENCY_UNAVAILABLE(9001, "依赖服务不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) { this.code = code; this.message = message; }

    public int code() { return code; }
    public String message() { return message; }
}
