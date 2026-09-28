package com.lrj.oa.org.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 目录确认的严格协议边界，拒绝宿主默认 JSON 类型转换和字段容错。 */
public record IdentityDirectoryAcknowledgement(long sequence, String fingerprint) {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 调用方最多读取上限加一字节，不能先无界读取再检查大小。 */
    public static IdentityDirectoryAcknowledgement parse(byte[] body) {
        if (body.length > 1024) { throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE); }
        try {
            var value = JSON.readTree(body);
            if (value == null || !value.isObject() || value.size() != 2 || !value.has("sequence") || !value.has("fingerprint")
                    || !value.get("sequence").isIntegralNumber() || !value.get("sequence").canConvertToLong()
                    || !value.get("fingerprint").isTextual()) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
            return new IdentityDirectoryAcknowledgement(value.get("sequence").longValue(), value.get("fingerprint").textValue());
        } catch (java.io.IOException malformed) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
}
