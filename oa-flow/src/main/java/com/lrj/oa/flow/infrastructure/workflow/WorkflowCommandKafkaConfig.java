package com.lrj.oa.flow.infrastructure.workflow;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 发往 workflow-platform 的命令要投到<b>中台自己的 Kafka</b>，不是 OA 的。
 *
 * <p>这一点很容易被漏掉：两个系统各有一套 compose（OA 39092 / workflow 29092），
 * 本地都叫 "kafka"，发件箱投递看起来完全成功（有 offset、无异常），
 * 但中台的消费者永远收不到——因为消息躺在另一条总线上。
 * 症状是"发起了审批却没有待办"，且日志里<b>没有任何错误</b>，极难排查。
 *
 * <p>因此这里显式建第二个 producer。留空 {@code oa.flow.workflow.kafka-bootstrap}
 * 时不装配本 bean，发件箱退回单总线行为（本地替身模式下就是这样）。
 */
@Configuration
// 用 SpEL 而不是 @ConditionalOnProperty：yml 里写成 ${OA_WORKFLOW_KAFKA:} 时属性【存在但为空】，
// @ConditionalOnProperty 会判为满足（它只排除字面量 false），于是建出一个 bootstrap.servers=""
// 的 producer，启动即炸。空字符串必须显式排掉。
@ConditionalOnExpression("'${oa.flow.workflow.kafka-bootstrap:}'.trim() != ''")
public class WorkflowCommandKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCommandKafkaConfig.class);

    /** 命令主题前缀。发件箱按它决定走哪条总线。 */
    public static final String COMMAND_TOPIC_PREFIX = "workflow.";

    @Bean("workflowCommandKafkaTemplate")
    public KafkaTemplate<String, String> workflowCommandKafkaTemplate(
            @org.springframework.beans.factory.annotation.Value("${oa.flow.workflow.kafka-bootstrap}") String bootstrap) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // 投递超时必须短于发件箱那边的 get(10s)，否则 publisher 线程会被拖住，
        // 整个发件箱的吞吐被一条发不出去的消息决定。
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 8000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(props);
        log.info("workflow 命令总线已装配：{}（前缀 {} 的主题走这条）", bootstrap, COMMAND_TOPIC_PREFIX);
        return new KafkaTemplate<>(pf);
    }
}
