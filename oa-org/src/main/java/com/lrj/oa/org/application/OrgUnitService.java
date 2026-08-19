package com.lrj.oa.org.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.api.event.OrgTreeChangedEvent;
import com.lrj.oa.org.application.command.OrgCommands;
import com.lrj.oa.org.domain.OrgStatus;
import com.lrj.oa.org.domain.OrgUnit;
import com.lrj.oa.org.domain.OrgUnitType;
import com.lrj.oa.org.infrastructure.mapper.OrgClosureMapper;
import com.lrj.oa.org.infrastructure.mapper.OrgUnitMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 组织单元的写侧。闭包表与物化路径的一致性全靠这里的事务边界守住。
 *
 * <p><b>三个不变式</b>，任何一个破了都会让数据权限静默出错：
 * <ol>
 *   <li>{@code path} 恒为 {@code /a/b/c/} 形式，首尾都有斜杠；</li>
 *   <li>{@code closure} 里存在 (a, d) ⟺ d 的 path 以 a 的 path 为前缀；</li>
 *   <li>{@code distance == d.depth - a.depth}。</li>
 * </ol>
 * {@code OrgClosureMapper#countInconsistencies()} 就是用来核对这三条的。
 */
@Service
public class OrgUnitService {

    private static final Logger log = LoggerFactory.getLogger(OrgUnitService.class);

    private final OrgUnitMapper orgUnitMapper;
    private final OrgClosureMapper closureMapper;
    private final ApplicationEventPublisher events;

    public OrgUnitService(OrgUnitMapper orgUnitMapper, OrgClosureMapper closureMapper,
                          ApplicationEventPublisher events) {
        this.orgUnitMapper = orgUnitMapper;
        this.closureMapper = closureMapper;
        this.events = events;
    }

    // ───────────────────────────────────────────── 新增

    @Transactional
    public Long create(OrgCommands.CreateOrg cmd) {
        OrgUnitType type = parseType(cmd.type());
        OrgUnit parent = null;
        if (cmd.parentId() != null) {
            parent = requireOrg(cmd.parentId());
            if (parent.statusEnum() == OrgStatus.DISSOLVED) {
                throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "上级组织已撤销，不能在其下新建");
            }
        }

        OrgUnit u = new OrgUnit();
        u.setTenantId(TenantContext.get());
        u.setParentId(cmd.parentId());
        u.setCode(cmd.code());
        u.setName(cmd.name());
        u.setShortName(cmd.shortName());
        u.setType(type.name());
        u.setSortOrder(cmd.sortOrder() == null ? 0 : cmd.sortOrder());
        u.setLeaderUserId(cmd.leaderUserId());
        u.setCostCenter(cmd.costCenter());
        u.setRemark(cmd.remark());
        u.setStatus(OrgStatus.ACTIVE.name());
        u.setEffectiveFrom(LocalDate.now());
        u.setCreatedBy(currentUser());
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        // path/depth 先占位：真实值要等自增 id 出来才能拼
        u.setPath("/0/");
        u.setDepth(parent == null ? 0 : parent.getDepth() + 1);
        u.setVersion(0);

        try {
            orgUnitMapper.insert(u);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BusinessException.of(ResultCode.CONFLICT, "组织编码已存在: " + cmd.code());
        }

        String path = (parent == null ? "/" : parent.getPath()) + u.getId() + "/";
        orgUnitMapper.fillPath(u.getId(), path, u.getDepth());
        u.setPath(path);

        if (parent == null) {
            closureMapper.insertSelfRow(u.getId());
        } else {
            closureMapper.insertForNewChild(u.getId(), parent.getId());
        }

        touchTree("create", u.getId());
        log.info("新建组织 id={} code={} path={} depth={}", u.getId(), u.getCode(), path, u.getDepth());
        return u.getId();
    }

    // ───────────────────────────────────────────── 移动子树

    /**
     * 把 orgId 连同整棵子树挂到 newParentId 之下（newParentId 为 null 则升为根）。
     *
     * <p>三步走，必须在同一事务里：断开旧祖先边 → 连上新祖先边 → 平移整棵子树的 path/depth。
     * 万人级最大子树几百节点、闭包行数千级，单事务毫无压力。
     */
    @Transactional
    public void move(Long orgId, Long newParentId) {
        OrgUnit org = requireOrg(orgId);

        if (orgId.equals(newParentId)) {
            throw BusinessException.of(ResultCode.ORG_CYCLE, "不能把组织挂到自己下面");
        }
        OrgUnit newParent = null;
        if (newParentId != null) {
            newParent = requireOrg(newParentId);
            // 成环检查：新父不能是自己的后代。闭包表一次查询搞定，不用递归。
            if (closureMapper.isAncestorOf(orgId, newParentId)) {
                throw BusinessException.of(ResultCode.ORG_CYCLE,
                        "目标上级 " + newParentId + " 位于 " + orgId + " 的子树内，移动会形成环");
            }
            if (newParent.statusEnum() == OrgStatus.DISSOLVED) {
                throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "目标上级组织已撤销");
            }
        }
        if (java.util.Objects.equals(org.getParentId(), newParentId)) {
            log.debug("组织 {} 的上级未变化，跳过", orgId);
            return;
        }

        String oldPath = org.getPath();
        int oldDepth = org.getDepth();
        String newPath = (newParent == null ? "/" : newParent.getPath()) + orgId + "/";
        int newDepth = (newParent == null ? 0 : newParent.getDepth() + 1);
        int depthDelta = newDepth - oldDepth;

        // ① 断开子树与所有外部祖先的连边（子树内部连边保留）
        int detached = closureMapper.detachSubtree(orgId);
        // ② 新父的全部祖先(含自身) × 子树全部节点，两两连边
        int attached = (newParentId == null) ? 0 : closureMapper.attachSubtree(orgId, newParentId);
        // ③ 整棵子树换路径前缀、平移深度
        int shifted = orgUnitMapper.shiftSubtreePath(oldPath, oldPath.length(), newPath, depthDelta);

        org.setParentId(newParentId);
        org.setPath(newPath);
        org.setDepth(newDepth);
        org.setUpdatedBy(currentUser());
        org.setUpdatedAt(OffsetDateTime.now());
        if (orgUnitMapper.updateById(org) == 0) {
            // @Version 乐观锁失败：有人同时在改这个节点
            throw BusinessException.of(ResultCode.CONFLICT, "组织已被他人修改，请刷新后重试");
        }

        touchTree("move", orgId);
        log.info("移动组织 id={} {} -> {} (断开{}边/新建{}边/平移{}个节点/深度{}{})",
                orgId, oldPath, newPath, detached, attached, shifted,
                depthDelta >= 0 ? "+" : "", depthDelta);
    }

    // ───────────────────────────────────────────── 更新与撤销

    @Transactional
    public void update(Long orgId, OrgCommands.UpdateOrg cmd) {
        OrgUnit org = requireOrg(orgId);
        if (cmd.name() != null) org.setName(cmd.name());
        if (cmd.shortName() != null) org.setShortName(cmd.shortName());
        if (cmd.type() != null) org.setType(parseType(cmd.type()).name());
        if (cmd.sortOrder() != null) org.setSortOrder(cmd.sortOrder());
        if (cmd.leaderUserId() != null) org.setLeaderUserId(cmd.leaderUserId());
        if (cmd.deputyLeaderUserId() != null) org.setDeputyLeaderUserId(cmd.deputyLeaderUserId());
        if (cmd.costCenter() != null) org.setCostCenter(cmd.costCenter());
        if (cmd.remark() != null) org.setRemark(cmd.remark());
        org.setUpdatedBy(currentUser());
        org.setUpdatedAt(OffsetDateTime.now());
        if (orgUnitMapper.updateById(org) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "组织已被他人修改，请刷新后重试");
        }
        // 名称/排序也会影响内存快照（前端树的显示顺序），同样要刷新
        touchTree("update", orgId);
    }

    /**
     * 撤销组织。<b>软删</b>：历史单据仍要能引用到它，物理删除会让两年前的审批单变成孤儿。
     */
    @Transactional
    public void dissolve(Long orgId) {
        OrgUnit org = requireOrg(orgId);
        int children = orgUnitMapper.countActiveChildren(orgId);
        if (children > 0) {
            throw BusinessException.of(ResultCode.ORG_HAS_CHILDREN, "该组织下还有 " + children + " 个子组织");
        }
        int members = orgUnitMapper.countActiveMembers(orgId);
        if (members > 0) {
            throw BusinessException.of(ResultCode.ORG_HAS_CHILDREN, "该组织下还有 " + members + " 名在职成员");
        }
        org.setStatus(OrgStatus.DISSOLVED.name());
        org.setEffectiveTo(LocalDate.now());
        org.setUpdatedBy(currentUser());
        org.setUpdatedAt(OffsetDateTime.now());
        if (orgUnitMapper.updateById(org) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "组织已被他人修改，请刷新后重试");
        }
        touchTree("dissolve", orgId);
        log.info("撤销组织 id={} code={}", orgId, org.getCode());
    }

    /** 一致性自检：closure 与 path 是否还对得上。正常恒为 0。 */
    public long checkConsistency() { return closureMapper.countInconsistencies(); }

    // ───────────────────────────────────────────── 内部

    private void touchTree(String reason, Long orgId) {
        orgUnitMapper.bumpTreeVersion();
        // 事件在事务提交后才会被 OrgTreeCache 消费（@TransactionalEventListener）
        events.publishEvent(OrgTreeChangedEvent.of(reason, orgId));
    }

    private OrgUnit requireOrg(Long id) {
        OrgUnit u = orgUnitMapper.selectById(id);
        if (u == null) throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "组织不存在: " + id);
        return u;
    }

    private static OrgUnitType parseType(String type) {
        try {
            return OrgUnitType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "非法组织类型: " + type);
        }
    }

    private static String currentUser() {
        var ctx = UserContextHolder.peek();
        return ctx == null ? "system" : ctx.userId();
    }
}
