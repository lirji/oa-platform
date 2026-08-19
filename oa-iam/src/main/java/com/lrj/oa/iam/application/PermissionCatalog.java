package com.lrj.oa.iam.application;

import com.lrj.oa.iam.domain.Permission;
import com.lrj.oa.iam.infrastructure.mapper.PermissionMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限点目录的内存副本。几百到一千条，随 epoch 刷新，避免每次重算快照都去查一遍。
 *
 * <p>{@code code -> id} 这个方向是判权热路径用的：注解里写的是 code，位图里存的是 id。
 */
@Component
public class PermissionCatalog {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalog.class);

    private final PermissionMapper permissionMapper;
    private volatile Snapshot current = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), List.of());

    public PermissionCatalog(PermissionMapper permissionMapper) { this.permissionMapper = permissionMapper; }

    public record Snapshot(Map<String, Integer> idByCode,
                           Map<Integer, String> codeById,
                           Map<Integer, String> moduleById,
                           Map<Integer, Boolean> elevationRequiredById,
                           List<Permission> all) {}

    @PostConstruct
    public void reload() {
        List<Permission> all = permissionMapper.selectCatalog();
        Map<String, Integer> idByCode = HashMap.newHashMap(all.size());
        Map<Integer, String> codeById = HashMap.newHashMap(all.size());
        Map<Integer, String> moduleById = HashMap.newHashMap(all.size());
        Map<Integer, Boolean> elevation = HashMap.newHashMap(all.size());
        for (Permission p : all) {
            // 位图用 int：权限点数量远小于 int 上限，但仍然显式挡一下，
            // 否则某天 id 超过 21 亿会静默截断成另一个权限点 —— 那是越权。
            long id = p.getId();
            if (id > Integer.MAX_VALUE) {
                throw new IllegalStateException("权限点 id 超出 int 范围，位图方案需要调整: " + id);
            }
            int i = (int) id;
            idByCode.put(p.getCode(), i);
            codeById.put(i, p.getCode());
            if (p.getModule() != null) moduleById.put(i, p.getModule());
            elevation.put(i, Boolean.TRUE.equals(p.getRequireElevation()));
        }
        current = new Snapshot(Map.copyOf(idByCode), Map.copyOf(codeById),
                Map.copyOf(moduleById), Map.copyOf(elevation), List.copyOf(all));
        log.info("权限点目录已加载：{} 条", all.size());
    }

    public Snapshot snapshot() { return current; }

    /** code → id；不存在返回 -1（判权时视为无此权限，而不是抛异常放行）。 */
    public int idOf(String code) { return current.idByCode().getOrDefault(code, -1); }

    public String codeOf(int id) { return current.codeById().get(id); }
    public String moduleOf(int id) { return current.moduleById().get(id); }
    public boolean requiresElevation(int id) { return Boolean.TRUE.equals(current.elevationRequiredById().get(id)); }
    public List<Permission> all() { return current.all(); }
}
