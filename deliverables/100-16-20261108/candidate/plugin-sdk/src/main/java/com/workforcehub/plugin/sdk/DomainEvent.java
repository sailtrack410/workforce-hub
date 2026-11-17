package com.workforcehub.plugin.sdk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 领域事件（宿主发布）。
 * <ul>
 *   <li>eventId / eventType / idempotencyKey / triggeredBy 必须非 blank</li>
 *   <li>occurredAt &gt; 0，version &gt;= 1（payload schema version，从 1 开始）</li>
 *   <li>eventType 不限于 {@link EventTypes} 常量，未知类型允许构造但订阅端自行识别</li>
 *   <li>null payload 归一化为空 Map</li>
 *   <li>payload 递归快照、深度不可变，仅允许 JSON 兼容值</li>
 * </ul>
 */
public record DomainEvent(
    String eventId,
    String eventType,
    long occurredAt,
    long version,
    String idempotencyKey,
    String triggeredBy,
    Map<String, Object> payload
) {
    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        if (eventId.isBlank()) throw new IllegalArgumentException("eventId 不能为空");
        Objects.requireNonNull(eventType, "eventType");
        if (eventType.isBlank()) throw new IllegalArgumentException("eventType 不能为空");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey 不能为空");
        Objects.requireNonNull(triggeredBy, "triggeredBy");
        if (triggeredBy.isBlank()) throw new IllegalArgumentException("triggeredBy 不能为空");
        if (occurredAt <= 0) throw new IllegalArgumentException("occurredAt 必须 > 0");
        if (version < 1) throw new IllegalArgumentException("version 必须 >= 1");

        payload = payload == null ? Map.of() : deepCopyMap(payload);
    }

    /**
     * 递归快照 Map：key 必须是 String，value 递归校验并拷贝。
     */
    private static Map<String, Object> deepCopyMap(Map<?, ?> src) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : src.entrySet()) {
            Object rawKey = entry.getKey();
            if (!(rawKey instanceof String key)) {
                throw new IllegalArgumentException("payload key 必须是 String，实际: " + (rawKey == null ? "null" : rawKey.getClass().getName()));
            }
            copy.put(key, sanitizeValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 递归快照 List。
     */
    private static List<Object> deepCopyList(List<?> src) {
        List<Object> copy = new ArrayList<>(src.size());
        for (Object item : src) {
            copy.add(sanitizeValue(item));
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * 校验并递归快照单个 payload 值。
     * 允许：null、String、Boolean、不可变数字类型（Float/Double 须有限值）、String-key Map、List。
     * 拒绝：DTO、Date、Set、数组、NaN/Infinity 等。
     */
    private static Object sanitizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
            || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Float f) {
            if (!Float.isFinite(f)) throw new IllegalArgumentException("payload 浮点值必须有限");
            return f;
        }
        if (value instanceof Double d) {
            if (!Double.isFinite(d)) throw new IllegalArgumentException("payload 浮点值必须有限");
            return d;
        }
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap(map);
        }
        if (value instanceof List<?> list) {
            return deepCopyList(list);
        }
        throw new IllegalArgumentException("payload 不支持的类型: " + value.getClass().getName());
    }
}
