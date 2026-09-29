package com.lrj.oa.flow.infrastructure.approval;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** 申请通道专用严格JSON编解码，不修改OA旧接口的全局序列化配置。 */
public final class CentralApprovalWire {
    private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private CentralApprovalWire() {}
    /** 只对验签完成的原始消息解析，拒绝重复/未知字段和模糊数值。 */
    public static <T>T read(byte[] bytes,Class<T> type) throws IOException { return JSON.readValue(bytes,type); }
    /** 出站始终采用契约snake_case，避免宿主配置改变跨服务字段。 */
    public static JsonNode body(Object value) { return JSON.valueToTree(value); }
}
