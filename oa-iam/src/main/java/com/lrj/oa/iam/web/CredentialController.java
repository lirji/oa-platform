package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.api.dto.CredentialDtos.CreateCredential;
import com.lrj.oa.iam.api.dto.CredentialDtos.CredentialView;
import com.lrj.oa.iam.api.dto.CredentialDtos.IssuedCredential;
import com.lrj.oa.iam.application.CredentialService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** NHI 凭证签发与查询。明文只在签发响应。 */
@RestController
public class CredentialController {

    private final CredentialService credentials;

    public CredentialController(CredentialService credentials) {
        this.credentials = credentials;
    }

    @PostMapping("/api/v1/iam/identities/{identityId}/credentials")
    @RequiresPerm("oa:iam:identity:admin")
    public Result<IssuedCredential> issue(@PathVariable String identityId,
                                          @RequestBody(required = false) CreateCredential body) {
        return Result.ok(credentials.issue(identityId, body));
    }

    @GetMapping("/api/v1/iam/identities/{identityId}/credentials")
    @RequiresPerm("oa:iam:identity:view")
    public Result<List<CredentialView>> list(@PathVariable String identityId) {
        return Result.ok(credentials.list(identityId));
    }

    @PostMapping("/api/v1/iam/credentials/{id}/revoke")
    @RequiresPerm("oa:iam:identity:admin")
    public Result<CredentialView> revoke(@PathVariable long id) {
        return Result.ok(credentials.revoke(id));
    }

    @PostMapping("/api/v1/iam/credentials/{id}/rotate")
    @RequiresPerm("oa:iam:identity:admin")
    public Result<IssuedCredential> rotate(@PathVariable long id) {
        return Result.ok(credentials.rotate(id));
    }
}
