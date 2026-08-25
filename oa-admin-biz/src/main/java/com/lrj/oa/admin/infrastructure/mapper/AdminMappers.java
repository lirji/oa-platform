package com.lrj.oa.admin.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

/**
 * 行政域 Mapper。必须放在 {@code ..infrastructure.mapper} 包下（@MapperScan 只扫这个通配）。
 *
 * <p>★ 需要数据权限的查询<b>必须</b>走 MyBatis：{@code @DataScope} 靠 MyBatis 拦截器改写 SQL，
 * 用 JdbcTemplate 手写会绕过它，注解形同虚设而且完全没有报错。
 */
public final class AdminMappers {

    private AdminMappers() {}

    public static class AssetRow {
        public Long id;
        public String assetNo;
        public String name;
        public String category;
        public String status;
        public String holderId;
        public Long orgId;
    }

    @Mapper
    public interface AssetQueryMapper {
        /** 别名 a 要与 {@code @DataScope(alias = "a")} 对上，拦截器按别名拼 org_path 前缀条件。 */
        @Select("""
                <script>
                SELECT a.id, a.asset_no AS assetNo, a.name, a.category, a.status,
                       a.holder_id AS holderId, a.org_id AS orgId
                  FROM oa_admin.asset a
                 WHERE 1 = 1
                 <if test="status != null and status != ''"> AND a.status = #{status} </if>
                 ORDER BY a.id
                 LIMIT #{limit}
                </script>
                """)
        List<AssetRow> search(@Param("status") String status, @Param("limit") int limit);

        @Select("SELECT a.status FROM oa_admin.asset a WHERE a.id=#{id} FOR UPDATE")
        String statusForUpdate(@Param("id") long id);

        @Update("UPDATE oa_admin.asset a SET status='IN_USE', holder_id=#{holderId} WHERE a.id=#{id} AND a.status='IDLE'")
        int claim(@Param("id") long id, @Param("holderId") String holderId);

        @Update("UPDATE oa_admin.asset a SET status='IDLE', holder_id=NULL WHERE a.id=#{id} AND a.holder_id=#{holderId} AND a.status='IN_USE'")
        int giveBack(@Param("id") long id, @Param("holderId") String holderId);

        @Insert("""
                INSERT INTO oa_admin.asset_txn(asset_id, action, actor_id, from_status, to_status, remark)
                VALUES (#{assetId}, #{action}, #{actorId}, #{fromStatus}, #{toStatus}, #{remark})
                """)
        int insertTxn(@Param("assetId") long assetId, @Param("action") String action,
                      @Param("actorId") String actorId, @Param("fromStatus") String fromStatus,
                      @Param("toStatus") String toStatus, @Param("remark") String remark);
    }

    public static class SupplyRow {
        public Long id;
        public String code;
        public String name;
        public String unit;
        public Integer stock;
    }

    @Mapper
    public interface SupplyMapper {
        @Select("SELECT id, code, name, unit, stock FROM oa_admin.supply WHERE tenant_id=#{tenantId} ORDER BY code")
        List<SupplyRow> list(@Param("tenantId") long tenantId);

        @Update("UPDATE oa_admin.supply SET stock=stock-#{qty} WHERE tenant_id=#{tenantId} AND id=#{id} AND stock&gt;=#{qty}")
        int take(@Param("tenantId") long tenantId, @Param("id") long id, @Param("qty") int qty);

        @Insert("""
                INSERT INTO oa_admin.supply_request(supply_id, requester_id, qty, org_id, org_path)
                VALUES (#{supplyId}, #{requesterId}, #{qty}, #{orgId}, #{orgPath})
                """)
        int insertRequest(@Param("supplyId") long supplyId, @Param("requesterId") String requesterId,
                          @Param("qty") int qty, @Param("orgId") Long orgId, @Param("orgPath") String orgPath);
    }

    public static class RoomRow {
        public Long id;
        public String code;
        public String name;
        public String location;
        public Integer capacity;
        public String equipment;
        public String status;
    }

    public static class BookingRow {
        public Long id;
        public Long roomId;
        public String roomName;
        public String bookerId;
        public String bookerName;
        public String subject;
        public OffsetDateTime startAt;
        public OffsetDateTime endAt;
        public String status;
    }

    @Mapper
    public interface RoomMapper {
        @Select("SELECT id, code, name, location, capacity, equipment, status FROM oa_admin.meeting_room WHERE tenant_id=#{tenantId} AND status='ACTIVE' ORDER BY code")
        List<RoomRow> activeRooms(@Param("tenantId") long tenantId);

        @Select("SELECT capacity FROM oa_admin.meeting_room WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE'")
        Integer activeCapacity(@Param("tenantId") long tenantId, @Param("id") long id);

