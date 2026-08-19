package com.lrj.oa.org.domain;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 员工。{@code userId} 是 Casdoor {@code sub}（UUID），全系统唯一主体标识。 */
@TableName("oa_org.employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String userId;
    private String empNo;
    private String name;
    private String enName;
    private String avatar;

    /** 手机密文（AES-GCM）。明文永不落库。 */
    private byte[] mobileEnc;
    /** 手机确定性 HMAC，供精确查询；不可逆，泄露不等于泄露号码。 */
    private String mobileHash;

    private String email;
    private byte[] idCardEnc;
    private String idCardHash;
    private String gender;
    private LocalDate birthday;
    private LocalDate hireDate;
    private LocalDate regularDate;
    private LocalDate leaveDate;
    private String employmentType;
    private String status;

    @Version
    private Integer version;

    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;

    public EmployeeStatus statusEnum() { return EmployeeStatus.valueOf(status); }
    /** 在职（含试用与离职交接中）——判断能否登录/被指派用它，而不是直接比 ACTIVE。 */
    public boolean inService() { return !EmployeeStatus.LEFT.name().equals(status); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEnName() { return enName; }
    public void setEnName(String enName) { this.enName = enName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public byte[] getMobileEnc() { return mobileEnc; }
    public void setMobileEnc(byte[] v) { this.mobileEnc = v; }
    public String getMobileHash() { return mobileHash; }
    public void setMobileHash(String v) { this.mobileHash = v; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public byte[] getIdCardEnc() { return idCardEnc; }
    public void setIdCardEnc(byte[] v) { this.idCardEnc = v; }
    public String getIdCardHash() { return idCardHash; }
    public void setIdCardHash(String v) { this.idCardHash = v; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate v) { this.birthday = v; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate v) { this.hireDate = v; }
    public LocalDate getRegularDate() { return regularDate; }
    public void setRegularDate(LocalDate v) { this.regularDate = v; }
    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate v) { this.leaveDate = v; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String v) { this.employmentType = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
