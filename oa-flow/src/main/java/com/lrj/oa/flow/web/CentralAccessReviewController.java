package com.lrj.oa.flow.web;

import com.lrj.authz.protocol.ApprovalDtos.Start;
import com.lrj.oa.common.api.Result;
import com.lrj.oa.flow.infrastructure.approval.CentralApprovalReview;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 当前审批任务的固定依据入口；前端P5可以展示同一request_version/snapshot_hash。 */
@RestController
@RequestMapping("/api/v1/flow/central-access/tasks")
@ConditionalOnProperty(name="oa.flow.central-approval.enabled",havingValue="true")
public class CentralAccessReviewController {
    private final CentralApprovalReview review;
    /** 鉴权和任务绑定由专用用例复用，不在控制器拼接SQL。 */
    public CentralAccessReviewController(CentralApprovalReview review){this.review=review;}
    /** 普通待办查看权限只作第一层检查，不能替代实际指派。 */
    @GetMapping("/{taskId}")
    @RequiresPerm("oa:flow:todo:view")
    public Result<Start> detail(@PathVariable String taskId){return Result.ok(review.review(taskId));}
}
