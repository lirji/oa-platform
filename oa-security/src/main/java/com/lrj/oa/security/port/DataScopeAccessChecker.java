package com.lrj.oa.security.port;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;

/** 对详情和写操作执行与列表 SQL 相同语义的数据范围断言。 */
public interface DataScopeAccessChecker {

    boolean allows(String permissionCode, Long orgId, String orgPath, String ownerUserId);

    default void require(String permissionCode, Long orgId, String orgPath, String ownerUserId) {
        if (!allows(permissionCode, orgId, orgPath, ownerUserId)) {
            throw BusinessException.of(ResultCode.DATA_SCOPE_DENIED,
                    "目标对象超出权限 " + permissionCode + " 的数据范围");
        }
    }
}
