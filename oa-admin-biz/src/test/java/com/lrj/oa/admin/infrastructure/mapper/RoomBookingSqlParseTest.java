package com.lrj.oa.admin.infrastructure.mapper;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 数据权限拦截器会用 JSQLParser 解析这条 SQL。PostgreSQL 的 {@code &&} 过不去，
 * 改成上下界比较之后必须仍能被解析，否则会议室列表会 500。
 */
class RoomBookingSqlParseTest {

    @Test
    void overlap_predicate_is_jsqlparser_safe() {
        String sql = """
                SELECT b.id, b.room_id AS roomId, r.name AS roomName, b.booker_id AS bookerId,
                       b.booker_name AS bookerName, b.subject,
                       lower(b.during) AS startAt, upper(b.during) AS endAt, b.status
                  FROM oa_admin.room_booking b
                  JOIN oa_admin.meeting_room r ON r.tenant_id=b.tenant_id AND r.id=b.room_id
                 WHERE b.tenant_id=? AND b.room_id=? AND b.status='BOOKED'
                   AND lower(b.during) < ? AND upper(b.during) > ?
                 ORDER BY lower(b.during)
                """;
        assertThatCode(() -> CCJSqlParserUtil.parse(sql)).doesNotThrowAnyException();
    }
}
