package com.lrj.oa.org.application;

import com.lrj.oa.org.api.event.OrgTreeChangedEvent;
import com.lrj.oa.org.infrastructure.mapper.OrgUnitMapper;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * 万人级组织与员工的批量装载。
 *
 * <p><b>为什么必须用 COPY 而不是逐条 insert</b>：10,000 员工 + 3,000 组织 + 约 15,000 条闭包
 * + 10,000 条任职 + 10,000 条汇报线 ≈ 5 万行。逐条 insert 每行一次网络往返，本机也要几分钟；
 * COPY 走单条流式协议，实测在秒级。验收标准写的是 30 秒内，那是给自己留的余量。
 *
 * <p><b>id 先领后灌</b>：路径 {@code /1/23/456/} 依赖父节点 id，而 bigserial 要插入后才知道 id。
 * 所以先从序列批量领号（{@code nextval} × N），在内存里把树和路径都拼好，再一次性 COPY。
 * 领号即推进序列，无需事后 setval。
 */
@Service
public class OrgSeedService {

    private static final Logger log = LoggerFactory.getLogger(OrgSeedService.class);

    private final DataSource dataSource;
    private final OrgUnitMapper orgUnitMapper;
    private final ApplicationEventPublisher events;

    public OrgSeedService(DataSource dataSource, OrgUnitMapper orgUnitMapper,
                          ApplicationEventPublisher events) {
        this.dataSource = dataSource;
        this.orgUnitMapper = orgUnitMapper;
        this.events = events;
    }

    public record SeedResult(int orgs, int closureRows, int employees, int assignments,
                             int reportingLines, int maxDepth, long elapsedMs) {}

    /** 组织节点的内存表示（灌库前用）。 */
    private record Seed(long id, Long parentId, String code, String name, String type,
                        String path, int depth) {}

