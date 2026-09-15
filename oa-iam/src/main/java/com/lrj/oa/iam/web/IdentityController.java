package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentityDtos.ChangeIdentityStatus;
import com.lrj.oa.iam.api.dto.IdentityDtos.CreateIdentity;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityGraph;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityPage;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityView;
import com.lrj.oa.iam.api.dto.IdentityDtos.ReplaceIdentityLabels;
import com.lrj.oa.iam.api.dto.IdentityDtos.UpdateIdentity;
import com.lrj.oa.iam.application.IdentityGraphService;
import com.lrj.oa.iam.application.IdentityService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一身份目录。Human 只读投影；NHI 在此创建与治理。
 */
@RestController
@RequestMapping("/api/v1/iam/identities")
public class IdentityController {

    private final IdentityService identities;
    private final IdentityGraphService graph;

    public IdentityController(IdentityService identities, IdentityGraphService graph) {
        this.identities = identities;
        this.graph = graph;
    }

    @GetMapping
    @RequiresPerm("oa:iam:identity:view")
    public Result<IdentityPage> list(@RequestParam(required = false) String type,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "50") int size) {
        Long seq = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                seq = Long.parseLong(cursor);
            } catch (NumberFormatException ex) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "cursor 必须是数字序号");
            }
        }
        return Result.ok(identities.list(type, status, q, seq, size));
    }

    @GetMapping("/{identityId}")
    @RequiresPerm("oa:iam:identity:view")
    public Result<IdentityView> get(@PathVariable String identityId) {
        return Result.ok(identities.get(identityId));
    }

    @GetMapping("/{identityId}/graph")
    @RequiresPerm("oa:iam:identity:view")
    public Result<IdentityGraph> graph(@PathVariable String identityId) {
        return Result.ok(graph.graph(identityId));
    }

    @PostMapping
    @RequiresPerm("oa:iam:identity:admin")
    public Result<IdentityView> create(@RequestBody CreateIdentity body) {
        return Result.ok(identities.create(body));
    }

    @PutMapping("/{identityId}")
    @RequiresPerm("oa:iam:identity:admin")
    public Result<IdentityView> update(@PathVariable String identityId, @RequestBody UpdateIdentity body) {
        return Result.ok(identities.update(identityId, body));
    }

    @PostMapping("/{identityId}/status")
    @RequiresPerm("oa:iam:identity:admin")
    public Result<IdentityView> changeStatus(@PathVariable String identityId,
                                             @RequestBody ChangeIdentityStatus body) {
        return Result.ok(identities.changeStatus(identityId, body));
    }

    @PutMapping("/{identityId}/labels")
    @RequiresPerm("oa:iam:identity:admin")
    public Result<IdentityView> replaceLabels(@PathVariable String identityId,
                                              @RequestBody ReplaceIdentityLabels body) {
        return Result.ok(identities.replaceLabels(identityId, body));
    }
}
