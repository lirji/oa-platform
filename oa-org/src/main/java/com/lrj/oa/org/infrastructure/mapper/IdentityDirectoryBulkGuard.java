package com.lrj.oa.org.infrastructure.mapper;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import java.sql.Connection;
import java.sql.SQLException;

/** COPY 使用自有 JDBC 事务，必须在同一连接上保护登记窗口，不能另开 Mapper 事务检查。 */
public final class IdentityDirectoryBulkGuard {
    private IdentityDirectoryBulkGuard() {}

    /** 持有排他门闩直到批量事务结束；任一企业已经接管后禁止绕开事务捕获器。 */
    public static void requireUnmanaged(Connection connection) throws SQLException {
        if (connection.getAutoCommit()) { throw new IllegalStateException("批量目录保护要求显式事务"); }
        try (var statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(7140310401)");
             var result = statement.executeQuery()) {
            if (!result.next() || !result.getBoolean(1)) { throw conflict(); }
        }
        try (var statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM oa_org.identity_directory_source)");
             var result = statement.executeQuery()) {
            if (!result.next() || result.getBoolean(1)) { throw conflict(); }
        }
    }

    private static BusinessException conflict() {
        return BusinessException.of(ResultCode.CONFLICT, "目录已接管或正在变更，禁止批量旁路写入和清空");
    }
}
