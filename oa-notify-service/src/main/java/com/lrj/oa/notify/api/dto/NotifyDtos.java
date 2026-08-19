package com.lrj.oa.notify.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 通知域对外 DTO。一律类型化，不用 Map（PG 转小写 + MyBatis 转驼峰，两层叠加后取值全靠猜）。 */
public final class NotifyDtos {

    private NotifyDtos() {}

    public record NotificationView(
            Long id, String category, String title, String content,
            String bizType, String bizId, String link,
            boolean read, OffsetDateTime createdAt) {}

    public record AnnouncementView(
            Long id, String title, String content, String publisherId, String publisherName,
            int audienceCount, String status, OffsetDateTime publishedAt, OffsetDateTime expireAt,
            Boolean readByMe) {}

    /** 发一条站内信。dedupKey 非空时重复投递不会产生第二条。 */
    public record SendNotification(
            List<String> userIds, String category, String title, String content,
            String bizType, String bizId, String link, String dedupKey) {}

    /**
     * 发布公告。
     *
     * <p>{@code recipients} 由调用方算好传进来 —— 通知域<b>不解析组织结构</b>。
     * 谁该收是业务语义（要过数据权限、组织树、角色），怎么送才是通知域的事。
     * 与 ADR-0010「审批人由 OA 侧算好塞进流程变量」是同一条原则。
     */
    public record PublishAnnouncement(
            String title, String content, List<String> recipients, OffsetDateTime expireAt) {}

    /** 已读统计。bitmapBytes 是验收指标（万人公告要 < 100KB）。 */
    public record ReadStats(
            Long announcementId, int audienceCount, int readCount, int unreadCount,
            int bitmapBytes, List<String> sampleUnread) {}
}
