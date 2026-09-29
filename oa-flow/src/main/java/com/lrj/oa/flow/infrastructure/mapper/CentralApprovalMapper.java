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
    /** 唯一实例只能绑定一次。 */
    int bind(@Param("id") String id,@Param("instance") long instance);
    /** 验签后登记nonce，重复传输不可第二次触发控制器。 */
    int nonce(@Param("nonce") String nonce);
    /** 固定消息用于恢复和审计，实例引用由OA负责。 */
    record Mapping(String requestId,long requestVersion,String snapshotHash,String payloadHash,String payloadJson,Long instanceId) {}
}
