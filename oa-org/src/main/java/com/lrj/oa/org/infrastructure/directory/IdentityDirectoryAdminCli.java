package com.lrj.oa.org.infrastructure.directory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.lrj.oa.org.application.directory.IdentityDirectoryInitializer;
import com.lrj.oa.org.application.directory.IdentityDirectoryPublisher;
import com.lrj.oa.org.infrastructure.mapper.IdentityDirectoryMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import java.util.Set;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 运维一次性目录接管工具；只装配本模块持久化，不启动 Web、调度器、外部连接器或整个 OA。 */
public final class IdentityDirectoryAdminCli {
    private static final Set<String> KEYS = Set.of("jdbc.url", "jdbc.username", "jdbc.password", "tenant.id", "source", "environment", "operator", "max.records");
    private IdentityDirectoryAdminCli() {}

    /** 私密文件授权 DBA 固定来源初始化；不接收任意 HTTP 注册请求，不自动迁移既有数据库。 */
    public static void main(String[] args) {
        if (run(args, new java.io.PrintWriter(System.out, true), new java.io.PrintWriter(System.err, true)) != 0) { System.exit(2); }
    }

    /** 返回非零表示未完成；错误不回显 SQL、原始配置、凭据或员工内容。 */
    public static int run(String[] args, java.io.PrintWriter output, java.io.PrintWriter error) {
        try {
            if (args.length != 2 || !"initialize".equals(args[0])) { throw new IllegalArgumentException("usage"); }
            Properties config = read(Path.of(args[1]));
            String url = config.getProperty("jdbc.url");
            if (url == null || !url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/[A-Za-z0-9_]+")) {
                // 当前 P1 仅允许隔离回环验证；正式远程接管另走已确认部署计划。
                throw new IllegalArgumentException("当前工具仅允许本地隔离 PostgreSQL");
            }
            String user = required(config, "jdbc.username"), password = required(config, "jdbc.password");
            var dataSource = new DriverManagerDataSource(url + "?connectTimeout=5&socketTimeout=65", user, password);
            var mybatis = new MybatisConfiguration(); mybatis.setMapUnderscoreToCamelCase(true);
            mybatis.addMapper(IdentityDirectoryMapper.class);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource); factory.setConfiguration(mybatis);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/directory/*.xml"));
            var session = new SqlSessionTemplate(java.util.Objects.requireNonNull(factory.getObject()));
            var mapper = session.getMapper(IdentityDirectoryMapper.class);
            var initializer = new IdentityDirectoryInitializer(mapper, new IdentityDirectoryPublisher(mapper));
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource)); transaction.setTimeout(60);
            var result = transaction.execute(status -> initializer.initialize(Long.parseLong(required(config, "tenant.id")),
                    required(config, "source"), required(config, "environment"), required(config, "operator"),
                    Integer.parseInt(config.getProperty("max.records", "10000"))));
            if (result == null || !result.initialized()) { throw new IllegalStateException("封存未完成"); }
            output.println("PASS: directory initialized; last_sequence=" + result.lastSequence());
            return 0;
        } catch (Exception failure) {
            error.println("FAIL: directory initialization rejected; inspect private configuration and source state");
            return 2;
        }
    }

    private static Properties read(Path file) throws java.io.IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 16384
                || !Files.getPosixFilePermissions(file).equals(PosixFilePermissions.fromString("rw-------"))) { throw new IllegalArgumentException("私密文件权限不正确"); }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        if (!KEYS.containsAll(properties.stringPropertyNames())) { throw new IllegalArgumentException("配置含未知键"); }
        return properties;
    }
    private static String required(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) { throw new IllegalArgumentException("必要配置缺失"); }
        return value;
    }
}
