package com.lrj.oa.app;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.authz.protocol.ApprovalDtos.*;
import com.lrj.authz.protocol.ScopeDtos;
import com.lrj.oa.flow.application.ApprovalService;
import com.lrj.oa.flow.infrastructure.approval.*;
import com.lrj.oa.flow.infrastructure.mapper.*;
import com.lrj.oa.flow.infrastructure.workflow.*;
import com.lrj.oa.iam.application.DelegationAuthorizationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实OA迁移/编排/SQL/Outbox验证；工作流引擎消费留给P4-07跨进程验收。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CentralApprovalPostgresIT {
    private JdbcTemplate jdbc;
    private CentralApprovalService service;
    private CentralApprovalMapper centralMapper;
    private CentralApprovalProperties settings;
    private final String tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString(),approver=UUID.randomUUID().toString();
    private DataSourceTransactionManager transactions;

    @BeforeAll void open() throws Exception {
        var file=Path.of(Objects.requireNonNull(System.getenv("OA_APPROVAL_TEST_CONFIG")));
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var properties=new Properties();try(var reader=Files.newBufferedReader(file)){properties.load(reader);}
        assertThat(properties.getProperty("jdbc.url")).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/auth_gov_p1_test_[a-z0-9_]+");
        var ds=new DriverManagerDataSource(properties.getProperty("jdbc.url"),properties.getProperty("jdbc.username"),properties.getProperty("jdbc.password"));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").outOfOrder(true).cleanDisabled(true).load().migrate();
        jdbc=new JdbcTemplate(ds);transactions=new DataSourceTransactionManager(ds);
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        for(Class<?> type:List.of(CentralApprovalMapper.class,FlowMappers.ApprovalInstanceMapper.class,FlowMappers.FormTemplateMapper.class,OutboxMapper.class,TodoMapper.class)) configuration.addMapper(type);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/CentralApprovalMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        var beans=new DefaultListableBeanFactory();
        var approvals=proxy(new ApprovalService(session.getMapper(FlowMappers.ApprovalInstanceMapper.class),session.getMapper(FlowMappers.FormTemplateMapper.class),
                session.getMapper(OutboxMapper.class),session.getMapper(TodoMapper.class),mock(WorkflowGateway.class),beans.getBeanProvider(LocalWorkflowGateway.class),new ObjectMapper(),mock(DelegationAuthorizationService.class)));
        var config=new CentralApprovalProperties(true,tenant,"commerce","test",1,"k".repeat(43),true,
                Map.of(member,new CentralApprovalProperties.Bridge(1,"requester",null,1,"/1/"),approver,new CentralApprovalProperties.Bridge(1,"approver",null,1,"/1/")));
        centralMapper=session.getMapper(CentralApprovalMapper.class);settings=config;
        service=proxy(new CentralApprovalService(centralMapper,config,approvals));
    }
    @SuppressWarnings("unchecked") private <T>T proxy(T target){
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();
    }
    private Start command(){return new Start(tenant,"commerce","test",UUID.randomUUID().toString(),1,"a".repeat(64),UUID.randomUUID().toString(),1,"b".repeat(64),member,1,approver,1,UUID.randomUUID().toString(),List.of("commerce.store.read"),new ScopeDtos.Rule(1,"store",List.of(new ScopeDtos.Clause(ScopeDtos.Kind.TENANT_ALL,List.of(),false))),Instant.now().toString(),Instant.now().plusSeconds(60).toString(),"测试");}
    @Test void concurrentStartsAndLostResponseRecoveryHaveOneRealInstanceAndOutbox() throws Exception {
        var command=command();
        try(var pool=Executors.newFixedThreadPool(3)){
            Callable<Instance> call=()->service.start(command);
            var results=pool.invokeAll(List.of(call,call,call));var first=results.getFirst().get();
            for(var result:results)assertThat(result.get()).isEqualTo(first);
            assertThat(service.lookup(new Lookup(tenant,"commerce","test",command.requestId(),1,command.snapshotHash()))).isEqualTo(first);
            assertThat(jdbc.queryForObject("select count(*) from oa_flow.approval_instance where business_key=?",Integer.class,command.businessKey())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from oa_flow.oa_outbox where msg_key=?",Integer.class,command.businessKey())).isEqualTo(1);
        }
    }
    @Test void unknownIdentityAndChangedBodyDoNotCreateNewInstances(){
        var c=command();service.start(c);
        var changed=new Start(c.tenantId(),c.applicationId(),c.environment(),c.requestId(),1,c.snapshotHash(),c.policyId(),1,c.policyHash(),c.membershipId(),1,c.approverMembershipId(),1,c.roleId(),c.capabilities(),c.scopeRule(),c.validFrom(),c.validTo(),"expanded");
        assertThatThrownBy(()->service.start(changed)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(()->service.lookup(new Lookup(tenant,"other","test",c.requestId(),1,c.snapshotHash()))).hasMessageContaining("403");
        var unknown=new Start(c.tenantId(),c.applicationId(),c.environment(),UUID.randomUUID().toString(),1,c.snapshotHash(),c.policyId(),1,c.policyHash(),UUID.randomUUID().toString(),1,c.approverMembershipId(),1,c.roleId(),c.capabilities(),c.scopeRule(),c.validFrom(),c.validTo(),c.reason());
        assertThatThrownBy(()->service.start(unknown)).hasMessageContaining("403");
    }
    @Test void failedOutboxRollsBackBothMappingAndApproval(){
        var c=command();String constraint="p4_fail_"+c.requestId().replace("-","");
        jdbc.execute("alter table oa_flow.oa_outbox add constraint "+constraint+" check(msg_key <> '"+c.businessKey()+"')");
        try {
            assertThatThrownBy(()->service.start(c)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select count(*) from oa_flow.central_access_request where request_id=?",Integer.class,c.requestId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from oa_flow.approval_instance where business_key=?",Integer.class,c.businessKey())).isZero();
        } finally {jdbc.execute("alter table oa_flow.oa_outbox drop constraint "+constraint);}
    }
    @Test void callbackRequiresActualActorLogAndConfirmedEngineEndThenRetriesFixedEvent() throws Exception {
        var c=command();var instance=service.start(c);long id=Long.parseLong(instance.approvalInstanceId());
        jdbc.update("update oa_flow.approval_instance set status='FINISHED',outcome='APPROVED' where id=?",id);
        jdbc.update("insert into oa_flow.approval_node_log(instance_id,task_id,actor_user_id,action) values(?,?,'approver','APPROVE')",id,"task-p4");
        var workflow=mock(WorkflowGateway.class);when(workflow.remote()).thenReturn(true);
        when(workflow.findProcess(c.businessKey())).thenReturn(Optional.empty());
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var received=new java.util.ArrayList<String>();var respondOk=new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/internal/oa-approval/v1/events",exchange->{
            byte[] body=exchange.getRequestBody().readAllBytes();
            com.lrj.authz.protocol.ApprovalSignature.verify("k".repeat(43),"oa-platform","test",exchange.getRequestURI().getPath(),body,exchange.getRequestHeaders().getFirst(com.lrj.authz.protocol.ApprovalSignature.HEADER),Instant.now());
            received.add(new String(body,java.nio.charset.StandardCharsets.UTF_8));
            var event=CentralApprovalWire.read(body,Decision.class);
            byte[] response=CentralApprovalWire.body(new Receipt(event.eventId(),"RECEIVED",null)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(respondOk.get()?202:503,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var delivery=new CentralDecisionDelivery(centralMapper,settings,workflow,transactions,"http://127.0.0.1:"+server.getAddress().getPort()+"/internal/oa-approval/v1/events","k".repeat(43));
            delivery.capture();
            assertThat(jdbc.queryForObject("select count(*) from oa_flow.central_decision_outbox where request_id=?",Integer.class,c.requestId())).isZero();
            when(workflow.findProcess(c.businessKey())).thenReturn(Optional.of(new WorkflowGateway.ProcessInfo("real-ref",c.businessKey(),false)));
            when(workflow.findTasks("oaGenericApproval",c.businessKey())).thenReturn(List.of());
            delivery.capture();delivery.capture();delivery.deliver();
            assertThat(jdbc.queryForObject("select state from oa_flow.central_decision_outbox where request_id=?",String.class,c.requestId())).isEqualTo("PENDING");
            jdbc.update("update oa_flow.central_decision_outbox set next_attempt_at=clock_timestamp() where request_id=?",c.requestId());
            respondOk.set(true);delivery.deliver();
            assertThat(received).hasSize(2);assertThat(received.get(0)).isEqualTo(received.get(1));
            assertThat(jdbc.queryForObject("select state from oa_flow.central_decision_outbox where request_id=?",String.class,c.requestId())).isEqualTo("DONE");
        } finally {server.stop(0);}
    }

}
