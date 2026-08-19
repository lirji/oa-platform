package com.lrj.oa.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
public class CacheInvalidationConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationConfig.class);

    @Bean
    @ConditionalOnMissingBean(InvalidationBus.class)
    public RedisInvalidationBus redisInvalidationBus(StringRedisTemplate redis,
                                                     ApplicationEventPublisher events) {
        return new RedisInvalidationBus(redis, events);
    }

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory factory, RedisInvalidationBus bus) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(factory);
        c.addMessageListener(bus, new ChannelTopic(RedisInvalidationBus.CHANNEL));
        log.info("已订阅缓存失效频道 {}", RedisInvalidationBus.CHANNEL);
        return c;
    }
}
