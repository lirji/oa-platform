package com.lrj.oa.app;

import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>CI 硬纪律</b>：每个 REST handler 都必须显式声明授权立场 ——
 * 要么 {@link RequiresPerm}（需要权限），要么 {@link PublicApi}（明确公开且写明理由）。
 *
 * <p>存在意义：最常见的越权漏洞不是权限模型设计错了，而是<b>新加了一个接口忘了加注解</b>。
 * 让构建来记这件事，而不是靠 code review 的注意力。
 *
 * <p>见 DECISION_RECORD ADR-0008：接口权限是系统唯一的安全边界。
 */
class ControllerPermissionCoverageTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.lrj.oa");

    @Test
    @DisplayName("每个 @RestController 的 handler 方法都必须标注 @RequiresPerm 或 @PublicApi")
    void every_handler_declares_its_authorization_stance() {
        List<String> violations = new ArrayList<>();

        CLASSES.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .flatMap(c -> c.getMethods().stream())
                .filter(ControllerPermissionCoverageTest::isHandler)
                .filter(m -> !declaresStance(m))
                .forEach(m -> violations.add(
                        m.getOwner().getSimpleName() + "#" + m.getName()));

        assertThat(violations)
                .as("""
                    以下 handler 未声明授权立场。修法二选一：
                      · 需要权限   -> @RequiresPerm("oa:xxx:yyy")
                      · 确定要公开 -> @PublicApi(reason = "为什么可以公开")
                    不要为了让构建过去而随手加 @PublicApi —— 它是会被安全审计 grep 的。""")
                .isEmpty();
    }

    @Test
    @DisplayName("@PublicApi 必须写明理由（空字符串不算）")
    void public_api_must_justify_itself() {
        List<String> violations = new ArrayList<>();

        CLASSES.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(PublicApi.class))
                .filter(m -> m.getAnnotationOfType(PublicApi.class).reason().isBlank())
                .forEach(m -> violations.add(m.getOwner().getSimpleName() + "#" + m.getName()));

        assertThat(violations).as("@PublicApi.reason 不能为空").isEmpty();
    }

    private static boolean isHandler(JavaMethod m) {
        if (!m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) return false;
        return m.isAnnotatedWith(RequestMapping.class) || m.isMetaAnnotatedWith(RequestMapping.class);
    }

    /** 注解可以打在方法上，也可以打在整个 Controller 上（整类同一立场）。 */
    private static boolean declaresStance(JavaMethod m) {
        return m.isAnnotatedWith(RequiresPerm.class)
                || m.isAnnotatedWith(PublicApi.class)
                || m.getOwner().isAnnotatedWith(RequiresPerm.class)
                || m.getOwner().isAnnotatedWith(PublicApi.class);
    }
}
