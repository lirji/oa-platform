package com.lrj.oa.notify.infrastructure.channel;

import com.lrj.oa.notify.NotifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部渠道分发。
 *
 * <p><b>永远在站内信落库之后、异步执行</b>：站内信是唯一保证送达的通道（它就是一行数据），
 * 外部渠道是尽力而为的加速。把外部渠道放进主事务里，等于让邮件服务器的抖动
 * 决定用户能不能提交一条通知。
 */
@Component
public class ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChannelDispatcher.class);

    private final List<NotifyChannel> channels = new ArrayList<>();
    private final JdbcTemplate jdbc;

    public ChannelDispatcher(NotifyProperties props, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        channels.add(new LoggingChannel("EMAIL", props.getChannel().isEmail()));
        channels.add(new LoggingChannel("SMS", props.getChannel().isSms()));
        channels.add(new LoggingChannel("WECOM", props.getChannel().isWecom()));
        channels.add(new LoggingChannel("FEISHU", props.getChannel().isFeishu()));
        List<String> on = channels.stream().filter(NotifyChannel::enabled).map(NotifyChannel::name).toList();
        log.info("外部渠道已启用：{}", on.isEmpty() ? "无（仅站内信）" : on);
    }

    /** 分发到全部已启用渠道。单个渠道失败只影响它自己。 */
    public void dispatch(String userId, String title, String content) {
        for (NotifyChannel ch : channels) {
            if (!ch.enabled()) continue;
            String status = "SENT";
            String error = null;
            try {
                if (!ch.send(userId, title, content)) {
                    status = "FAILED";
                    error = "渠道返回失败";
                }
            } catch (Exception e) {
                // SPI 约定不抛异常，但实现方可能违约。渠道之间必须互相隔离。
                status = "FAILED";
                error = e.toString();
            }
            logDelivery(ch.name(), userId, title, status, error);
        }
    }

    private void logDelivery(String channel, String userId, String title, String status, String error) {
        try {
            jdbc.update("INSERT INTO oa_notify.channel_log(channel, user_id, title, status, error)"
                    + " VALUES (?,?,?,?,?)", channel, userId, title, status, error);
        } catch (Exception e) {
            log.warn("渠道流水写入失败（不影响投递本身）：{}", e.toString());
        }
    }

    public List<String> enabledChannels() {
        return channels.stream().filter(NotifyChannel::enabled).map(NotifyChannel::name).toList();
    }
}
