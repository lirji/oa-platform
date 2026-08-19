package com.lrj.oa.org.infrastructure.mapper;

import java.time.LocalDate;

/**
 * 通讯录查询的行结果。
 *
 * <p>刻意<b>不</b>用 {@code Map<String,Object>} 接结果：PostgreSQL 把列名转小写、
 * MyBatis 又可能按 {@code mapUnderscoreToCamelCase} 改键名，两层规则叠加后
 * {@code map.get("mobile_enc")} 到底该写成什么全靠猜 —— 猜错就是静默返回 null。
 * 这个坑在 Phase 2 里踩了两次（另一次是角色权限展开），一律改成类型化 DTO。
 */
public class DirectoryRow {

    private Long employeeId;
    private String userId;
    private String empNo;
    private String name;
    private String email;
    private String status;
    private LocalDate hireDate;
    private Long orgId;
    private String orgPath;
    private String orgName;
    private String positionName;
    private Boolean leader;
    private byte[] mobileEnc;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String v) { this.empNo = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate v) { this.hireDate = v; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String v) { this.orgPath = v; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String v) { this.orgName = v; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String v) { this.positionName = v; }
    public Boolean getLeader() { return leader; }
    public void setLeader(Boolean v) { this.leader = v; }
    public byte[] getMobileEnc() { return mobileEnc; }
    public void setMobileEnc(byte[] v) { this.mobileEnc = v; }

    /** 增量同步水位线。客户端存下最大值作为下次的 since。 */
    private Long syncSeq;
    public Long getSyncSeq() { return syncSeq; }
    public void setSyncSeq(Long v) { this.syncSeq = v; }
}
