package com.lrj.oa.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冻结尚未迁移的请求路径 JDBC 旁路。
 *
 * <p>这是迁移期阻断门禁：现有债务逐项删除，任何新增 application/web 直接 JDBC 都会使构建失败。
 */
class JdbcBypassArchitectureTest {

    private static final Pattern DIRECT_JDBC_IMPORT = Pattern.compile(
            "(?:org\\.springframework\\.jdbc\\.core\\.(?:JdbcTemplate|NamedParameterJdbcTemplate)"
                    + "|javax\\.sql\\.DataSource|java\\.sql\\.Connection)");

    @Test
    @DisplayName("application/web 直接 JDBC 旁路没有增长")
    void direct_jdbc_surface_matches_reviewed_baseline() throws Exception {
        Path root = repositoryRoot();
        TreeSet<String> actual = new TreeSet<>();
        try (var files = productionSources(root).stream()) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/application/")
                            || path.toString().contains("/web/"))
                    .forEach(path -> {
                        try {
                            if (DIRECT_JDBC_IMPORT.matcher(Files.readString(path)).find()) {
                                actual.add(root.relativize(path).toString());
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException("无法检查 JDBC 旁路: " + path, e);
                        }
                    });
        }

        List<String> expected = Files.readAllLines(
                Path.of("src/test/resources/jdbc-bypass-surface.golden"), StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertThat(actual)
                .as("新增 application/web 直接 JDBC 必须迁移到 MyBatis，或经安全评审更新迁移基线")
                .containsExactlyElementsOf(expected);

        try (var files = productionSources(root).stream()) {
            List<Path> ignored = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("@InterceptorIgnore(dataPermission = \"true\")");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            assertThat(ignored).as("业务代码禁止关闭 MyBatis 数据权限拦截器").isEmpty();
        }
    }

    @Test
    @DisplayName("扫描包含 OA 模块生产代码，但不包含嵌套依赖仓库或构建产物")
    void production_scan_is_scoped_to_repository_modules(@TempDir Path root) throws Exception {
        Path module = root.resolve("oa-example");
        Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Path source = module.resolve("src/main/java/example/application/Service.java");
        Path dependency = root.resolve(".ci-deps/auth-platform/auth-platform-governance/src/main/java/example/application/Service.java");
        Path test = module.resolve("src/test/java/example/ServiceTest.java");
        Path generated = module.resolve("target/generated-sources/example/Generated.java");
        for (Path path : List.of(source, dependency, test, generated)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "import javax.sql.DataSource;");
        }
        assertThat(productionSources(root)).containsExactly(source);
        assertThat(DIRECT_JDBC_IMPORT.matcher(Files.readString(source)).find()).isTrue();
    }

    /** 只遍历仓库直属 Maven 模块的生产源码，避免把 CI 拉取的其他仓库当作 OA 债务。 */
    private static List<Path> productionSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (var modules = Files.list(root)) {
            for (Path module : modules.filter(path -> Files.isRegularFile(path.resolve("pom.xml"))).toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) continue;
                try (var files = Files.walk(sourceRoot)) {
                    sources.addAll(files.filter(path -> path.toString().endsWith(".java")).toList());
                }
            }
        }
        return sources;
    }

    private static Path repositoryRoot() {
        Path root = Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        if (Files.isDirectory(root.resolve("oa-app"))) return root;
        Path parent = root.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("oa-app"))) return parent;
        throw new IllegalStateException("无法定位 oa-platform 仓库根目录: " + root);
    }
}
