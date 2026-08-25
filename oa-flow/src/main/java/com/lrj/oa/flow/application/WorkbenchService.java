package com.lrj.oa.flow.application;

import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.flow.api.dto.MyApplicationView;
import com.lrj.oa.flow.infrastructure.mapper.WorkbenchMapper;
import com.lrj.oa.security.annotation.ObjectScope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkbenchService {
    private final WorkbenchMapper mapper;

    public WorkbenchService(WorkbenchMapper mapper) {
        this.mapper = mapper;
    }

    @ObjectScope(permission = "oa:flow:todo:view", tables = {"oa_flow.leave_request", "oa_flow.business_doc"},
            strategy = ObjectScope.Strategy.OWNER, reason = "联合查询两张单据表且都固定当前申请人")
    public List<MyApplicationView> myApplications(String userId, String bizType, int limit) {
        int cap = Math.min(Math.max(limit, 1), 200);
        return mapper.selectOwn(TenantContext.get(), userId, bizType, cap).stream()
                .map(r -> new MyApplicationView(r.bizType, r.docNo, r.status, r.title, r.summary,
                        r.amount, r.days, r.createdAt)).toList();
    }
}
