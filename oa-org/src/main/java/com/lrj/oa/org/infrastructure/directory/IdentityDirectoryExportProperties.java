package com.lrj.oa.org.infrastructure.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 默认关闭 HTTP 出口；捕获是否启用只由数据库接管登记决定，两者职责独立。 */
@ConfigurationProperties("oa.identity-directory.export")
public record IdentityDirectoryExportProperties(boolean enabled, long tenantId, String source, String environment,
                                                String credentialSha256, boolean allowLoopbackHttp) {
    public IdentityDirectoryExportProperties {
        if (enabled && (tenantId < 1 || source == null || source.length() > 100 || !source.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")
                || environment == null || environment.length() > 40 || !environment.matches("[a-z0-9][a-z0-9-]*")
                || credentialSha256 == null || !credentialSha256.matches("[a-f0-9]{64}"))) {
            throw new IllegalArgumentException("目录出口必须配置固定来源、企业及凭据摘要");
        }
    }
    /** 不在日志和配置诊断中输出完整凭据摘要。 */
    @Override public String toString() { return "IdentityDirectoryExportProperties[enabled=" + enabled + ", tenantId=" + tenantId + ", credentialSha256=REDACTED]"; }
}
