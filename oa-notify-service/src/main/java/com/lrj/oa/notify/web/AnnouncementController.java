package com.lrj.oa.notify.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.notify.api.dto.NotifyDtos;
import com.lrj.oa.notify.application.AnnouncementService;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 公告 REST。:8401 */
@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresPerm("oa:announce:publish")
    public Result<Map<String, Object>> publish(@RequestBody NotifyDtos.PublishAnnouncement cmd) {
        UserContext ctx = UserContextHolder.require();
        List<String> recipients = AnnouncementService.normalizeRecipients(cmd.recipients());
        long t0 = System.currentTimeMillis();
        long id = service.publish(cmd, ctx.userId(), ctx.username());
        // 推送在发布事务提交之后：事务里推，接收端可能先收到再查库却查不到。
        int delivered = service.broadcastPush(id, recipients, cmd.title());
        return Result.ok(Map.of("id", id, "audience", recipients.size(),
                "pushedSessions", delivered, "elapsedMs", System.currentTimeMillis() - t0));
    }

    @GetMapping
    @RequiresPerm("oa:announce:read")
    public Result<List<NotifyDtos.AnnouncementView>> list(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.list(UserContextHolder.require().userId(), limit));
    }

    @PostMapping("/{id}/read")
    @RequiresPerm("oa:announce:read")
    public Result<Void> markRead(@PathVariable long id) {
        service.markRead(id, UserContextHolder.require().userId());
        return Result.ok();
    }

    @GetMapping("/{id}/stats")
    @RequiresPerm("oa:announce:stats")
    public Result<NotifyDtos.ReadStats> stats(@PathVariable long id,
                                              @RequestParam(defaultValue = "5") int unreadSample) {
        return Result.ok(service.stats(id, unreadSample));
    }

    @PostMapping("/{id}/revoke")
    @RequiresPerm("oa:announce:revoke")
    public Result<Void> revoke(@PathVariable long id) {
        service.revoke(id);
        return Result.ok();
    }
}
