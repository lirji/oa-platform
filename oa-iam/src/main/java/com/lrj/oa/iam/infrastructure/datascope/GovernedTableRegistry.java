package com.lrj.oa.iam.infrastructure.datascope;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 已完成严格迁移的数据表注册表。
 *
 * <p>这些表只要经过 MyBatis 就必须存在 {@code @DataScope} 执行上下文；否则在 SQL 发出前拒绝。
 * 新表完成方法 Golden、权限绑定和 IDOR 回归后，才能加入这里。
 */
@Component
public class GovernedTableRegistry {

    private static final Set<String> TABLES = Set.of(
            "oa_org.v_employee_directory",
            "oa_admin.asset",
            "oa_admin.room_booking",
            "oa_admin.visitor",
            "oa_doc.official_doc",
            "oa_sys.v_headcount",
            "oa_sys.v_approval_efficiency_scoped",
            "oa_sys.v_attendance_summary_scoped",
            "oa_sys.v_asset_summary_scoped"
    );

    public boolean contains(String table) {
        return TABLES.contains(normalize(table));
    }

    public static Set<String> tables() { return TABLES; }

    private static String normalize(String table) {
        return table == null ? "" : table.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }
}
