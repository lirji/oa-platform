-- 行政域数据字典注释。

COMMENT ON TABLE oa_admin.meeting_room IS '会议室资源目录';
COMMENT ON COLUMN oa_admin.meeting_room.id IS '会议室主键';
COMMENT ON COLUMN oa_admin.meeting_room.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_admin.meeting_room.code IS '租户内唯一的会议室编码';
COMMENT ON COLUMN oa_admin.meeting_room.name IS '会议室名称';
COMMENT ON COLUMN oa_admin.meeting_room.location IS '会议室位置';
COMMENT ON COLUMN oa_admin.meeting_room.capacity IS '可容纳人数';
COMMENT ON COLUMN oa_admin.meeting_room.equipment IS '会议室设备说明';
COMMENT ON COLUMN oa_admin.meeting_room.status IS '会议室状态：ACTIVE 或 DISABLED';
COMMENT ON COLUMN oa_admin.meeting_room.org_id IS '管理该会议室的组织主键';
COMMENT ON COLUMN oa_admin.meeting_room.org_path IS '管理组织路径，用于数据权限过滤';
COMMENT ON COLUMN oa_admin.meeting_room.created_at IS '创建时间';

COMMENT ON TABLE oa_admin.room_booking IS '会议室预订记录，通过排他约束阻止有效预订时间重叠';
COMMENT ON COLUMN oa_admin.room_booking.id IS '会议室预订主键';
COMMENT ON COLUMN oa_admin.room_booking.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_admin.room_booking.room_id IS '会议室主键';
COMMENT ON COLUMN oa_admin.room_booking.booker_id IS '预订人的 Casdoor sub';
COMMENT ON COLUMN oa_admin.room_booking.booker_name IS '预订人姓名快照';
COMMENT ON COLUMN oa_admin.room_booking.subject IS '会议主题';
COMMENT ON COLUMN oa_admin.room_booking.attendees IS '预计参会人数';
COMMENT ON COLUMN oa_admin.room_booking.during IS '会议起止时间范围，采用左闭右开语义';
COMMENT ON COLUMN oa_admin.room_booking.status IS '预订状态：BOOKED 或 CANCELLED';
COMMENT ON COLUMN oa_admin.room_booking.org_id IS '预订发生时的组织主键快照';
COMMENT ON COLUMN oa_admin.room_booking.org_path IS '预订发生时的组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_admin.room_booking.created_at IS '创建时间';

COMMENT ON TABLE oa_admin.asset IS '固定资产当前状态与持有人信息';
COMMENT ON COLUMN oa_admin.asset.id IS '资产主键';
COMMENT ON COLUMN oa_admin.asset.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_admin.asset.asset_no IS '租户内唯一的资产编号';
COMMENT ON COLUMN oa_admin.asset.name IS '资产名称';
COMMENT ON COLUMN oa_admin.asset.category IS '资产分类';
COMMENT ON COLUMN oa_admin.asset.brand IS '品牌或制造商';
COMMENT ON COLUMN oa_admin.asset.price IS '购置价格';
COMMENT ON COLUMN oa_admin.asset.purchased_on IS '购置日期';
COMMENT ON COLUMN oa_admin.asset.status IS '资产状态：IDLE、IN_USE、REPAIR 或 SCRAPPED';
COMMENT ON COLUMN oa_admin.asset.holder_id IS '当前领用人的 Casdoor sub';
COMMENT ON COLUMN oa_admin.asset.org_id IS '资产所属组织主键';
COMMENT ON COLUMN oa_admin.asset.org_path IS '资产所属组织路径，用于数据权限过滤';
COMMENT ON COLUMN oa_admin.asset.created_at IS '创建时间';

COMMENT ON TABLE oa_admin.asset_txn IS '资产领用、归还、报修和报废的事实流水';
COMMENT ON COLUMN oa_admin.asset_txn.id IS '资产流水主键';
COMMENT ON COLUMN oa_admin.asset_txn.asset_id IS '资产主键';
COMMENT ON COLUMN oa_admin.asset_txn.action IS '资产动作：CLAIM、RETURN、REPAIR 或 SCRAP';
COMMENT ON COLUMN oa_admin.asset_txn.actor_id IS '操作人的 Casdoor sub';
COMMENT ON COLUMN oa_admin.asset_txn.from_status IS '操作前资产状态';
COMMENT ON COLUMN oa_admin.asset_txn.to_status IS '操作后资产状态';
COMMENT ON COLUMN oa_admin.asset_txn.remark IS '操作备注';
COMMENT ON COLUMN oa_admin.asset_txn.created_at IS '操作时间';

