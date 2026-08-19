package com.lrj.oa.notify;

import com.lrj.oa.security.archrule.ControllerAuthorizationStance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 独立服务同样受"每个 handler 必须声明授权立场"这条硬纪律约束。
 * 规则本体在 oa-security 的 test-jar 里，只此一份。
 */
class NotifyAuthorizationStanceTest {

    @Test
    @DisplayName("notify 服务的每个 handler 都声明了授权立场")
    void handlers_declare_stance() {
        ControllerAuthorizationStance.assertEveryHandlerDeclaresStance("com.lrj.oa.notify");
    }

    @Test
    @DisplayName("@PublicApi 必须写明理由")
    void public_api_justified() {
        ControllerAuthorizationStance.assertPublicApiJustified("com.lrj.oa.notify");
    }
}
