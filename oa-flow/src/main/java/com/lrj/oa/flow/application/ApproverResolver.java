package com.lrj.oa.flow.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.api.OrgQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 审批人计算 —— <b>ADR-0010 的核心实现</b>。
 *
 * <p>审批人依赖组织树、汇报线、岗位与委托代理，这些数据全在 OA。
 * 所以由 OA 算好、塞进流程变量交给中台；<b>绝不让中台反查 OA</b> ——
 * 那会把"消费方极小 SDK 接入"的中台变成 OA 的下游耦合方。
 *
 * <p>算法刻意简单且可解释：沿<b>实线汇报线</b>逐级向上取 N 级，
 * 取不满则回落到部门负责人（允许向上冒泡，否则新建部门还没配负责人单据就卡死）。
 * 层级数由金额/天数这类业务量级决定 —— 这是"审批级数随金额变"的通用做法。
 */
@Component
public class ApproverResolver {

    private static final Logger log = LoggerFactory.getLogger(ApproverResolver.class);

    private final OrgQueryApi orgQuery;
    private final int maxLevels;

    public ApproverResolver(OrgQueryApi orgQuery,
                            @Value("${oa.flow.approval.max-levels:5}") int maxLevels) {
        this.orgQuery = orgQuery;
        this.maxLevels = maxLevels;
    }

    /**
     * 按请假天数决定审批级数：
     * ≤3 天一级（直属上级）、≤7 天两级、更长三级。真实企业的规则会更细，
     * 但形状就是这样 —— 量级驱动级数，规则集中在一处而不是散在各处 if。
     */
    public int levelsForLeave(BigDecimal days) {
        if (days.compareTo(new BigDecimal("3")) <= 0) return 1;
        if (days.compareTo(new BigDecimal("7")) <= 0) return 2;
        return 3;
    }

    /**
     * 解析出一条审批人链（按顺序逐级审批）。
     *
     * @param applicantUserId 申请人
     * @param levels          需要几级
     * @return 去重后的审批人 userId 列表；为空说明这个人头上没有任何可用审批人
     */
    public List<String> resolveChain(String applicantUserId, int levels) {
        int want = Math.min(levels, maxLevels);
        LinkedHashSet<String> chain = new LinkedHashSet<>(orgQuery.managerChain(applicantUserId, want));

        if (chain.size() < want) {
            // 汇报线不够长（新员工还没配上级、或到了顶）→ 回落到部门负责人，允许向上冒泡
            Long orgId = orgQuery.primaryOrgId(applicantUserId);
            if (orgId != null) {
                for (String leader : orgQuery.orgLeaderUserIds(orgId, true)) {
                    if (chain.size() >= want) break;
                    chain.add(leader);
                }
            }
        }
        chain.remove(applicantUserId);   // 不能自己审自己

        List<String> result = new ArrayList<>(chain);
        if (result.isEmpty()) {
            throw BusinessException.of(ResultCode.FLOW_START_FAILED,
                    "找不到任何可用审批人：请先为申请人配置实线上级或部门负责人");
        }
        log.debug("审批人链 applicant={} levels={} -> {}", applicantUserId, want, result);
        return result;
    }
}
