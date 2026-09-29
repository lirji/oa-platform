package com.lrj.oa.flow.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

/** 中央申请映射的持久化边界，不访问auth表。 */
@Mapper
public interface CentralApprovalMapper {
    /** 唯一键占位和实例创建同事务，崩溃不留下半张映射。 */
    int reserve(@Param("id") String id,@Param("version") long version,@Param("hash") String hash,
                @Param("payloadHash") String payloadHash,@Param("payload") String payload);
    /** 并发请求锁定同一映射，不依赖进程内锁。 */
    Mapping lock(@Param("id") String id);
    /** 查询既有结果，不推断未完成远程任务。 */
    Mapping find(@Param("id") String id);
    /** 通过真实任务业务键读取同一固定申请，不开放任意成员查询。 */
    Mapping findBusiness(@Param("business") String business);
    /** 唯一实例只能绑定一次。 */
    int bind(@Param("id") String id,@Param("instance") long instance);
    /** 验签后登记nonce，重复传输不可第二次触发控制器。 */
    int nonce(@Param("nonce") String nonce);
    /** 只选择有实际办理日志的终态单据；没有人工动作不能推断批准。 */
    java.util.List<Terminal> terminals(@Param("tenant") String tenant,@Param("app") String app,@Param("env") String env);
    /** 固定事件与申请来源唯一，重试不重新生成事件ID或时间。 */
    int enqueueDecision(@Param("request") String request,@Param("event") String event,@Param("payload") String payload,@Param("tenant") String tenant,@Param("app") String app,@Param("env") String env);
    /** 多进程有界领取，旧租约不能覆盖新回执。 */
    Delivery claimDecision(@Param("lease") String lease,@Param("tenant") String tenant,@Param("app") String app,@Param("env") String env);
    /** 终态回执只接受当前租约。 */
    int finishDecision(@Param("request") String request,@Param("lease") String lease);
    /** 有界退避后进入可见DEAD状态。 */
    int failDecision(@Param("request") String request,@Param("lease") String lease);
    /** 第五次领取后崩溃也必须收敛耗尽。 */
    int exhaustDecisions();
    /** 办理人取持久节点日志，不信任回调调用方提交的角色标签。 */
    record Terminal(String payloadJson,long instanceId,String outcome,String actorUserId,String onBehalfOf,String action,java.time.Instant actedAt) {}
    /** 已冻结事件原文与当前领取。 */
    record Delivery(String requestId,String eventId,String payloadJson) {}
    /** 固定消息用于恢复和审计，实例引用由OA负责。 */
    record Mapping(String requestId,long requestVersion,String snapshotHash,String payloadHash,String payloadJson,Long instanceId) {}
}
