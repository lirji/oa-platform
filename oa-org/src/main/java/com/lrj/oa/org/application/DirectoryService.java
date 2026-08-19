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
        return v;
    }
}
