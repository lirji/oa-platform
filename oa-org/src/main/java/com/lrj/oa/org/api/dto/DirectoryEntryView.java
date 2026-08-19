package com.lrj.oa.org.api.dto;

import com.lrj.oa.security.annotation.Sensitive;
import com.lrj.oa.security.model.SensitiveType;

import java.time.LocalDate;

/**
 * 通讯录条目。
 *
 * <p>手机号带 {@link Sensitive}：没有 {@code oa:field:mobile} 权限的人拿到的是
 * {@code 138****1234}，而且脱敏发生在<b>序列化层</b>，任何返回这个 VO 的接口都自动生效。
 */
public class DirectoryEntryView {

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

    @Sensitive(type = SensitiveType.MOBILE, perm = "oa:field:mobile")
    private String mobile;

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
    public String getMobile() { return mobile; }
    public void setMobile(String v) { this.mobile = v; }

    /** 增量同步水位线。前端据此推进本地 since。 */
    private Long syncSeq;
    public Long getSyncSeq() { return syncSeq; }
    public void setSyncSeq(Long v) { this.syncSeq = v; }
}
