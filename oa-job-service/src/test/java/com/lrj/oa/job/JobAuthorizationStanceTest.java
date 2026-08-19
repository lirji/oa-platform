package com.lrj.oa.job;

import com.lrj.oa.security.archrule.ControllerAuthorizationStance;
import org.junit.jupiter.api.Test;

/** 跑批服务同样受"每个 handler 必须声明授权立场"约束。规则本体在 oa-security 的 test-jar。 */
class JobAuthorizationStanceTest {

    @Test
    void handlers_declare_stance() {
        ControllerAuthorizationStance.assertEveryHandlerDeclaresStance("com.lrj.oa.job");
    }

    @Test
    void public_api_justified() {
        ControllerAuthorizationStance.assertPublicApiJustified("com.lrj.oa.job");
    }
}
