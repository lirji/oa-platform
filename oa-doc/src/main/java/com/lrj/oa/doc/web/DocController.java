package com.lrj.oa.doc.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.doc.api.dto.DocDtos;
import com.lrj.oa.doc.application.KnowledgeBaseService;
import com.lrj.oa.doc.application.OfficialDocService;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 文档域 REST：公文 + 知识库。 */
@RestController
@RequestMapping("/api/v1/doc")
public class DocController {

    private final OfficialDocService docs;
    private final KnowledgeBaseService kb;

    public DocController(OfficialDocService docs, KnowledgeBaseService kb) {
        this.docs = docs;
        this.kb = kb;
    }

    // ───────────────────────────── 公文
    @PostMapping("/official")
    @RequiresPerm("oa:doc:draft")
    public Result<Map<String, Object>> draft(@Valid @RequestBody DocDtos.DraftDoc cmd) {
        return Result.ok(Map.of("id", docs.draft(cmd)));
    }

    @PostMapping("/official/{id}/issue")
    @RequiresPerm("oa:doc:issue")
    public Result<Map<String, Object>> issue(@PathVariable long id) {
        return Result.ok(Map.of("docNumber", docs.issue(id)));
    }

    @PostMapping("/official/{id}/archive")
    @RequiresPerm("oa:doc:archive")
    public Result<Void> archive(@PathVariable long id) { docs.archive(id); return Result.ok(); }

    @GetMapping("/official")
    @RequiresPerm("oa:doc:read")
    public Result<List<DocDtos.DocView>> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(docs.search(keyword, status, limit));
    }

    // ───────────────────────────── 知识库
    @PostMapping("/kb")
    @RequiresPerm("oa:kb:write")
    public Result<Map<String, Object>> createKb(@Valid @RequestBody DocDtos.CreateKbDoc cmd) {
        return Result.ok(Map.of("id", kb.create(cmd)));
    }

    @GetMapping("/kb/{id}")
    @RequiresPerm("oa:kb:read")
    public Result<DocDtos.KbDocView> readKb(@PathVariable long id) { return Result.ok(kb.read(id)); }

    @PutMapping("/kb/{id}")
    @RequiresPerm("oa:kb:read")   // 接口层只要求"能用知识库"，能不能改由对象级判权决定
    public Result<Void> updateKb(@PathVariable long id, @Valid @RequestBody DocDtos.CreateKbDoc cmd) {
        kb.update(id, cmd);
        return Result.ok();
    }

    @GetMapping("/kb")
    @RequiresPerm("oa:kb:read")
    public Result<List<DocDtos.KbDocView>> listKb(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(kb.listVisible(limit));
    }

    @PostMapping("/kb/share")
    @RequiresPerm("oa:kb:share")
    public Result<Void> share(@Valid @RequestBody DocDtos.ShareKb cmd) { kb.share(cmd); return Result.ok(); }

    /** 对象级判权的解释器：为什么这个人能（不能）看这份文档。 */
    @GetMapping("/kb/{id}/explain")
    @RequiresPerm("oa:kb:share")
    public Result<DocDtos.KbAccessExplain> explain(@PathVariable long id,
                                                   @RequestParam(required = false) String userId) {
        String who = userId == null ? UserContextHolder.require().userId() : userId;
        return Result.ok(kb.explain(id, who));
    }

    @GetMapping("/kb/authorizer")
    @RequiresPerm("oa:kb:share")
    public Result<Map<String, String>> authorizer() {
        return Result.ok(Map.of("impl", kb.authorizerName()));
    }
}
