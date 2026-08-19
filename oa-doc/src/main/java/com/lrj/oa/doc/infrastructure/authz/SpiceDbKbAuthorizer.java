package com.lrj.oa.doc.infrastructure.authz;

import com.lrj.oa.doc.api.dto.DocDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SpiceDB 路径（{@code oa.authz.spicedb.enabled=true}）。
 *
 * <p><b>为什么现在只是骨架而不是完整实现</b>：接通它需要先把 {@code oa.zed} 与
 * knowledge / his / recsys / risk 四份现存 schema <b>合并后整体写入</b> ——
 * SpiceDB 的 {@code :8543} 写 schema 是<b>全量替换</b>语义（ADR 风险 R7），
 * 单独写一份 oa.zed 会把另外四个系统的定义全部抹掉。
 * 合并脚本属于跨系统运维动作，不能顺手在业务代码里做掉。
 *
 * <p>所以这里的选择是：把开关和端口留好、把风险写清楚、让默认路径（本地表）
 * 完整可用，而<b>不是</b>写一个"看起来接通了"的实现。一个会静默降级的授权器
 * 比没有更危险 —— 它启用时不报错，只是谁都能看。
 */
@Component
@ConditionalOnProperty(name = "oa.authz.spicedb.enabled", havingValue = "true")
public class SpiceDbKbAuthorizer implements KbAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(SpiceDbKbAuthorizer.class);

    public SpiceDbKbAuthorizer() {
        log.warn("""

            ╔══════════════════════════════════════════════════════════════════╗
            ║  oa.authz.spicedb.enabled=true —— SpiceDB 授权路径【尚未接通】     ║
            ║  接通前置：把 oa.zed 与 knowledge/his/recsys/risk 合并后整体写入   ║
            ║  （:8543 写 schema 是全量替换，单写会抹掉另外四份）。              ║
            ║  在那之前请置回 false，用内置 kb_share 表 —— 它是完整可用的。      ║
            ╚══════════════════════════════════════════════════════════════════╝""");
    }

    @Override public String name() { return "SPICEDB(未接通)"; }

    @Override
    public boolean canAccess(String userId, String resourceType, long resourceId, String level) {
        // fail-closed：未接通时一律拒绝，而不是放行。
        // 放行会让"打开开关"变成一次静默的全量开放，这比功能不可用严重得多。
        throw new UnsupportedOperationException(
                "SpiceDB 授权路径未接通（需先合并写入 oa.zed）。请置 oa.authz.spicedb.enabled=false。");
    }

    @Override
    public DocDtos.KbAccessExplain explain(String userId, long docId) {
        return new DocDtos.KbAccessExplain(docId, userId, false, null, name(),
                "SpiceDB 路径未接通；置 oa.authz.spicedb.enabled=false 走内置 kb_share");
    }
}
