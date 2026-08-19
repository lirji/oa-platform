package com.lrj.oa.admin.web;

import com.lrj.oa.admin.api.dto.AdminDtos;
import com.lrj.oa.admin.application.AssetService;
import com.lrj.oa.admin.application.RoomBookingService;
import com.lrj.oa.admin.application.VehicleVisitorService;
import com.lrj.oa.common.api.Result;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 行政域 REST：会议室 / 资产 / 用品 / 车辆 / 访客。 */
@RestController
@RequestMapping("/api/v1/admin-biz")
public class AdminBizController {

    private final RoomBookingService rooms;
    private final AssetService assets;
    private final VehicleVisitorService vv;

    public AdminBizController(RoomBookingService rooms, AssetService assets, VehicleVisitorService vv) {
        this.rooms = rooms;
        this.assets = assets;
        this.vv = vv;
    }

    // ───────────────────────────── 会议室
    @GetMapping("/rooms")
    @RequiresPerm("oa:room:book")
    public Result<List<AdminDtos.RoomView>> rooms() { return Result.ok(rooms.rooms()); }

    @PostMapping("/rooms/bookings")
    @RequiresPerm("oa:room:book")
    public Result<Map<String, Object>> book(@Valid @RequestBody AdminDtos.BookRoom cmd) {
        return Result.ok(Map.of("id", rooms.book(cmd)));
    }

    @GetMapping("/rooms/{roomId}/bookings")
    @RequiresPerm("oa:room:book")
    public Result<List<AdminDtos.BookingView>> bookings(
            @PathVariable long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return Result.ok(rooms.bookingsOf(roomId, from, to));
    }

    @PostMapping("/rooms/bookings/{id}/cancel")
    @RequiresPerm("oa:room:book")
    public Result<Void> cancelBooking(@PathVariable long id) {
        rooms.cancel(id);
        return Result.ok();
    }

    // ───────────────────────────── 资产
    @GetMapping("/assets")
    @RequiresPerm("oa:asset:read")
    public Result<List<AdminDtos.AssetView>> assets(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(assets.listAssets(status, limit));
    }

    @PostMapping("/assets/claim")
    @RequiresPerm("oa:asset:claim")
    public Result<Void> claim(@Valid @RequestBody AdminDtos.ClaimAsset cmd) {
        assets.claim(cmd.assetId(), cmd.remark());
        return Result.ok();
    }

    @PostMapping("/assets/{id}/return")
    @RequiresPerm("oa:asset:claim")
    public Result<Void> giveBack(@PathVariable long id) {
        assets.giveBack(id);
        return Result.ok();
    }

    // ───────────────────────────── 用品
    @GetMapping("/supplies")
    @RequiresPerm("oa:supply:request")
    public Result<List<AdminDtos.SupplyView>> supplies() { return Result.ok(assets.supplies()); }

    @PostMapping("/supplies/requests")
    @RequiresPerm("oa:supply:request")
    public Result<Void> requestSupply(@Valid @RequestBody AdminDtos.RequestSupply cmd) {
        assets.requestSupply(cmd.supplyId(), cmd.qty());
        return Result.ok();
    }

    // ───────────────────────────── 车辆
    @GetMapping("/vehicles")
    @RequiresPerm("oa:vehicle:book")
    public Result<List<Map<String, Object>>> vehicles() { return Result.ok(vv.vehicles()); }

    @PostMapping("/vehicles/bookings")
    @RequiresPerm("oa:vehicle:book")
    public Result<Map<String, Object>> bookVehicle(@Valid @RequestBody AdminDtos.BookVehicle cmd) {
        return Result.ok(Map.of("id", vv.bookVehicle(cmd)));
    }

    // ───────────────────────────── 访客
    @PostMapping("/visitors")
    @RequiresPerm("oa:visitor:invite")
    public Result<Map<String, Object>> invite(@Valid @RequestBody AdminDtos.InviteVisitor cmd) {
        return Result.ok(Map.of("id", vv.inviteVisitor(cmd)));
    }

    @GetMapping("/visitors")
    @RequiresPerm("oa:visitor:invite")
    public Result<List<AdminDtos.VisitorView>> myVisitors(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(vv.myVisitors(limit));
    }

    @PostMapping("/visitors/{id}/check-in")
    @RequiresPerm("oa:visitor:manage")
    public Result<Void> checkIn(@PathVariable long id) { vv.checkIn(id); return Result.ok(); }

    @PostMapping("/visitors/{id}/check-out")
    @RequiresPerm("oa:visitor:manage")
    public Result<Void> checkOut(@PathVariable long id) { vv.checkOut(id); return Result.ok(); }
}
