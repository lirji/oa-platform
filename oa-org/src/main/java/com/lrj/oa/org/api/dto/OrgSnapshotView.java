package com.lrj.oa.org.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 某人在<b>某个历史时点</b>的组织归属快照。
 *
 * <p>存在的意义：一张两年前的审批单要能解释"当时他在哪个部门、上级是谁"。
 * 数据来自任职拉链表按 as-of 查询，而不是拿今天的组织去套历史。
 */
public record OrgSnapshotView(
        String userId, LocalDate asOf,
        Long primaryOrgId, String primaryOrgPath, String primaryOrgName,
        List<Long> allOrgIds, List<String> pathPrefixes,
        String managerUserId
) {}
