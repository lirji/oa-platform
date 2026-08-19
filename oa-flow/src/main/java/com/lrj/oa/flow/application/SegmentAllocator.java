package com.lrj.oa.flow.application;

import com.lrj.oa.flow.infrastructure.mapper.SegmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 号段领取。
 *
 * <p>必须是<b>独立事务</b>（{@code REQUIRES_NEW}）：号段推进不能跟着业务事务回滚 ——
 * 否则一张提交失败的单据会把号段退回去，下一张单据拿到<b>同一批号码</b>，
 * 撞上单号唯一约束才暴露。号码浪费一段无所谓，重复才是问题。
 *
 * <p>注意：{@code @Transactional} 标在 Mapper 接口上是<b>无效</b>的 ——
 * Mapper 由 MyBatis 代理，不走 Spring 的事务代理。必须像这样放在 Spring Bean 的方法上。
 */
@Service
public class SegmentAllocator {

    private final SegmentMapper mapper;

    public SegmentAllocator(SegmentMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> allocate(String bizTag) {
        return mapper.fetchNextSegment(bizTag);
    }
}
