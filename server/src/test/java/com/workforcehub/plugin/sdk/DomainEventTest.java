package com.workforcehub.plugin.sdk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventTest {

    // --- 有效事件 ---

    @Test
    void shouldCreateWithPayload() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        data.put("count", 42);

        DomainEvent event = new DomainEvent(
            "evt-1", "TEST_EVENT", 1000L, 1L, "idem-1", "user-1", data);

        assertEquals("evt-1", event.eventId());
        assertEquals("TEST_EVENT", event.eventType());
        assertEquals(1000L, event.occurredAt());
        assertEquals(1L, event.version());
        assertEquals("idem-1", event.idempotencyKey());
        assertEquals("user-1", event.triggeredBy());
        assertEquals("value", event.payload().get("key"));
        assertEquals(42, event.payload().get("count"));
    }

    @Test
    void shouldTreatNullPayloadAsEmptyMap() {
        DomainEvent event = new DomainEvent(
            "evt-2", "TEST", 1L, 1L, "idem-2", "system", null);

        assertNotNull(event.payload());
        assertTrue(event.payload().isEmpty());
    }

    @Test
    void shouldAllowUnknownEventType() {
        DomainEvent event = new DomainEvent(
            "evt-x", "UNKNOWN_FUTURE_TYPE", 1L, 1L, "idem-x", "system", null);
        assertEquals("UNKNOWN_FUTURE_TYPE", event.eventType());
    }

    // --- 非法 envelope 字段 ---

    @Test
    void shouldRejectBlankEventId() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("", "T", 1L, 1L, "k", "s", null));
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("  ", "T", 1L, 1L, "k", "s", null));
    }

    @Test
    void shouldRejectNullEventId() {
        assertThrows(NullPointerException.class,
            () -> new DomainEvent(null, "T", 1L, 1L, "k", "s", null));
    }

    @Test
    void shouldRejectBlankEventType() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "", 1L, 1L, "k", "s", null));
    }

    @Test
    void shouldRejectNullEventType() {
        assertThrows(NullPointerException.class,
            () -> new DomainEvent("e", null, 1L, 1L, "k", "s", null));
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "", "s", null));
    }

    @Test
    void shouldRejectNullIdempotencyKey() {
        assertThrows(NullPointerException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, null, "s", null));
    }

    @Test
    void shouldRejectBlankTriggeredBy() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "", null));
    }

    @Test
    void shouldRejectNullTriggeredBy() {
        assertThrows(NullPointerException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", null, null));
    }

    @Test
    void shouldRejectZeroOccurredAt() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 0L, 1L, "k", "s", null));
    }

    @Test
    void shouldRejectNegativeOccurredAt() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", -1L, 1L, "k", "s", null));
    }

    @Test
    void shouldRejectZeroVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 0L, "k", "s", null));
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, -1L, "k", "s", null));
    }

    // --- 深度快照 ---

    @Test
    void shouldDeepCopyNestedMap() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("a", 1);
        Map<String, Object> data = new HashMap<>();
        data.put("nested", inner);

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);

        // 修改源不影响事件
        inner.put("a", 999);
        assertEquals(1, ((Map<?, ?>) event.payload().get("nested")).get("a"));

        // 事件内层也不可修改
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) event.payload().get("nested")).put("b", 2));
    }

    @Test
    void shouldDeepCopyNestedList() {
        List<Object> list = new ArrayList<>(Arrays.asList("x", 42));
        Map<String, Object> data = new HashMap<>();
        data.put("items", list);

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);

        // 修改源不影响事件
        list.set(0, "mutated");
        assertEquals("x", ((List<?>) event.payload().get("items")).get(0));

        // 事件内列表不可修改
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) event.payload().get("items")).add("new"));
    }

    @Test
    void shouldPreserveIterationOrder() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("z", 1);
        data.put("a", 2);
        data.put("m", 3);

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);

        List<String> keys = new ArrayList<>(event.payload().keySet());
        assertEquals(List.of("z", "a", "m"), keys);
    }

    // --- 非法 payload 类型 ---

    @Test
    void shouldRejectNonStringKey() {
        Map<Object, Object> data = new HashMap<>();
        data.put(123, "value");
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", (Map<String, Object>) (Map<?, ?>) data));
    }

    @Test
    void shouldRejectDateValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("when", new Date());
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectSetValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("tags", new HashSet<>(Arrays.asList("a", "b")));
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectArrayValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("arr", new int[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectObjectValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("obj", new Object());
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectNaNDouble() {
        Map<String, Object> data = new HashMap<>();
        data.put("val", Double.NaN);
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectInfinityDouble() {
        Map<String, Object> data = new HashMap<>();
        data.put("val", Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectNaNFloat() {
        Map<String, Object> data = new HashMap<>();
        data.put("val", Float.NaN);
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    @Test
    void shouldRejectInfinityFloat() {
        Map<String, Object> data = new HashMap<>();
        data.put("val", Float.NEGATIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", data));
    }

    // --- 允许的数字类型 ---

    @Test
    void shouldAcceptAllIntegerTypes() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", (byte) 1);
        data.put("b", (short) 2);
        data.put("c", 3);
        data.put("d", 4L);
        data.put("e", 5.0f);
        data.put("f", 6.0);

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);
        assertEquals(6, event.payload().size());
    }

    // --- EventTypes 常量 ---

    @Test
    void shouldHaveCorrectEventTypes() {
        assertEquals("WORKFLOW_APPROVED", EventTypes.WORKFLOW_APPROVED);
        assertEquals("WORKFLOW_REJECTED", EventTypes.WORKFLOW_REJECTED);
        assertEquals("EMPLOYEE_HIRED", EventTypes.EMPLOYEE_HIRED);
        assertEquals("EMPLOYEE_LEFT", EventTypes.EMPLOYEE_LEFT);
        assertEquals("FILE_UPLOADED", EventTypes.FILE_UPLOADED);
        assertEquals("ATTENDANCE_EXCEPTION", EventTypes.ATTENDANCE_EXCEPTION);
    }

    // --- 顶层 payload 不可修改 ---

    @Test
    void shouldRejectTopLevelPayloadMutation() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);
        assertThrows(UnsupportedOperationException.class,
            () -> event.payload().put("new", "val"));
    }

    // --- Map→List→Map 多层混合不可变 ---

    @Test
    void shouldDeepCopyMapListMapMix() {
        Map<String, Object> leaf = new HashMap<>();
        leaf.put("deep", 42);
        List<Object> list = new ArrayList<>();
        list.add(leaf);
        Map<String, Object> data = new HashMap<>();
        data.put("items", list);

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);

        // 修改源不影响快照
        leaf.put("deep", 999);
        list.add("extra");
        assertEquals(42, ((Map<?, ?>) ((List<?>) event.payload().get("items")).get(0)).get("deep"));
        assertEquals(1, ((List<?>) event.payload().get("items")).size());

        // 事件内任意层不可修改
        assertThrows(UnsupportedOperationException.class,
            () -> event.payload().put("x", 1));
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) event.payload().get("items")).add("x"));
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) ((List<?>) event.payload().get("items")).get(0)).put("x", 1));
    }

    // --- 嵌套 null ---

    @Test
    void shouldPreserveNestedNull() {
        Map<String, Object> data = new HashMap<>();
        data.put("val", null);
        List<Object> list = new ArrayList<>();
        list.add(null);
        data.put("list", list);

        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);
        assertNull(event.payload().get("val"));
        assertNull(((List<?>) event.payload().get("list")).get(0));
    }

    // --- BigInteger / BigDecimal ---

    @Test
    void shouldAcceptBigInteger() {
        Map<String, Object> data = new HashMap<>();
        data.put("big", new BigInteger("99999999999999999999"));
        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);
        assertEquals(new BigInteger("99999999999999999999"), event.payload().get("big"));
    }

    @Test
    void shouldAcceptBigDecimal() {
        Map<String, Object> data = new HashMap<>();
        data.put("price", new BigDecimal("123.456"));
        DomainEvent event = new DomainEvent("e", "T", 1L, 1L, "k", "s", data);
        assertEquals(new BigDecimal("123.456"), event.payload().get("price"));
    }

    // --- 嵌套非 String key / null key ---

    @Test
    void shouldRejectNestedNonStringKey() {
        Map<Object, Object> inner = new HashMap<>();
        inner.put(42, "val");
        Map<String, Object> data = new HashMap<>();
        data.put("nested", inner);
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", (Map<String, Object>) (Map<?, ?>) data));
    }

    @Test
    void shouldRejectNullKey() {
        Map<Object, Object> data = new HashMap<>();
        data.put(null, "val");
        assertThrows(IllegalArgumentException.class,
            () -> new DomainEvent("e", "T", 1L, 1L, "k", "s", (Map<String, Object>) (Map<?, ?>) data));
    }
}
