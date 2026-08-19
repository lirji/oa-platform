package com.lrj.oa.file;

import com.lrj.oa.security.archrule.ControllerAuthorizationStance;
import org.junit.jupiter.api.Test;

/** 文件服务的 handler 授权立场检查。下载接口是 IDOR 重灾区，这条检查尤其不能缺。 */
class FileAuthorizationStanceTest {

    @Test
    void handlers_declare_stance() {
        ControllerAuthorizationStance.assertEveryHandlerDeclaresStance("com.lrj.oa.file");
    }

    @Test
    void public_api_justified() {
        ControllerAuthorizationStance.assertPublicApiJustified("com.lrj.oa.file");
    }
}
