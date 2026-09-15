package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.api.dto.IdentitySyncDtos.CreateSyncJob;
import com.lrj.oa.iam.api.dto.IdentitySyncDtos.SyncJobView;
import com.lrj.oa.iam.application.IdentitySyncService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 身份同步。INTERNAL 员工投影 + MOCK 目录；无 LDAP。 */
@RestController
@RequestMapping("/api/v1/iam/sync/jobs")
public class IdentitySyncController {

    private final IdentitySyncService sync;

    public IdentitySyncController(IdentitySyncService sync) {
        this.sync = sync;
    }

    @PostMapping
    @RequiresPerm("oa:iam:admin")
    public Result<SyncJobView> start(@RequestBody(required = false) CreateSyncJob body) {
        return Result.ok(sync.start(body));
    }

    @GetMapping("/{id}")
    @RequiresPerm("oa:iam:admin")
    public Result<SyncJobView> get(@PathVariable long id) {
        return Result.ok(sync.get(id));
    }
}
