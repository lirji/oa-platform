package com.lrj.oa.iam.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.iam.domain.AbacBranch;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import org.roaringbitmap.RoaringBitmap;

import java.io.*;
import java.util.*;

/**
 * L2（Redis）的快照编解码。
 *
 * <p><b>为什么用 JSON 而不是 Kryo/Protobuf</b>：L2 不在热路径上 —— 命中 L1 时根本走不到这里，
 * 只有进程冷启动或被挤出 L1 才会读一次。为几毫秒的序列化差异引入一个二进制框架
 * （以及它的版本兼容问题、调试不可读、类注册表）不划算。位图本身仍走 RoaringBitmap
 * 自己的紧凑二进制格式，再 base64 塞进 JSON，体积主要由它决定。
 */
public final class SnapshotCodec {

    private static final int SCHEMA_VERSION = 2;

    private final ObjectMapper json;

    public SnapshotCodec(ObjectMapper json) { this.json = json; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ScopeDto(String type, List<String> prefixes, List<Long> orgIds, String selfUserId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SnapshotDto(int schemaVersion, String userId, long epoch, long userVersion,
                       String permBits, String elevatedBits, List<String> permCodes,
                       ScopeDto merged, Map<String, ScopeDto> modules, Map<Integer, ScopeDto> permissions,
                       List<String> delegators, boolean abacEnabled, String abacUnconditionalBits,
                       Map<Integer, List<AbacBranch>> abacBranches,
                       long builtAt, long expireAt) {}

    public String encode(PermissionSnapshot s) throws IOException {
        Map<String, ScopeDto> modules = HashMap.newHashMap(s.moduleScope().size());
        s.moduleScope().forEach((k, v) -> modules.put(k, toDto(v)));
        Map<Integer, ScopeDto> permissions = HashMap.newHashMap(s.permissionScope().size());
        s.permissionScope().forEach((k, v) -> permissions.put(k, toDto(v)));
        return json.writeValueAsString(new SnapshotDto(
                SCHEMA_VERSION, s.userId(), s.epoch(), s.userVersion(),
                encodeBitmap(s.permBits()), encodeBitmap(s.elevatedBits()),
                List.copyOf(s.permCodes()), toDto(s.mergedScope()), modules, permissions,
                List.copyOf(s.delegators()), s.abacEnabled(), encodeBitmap(s.abacUnconditionalBits()),
                s.abacBranches(), s.builtAt(), s.expireAt()));
    }

    public PermissionSnapshot decode(String payload) throws IOException {
        SnapshotDto d = json.readValue(payload, SnapshotDto.class);
        if (d.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException("权限快照协议版本不兼容: " + d.schemaVersion());
        }
        Map<String, DataScopeRule> modules = HashMap.newHashMap(d.modules() == null ? 0 : d.modules().size());
        if (d.modules() != null) d.modules().forEach((k, v) -> modules.put(k, fromDto(v)));
        Map<Integer, DataScopeRule> permissions = HashMap.newHashMap(
                d.permissions() == null ? 0 : d.permissions().size());
        if (d.permissions() != null) d.permissions().forEach((k, v) -> permissions.put(k, fromDto(v)));
        return PermissionSnapshot.builder(d.userId())
                .epoch(d.epoch()).userVersion(d.userVersion())
                .rawBits(decodeBitmap(d.permBits()), decodeBitmap(d.elevatedBits()),
                        d.permCodes() == null ? List.of() : d.permCodes())
                .rawScope(fromDto(d.merged()), modules)
                .rawPermissionScope(permissions)
                .delegators(d.delegators() == null ? List.of() : d.delegators())
                .rawAbac(d.abacEnabled(), decodeBitmap(d.abacUnconditionalBits()),
                        d.abacBranches() == null ? Map.of() : d.abacBranches())
                .builtAt(d.builtAt()).expireAt(d.expireAt())
                .build();
    }

    private static ScopeDto toDto(DataScopeRule r) {
        if (r == null) return null;
        return new ScopeDto(r.type().name(), List.copyOf(r.pathPrefixes()),
                List.copyOf(r.orgIds()), r.selfUserId());
    }

    private static DataScopeRule fromDto(ScopeDto d) {
        if (d == null) return DataScopeRule.none();
        return new DataScopeRule(DataScopeType.valueOf(d.type()),
                d.prefixes() == null ? List.of() : d.prefixes(),
                d.orgIds() == null ? Set.of() : Set.copyOf(d.orgIds()),
                d.selfUserId());
    }

    static String encodeBitmap(RoaringBitmap bm) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            bm.runOptimize();
            bm.serialize(dos);
            dos.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static RoaringBitmap decodeBitmap(String b64) {
        RoaringBitmap bm = new RoaringBitmap();
        if (b64 == null || b64.isEmpty()) return bm;
        try (DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(b64)))) {
            bm.deserialize(dis);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bm;
    }
}
