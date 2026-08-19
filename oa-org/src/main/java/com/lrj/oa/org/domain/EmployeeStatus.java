package com.lrj.oa.org.domain;

/** 员工状态。LEAVING = 已提离职待办交接，此时仍需登录处理交接，故不能等同 LEFT。 */
public enum EmployeeStatus { PROBATION, ACTIVE, LEAVING, LEFT }
