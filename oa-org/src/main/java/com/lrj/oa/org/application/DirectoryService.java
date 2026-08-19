package com.lrj.oa.org.application;

import com.lrj.oa.common.crypto.SensitiveCrypto;
import com.lrj.oa.org.api.dto.DirectoryEntryView;
import com.lrj.oa.org.infrastructure.mapper.DirectoryMapper;
import com.lrj.oa.org.infrastructure.mapper.DirectoryRow;
import com.lrj.oa.security.annotation.DataScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通讯录查询 —— 数据权限的第一个真实落点。
 *
 * <p>{@code @DataScope} 让同一个接口对不同的人返回不同的行：
 * 部门负责人看到本部门及所有子部门，普通员工只看到自己。
 * 过滤发生在 SQL 里，不是查出来再在内存里筛 —— 后者在万人级下既慢又容易漏。
 */
@Service
public class DirectoryService {

    private final DirectoryMapper directoryMapper;
    private final SensitiveCrypto crypto;

    public DirectoryService(DirectoryMapper directoryMapper, SensitiveCrypto crypto) {
        this.directoryMapper = directoryMapper;
        this.crypto = crypto;
    }

    @DataScope(alias = "t", orgColumn = "org_id", pathColumn = "org_path",
               userColumn = "user_id", module = "org")
    public List<DirectoryEntryView> search(String keyword, int limit) {
        List<DirectoryRow> rows = directoryMapper.search(keyword, Math.min(limit, 500));
        List<DirectoryEntryView> out = new ArrayList<>(rows.size());
        for (DirectoryRow r : rows) out.add(toView(r));
        return out;
    }

    @DataScope(alias = "t", orgColumn = "org_id", pathColumn = "org_path",
               userColumn = "user_id", module = "org")
    public long countVisible() {
        return directoryMapper.countVisible();
    }

    private DirectoryEntryView toView(DirectoryRow r) {
        DirectoryEntryView v = new DirectoryEntryView();
        v.setEmployeeId(r.getEmployeeId());
        v.setUserId(r.getUserId());
        v.setEmpNo(r.getEmpNo());
        v.setName(r.getName());
        v.setEmail(r.getEmail());
        v.setStatus(r.getStatus());
        v.setHireDate(r.getHireDate());
        v.setOrgId(r.getOrgId());
        v.setOrgPath(r.getOrgPath());
        v.setOrgName(r.getOrgName());
        v.setPositionName(r.getPositionName());
        v.setLeader(Boolean.TRUE.equals(r.getLeader()));
        // 这里只负责解密；给不给明文由序列化层按 @Sensitive 决定，权限判断不在业务层做
        if (r.getMobileEnc() != null) v.setMobile(crypto.decrypt(r.getMobileEnc()));
        v.setSyncSeq(r.getSyncSeq());
        return v;
    }

    /** 游标分页。带数据权限 —— 分页绝不能成为绕过它的口子。 */
    @DataScope(alias = "t", orgColumn = "org_id", pathColumn = "org_path",
               userColumn = "user_id", module = "org")
    public List<DirectoryEntryView> page(Long cursor, String keyword, int size) {
        List<DirectoryRow> rows = directoryMapper.page(cursor, keyword, Math.min(Math.max(size, 1), 500));
        List<DirectoryEntryView> out = new ArrayList<>(rows.size());
        for (DirectoryRow r : rows) out.add(toView(r));
        return out;
    }

    /** 增量：自 since 之后变化的行（带数据权限）。 */
    @DataScope(alias = "t", orgColumn = "org_id", pathColumn = "org_path",
               userColumn = "user_id", module = "org")
    public List<DirectoryEntryView> changedSince(long since, int size) {
        List<DirectoryRow> rows = directoryMapper.changedSince(since, Math.min(Math.max(size, 1), 500));
        List<DirectoryEntryView> out = new ArrayList<>(rows.size());
        for (DirectoryRow r : rows) out.add(toView(r));
        return out;
    }

    /**
     * 墓碑：客户端应删除的 employeeId。
     *
     * <p>★ 刻意<b>不带</b> {@code @DataScope}：告诉客户端"删掉这个 id"不泄露任何信息
     * （它本来就在客户端本地）。反过来若墓碑也被过滤，一个人调岗出我的可见范围时
     * 我收不到墓碑，本地就永远留着他 —— <b>那才是真正的泄露</b>。
     */
    public List<Long> tombstonesSince(long since, int size) {
        return directoryMapper.tombstonesSince(since, Math.min(Math.max(size, 1), 1000)).stream()
                .map(m -> ((Number) m.get("employee_id")).longValue())
                .toList();
    }

    public long currentWatermark() {
        return directoryMapper.currentWatermark();
    }
}
