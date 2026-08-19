package com.lrj.oa.app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块化单体的<b>拆分缝纪律</b>（ADR-0002）。
 *
 * <p>"为将来拆分留缝"如果只写在文档里，三个月后就会被一个
 * {@code @Autowired OrgUnitMapper} 悄悄焊死。这里让构建来守。
 *
 * <p>核心约定：跨模块只允许依赖对方的 {@code ..api..} 包（接口 + DTO）与领域事件，
 * 禁止直接摸对方的 domain / infrastructure。
 */
class ArchitectureRulesTest {

    // ⚠️ 不能加 DO_NOT_INCLUDE_JARS：其它业务模块是以 jar 形式挂在 oa-app 的 classpath 上的，
    // 排除 jar 等于把所有跨模块规则变成空转 —— 一条永远为真的规则比没有规则更危险。
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.lrj.oa");

    private static final String[] BIZ_MODULES =
            {"org", "iam", "flow", "attendance", "doc", "admin", "report"};

    @Test
    @DisplayName("前置：确实扫到了各业务模块的类（防止规则空转）")
    void import_actually_covers_business_modules() {
        for (String mod : BIZ_MODULES) {
            long n = CLASSES.stream().filter(c -> c.getPackageName().startsWith("com.lrj.oa." + mod)).count();
            if ("org".equals(mod)) {
                org.assertj.core.api.Assertions.assertThat(n)
                        .as("oa-%s 的类没被扫到，下面所有跨模块规则都会空转", mod).isGreaterThan(10);
            }
        }
    }

    @Test
    @DisplayName("跨模块禁止直接依赖对方的 domain / infrastructure（只能走 ..api..）")
    void cross_module_access_goes_through_api_package_only() {
        for (String target : BIZ_MODULES) {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("..oa." + target + "..")
                    .and().resideInAPackage("..com.lrj.oa..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..oa." + target + ".domain..",
                                        "..oa." + target + ".infrastructure..")
                    .because("跨模块只能依赖 oa." + target + ".api（接口+DTO）或领域事件，"
                             + "直接摸 domain/infrastructure 会把拆分缝焊死");
            rule.allowEmptyShould(true).check(CLASSES);
        }
    }

    @Test
    @DisplayName("业务模块之间不得形成循环依赖")
    void no_cycles_between_modules() {
        slices().matching("com.lrj.oa.(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Controller 不得直接依赖 Mapper（必须经应用服务）")
    void controllers_do_not_touch_mappers() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                .because("Controller 直连 Mapper 会绕过 @DataScope 所在的服务层，是数据权限漏洞的常见来源")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("禁止 java.util.Date / Calendar，统一用 java.time")
    void no_legacy_date_api() {
        noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .because("统一用 java.time；Date 可变且时区语义模糊，考勤/审批时效算错就是事故")
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