COMMENT ON TABLE oa_admin.supply IS '办公用品目录与当前库存';
COMMENT ON COLUMN oa_admin.supply.id IS '办公用品主键';
COMMENT ON COLUMN oa_admin.supply.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_admin.supply.code IS '租户内唯一的用品编码';
COMMENT ON COLUMN oa_admin.supply.name IS '用品名称';
COMMENT ON COLUMN oa_admin.supply.unit IS '计量单位';
COMMENT ON COLUMN oa_admin.supply.stock IS '当前可用库存，数据库约束保证不为负';
COMMENT ON COLUMN oa_admin.supply.created_at IS '创建时间';

COMMENT ON TABLE oa_admin.supply_request IS '办公用品领用申请记录';
COMMENT ON COLUMN oa_admin.supply_request.id IS '用品领用记录主键';
COMMENT ON COLUMN oa_admin.supply_request.supply_id IS '办公用品主键';
COMMENT ON COLUMN oa_admin.supply_request.requester_id IS '领用人的 Casdoor sub';
COMMENT ON COLUMN oa_admin.supply_request.qty IS '领用数量';
COMMENT ON COLUMN oa_admin.supply_request.org_id IS '领用发生时的组织主键快照';
COMMENT ON COLUMN oa_admin.supply_request.org_path IS '领用发生时的组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_admin.supply_request.created_at IS '领用时间';

COMMENT ON TABLE oa_admin.vehicle IS '公务车辆资源目录';
COMMENT ON COLUMN oa_admin.vehicle.id IS '车辆主键';
COMMENT ON COLUMN oa_admin.vehicle.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_admin.vehicle.plate_no IS '租户内唯一的车牌号';
COMMENT ON COLUMN oa_admin.vehicle.model IS '车辆品牌型号';
COMMENT ON COLUMN oa_admin.vehicle.seats IS '核定座位数';
COMMENT ON COLUMN oa_admin.vehicle.status IS '车辆状态，默认 ACTIVE';
COMMENT ON COLUMN oa_admin.vehicle.created_at IS '创建时间';

COMMENT ON TABLE oa_admin.vehicle_booking IS '公务车辆预订记录，通过排他约束阻止有效预订时间重叠';
COMMENT ON COLUMN oa_admin.vehicle_booking.id IS '车辆预订主键';
COMMENT ON COLUMN oa_admin.vehicle_booking.vehicle_id IS '公务车辆主键';
COMMENT ON COLUMN oa_admin.vehicle_booking.booker_id IS '用车申请人的 Casdoor sub';
COMMENT ON COLUMN oa_admin.vehicle_booking.purpose IS '用车事由';
COMMENT ON COLUMN oa_admin.vehicle_booking.during IS '用车起止时间范围，采用左闭右开语义';
COMMENT ON COLUMN oa_admin.vehicle_booking.status IS '预订状态，默认 BOOKED';
COMMENT ON COLUMN oa_admin.vehicle_booking.org_id IS '申请发生时的组织主键快照';
COMMENT ON COLUMN oa_admin.vehicle_booking.org_path IS '申请发生时的组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_admin.vehicle_booking.created_at IS '创建时间';

COMMENT ON TABLE oa_admin.visitor IS '访客预约、到访和离场记录';
COMMENT ON COLUMN oa_admin.visitor.id IS '访客记录主键';
COMMENT ON COLUMN oa_admin.visitor.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_admin.visitor.name IS '访客姓名';
COMMENT ON COLUMN oa_admin.visitor.phone_enc IS '访客手机号密文';
COMMENT ON COLUMN oa_admin.visitor.phone_hash IS '访客手机号确定性 HMAC，用于精确检索';
COMMENT ON COLUMN oa_admin.visitor.company IS '访客所属单位';
COMMENT ON COLUMN oa_admin.visitor.host_id IS '接待人的 Casdoor sub';
COMMENT ON COLUMN oa_admin.visitor.visit_at IS '计划或实际到访时间';
COMMENT ON COLUMN oa_admin.visitor.leave_at IS '离场时间';
COMMENT ON COLUMN oa_admin.visitor.status IS '访客状态：BOOKED、CHECKED_IN、LEFT 或 CANCELLED';
COMMENT ON COLUMN oa_admin.visitor.org_id IS '接待人所属组织主键快照';
COMMENT ON COLUMN oa_admin.visitor.org_path IS '接待人所属组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_admin.visitor.created_at IS '创建时间';
