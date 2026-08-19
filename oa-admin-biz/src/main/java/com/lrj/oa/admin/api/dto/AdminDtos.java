package com.lrj.oa.admin.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** 行政域 DTO。一律类型化，不用 Map。 */
public final class AdminDtos {

    private AdminDtos() {}

    public record RoomView(Long id, String code, String name, String location,
                           int capacity, String equipment, String status) {}

    public record BookRoom(@NotNull Long roomId, @NotBlank String subject,
                           @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt,
                           @Min(1) int attendees) {}

    public record BookingView(Long id, Long roomId, String roomName, String bookerId, String bookerName,
                              String subject, OffsetDateTime startAt, OffsetDateTime endAt, String status) {}

    public record AssetView(Long id, String assetNo, String name, String category,
                            String status, String holderId, Long orgId) {}

    public record ClaimAsset(@NotNull Long assetId, String remark) {}

    public record RequestSupply(@NotNull Long supplyId, @Min(1) int qty) {}

    public record SupplyView(Long id, String code, String name, String unit, int stock) {}

    public record BookVehicle(@NotNull Long vehicleId, String purpose,
                              @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {}

    public record InviteVisitor(@NotBlank String name, String phone, String company,
                                @NotNull OffsetDateTime visitAt) {}

    public record VisitorView(Long id, String name, String company, String hostId,
                              OffsetDateTime visitAt, OffsetDateTime leaveAt, String status) {}
}
