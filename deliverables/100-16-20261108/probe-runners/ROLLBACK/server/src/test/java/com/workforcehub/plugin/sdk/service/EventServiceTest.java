package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.RequiresCapability;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceTest {

    @Test
    void shouldExtendHostService() {
        assertTrue(HostService.class.isAssignableFrom(EventService.class));
    }

    @Test
    void shouldHaveSubscribeMethodWithCorrectSignature() throws NoSuchMethodException {
        Method m = EventService.class.getMethod("subscribe",
            InvocationContext.class, String.class, String.class, String.class);
        assertReturnsServiceResultOfVoid(m);
    }

    @Test
    void shouldHaveUnsubscribeMethodWithCorrectSignature() throws NoSuchMethodException {
        Method m = EventService.class.getMethod("unsubscribe",
            InvocationContext.class, String.class, String.class);
        assertReturnsServiceResultOfVoid(m);
    }

    private void assertReturnsServiceResultOfVoid(Method m) {
        Type rt = m.getGenericReturnType();
        assertInstanceOf(ParameterizedType.class, rt, "返回类型应为 ParameterizedType");
        ParameterizedType pt = (ParameterizedType) rt;
        assertEquals(ServiceResult.class, pt.getRawType());
        Type[] args = pt.getActualTypeArguments();
        assertEquals(1, args.length);
        assertEquals(Void.class, args[0]);
    }

    @Test
    void shouldAnnotateSubscribeWithEventSubscribeCapability() throws NoSuchMethodException {
        Method m = EventService.class.getMethod("subscribe",
            InvocationContext.class, String.class, String.class, String.class);
        RequiresCapability ann = m.getAnnotation(RequiresCapability.class);

        assertNotNull(ann);
        assertEquals("EVENT_SUBSCRIBE", ann.value());
    }

    @Test
    void shouldAnnotateUnsubscribeWithEventSubscribeCapability() throws NoSuchMethodException {
        Method m = EventService.class.getMethod("unsubscribe",
            InvocationContext.class, String.class, String.class);
        RequiresCapability ann = m.getAnnotation(RequiresCapability.class);

        assertNotNull(ann);
        assertEquals("EVENT_SUBSCRIBE", ann.value());
    }

    @Test
    void shouldHaveExactlyTwoBusinessMethods() {
        Map<String, Method> businessMethods = Arrays.stream(EventService.class.getMethods())
            .filter(m -> m.getDeclaringClass() != Object.class)
            .collect(Collectors.toMap(Method::getName, m -> m));

        assertEquals(2, businessMethods.size());
        assertTrue(businessMethods.containsKey("subscribe"));
        assertTrue(businessMethods.containsKey("unsubscribe"));
    }

    @Test
    void shouldNotHaveDefaultMethods() {
        assertFalse(EventService.class.isInterface()
            && Arrays.stream(EventService.class.getMethods())
                .anyMatch(Method::isDefault),
            "EventService 不应包含 default 方法");
    }
}