        @Select("""
                INSERT INTO oa_admin.room_booking
                    (tenant_id, room_id, booker_id, booker_name, subject, attendees, during, org_id, org_path)
                VALUES (#{tenantId}, #{roomId}, #{bookerId}, #{bookerName}, #{subject}, #{attendees},
                        tstzrange(#{startAt}, #{endAt}, '[)'), #{orgId}, #{orgPath})
                RETURNING id
                """)
        Long insertBooking(@Param("tenantId") long tenantId, @Param("roomId") long roomId,
                           @Param("bookerId") String bookerId, @Param("bookerName") String bookerName,
                           @Param("subject") String subject, @Param("attendees") int attendees,
                           @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
                           @Param("orgId") Long orgId, @Param("orgPath") String orgPath);

        @Select("""
                SELECT b.id, b.room_id AS roomId, r.name AS roomName, b.booker_id AS bookerId,
                       b.booker_name AS bookerName, b.subject,
                       lower(b.during) AS startAt, upper(b.during) AS endAt, b.status
                  FROM oa_admin.room_booking b
                  JOIN oa_admin.meeting_room r ON r.tenant_id=b.tenant_id AND r.id=b.room_id
                 WHERE b.tenant_id=#{tenantId} AND b.room_id=#{roomId} AND b.status='BOOKED'
                   AND b.during &amp;&amp; tstzrange(#{from}, #{to}, '[)')
                 ORDER BY lower(b.during)
                """)
        List<BookingRow> bookings(@Param("tenantId") long tenantId, @Param("roomId") long roomId,
                                  @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

        @Update("UPDATE oa_admin.room_booking SET status='CANCELLED' WHERE tenant_id=#{tenantId} AND id=#{id} AND booker_id=#{bookerId} AND status='BOOKED'")
        int cancelOwn(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("bookerId") String bookerId);
    }

    public static class VisitorRow {
        public Long id;
        public String name;
        public String company;
        public String hostId;
        public OffsetDateTime visitAt;
        public OffsetDateTime leaveAt;
        public String status;
    }

    @Mapper
    public interface VehicleVisitorMapper {
        @Select("SELECT id, plate_no, model, seats FROM oa_admin.vehicle WHERE tenant_id=#{tenantId} AND status='ACTIVE' ORDER BY plate_no")
        List<Map<String, Object>> activeVehicles(@Param("tenantId") long tenantId);

        @Select("""
                INSERT INTO oa_admin.vehicle_booking
                    (tenant_id, vehicle_id, booker_id, purpose, during, org_id, org_path)
                VALUES (#{tenantId}, #{vehicleId}, #{bookerId}, #{purpose},
                        tstzrange(#{startAt}, #{endAt}, '[)'), #{orgId}, #{orgPath})
                RETURNING id
                """)
        Long insertVehicleBooking(@Param("tenantId") long tenantId, @Param("vehicleId") long vehicleId,
                                  @Param("bookerId") String bookerId, @Param("purpose") String purpose,
                                  @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
                                  @Param("orgId") Long orgId, @Param("orgPath") String orgPath);

        @Select("""
                INSERT INTO oa_admin.visitor
                    (tenant_id, name, phone_enc, phone_hash, company, host_id, visit_at, org_id, org_path)
                VALUES (#{tenantId}, #{name}, #{phoneEnc}, #{phoneHash}, #{company}, #{hostId},
                        #{visitAt}, #{orgId}, #{orgPath}) RETURNING id
                """)
        Long insertVisitor(@Param("tenantId") long tenantId, @Param("name") String name,
                           @Param("phoneEnc") byte[] phoneEnc, @Param("phoneHash") String phoneHash,
                           @Param("company") String company, @Param("hostId") String hostId,
                           @Param("visitAt") OffsetDateTime visitAt, @Param("orgId") Long orgId,
                           @Param("orgPath") String orgPath);

        @Update("UPDATE oa_admin.visitor v SET status='CHECKED_IN' WHERE v.tenant_id=#{tenantId} AND v.id=#{id} AND v.status='BOOKED'")
        int checkIn(@Param("tenantId") long tenantId, @Param("id") long id);

        @Update("UPDATE oa_admin.visitor v SET status='LEFT', leave_at=now() WHERE v.tenant_id=#{tenantId} AND v.id=#{id} AND v.status='CHECKED_IN'")
        int checkOut(@Param("tenantId") long tenantId, @Param("id") long id);

        @Select("""
                SELECT v.id, v.name, v.company, v.host_id AS hostId, v.visit_at AS visitAt,
                       v.leave_at AS leaveAt, v.status
                  FROM oa_admin.visitor v
                 WHERE v.tenant_id=#{tenantId} AND v.host_id=#{hostId}
                 ORDER BY v.visit_at DESC LIMIT #{limit}
                """)
        List<VisitorRow> ownVisitors(@Param("tenantId") long tenantId, @Param("hostId") String hostId,
                                     @Param("limit") int limit);
    }
}
