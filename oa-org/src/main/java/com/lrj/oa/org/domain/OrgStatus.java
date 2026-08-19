package com.lrj.oa.org.domain;

/** 组织状态。撤销用软删（DISSOLVED），历史单据仍要能引用到它。 */
public enum OrgStatus { ACTIVE, FROZEN, DISSOLVED }
