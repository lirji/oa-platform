package com.lrj.oa.app;

import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.annotation.DataScopeBypass;
import com.lrj.oa.security.annotation.ObjectScope;
import com.lrj.oa.iam.infrastructure.datascope.GovernedTableRegistry;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** 冻结已纳入强制数据权限协议的方法，防止注解或目标表静默漂移。 */
class DataAccessSurfaceGoldenTest {

    @Test
    @DisplayName("数据权限方法、权限点、目标表和模式未发生未审查漂移")
    void data_access_surface_matches_golden() throws Exception {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.lrj.oa");
        TreeSet<String> actual = new TreeSet<>();
        for (var owner : classes) {
            for (JavaMethod method : owner.getMethods()) {
                if (method.isAnnotatedWith(DataScope.class)) {
                    DataScope scope = method.getAnnotationOfType(DataScope.class);
                    assertThat(scope.permission()).as(method.getFullName()).isNotBlank();
                    assertThat(scope.table()).as(method.getFullName()).contains(".");
                    assertThat(GovernedTableRegistry.tables()).as(method.getFullName())
                            .contains(scope.table().toLowerCase());
                    assertThat(scope.alias()).as(method.getFullName()).matches("[A-Za-z][A-Za-z0-9_]*");
                    actual.add("%s#%s SCOPED %s %s %s %s".formatted(
                            owner.getName(), method.getName(), scope.permission(), scope.table(),
                            scope.module(), scope.alias()));
                }
                if (method.isAnnotatedWith(DataScopeBypass.class)) {
                    DataScopeBypass bypass = method.getAnnotationOfType(DataScopeBypass.class);
                    assertThat(bypass.reason()).as(method.getFullName()).isNotBlank();
                    assertThat(bypass.tables()).as(method.getFullName()).isNotEmpty().allMatch(t -> !t.isBlank());
                    assertThat(owner.getPackageName())
                            .as("普通 web/application 禁止声明旁路: " + method.getFullName())
                            .doesNotMatch(".*\\.(web|application)(\\..*)?$");
                    actual.add("%s#%s BYPASS - %s - -".formatted(
                            owner.getName(), method.getName(), String.join(",", bypass.tables())));
                }
                if (method.isAnnotatedWith(ObjectScope.class)) {
                    ObjectScope scope = method.getAnnotationOfType(ObjectScope.class);
                    assertThat(scope.permission()).as(method.getFullName()).isNotBlank();
                    assertThat(scope.reason()).as(method.getFullName()).isNotBlank();
                    assertThat(scope.tables()).as(method.getFullName()).isNotEmpty().allMatch(t -> !t.isBlank());
                    actual.add("%s#%s OBJECT_%s %s %s - -".formatted(
                            owner.getName(), method.getName(), scope.strategy(), scope.permission(),
                            String.join(",", scope.tables())));
                }
            }
        }

        Path golden = Path.of("src/test/resources/data-access-surface.golden");
        List<String> expected = Files.readAllLines(golden, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertThat(actual).isNotEmpty().containsExactlyElementsOf(expected);
    }
}
