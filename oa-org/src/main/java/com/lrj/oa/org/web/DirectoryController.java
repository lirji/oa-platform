package com.lrj.oa.org.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.org.api.dto.DirectoryEntryView;
import com.lrj.oa.org.application.DirectoryService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/org/directory")
public class DirectoryController {

    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) { this.directoryService = directoryService; }

    @GetMapping
    @RequiresPerm("oa:employee:view")
    public Result<List<DirectoryEntryView>> search(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(directoryService.search(keyword, limit));
    }

    /** 当前用户能看到多少人 —— 数据权限差异最直观的观测点。 */
    @GetMapping("/count")
    @RequiresPerm("oa:employee:view")
    public Result<Map<String, Object>> count() {
        return Result.ok(Map.of("visible", directoryService.countVisible()));
    }
}
