package com.lrj.oa.notify.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.notify.api.dto.NotifyDtos;
import com.lrj.oa.notify.application.NotifyService;
import com.lrj.oa.notify.infrastructure.channel.ChannelDispatcher;
import com.lrj.oa.notify.infrastructure.ws.SessionRegistry;
import com.lrj.oa.notify.infrastructure.ws.WsTicketService;
import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 站内信 REST。:8401 */
@RestController
@RequestMapping("/api/v1/notify")
public class NotifyController {

    private final NotifyService notifyService;
    private final SessionRegistry sessions;
    private final ChannelDispatcher channels;
    private final WsTicketService wsTickets;

    public NotifyController(NotifyService notifyService, SessionRegistry sessions, ChannelDispatcher channels,
                            WsTicketService wsTickets) {
        this.notifyService = notifyService;
        this.sessions = sessions;
        this.channels = channels;
        this.wsTickets = wsTickets;
    }

    @GetMapping("/messages")
    @RequiresPerm("oa:notify:read")
    public Result<List<NotifyDtos.NotificationView>> myMessages(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        // 只查自己的：userId 取自认证上下文，绝不接受请求参数指定 —— 那就是 IDOR 本身。
        return Result.ok(notifyService.list(UserContextHolder.require().userId(), unreadOnly, limit));
    }

    @GetMapping("/messages/unread-count")
    @RequiresPerm("oa:notify:read")
    public Result<Map<String, Integer>> unreadCount() {
        return Result.ok(Map.of("unread", notifyService.unreadCount(UserContextHolder.require().userId())));
    }

    @PostMapping("/messages/{id}/read")
    @RequiresPerm("oa:notify:read")
    public Result<Void> markRead(@PathVariable long id) {
        notifyService.markRead(id, UserContextHolder.require().userId());
        return Result.ok();
    }

    @PostMapping("/messages/read-all")
    @RequiresPerm("oa:notify:read")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.ok(Map.of("updated", notifyService.markAllRead(UserContextHolder.require().userId())));
    }

    /** 系统间调用：工作台产生待办时发提醒。 */
    @PostMapping("/messages")
    @RequiresPerm("oa:notify:send")
    public Result<Map<String, Integer>> send(@RequestBody NotifyDtos.SendNotification cmd) {
        int n = notifyService.send(cmd);
        notifyService.pushAfterCommit(cmd);   // 推送在事务外，避免接收方先于提交查库
        return Result.ok(Map.of("inserted", n));
    }

    @GetMapping("/ws-stats")
    @RequiresPerm("oa:notify:send")
    public Result<Map<String, Object>> wsStats() {
        return Result.ok(Map.of("ws", sessions.stats(), "channels", channels.enabledChannels()));
    }

    /**
     * 浏览器原生 WebSocket 不能设置 Authorization header，因此先用 Bearer REST 请求换
     * 一枚短期、一次性 ticket。ticket 只承载随机熵，长期 access token 永不进入 URL。
     */
    @PostMapping("/ws-ticket")
    @RequiresPerm("oa:notify:read")
    public Result<Map<String, Object>> wsTicket() {
        UserContext ctx = UserContextHolder.require();
        WsTicketService.IssuedTicket issued = wsTickets.issue(ctx.userId(), ctx.tenantId());
        return Result.ok(Map.of("ticket", issued.ticket(), "expiresInSeconds", issued.expiresInSeconds()));
    }

    @GetMapping("/ping")
    @PublicApi(reason = "存活探针，不返回任何业务数据；容器 healthcheck 与冒烟脚本用")
    public Result<String> ping() {
        return Result.ok("notify-ok");
    }
}
