package com.lrj.oa.org.infrastructure.mapper;

import com.lrj.oa.security.annotation.DataScopeBypass;
import com.lrj.oa.org.domain.IdentityDirectoryModels.*;
import com.lrj.authz.protocol.DirectoryEvents.Assignment;
import com.lrj.authz.protocol.DirectoryEvents.ReportingLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 权威目录系统出口，所有查询显式绑定已登记 tenant，不采用个人可见范围生成残缺目录。
 * 只由组织事务捕获和固定服务凭据出口调用，不能作为员工通讯录通用查询接口。
 */
@Mapper
public interface IdentityDirectoryMapper {
    /** 普通写事务取得共享登记门闩；初始化/批量入口持排他门闩时立即拒绝并要求重试。 */
    boolean tryCaptureGate();
    /** 受控登记与 COPY/truncate 使用同一个排他门闩，避免检查之后被并发接管。 */
    boolean tryRegistrationGate();
    /** 已登记企业的写操作串行，无登记返回空；NOWAIT 不无限占住业务线程。 */
    Source lockSource(@Param("tenant") long tenant);
    /** 服务出口只读固定企业的来源状态。 */
    Source source(@Param("tenant") long tenant);
    /** 只用于排他门闩内保护全域种子/清空入口，不返回员工数据。 */
    boolean anyRegisteredSource();
    /** 初次受控登记不覆盖既有来源/环境。 */
    int register(@Param("tenant") long tenant, @Param("source") String source, @Param("environment") String environment, @Param("operator") String operator);
    /** 已持有来源行锁，序号与事件共事务，回滚不会留下空洞。 */
    Long nextSequence(@Param("tenant") long tenant);
    /** 同一员工的资料、任职与汇报变化共享专用聚合版本。 */
    default Long nextVersion(long tenant, String type, long id) {
        // 来源行锁已串行化；分步 SQL 避免现有 JSqlParser 无法解析 PG 多列冲突目标，仍在同一事务内。
        if (ensureVersion(tenant, type, id) == 0 && incrementVersion(tenant, type, id) != 1) { return null; }
        return version(tenant, type, id);
    }
    /** 首次聚合从 1 开始；重复聚合由调用者在已持来源锁的同一事务递增。 */
    int ensureVersion(@Param("tenant") long tenant, @Param("type") String type, @Param("id") long id);
    /** 版本耗尽显式返回失败，不能溢出或回绕。 */
    int incrementVersion(@Param("tenant") long tenant, @Param("type") String type, @Param("id") long id);
    /** 精确读取当前聚合版本，不能用行时间或业务实体版本代替。 */
    Long version(@Param("tenant") long tenant, @Param("type") String type, @Param("id") long id);
    /** 仅输出指定企业员工，不通过姓名邮箱猜测身份。 */
    @DataScopeBypass(reason = "权威目录捕获与封存，SQL 显式绑定已登记企业，不采用个人通讯录可见范围", tables = "oa_org.employee")
    EmployeeFact employee(@Param("tenant") long tenant, @Param("id") long id);
    /** 验证组织存在于同一来源企业，跨租户主键不能出现在目录事实中。 */
    @DataScopeBypass(reason = "权威目录捕获与封存，SQL 显式绑定已登记企业，不采用个人通讯录可见范围", tables = "oa_org.org_unit")
    OrganizationFact organization(@Param("tenant") long tenant, @Param("id") long id);
    /** 直接任职有界，多取一条使超限能显式失败，不能截断伪装完整。 */
    @DataScopeBypass(reason = "权威目录捕获与封存，SQL 显式绑定已登记企业，不采用个人通讯录可见范围", tables = "oa_org.employee_org_assignment")
    List<Assignment> assignments(@Param("tenant") long tenant, @Param("employee") long employee, @Param("today") java.time.LocalDate today);
    /** 当前与未来直接汇报线有界，历史已失效关系不冒充当前授权。 */
    @DataScopeBypass(reason = "权威目录捕获与封存，SQL 显式绑定已登记企业，不采用个人通讯录可见范围", tables = "oa_org.reporting_line")
    List<ReportingLine> reportingLines(@Param("tenant") long tenant, @Param("employee") long employee, @Param("today") java.time.LocalDate today);
    /** 业务事务结束前写入不可变事件正文和完整摘要。 */
    int append(@Param("tenant") long tenant, @Param("sequence") long sequence, @Param("event") String event,
               @Param("type") String type, @Param("aggregate") String aggregate, @Param("version") long version,
               @Param("snapshot") String snapshot, @Param("fingerprint") String fingerprint, @Param("json") String json);
    /** 初始化封存完成后再开放本来源的正常增量出口。 */
    int initialized(@Param("tenant") long tenant);
    /** 稳定主键扫描在登记排他门闩内使用，超限停止，不提供不完整全量。 */
    @DataScopeBypass(reason = "权威目录捕获与封存，SQL 显式绑定已登记企业，不采用个人通讯录可见范围", tables = "oa_org.employee")
    List<Long> employees(@Param("tenant") long tenant, @Param("limit") int limit);
    /** 初始化先输出父节点，再输出子节点，按直接树深度与主键稳定排序。 */
    @DataScopeBypass(reason = "权威目录捕获与封存，SQL 显式绑定已登记企业，不采用个人通讯录可见范围", tables = "oa_org.org_unit")
    List<Long> organizations(@Param("tenant") long tenant, @Param("limit") int limit);
    /** 服务端限定页长，序号来自不可变 Outbox，不读 UI delta/tombstone。 */
    List<Outbox> events(@Param("tenant") long tenant, @Param("after") long after, @Param("limit") int limit);
    /** 确认前精确核对源端既有序号的摘要。 */
    String fingerprint(@Param("tenant") long tenant, @Param("sequence") long sequence);
    /** auth 的成功确认单调推进；网络重试不会回退水位。 */
    int acknowledge(@Param("tenant") long tenant, @Param("sequence") long sequence, @Param("fingerprint") String fingerprint);
}
