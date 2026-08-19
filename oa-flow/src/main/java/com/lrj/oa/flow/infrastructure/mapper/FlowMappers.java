package com.lrj.oa.flow.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.flow.domain.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 审批域的 CRUD mapper。复杂查询单独成类，这里只放实体级操作。 */
public final class FlowMappers {

    private FlowMappers() {}

    @Mapper
    public interface FormTemplateMapper extends BaseMapper<FormTemplate> {
        @Select("""
                SELECT * FROM oa_flow.form_template
                 WHERE tenant_id = #{tenantId} AND code = #{code} AND status = 'PUBLISHED'
                 ORDER BY version DESC LIMIT 1
                """)
        FormTemplate selectLatestPublished(@Param("tenantId") Long tenantId, @Param("code") String code);

        @Select("SELECT coalesce(max(version), 0) FROM oa_flow.form_template WHERE tenant_id = #{tenantId} AND code = #{code}")
        int maxVersion(@Param("tenantId") Long tenantId, @Param("code") String code);
    }

    @Mapper
    public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstance> {
        @Select("SELECT * FROM oa_flow.approval_instance WHERE biz_type = #{bizType} AND business_key = #{businessKey}")
        ApprovalInstance selectByBusiness(@Param("bizType") String bizType, @Param("businessKey") String businessKey);

        @Select("SELECT * FROM oa_flow.approval_instance WHERE process_instance_id = #{pid}")
        ApprovalInstance selectByProcessInstance(@Param("pid") String pid);

        @Update("UPDATE oa_flow.approval_instance SET process_instance_id = #{pid}, status = 'RUNNING' WHERE id = #{id}")
        int bindProcessInstance(@Param("id") Long id, @Param("pid") String pid);

        /** 对账用：还没结束的实例。process_instance_id 为空的说明中台那边还没起来（或起了但没回绑）。 */
        @Select("""
                SELECT * FROM oa_flow.approval_instance
                 WHERE status <> 'FINISHED'
                 ORDER BY id
                 LIMIT #{limit}
                """)
        List<ApprovalInstance> selectUnfinished(@Param("limit") int limit);
    }

    @Mapper
    public interface LeaveTypeMapper extends BaseMapper<LeaveType> {
        @Select("SELECT * FROM oa_flow.leave_type WHERE status = 'ACTIVE' ORDER BY id")
        List<LeaveType> selectActive();

        @Select("SELECT * FROM oa_flow.leave_type WHERE code = #{code}")
        LeaveType selectByCode(@Param("code") String code);
    }

    @Mapper
    public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
        @Select("SELECT * FROM oa_flow.leave_request WHERE request_no = #{no}")
        LeaveRequest selectByNo(@Param("no") String no);
    }

    @Mapper
    public interface LeaveBalanceMapper extends BaseMapper<LeaveBalance> {
        @Select("""
                SELECT * FROM oa_flow.leave_balance
                 WHERE tenant_id = #{tenantId} AND user_id = #{userId}
                   AND leave_type_id = #{typeId} AND period = #{period}
                """)
        LeaveBalance select(@Param("tenantId") Long tenantId, @Param("userId") String userId,
                            @Param("typeId") Long typeId, @Param("period") String period);

        @Insert("""
                INSERT INTO oa_flow.leave_balance(tenant_id, user_id, leave_type_id, period, total_days)
                VALUES (#{tenantId}, #{userId}, #{typeId}, #{period}, #{totalDays})
                ON CONFLICT (tenant_id, user_id, leave_type_id, period) DO NOTHING
                """)
        int grantIfAbsent(@Param("tenantId") Long tenantId, @Param("userId") String userId,
                          @Param("typeId") Long typeId, @Param("period") String period,
                          @Param("totalDays") java.math.BigDecimal totalDays);

        /**
         * 记一笔额度流水。{@code (request_id, action)} 上有唯一约束 ——
         * <b>幂等由数据库保证</b>，不靠应用层记得判重。重复调用返回 0 行，调用方据此跳过。
         */
        @Insert("""
                INSERT INTO oa_flow.leave_balance_txn(balance_id, request_id, action, days, remark)
                VALUES (#{balanceId}, #{requestId}, #{action}, #{days}, #{remark})
                ON CONFLICT (request_id, action) DO NOTHING
                """)
        int recordTxn(@Param("balanceId") Long balanceId, @Param("requestId") String requestId,
                      @Param("action") String action, @Param("days") java.math.BigDecimal days,
                      @Param("remark") String remark);
    }
}
