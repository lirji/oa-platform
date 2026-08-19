package com.lrj.oa.flow.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 号段分配。
 *
 * <p>{@code UPDATE ... RETURNING} 一条语句完成"加步长并取回新值"，
 * 中间没有并发窗口，也不需要 {@code SELECT ... FOR UPDATE} 把提单串行化。
 */
@Mapper
public interface SegmentMapper {

    @Select("""
            UPDATE oa_flow.id_segment
               SET max_id = max_id + step, updated_at = now()
             WHERE biz_tag = #{bizTag}
            RETURNING max_id, step
            """)
    Map<String, Object> fetchNextSegment(@Param("bizTag") String bizTag);
}
