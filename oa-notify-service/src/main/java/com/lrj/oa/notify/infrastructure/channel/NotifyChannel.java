package com.lrj.oa.notify.infrastructure.channel;

/**
 * 外部投递渠道的 SPI。站内信不在此列 —— 它是一张表，不是"外部"。
 *
 * <p>所有实现默认<b>关闭</b>：引入即安全。打开一个没配好凭据的渠道，
 * 每条通知都会在它上面等一次网络超时，把站内信这条主链路一起拖慢。
 */
public interface NotifyChannel {

    /** 渠道名，会落进 channel_log。 */
    String name();

    /** 是否启用。关闭时 {@link #send} 不会被调用。 */
    boolean enabled();

    /**
     * 投递。<b>不得抛异常</b>：一个渠道挂掉不该让整条通知失败 —— 站内信已经落库了。
     *
     * @return 是否投递成功
     */
    boolean send(String userId, String title, String content);
}
