package com.lrj.oa.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 通知服务配置。前缀 {@code oa.notify}。 */
@ConfigurationProperties(prefix = "oa.notify")
public class NotifyProperties {

    private final Ws ws = new Ws();
    private final Broadcast broadcast = new Broadcast();
    private final Channel channel = new Channel();

    public Ws getWs() { return ws; }
    public Broadcast getBroadcast() { return broadcast; }
    public Channel getChannel() { return channel; }

    public static class Ws {
        private int maxSessions = 20000;
        private long heartbeatMs = 30000;
        private long ticketTtlSeconds = 30;
        public int getMaxSessions() { return maxSessions; }
        public void setMaxSessions(int v) { this.maxSessions = v; }
        public long getHeartbeatMs() { return heartbeatMs; }
        public void setHeartbeatMs(long v) { this.heartbeatMs = v; }
        public long getTicketTtlSeconds() { return ticketTtlSeconds; }
        public void setTicketTtlSeconds(long v) { this.ticketTtlSeconds = v; }
    }

    public static class Broadcast {
        private int batchSize = 1000;
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int v) { this.batchSize = v; }
    }

    public static class Channel {
        private boolean email, sms, wecom, feishu;
        public boolean isEmail() { return email; }
        public void setEmail(boolean v) { this.email = v; }
        public boolean isSms() { return sms; }
        public void setSms(boolean v) { this.sms = v; }
        public boolean isWecom() { return wecom; }
        public void setWecom(boolean v) { this.wecom = v; }
        public boolean isFeishu() { return feishu; }
        public void setFeishu(boolean v) { this.feishu = v; }
    }
}
