package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.ChangeStatusRequest;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.RiskFindingPage;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.RiskFindingView;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.ScanResult;
import com.lrj.oa.iam.application.RiskFindingService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 风险发现。规则内置；扫描在请求内完成。 */
@RestController
@RequestMapping("/api/v1/iam/risk")
public class RiskFindingController {

    private final RiskFindingService risk;

    public RiskFindingController(RiskFindingService risk) {
        this.risk = risk;
    }

    @PostMapping("/scan")
    @RequiresPerm("oa:iam:admin")
    public Result<ScanResult> scan() {
        return Result.ok(risk.scan());
    }

    @GetMapping("/findings")
    @RequiresPerm("oa:iam:admin")
    public Result<RiskFindingPage> list(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "50") int size) {
        return Result.ok(risk.list(status, parseCursor(cursor), size));
    }

    @PostMapping("/findings/{id}/status")
    @RequiresPerm("oa:iam:admin")
    public Result<RiskFindingView> changeStatus(@PathVariable long id,
                                                @RequestBody ChangeStatusRequest body) {
        return Result.ok(risk.changeStatus(id, body));
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
