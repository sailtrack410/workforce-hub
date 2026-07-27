package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.RequiresCapability;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主服务注册表。
 * 启动时一次性构建，不可变。
 * getRequired 返回 JDK 动态代理，逐次校验能力与调用者身份。
 */
final class HostServiceRegistry {

    private final Map<Class<? extends HostService>, HostService> services;
    private final PluginPermissionService permissionService;

    /**
     * 扫描每个实现所实现的全部接口，只注册"interface 且继承 HostService 且不等于 HostService.class"的接口。
     * 同一子接口出现两个实现时直接抛 IllegalStateException；仅 marker 实现也拒绝。
     * 同时校验每个方法必须有 @RequiresCapability 注解且首参为 InvocationContext。
     */
    HostServiceRegistry(List<HostService> implementations, PluginPermissionService permissionService) {
        this.permissionService = java.util.Objects.requireNonNull(permissionService, "permissionService 不能为 null");
        Map<Class<? extends HostService>, HostService> map = new HashMap<>();

        for (HostService impl : implementations) {
            Class<?>[] interfaces = ClassUtils.getAllInterfaces(impl);
            boolean registeredAny = false;

            for (Class<?> iface : interfaces) {
                if (!iface.isInterface()) continue;
                if (!HostService.class.isAssignableFrom(iface)) continue;
                if (iface == HostService.class) continue;

                @SuppressWarnings("unchecked")
                Class<? extends HostService> serviceType = (Class<? extends HostService>) iface;

                if (map.containsKey(serviceType)) {
                    throw new IllegalStateException(
                        "HostService 子接口重复实现: %s 由 %s 和 %s 同时实现"
                            .formatted(serviceType.getName(),
                                map.get(serviceType).getClass().getName(),
                                impl.getClass().getName()));
                }

                validateMethodContracts(serviceType);
                map.put(serviceType, impl);
                registeredAny = true;
            }

            if (!registeredAny) {
                throw new IllegalStateException(
                    "HostService 实现未暴露有效子接口: " + impl.getClass().getName());
            }
        }

        this.services = Map.copyOf(map);
    }

    /**
     * 校验接口方法契约：每个方法必须有 @RequiresCapability 且首参为 InvocationContext。
     */
    private void validateMethodContracts(Class<? extends HostService> serviceType) {
        boolean hasBusinessMethod = false;
        for (Method method : serviceType.getMethods()) {
            // 跳过 Object 方法
            if (method.getDeclaringClass() == Object.class) continue;

            hasBusinessMethod = true;

            RequiresCapability annotation = method.getAnnotation(RequiresCapability.class);
            if (annotation == null || annotation.value().isBlank()) {
                throw new IllegalStateException(
                    "HostService 方法缺少 @RequiresCapability 注解: %s#%s"
                        .formatted(serviceType.getName(), method.getName()));
            }

            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 0 || paramTypes[0] != InvocationContext.class) {
                throw new IllegalStateException(
                    "HostService 方法首参必须为 InvocationContext: %s#%s"
                        .formatted(serviceType.getName(), method.getName()));
            }
        }

        if (!hasBusinessMethod) {
            throw new IllegalStateException(
                "HostService 子接口必须至少有一个业务方法: " + serviceType.getName());
        }
    }

    /**
     * 获取已注册服务的代理。拒绝 null、HostService.class、具体实现类、未注册接口。
     * 代理绑定 pluginId，每次调用校验能力与身份。
     */
    <T extends HostService> T getRequired(Class<T> type, String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("服务类型不能为 null");
        }
        if (type == HostService.class) {
            throw new IllegalArgumentException("不能直接获取 HostService 标记接口，请使用具体子接口");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("只能按接口获取，不能按实现类获取: " + type.getName());
        }

        HostService service = services.get(type);
        if (service == null) {
            throw new IllegalArgumentException("宿主服务未注册: " + type.getName());
        }

        return createProxy(type, service, pluginId);
    }

    @SuppressWarnings("unchecked")
    private <T extends HostService> T createProxy(Class<T> type, HostService delegate, String pluginId) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{ type },
            new CapabilityCheckingHandler(delegate, pluginId, permissionService)
        );
    }

    /**
     * 代理调用处理器：逐次校验能力码、pluginId 匹配、InvocationContext 首参。
     */
    private static final class CapabilityCheckingHandler implements InvocationHandler {

        private final HostService delegate;
        private final String pluginId;
        private final PluginPermissionService permissionService;

        CapabilityCheckingHandler(HostService delegate, String pluginId, PluginPermissionService permissionService) {
            this.delegate = delegate;
            this.pluginId = pluginId;
            this.permissionService = permissionService;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 跳过 Object 方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }

            // 首参必须为非 null InvocationContext
            if (args == null || args.length == 0 || !(args[0] instanceof InvocationContext ctx)) {
                throw new PluginPermissionService.PermissionDeniedException(
                    "HostService 方法首参必须为 InvocationContext");
            }

            // ctx.pluginId 必须等于代理绑定的 pluginId
            if (!pluginId.equals(ctx.getPluginId())) {
                throw new PluginPermissionService.PermissionDeniedException(
                    "调用者 pluginId 不匹配: 期望 %s, 实际 %s"
                        .formatted(pluginId, ctx.getPluginId()));
            }

            // 方法必须有非空能力注解
            RequiresCapability annotation = method.getAnnotation(RequiresCapability.class);
            if (annotation == null || annotation.value().isBlank()) {
                throw new PluginPermissionService.PermissionDeniedException(
                    "方法缺少能力声明: " + method.getName());
            }

            // 检查插件能力（数据库为准）
            permissionService.checkPluginCapability(pluginId, annotation.value());

            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
