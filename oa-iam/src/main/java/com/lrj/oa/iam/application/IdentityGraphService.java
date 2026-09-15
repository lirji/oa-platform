package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentityDtos.GraphEdge;
import com.lrj.oa.iam.api.dto.IdentityDtos.GraphNode;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityGraph;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.IdentityStatus;
import com.lrj.oa.iam.domain.IdentityType;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper.RoleGrantRow;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.OrgUnitView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 身份 1 跳图谱只读投影。边由 identity / grant_record 现算，不对前端开放写边。
 */
@Service
public class IdentityGraphService {

    private static final int OWNED_LIMIT = 50;

    private final IdentityMapper mapper;
    private final OrgQueryApi orgs;

    public IdentityGraphService(IdentityMapper mapper, OrgQueryApi orgs) {
        this.mapper = mapper;
        this.orgs = orgs;
    }

    public IdentityGraph graph(String identityId) {
        if (!StringUtils.hasText(identityId)) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        long tenantId = TenantContext.get();
        Identity principal = mapper.selectById(tenantId, identityId);
        if (principal == null || IdentityStatus.DELETED.name().equals(principal.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        String self = nodeId("IDENTITY", principal.getId());
        nodes.put(self, new GraphNode(self, "IDENTITY", principal.getDisplayName(), principal.getId()));

        addBelongsTo(principal, self, nodes, edges);
        addOwner(tenantId, principal, self, nodes, edges);
        addOwned(tenantId, principal, self, nodes, edges);
        addRoles(tenantId, principal, self, nodes, edges);
        return new IdentityGraph(List.copyOf(nodes.values()), List.copyOf(edges));
    }

    private void addBelongsTo(Identity principal, String self,
                              Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        if (principal.getOrgId() == null) {
            return;
        }
        String orgNode = nodeId("ORG_UNIT", String.valueOf(principal.getOrgId()));
        nodes.put(orgNode, new GraphNode(orgNode, "ORG_UNIT", orgLabel(principal), String.valueOf(principal.getOrgId())));
        edges.add(new GraphEdge(self, orgNode, "BELONGS_TO"));
    }

    private void addOwner(long tenantId, Identity principal, String self,
                          Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        if (!StringUtils.hasText(principal.getOwnerIdentityId())) {
            return;
        }
        Identity owner = mapper.selectById(tenantId, principal.getOwnerIdentityId());
        if (owner == null || IdentityStatus.DELETED.name().equals(owner.getStatus())) {
            return;
        }
        String ownerNode = nodeId("IDENTITY", owner.getId());
        nodes.putIfAbsent(ownerNode, new GraphNode(ownerNode, "IDENTITY", owner.getDisplayName(), owner.getId()));
        edges.add(new GraphEdge(ownerNode, self, "OWNS"));
    }

    private void addOwned(long tenantId, Identity principal, String self,
                          Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        for (Identity owned : mapper.selectOwnedBy(tenantId, principal.getId(), OWNED_LIMIT)) {
            String child = nodeId("IDENTITY", owned.getId());
            nodes.putIfAbsent(child, new GraphNode(child, "IDENTITY", owned.getDisplayName(), owned.getId()));
            edges.add(new GraphEdge(self, child, "OWNS"));
        }
    }

    private void addRoles(long tenantId, Identity principal, String self,
                          Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        if (!IdentityType.USER.name().equals(principal.getIdentityType())) {
            return;
        }
        List<RoleGrantRow> grants = mapper.selectActiveRoleGrants(
                tenantId, principal.getExternalKey(), principal.getOrgId(), OffsetDateTime.now());
        if (grants == null) {
            return;
        }
        for (RoleGrantRow grant : grants) {
            String roleNode = nodeId("ROLE", String.valueOf(grant.roleId()));
            String label = grant.roleName() + "（" + grant.roleCode() + "）";
            nodes.putIfAbsent(roleNode, new GraphNode(roleNode, "ROLE", label, String.valueOf(grant.roleId())));
            edges.add(new GraphEdge(self, roleNode, "HAS_ROLE"));
        }
    }

    private String orgLabel(Identity principal) {
        try {
            OrgUnitView org = orgs.getOrg(principal.getOrgId());
            if (org != null && StringUtils.hasText(org.name())) {
                return org.name();
            }
        } catch (RuntimeException ignored) {
            // 组织缓存未命中时仍用投影路径，不让整张图谱 2001
        }
        if (StringUtils.hasText(principal.getOrgPath())) {
            return principal.getOrgPath();
        }
        return "组织 " + principal.getOrgId();
    }

    private static String nodeId(String type, String ref) {
        return type + ":" + ref;
    }
}
