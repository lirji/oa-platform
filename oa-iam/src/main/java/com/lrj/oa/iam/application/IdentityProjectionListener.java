package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.EmployeeView;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import com.lrj.oa.org.api.event.EmployeeCreatedEvent;
import com.lrj.oa.org.api.event.OrgTreeChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 把组织域的人事实同步成 IAM Identity 投影。
 * 不写 oa_org 表，也不改 grant 热路径。
 */
@Component
public class IdentityProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(IdentityProjectionListener.class);

    private final IdentityService identities;
    private final OrgQueryApi org;

    public IdentityProjectionListener(IdentityService identities, OrgQueryApi org) {
        this.identities = identities;
        this.org = org;
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(EmployeeCreatedEvent event) {
        identities.projectUser(event.userId(), event.employeeId(), event.name(),
                event.employmentType(), event.status(), event.primaryOrgId(), event.primaryOrgPath());
        log.info("已投影新建员工身份 userId={} employeeId={}", event.userId(), event.employeeId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAssignmentChanged(EmployeeAssignmentChangedEvent event) {
        if (event.left()) {
            identities.disableByUserId(event.userId());
            log.warn("员工 {} 离职，身份已禁用", event.userId());
            return;
        }
        EmployeeView employee;
        try {
            employee = org.getEmployeeByUserId(event.userId());
        } catch (BusinessException ex) {
            if (ex.resultCode() == ResultCode.EMPLOYEE_NOT_FOUND) {
                return;
            }
            throw ex;
        }
        identities.projectUser(employee.userId(), employee.id(), employee.name(),
                employee.employmentType(), employee.status(),
                employee.primaryOrgId(), employee.primaryOrgPath());
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrgTreeChanged(OrgTreeChangedEvent event) {
        if (event.reason() == null || !event.reason().contains("seed")) {
            return;
        }
        int inserted = identities.backfillFromEmployees();
        log.info("组织种子装载后回填身份 {} 条", inserted);
    }
}
