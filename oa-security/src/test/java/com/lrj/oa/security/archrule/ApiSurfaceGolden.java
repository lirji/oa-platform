package com.lrj.oa.security.archrule;

import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>API 契约 golden 测试</b>：把「HTTP 方法 + 路径 + 权限点」冻结成一份快照文件，
 * 任何改动都必须显式更新那份文件才能通过构建。
 *
 * <p><b>它防的是什么</b>：前端按 {@code permCode} 渲染按钮、按路径调接口。
 * 后端悄悄把一个接口的权限点从 {@code oa:org:view} 改成 {@code oa:org:admin}，
 * 或者把路径从 {@code /units} 改成 {@code /org-units} ——
 * 编译过、测试过、冒烟也可能过（冒烟只覆盖主流程），
 * 而前端会在<b>某个没人常点的页面</b>上静默地藏起按钮或 404。
 * 这类问题发现得越晚越贵。
 *
 * <p>它<b>不</b>防字段级的契约变化（那由 {@code /v3/api-docs} 与前端生成的类型负责），
 * 只防"这个能力还在不在、还归谁管"。这是前端最依赖、也最容易被无意改掉的一层。
 *
 * <p>更新 golden 的正确姿势：确认改动是<b>有意的</b>，然后把断言失败信息里打印的
 * 实际清单写回快照文件，并在提交信息里说明为什么改。让它成为一次需要解释的动作，
 * 而不是一次沉默的漂移。
 */
public final class ApiSurfaceGolden {

    private ApiSurfaceGolden() {
    }

    /** 扫描一个包，返回排序后的 "METHOD path -> permCode" 行。 */
    public static List<String> surfaceOf(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);

        TreeSet<String> lines = new TreeSet<>();
        classes.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .forEach(c -> {
                    String base = c.isAnnotatedWith(RequestMapping.class)
                            ? firstPath(c.getAnnotationOfType(RequestMapping.class).value(),
                                        c.getAnnotationOfType(RequestMapping.class).path())
                            : "";
                    for (JavaMethod m : c.getMethods()) {
                        MethodMapping mm = mappingOf(m);
                        if (mm == null) continue;
                        lines.add("%-6s %-52s %s".formatted(mm.verb, base + mm.path, stanceOf(m)));
                    }
                });
        return new ArrayList<>(lines);
    }

    /**
     * 与 golden 文件比对。
     *
     * @param goldenPath 相对模块根的路径，如 {@code src/test/resources/api-surface.golden}
     */
    public static void assertMatchesGolden(String basePackage, String goldenPath) throws IOException {
        List<String> actual = surfaceOf(basePackage);
        assertThat(actual)
                .as("包 %s 下没有扫到任何 handler —— 检查包名或模块是否已编译", basePackage)
                .isNotEmpty();

        Path p = Path.of(goldenPath);
        if (!Files.exists(p)) {
            // 首次运行：把快照写出来，并明确失败。
            // 自动写完就通过的话，第一次跑的人根本不会去看内容 —— 而 golden 文件的价值
            // 恰恰在于有人真的读过它一遍。
            Files.createDirectories(p.getParent());
            Files.writeString(p, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
            throw new AssertionError("""
                    golden 文件不存在，已生成：%s
                    请【逐行读一遍】确认接口面与权限点符合预期，然后重新运行测试。""".formatted(p));
        }

        List<String> expected = Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank() && !l.startsWith("#"))
                .toList();

        assertThat(actual)
                .as("""
                    API 契约发生变化。前端按路径与权限点工作，这类改动不会在编译期暴露，
                    却会让某个页面静默地藏起按钮或 404。

                    确认改动是【有意的】之后，把下面的实际清单写回 %s：

                    %s
                    """.formatted(p, String.join("\n", actual)))
                .containsExactlyElementsOf(expected);
    }

    private record MethodMapping(String verb, String path) {}

    private static MethodMapping mappingOf(JavaMethod m) {
        for (var ann : m.getAnnotations()) {
            String simple = ann.getRawType().getSimpleName();
            String verb = switch (simple) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "DeleteMapping" -> "DELETE";
                case "PatchMapping" -> "PATCH";
                default -> null;
            };
            if (verb == null) continue;
            String path = "";
            Object v = ann.getProperties().get("value");
            Object pth = ann.getProperties().get("path");
            path = firstPath(asArray(v), asArray(pth));
            return new MethodMapping(verb, path);
        }
        return null;
    }

    private static String[] asArray(Object o) {
        if (o instanceof String[] a) return a;
        if (o instanceof Object[] a) {
            String[] out = new String[a.length];
            for (int i = 0; i < a.length; i++) out[i] = String.valueOf(a[i]);
            return out;
        }
        return new String[0];
    }

    private static String firstPath(String[] value, String[] path) {
        if (value != null && value.length > 0 && !value[0].isBlank()) return value[0];
        if (path != null && path.length > 0 && !path[0].isBlank()) return path[0];
        return "";
    }

    private static String stanceOf(JavaMethod m) {
        if (m.isAnnotatedWith(RequiresPerm.class)) {
            RequiresPerm rp = m.getAnnotationOfType(RequiresPerm.class);
            String codes = String.join("|", rp.value());
            return rp.elevation() ? codes + " [需提权]" : codes;
        }
        if (m.isAnnotatedWith(PublicApi.class)) return "(public)";
        // 走到这里说明 ControllerAuthorizationStance 那条检查漏了 —— golden 里把它标出来
        return "!! 无授权声明 !!";
    }
}
