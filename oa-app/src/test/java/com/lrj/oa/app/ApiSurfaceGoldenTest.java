package com.lrj.oa.app;

import com.lrj.oa.security.archrule.ApiSurfaceGolden;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API 契约 golden：冻结"HTTP 方法 + 路径 + 权限点"。
 *
 * <p>前端按路径与 permCode 工作。悄悄改一个接口的权限点或路径，编译过、单测过、
 * 冒烟也可能过（冒烟只覆盖主流程），而前端会在某个不常点的页面上静默藏起按钮或 404。
 * 让这类改动必须显式更新快照，就把"沉默的漂移"变成"一次需要解释的动作"。
 */
class ApiSurfaceGoldenTest {

    @Test
    @DisplayName("接口面与权限点未发生未声明的变化")
    void api_surface_matches_golden() throws Exception {
        ApiSurfaceGolden.assertMatchesGolden("com.lrj.oa", "src/test/resources/api-surface.golden");
    }
}
