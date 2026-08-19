package com.lrj.oa.flow.infrastructure.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 把 OA 自己的 BPMN 推到中台（tenant=oa）。
 *
 * <p><b>为什么定义放 OA 而不是中台</b>：{@code oa-generic-approval-v1} 是 OA 的业务编排，
 * 放进中台的 classpath 就等于每加一个消费方就要改一次中台、发一次中台的版本。
 * 中台已经提供了 {@code POST /api/v1/admin/definitions/deploy}，消费方自带定义、自己部署，
 * 中台对 OA 保持零感知 —— 这才是"编排通用、语义归业务"的落法。
 *
 * <p><b>为什么先查后部</b>：中台的 deploy 端点<b>不做</b>重复过滤（不同于它自己的
 * {@code BpmnAutoDeployer} 用了 {@code enableDuplicateFiltering}），每调一次就是一个新版本。
 * 不查直接部署的话，OA 每重启一次中台就多一个版本号，翻历史时全是噪音。
 * 改了 BPMN 需要重新部署时置 {@code oa.flow.workflow.force-deploy=true}。
 */
@Configuration
@ConditionalOnProperty(name = "oa.flow.workflow.mode", havingValue = "REMOTE", matchIfMissing = true)
public class OaBpmnDeployer {

    private static final Logger log = LoggerFactory.getLogger(OaBpmnDeployer.class);
    private static final String TENANT = "oa";
    private static final String DEFINITION_KEY = "oaGenericApproval";
    private static final String RESOURCE = "bpmn/oa-generic-approval-v1.bpmn20.xml";
    private static final String DEPLOY_NAME = "oa-generic-approval-v1";

    @Bean
    public ApplicationRunner deployOaBpmn(
            @Value("${oa.flow.workflow.base-url:http://localhost:8300}") String baseUrl,
            @Value("${oa.flow.workflow.force-deploy:false}") boolean forceDeploy) {
        return args -> {
            try {
                RestClient http = RestClient.builder().baseUrl(baseUrl).build();

                List<Map<String, Object>> existing = http.get().uri("/api/v1/admin/definitions")
                        .header("X-Workflow-Tenant", TENANT)
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {});
                boolean present = existing != null && existing.stream()
                        .anyMatch(d -> DEFINITION_KEY.equals(d.get("key")));
                if (present && !forceDeploy) {
                    log.info("BPMN {} 已在中台就绪（tenant={}），跳过部署", DEFINITION_KEY, TENANT);
                    return;
                }

                String xml = new String(new ClassPathResource(RESOURCE).getContentAsByteArray(),
                        StandardCharsets.UTF_8);
                Map<?, ?> view = http.post().uri("/api/v1/admin/definitions/deploy")
                        .header("X-Workflow-Tenant", TENANT)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(Map.of("name", DEPLOY_NAME, "bpmnXml", xml))
                        .retrieve()
                        .body(Map.class);
                log.info("BPMN {} 已部署到中台：key={} version={}", DEPLOY_NAME,
                        view == null ? "?" : view.get("key"), view == null ? "?" : view.get("version"));
            } catch (Exception e) {
                // 中台不可达不该拖垮 OA 启动：OA 的组织/权限/考勤都不依赖它。
                // 但必须是 WARN 且说清后果，不能静默——否则表现为"提了单没有待办"且无从查起。
                log.warn("BPMN 部署到中台失败（{}）：{}。审批发起会进发件箱等待重投，"
                        + "但在中台拿到定义之前不会产生待办。", baseUrl, e.toString());
            }
        };
    }
}