    public SeedResult seed(int orgCount, int employeeCount) {
        long t0 = System.nanoTime();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            CopyManager copy = conn.unwrap(PGConnection.class).getCopyAPI();

            List<Long> orgIds = reserveIds(conn, "oa_org.org_unit_id_seq", orgCount);
            List<Seed> orgs = buildTree(orgIds);
            int closureRows = copyOrgs(copy, orgs);

            List<Long> empIds = reserveIds(conn, "oa_org.employee_id_seq", employeeCount);
            List<Long> assignIds = reserveIds(conn, "oa_org.employee_org_assignment_id_seq", employeeCount);
            List<Long> rlIds = reserveIds(conn, "oa_org.reporting_line_id_seq", employeeCount);
            int[] counts = copyEmployees(copy, orgs, empIds, assignIds, rlIds);

            conn.commit();

            int maxDepth = orgs.stream().mapToInt(Seed::depth).max().orElse(0);
            orgUnitMapper.bumpTreeVersion();
            events.publishEvent(OrgTreeChangedEvent.bulk("seed"));

            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("批量装载完成: 组织={} 闭包={} 员工={} 任职={} 汇报线={} 最大深度={} 耗时={}ms",
                    orgs.size(), closureRows, counts[0], counts[1], counts[2], maxDepth, ms);
            return new SeedResult(orgs.size(), closureRows, counts[0], counts[1], counts[2], maxDepth, ms);

        } catch (Exception e) {
            throw new IllegalStateException("批量装载失败: " + e.getMessage(), e);
        }
    }

    // ───────────────────────────────────────────── 建树

    /**
     * 生成一棵形状接近真实企业的组织树：集团 → 公司 → 事业部 → 中心 → 部门 → 团队 → 组。
     * 分支因子 4，深度自然落在 6-7 层 —— 正是万人企业的典型形态。
     */
    private List<Seed> buildTree(List<Long> ids) {
        String[] types = {"GROUP", "COMPANY", "BU", "CENTER", "DEPT", "TEAM", "SQUAD"};
        List<Seed> out = new ArrayList<>(ids.size());
        if (ids.isEmpty()) return out;

        long rootId = ids.get(0);
        out.add(new Seed(rootId, null, "ORG-" + rootId, "集团总部", types[0], "/" + rootId + "/", 0));

        List<Seed> currentLevel = new ArrayList<>(List.of(out.get(0)));
        int cursor = 1, depth = 1;
        while (cursor < ids.size()) {
            List<Seed> nextLevel = new ArrayList<>();
            String type = types[Math.min(depth, types.length - 1)];
            for (Seed parent : currentLevel) {
                for (int b = 0; b < 4 && cursor < ids.size(); b++) {
                    long id = ids.get(cursor++);
                    String path = parent.path() + id + "/";
                    nextLevel.add(new Seed(id, parent.id(), "ORG-" + id,
                            typeLabel(type) + "-" + id, type, path, depth));
                }
            }
            if (nextLevel.isEmpty()) break;      // 兜底：不会发生，但别让循环有机会转死
            out.addAll(nextLevel);
            currentLevel = nextLevel;
            depth++;
        }
        return out;
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "COMPANY" -> "公司"; case "BU" -> "事业部"; case "CENTER" -> "中心";
            case "DEPT" -> "部门";   case "TEAM" -> "团队"; case "SQUAD" -> "组";
            default -> "组织";
        };
    }

    // ───────────────────────────────────────────── 灌库

    private int copyOrgs(CopyManager copy, List<Seed> orgs) throws Exception {
        Map<Long, Seed> byId = HashMap.newHashMap(orgs.size());
        orgs.forEach(s -> byId.put(s.id(), s));

        StringBuilder units = new StringBuilder(orgs.size() * 96);
        StringBuilder closure = new StringBuilder(orgs.size() * 96);
        int closureRows = 0;

        for (Seed s : orgs) {
            units.append(s.id()).append(",1,")
                 .append(s.parentId() == null ? "" : s.parentId()).append(',')
                 .append(s.code()).append(',').append(csv(s.name())).append(',')
                 .append(s.type()).append(',').append(s.path()).append(',')
                 .append(s.depth()).append(",0,ACTIVE\n");

            // 自身 + 逐级向上，一次遍历同时产出全部闭包行
            long ancestor = s.id();
            int distance = 0;
            while (true) {
                closure.append(ancestor).append(',').append(s.id()).append(',').append(distance).append('\n');
                closureRows++;
                Seed a = byId.get(ancestor);
                if (a == null || a.parentId() == null) break;
                ancestor = a.parentId();
                distance++;
            }
        }

        copy.copyIn("COPY oa_org.org_unit (id,tenant_id,parent_id,code,name,type,path,depth,sort_order,status)"
                + " FROM STDIN WITH (FORMAT csv)", stream(units));
        copy.copyIn("COPY oa_org.org_closure (ancestor_id,descendant_id,distance)"
                + " FROM STDIN WITH (FORMAT csv)", stream(closure));
        return closureRows;
    }

    /**
     * 员工 + 任职 + 汇报线。
     *
     * <p>汇报线的生成规则刻意贴近现实：每个组织的第一个人是负责人（{@code is_leader}），
     * 组织内其他人向他汇报；负责人本人向<b>最近的、有负责人的上级组织</b>的负责人汇报。
     * 这样能造出真实深度的上报链，让 {@code managerChain} 在万人规模下被真正测到。
     */
    private int[] copyEmployees(CopyManager copy, List<Seed> orgs,
                                List<Long> empIds, List<Long> assignIds, List<Long> rlIds) throws Exception {
        // 只把人放到足够深的节点上（depth>=3），贴近"人在部门/团队里而不在集团层"
        List<Seed> hostable = orgs.stream().filter(s -> s.depth() >= 3).toList();
        if (hostable.isEmpty()) hostable = orgs;

        Map<Long, Seed> byId = HashMap.newHashMap(orgs.size());
        orgs.forEach(s -> byId.put(s.id(), s));

        int n = empIds.size();
        Map<Long, Long> orgLeader = HashMap.newHashMap(hostable.size());   // orgId -> 负责人 employeeId
        long[] empOrg = new long[n];
        boolean[] isLeader = new boolean[n];

        for (int i = 0; i < n; i++) {
            Seed org = hostable.get(i % hostable.size());
            empOrg[i] = org.id();
            if (orgLeader.putIfAbsent(org.id(), empIds.get(i)) == null) isLeader[i] = true;
        }

        StringBuilder emps = new StringBuilder(n * 96);
        StringBuilder assigns = new StringBuilder(n * 64);
        StringBuilder lines = new StringBuilder(n * 48);
        LocalDate hire = LocalDate.now().minusYears(2);
        int rlCount = 0;

        for (int i = 0; i < n; i++) {
            long empId = empIds.get(i);
            emps.append(empId).append(",1,")
                .append("seed-user-").append(empId).append(',')
                .append("E").append(String.format("%06d", i + 1)).append(',')
                .append(csv("员工" + (i + 1))).append(",FULL_TIME,ACTIVE,").append(hire).append('\n');

            assigns.append(assignIds.get(i)).append(",1,").append(empId).append(',')
                   .append(empOrg[i]).append(",PRIMARY,").append(isLeader[i]).append(',')
                   .append(hire).append('\n');

            Long manager = isLeader[i]
                    ? leaderOfNearestAncestor(byId, empOrg[i], orgLeader)
                    : orgLeader.get(empOrg[i]);
            if (manager != null && manager != empId) {
                lines.append(rlIds.get(i)).append(",1,").append(empId).append(',')
                     .append(manager).append(",SOLID,").append(hire).append('\n');
                rlCount++;
            }
        }

        copy.copyIn("COPY oa_org.employee (id,tenant_id,user_id,emp_no,name,employment_type,status,hire_date)"
                + " FROM STDIN WITH (FORMAT csv)", stream(emps));
        copy.copyIn("COPY oa_org.employee_org_assignment"
                + " (id,tenant_id,employee_id,org_unit_id,assignment_type,is_leader,valid_from)"
                + " FROM STDIN WITH (FORMAT csv)", stream(assigns));
        copy.copyIn("COPY oa_org.reporting_line (id,tenant_id,employee_id,manager_employee_id,type,valid_from)"
                + " FROM STDIN WITH (FORMAT csv)", stream(lines));
        return new int[]{n, n, rlCount};
    }

    private static Long leaderOfNearestAncestor(Map<Long, Seed> byId, long orgId, Map<Long, Long> orgLeader) {
        Seed cur = byId.get(orgId);
        while (cur != null && cur.parentId() != null) {
            Long leader = orgLeader.get(cur.parentId());
            if (leader != null) return leader;
            cur = byId.get(cur.parentId());
        }
        return null;
    }

    // ───────────────────────────────────────────── 工具

    private static List<Long> reserveIds(Connection conn, String sequence, int count) throws Exception {
        List<Long> ids = new ArrayList<>(count);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nextval(?) FROM generate_series(1, ?)")) {
            ps.setString(1, sequence);
            ps.setInt(2, count);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /** CSV 字段转义：含逗号/引号/换行的值加双引号并把内部引号翻倍。 */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0) return v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private static ByteArrayInputStream stream(CharSequence cs) {
        return new ByteArrayInputStream(cs.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 清空组织域全部数据（仅供冒烟脚本重置环境）。 */
    public void truncateAll() {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("""
                TRUNCATE oa_org.reporting_line, oa_org.employee_org_assignment,
                         oa_org.employee, oa_org.org_closure, oa_org.org_unit RESTART IDENTITY CASCADE
                """);
            orgUnitMapper.bumpTreeVersion();
            events.publishEvent(OrgTreeChangedEvent.bulk("truncate"));
        } catch (Exception e) {
            throw new IllegalStateException("清空组织数据失败", e);
        }
    }
}
