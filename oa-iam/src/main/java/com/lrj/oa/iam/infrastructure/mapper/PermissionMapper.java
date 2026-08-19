package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /** 权限点目录一次全取（万人级也就几百到一千条），常驻内存，随 epoch 刷新。 */
    @Select("SELECT * FROM oa_iam.permission WHERE status = 'ACTIVE' ORDER BY sort_order, id")
    List<Permission> selectCatalog();
}
