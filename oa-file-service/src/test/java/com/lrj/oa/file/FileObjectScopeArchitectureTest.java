package com.lrj.oa.file;

import com.lrj.oa.file.application.FileService;
import com.lrj.oa.security.annotation.ObjectScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** 文件服务不在 oa-app 进程中，单独冻结所有外部元数据访问的 owner 门禁。 */
class FileObjectScopeArchitectureTest {
    @Test
    void everyExternalMetadataOperationDeclaresOwnerScope() {
        Set<String> expected = Set.of("upload", "download", "presign", "delete", "myFiles");
        Set<String> actual = new TreeSet<>();
        for (Method method : FileService.class.getDeclaredMethods()) {
            ObjectScope scope = method.getAnnotation(ObjectScope.class);
            if (scope == null) continue;
            actual.add(method.getName());
            assertThat(scope.strategy()).isEqualTo(ObjectScope.Strategy.OWNER);
            assertThat(scope.tables()).containsExactly("oa_sys.file_object");
            assertThat(scope.reason()).isNotBlank();
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }
}
