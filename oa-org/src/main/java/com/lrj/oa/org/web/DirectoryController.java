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

    /**
     * 游标分页。万人通讯录用它做首屏与"加载更多"。
     *
     * <p>用 cursor 而不是 offset：OFFSET 9000 要先扫过前 9000 行，
     * 且翻页期间有人入职会让某一行被跳过或重复。
     */
    @GetMapping("/page")
    @RequiresPerm("oa:employee:view")
    public Result<Map<String, Object>> page(@RequestParam(required = false) Long cursor,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "200") int size) {
        List<DirectoryEntryView> items = directoryService.page(cursor, keyword, size);
        Long next = items.isEmpty() ? null : items.get(items.size() - 1).getSyncSeq();
        return Result.ok(Map.of(
                "items", items,
                "nextCursor", next == null ? "" : next,
                "hasMore", items.size() >= Math.min(Math.max(size, 1), 500),
                "watermark", directoryService.currentWatermark()));
    }

    /**
     * 增量同步。客户端本地存 {@code since}，只拉变化的行 + 应删除的 id。
     *
     * <p>★ {@code deletions} 不可省：一个人离职后他的行是从视图里<b>消失</b>而不是<b>变化</b>，
     * 只发变更行的话客户端永远收不到消息，本地缓存里会一直躺着已离职的人。
     * 这是增量同步最经典的 bug，且只在低频事件后才显形。
     *
     * <p>★ {@code fullResync} 是逃生舱：since 过旧或客户端版本对不上时，
     * 让它清库重来，而不是硬凑一个可能已经不完整的增量。
     */
    @GetMapping("/delta")
    @RequiresPerm("oa:employee:view")
    public Result<Map<String, Object>> delta(@RequestParam(defaultValue = "0") long since,
                                             @RequestParam(defaultValue = "500") int size) {
        long watermark = directoryService.currentWatermark();
        // since 大于当前水位线 = 客户端拿的是另一个库的水位线（换了环境、库被重建）。
        // 这种情况下增量毫无意义，必须让它全量重来。
        boolean fullResync = since > watermark;
        if (fullResync) {
            return Result.ok(Map.of("fullResync", true, "changes", List.of(),
                    "deletions", List.of(), "watermark", watermark));
        }
        List<DirectoryEntryView> changes = directoryService.changedSince(since, size);
        List<Long> deletions = directoryService.tombstonesSince(since, size * 2);
        long next = changes.isEmpty() ? watermark
                : Math.max(changes.get(changes.size() - 1).getSyncSeq(), since);
        return Result.ok(Map.of(
                "fullResync", false,
                "changes", changes,
                "deletions", deletions,
                // hasMore 为 true 时客户端要带着 nextSince 继续拉，而不是就此把 watermark 存下来 ——
                // 存早了会永久跳过中间那段变更。
                "hasMore", changes.size() >= Math.min(Math.max(size, 1), 500),
                "nextSince", next,
                "watermark", watermark));
    }

    /** 当前用户能看到多少人 —— 数据权限差异最直观的观测点。 */
    @GetMapping("/count")
    @RequiresPerm("oa:employee:view")
    public Result<Map<String, Object>> count() {
        return Result.ok(Map.of("visible", directoryService.countVisible()));
    }
}
