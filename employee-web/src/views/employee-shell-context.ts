import { inject, provide, proxyRefs } from 'vue';

export type EmployeeShellContext = Record<string, any>;

const employeeShellContextKey = Symbol('employee-shell-context');

export function provideEmployeeShellContext(context: EmployeeShellContext) {
  provide(employeeShellContextKey, context);
}

export function useEmployeeShellContext() {
  const context = inject<EmployeeShellContext>(employeeShellContextKey);
  if (!context) {
    throw new Error('Employee shell context is not available');
  }
  return proxyRefs(context);
}
