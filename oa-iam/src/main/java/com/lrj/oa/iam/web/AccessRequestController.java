package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.AccessRequestDecision;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.AccessRequestPage;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.AccessRequestView;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.CreateAccessRequest;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.RoleOption;
import com.lrj.oa.iam.application.AccessRequestService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 权限申请。本地四眼，批准后 grant.source=APPROVAL。 */
@RestController
@RequestMapping("/api/v1/iam/access-requests")
public class AccessRequestController {

    private final AccessRequestService requests;

    public AccessRequestController(AccessRequestService requests) {
        this.requests = requests;
    }

    @PostMapping
    @RequiresPerm("oa:iam:request")
    public Result<AccessRequestView> create(@RequestBody CreateAccessRequest body) {
        return Result.ok(requests.create(body));
    }

    @GetMapping("/roles")
    @RequiresPerm("oa:iam:request")
    public Result<List<RoleOption>> roles() {
        return Result.ok(requests.requestableRoles());
    }

    @GetMapping("/mine")
    @RequiresPerm("oa:iam:request")
    public Result<AccessRequestPage> mine(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "50") int size) {
        return Result.ok(requests.listMine(status, parseCursor(cursor), size));
    }

    @GetMapping
    @RequiresPerm("oa:iam:admin")
    public Result<AccessRequestPage> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "50") int size) {
        return Result.ok(requests.listAll(status, parseCursor(cursor), size));
    }

    @PostMapping("/{id}/approve")
    @RequiresPerm("oa:iam:admin")
    public Result<AccessRequestView> approve(@PathVariable long id,
                                             @RequestBody(required = false) AccessRequestDecision body) {
        return Result.ok(requests.approve(id, body == null ? null : body.reason()));
    }

    @PostMapping("/{id}/reject")
    @RequiresPerm("oa:iam:admin")
    public Result<AccessRequestView> reject(@PathVariable long id,
                                            @RequestBody(required = false) AccessRequestDecision body) {
        return Result.ok(requests.reject(id, body == null ? null : body.reason()));
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ex) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "cursor 必须是数字序号");
        }
    }
}
