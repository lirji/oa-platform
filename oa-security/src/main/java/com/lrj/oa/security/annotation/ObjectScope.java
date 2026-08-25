package com.lrj.oa.security.annotation;

import java.lang.annotation.*;

/**
 * 声明数据访问由业务对象自身的 owner/ACL 谓词保护，而不是组织行级 SQL 改写。
 *
 * <p>适用于知识库共享、文件所有者、审批参与人等天然对象权限模型。运行时仍要求入口
 * 权限已经通过，具体对象谓词必须由服务端查询/更新条件执行；该元数据进入 Golden 门禁。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ObjectScope {
    String permission();
    String[] tables();
    Strategy strategy();
    String reason();

    enum Strategy { OWNER, ACL, PARTICIPANT, DATA_SCOPE, RESOURCE }
}
