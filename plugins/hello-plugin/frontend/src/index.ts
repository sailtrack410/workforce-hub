const host = window.__WORKFORCE_HOST__;
if (!host) {
  throw new Error('宿主 SDK 未初始化：window.__WORKFORCE_HOST__ 不存在');
}

const { Vue } = host;
const { defineComponent, h } = Vue;

const component = defineComponent({
  name: 'HelloPluginView',
  setup() {
    return () => h('div', { class: 'hello-plugin' }, 'Hello Plugin');
  },
});

host.register('hello-plugin', {
  id: 'hello-plugin',
  name: 'Hello Plugin',
  component: () => Promise.resolve(component),
});
