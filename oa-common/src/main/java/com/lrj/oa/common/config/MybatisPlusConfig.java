package com.lrj.oa.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 统一配置。放在 oa-common 且依赖标 optional：
 * 装了 MP 的部署单元（oa-app / oa-job-service）自动获得，没装的（oa-file-service）无感。
 *
 * <p><b>拦截器顺序有讲究</b>：分页拦截器必须在最后，否则它改写出的 count 语句
 * 会绕过前面的拦截器 —— Phase 2 的 {@code @DataScope} 数据权限拦截器要插在分页<b>之前</b>，
 * 否则会出现"列表被数据权限过滤了、总数却没被过滤"的经典越权。
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusConfig {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁：org_unit / employee / approval_instance 等带 version 列的实体
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 防全表更新删除：漏写 where 的 UPDATE/DELETE 直接抛异常，而不是清库
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 分页（必须最后加）
        PaginationInnerInterceptor page = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        page.setMaxLimit(500L);          // 单页上限，防 size=999999 拖垮库
        page.setOverflow(false);
        interceptor.addInnerInterceptor(page);

        return interceptor;
    }
}
