package com.lrj.oa.security.archrule;

import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "每个 handler 必须显式声明授权立场"这条 CI 硬纪律的<b>可复用</b>实现。
 *
 * <p>为什么要抽出来：原来这条检查只活在 oa-app 的测试里，而 ArchUnit 扫的是
 * <b>classpath</b> —— oa-app 不依赖三个独立服务（notify / file / job），
 * 于是那三个可部署单元的 Controller <b>一个都没被检查过</b>。
 * 文件下载、通知发送、跑批触发恰恰是最容易出越权和 IDOR 的地方。
 *
 * <p>发到 test-jar 里而不是 main：这是测试期规则，不该让 archunit 进生产依赖树。
 * 各模块自己写一个三行的测试调用它，规则本体只有一份。
 */
public final class ControllerAuthorizationStance {

    private ControllerAuthorizationStance() {
    }

    /** 校验指定包下所有 {@code @RestController} 的 handler 都声明了授权立场。 */
    public static void assertEveryHandlerDeclaresStance(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);

        // 先证明确实扫到了东西。一个什么都没扫到的检查会永远"通过"——
        // 这正是这条规则以前形同虚设的原因，不能在重构它的时候再犯一次。
        assertThat(classes.stream().filter(c -> c.isAnnotatedWith(RestController.class)).count())
                .as("包 %s 下没有扫到任何 @RestController —— 检查是不是包名写错或模块没编译", basePackage)
                .isGreaterThan(0);

        List<String> violations = new ArrayList<>();
        classes.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .flatMap(c -> c.getMethods().stream())
                .filter(ControllerAuthorizationStance::isHandler)
                .filter(m -> !declaresStance(m))
                .forEach(m -> violations.add(m.getOwner().getSimpleName() + "#" + m.getName()));

        assertThat(violations)
                .as("""
                    以下 handler 未声明授权立场。修法二选一：
                      · 需要权限   -> @RequiresPerm("oa:xxx:yyy")
                      · 确定要公开 -> @PublicApi(reason = "为什么可以公开")
                    不要为了让构建过去而随手加 @PublicApi —— 它是会被安全审计 grep 的。""")
                .isEmpty();
    }

    /** 校验 {@code @PublicApi} 都写了理由。空理由等于没声明立场。 */
    public static void assertPublicApiJustified(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        List<String> violations = new ArrayList<>();
        classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(PublicApi.class))
                .filter(m -> {
                    String reason = m.getAnnotationOfType(PublicApi.class).reason();
                    return reason == null || reason.isBlank();
                })
                .forEach(m -> violations.add(m.getOwner().getSimpleName() + "#" + m.getName()));
        assertThat(violations).as("@PublicApi 必须写明 reason").isEmpty();
    }

    private static boolean isHandler(JavaMethod m) {
        return m.isAnnotatedWith(RequestMapping.class)
                || m.getAnnotations().stream()
                .anyMatch(a -> a.getRawType().isAnnotatedWith(RequestMapping.class));
    }

    private static boolean declaresStance(JavaMethod m) {
        return m.isAnnotatedWith(RequiresPerm.class)
                || m.isAnnotatedWith(PublicApi.class)
                || m.getOwner().isAnnotatedWith(RequiresPerm.class)
                || m.getOwner().isAnnotatedWith(PublicApi.class);
    }
}
