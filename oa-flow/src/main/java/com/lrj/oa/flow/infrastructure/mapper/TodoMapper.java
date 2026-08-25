package com.lrj.oa.flow.infrastructure.mapper;

import com.lrj.oa.flow.api.dto.TodoView;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * 待办读模型。
 *
 * <p>工作台首屏必须一条 SQL 出结果 —— 万人级下现场 join 五张表是首屏杀手。
 * 代价是要维护冗余字段，由事件投影 + 定时对账两条路保证它不漂。
 */
@Mapper
public interface TodoMapper {

    @Insert("""
            INSERT INTO oa_flow.todo_item(tenant_id, task_id, process_instance_id, instance_id, biz_type,
                                          title, summary, applicant_user_id, applicant_name,
                                          assignee_user_id, candidate_group, state, org_id, org_path, due_at)
            VALUES (#{tenantId}, #{taskId}, #{processInstanceId}, #{instanceId}, #{bizType},
                    #{title}, #{summary}, #{applicantUserId}, #{applicantName},
                    #{assigneeUserId}, #{candidateGroup}, 'PENDING', #{orgId}, #{orgPath}, #{dueAt})
            ON CONFLICT (tenant_id, task_id) DO UPDATE SET
                assignee_user_id = excluded.assignee_user_id,
                candidate_group  = excluded.candidate_group,
                title            = excluded.title,
                summary          = excluded.summary
            """)
    int upsert(TodoUpsert cmd);

    @Update("""
            UPDATE oa_flow.todo_item SET state = #{state}, finished_at = now()
             WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND state = 'PENDING'
            """)
    int finish(@Param("tenantId") Long tenantId, @Param("taskId") String taskId, @Param("state") String state);

    @Update("""
            UPDATE oa_flow.todo_item SET state = 'CANCELLED', finished_at = now()
             WHERE process_instance_id = #{pid} AND state = 'PENDING'
            """)
    int cancelByProcess(@Param("pid") String pid);

    /**
     * 我的待办。{@code assignees} 里除了本人，还包含<b>我正在代理的人</b> ——
     * 这就是委托代理在待办侧的落点：代理人能看到并办理被代理人的待办，
     * 但不会因此获得对方的其它权限。
     */
    @Select("""
            <script>
            SELECT t.id, t.task_id AS taskId, t.process_instance_id AS processInstanceId,
                   i.process_definition_key AS processDefinitionKey,
                   t.instance_id AS instanceId, t.biz_type AS bizType, t.title, t.summary,
                   t.applicant_user_id AS applicantUserId, t.applicant_name AS applicantName,
                   t.assignee_user_id AS assigneeUserId, t.candidate_group AS candidateGroup,
                   t.state, t.org_id AS orgId,
                   t.org_path AS orgPath, t.created_at AS createdAt, t.due_at AS dueAt
              FROM oa_flow.todo_item t
              LEFT JOIN oa_flow.approval_instance i
                ON i.tenant_id = t.tenant_id AND i.id = t.instance_id
             WHERE t.state = 'PENDING'
               AND t.tenant_id = #{tenantId}
               AND t.assignee_user_id IN
               <foreach item="a" collection="assignees" open="(" separator="," close=")">#{a}</foreach>
             ORDER BY t.created_at DESC
             LIMIT #{limit}
            </script>
            """)
    List<TodoView> selectPending(@Param("tenantId") long tenantId,
                                 @Param("assignees") Collection<String> assignees,
                                 @Param("limit") int limit);

    @Select("""
            SELECT count(*) FROM oa_flow.todo_item t
             WHERE t.state = 'PENDING' AND t.tenant_id = #{tenantId}
               AND t.assignee_user_id = #{userId}
            """)
    long countPending(@Param("tenantId") long tenantId, @Param("userId") String userId);

    @Select("SELECT task_id FROM oa_flow.todo_item WHERE state = 'PENDING' AND tenant_id = #{tenantId}")
    List<String> selectPendingTaskIds(@Param("tenantId") Long tenantId);

    /** upsert 的入参。字段名与 SQL 里的 #{} 一一对应。 */
    class TodoUpsert {
        public Long tenantId; public String taskId; public String processInstanceId; public Long instanceId;
        public String bizType; public String title; public String summary;
        public String applicantUserId; public String applicantName;
        public String assigneeUserId; public String candidateGroup;
        public Long orgId; public String orgPath; public java.time.OffsetDateTime dueAt;
    }
}
