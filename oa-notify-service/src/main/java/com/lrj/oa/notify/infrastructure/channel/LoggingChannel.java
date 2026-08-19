package com.lrj.oa.notify.infrastructure.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 四个外部渠道（邮件 / 短信 / 企微 / 飞书）的<b>占位实现</b>：只打日志、只记流水，不真发。
 *
 * <p>为什么留占位而不是不写：渠道分发、开关、失败隔离、投递流水这些结构是真的，
 * 换成真实 SDK 只需要替换 {@link #deliver}。而写一个"看起来发了邮件"的假实现
 * 会让人以为通道通了 —— 所以这里的日志级别是 INFO 且带 [占位] 前缀，
 * 流水表里的状态也如实记为 SENT(占位)，不冒充真实投递。
 */
public class LoggingChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingChannel.class);

    private final String name;
    private final boolean enabled;

    public LoggingChannel(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    @Override public String name() { return name; }
    @Override public boolean enabled() { return enabled; }

    @Override
    public boolean send(String userId, String title, String content) {
        return deliver(userId, title, content);
    }

    protected boolean deliver(String userId, String title, String content) {
        log.info("[占位渠道 {}] → {}：{}（未接真实 SDK）", name, userId, title);
        return true;
    }
}
