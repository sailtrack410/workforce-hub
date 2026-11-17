import { describe, expect, it } from 'vitest';
import pluginSource from './PluginManagementView.vue?raw';

describe('plugin management interaction contracts', () => {
  it('confirms lifecycle and destructive operations with admin dialogs', () => {
    expect(pluginSource).toContain('<AdminConfirmDialog');
    expect(pluginSource).toContain('@confirm="confirmLifecycle"');
    expect(pluginSource).toContain('@confirm="confirmConfigDelete"');
    expect(pluginSource).toContain("return plugin.status !== 'UNINSTALLED'");
    expect(pluginSource).not.toContain('window.confirm');
  });

  it('keeps plugin diagnostics and secret configuration values private', () => {
    expect(pluginSource).toContain("if (row.secret) return row.hasValue ? '已设置' : '未设置'");
    expect(pluginSource).toContain("configForm.value = row.secret ? '' : row.value ?? ''");
    expect(pluginSource).toContain("? (configForm.value || null)");
    expect(pluginSource).toContain('内部异常明细已隐藏');
    expect(pluginSource).not.toContain('selectedPlugin.errorMessage');
  });

  it('validates bounded uploads and configuration writes with versions', () => {
    expect(pluginSource).toContain('const MAX_JAR_BYTES = 32 * 1024 * 1024');
    expect(pluginSource).toContain('const MAX_SIGNATURE_BYTES = 65_535');
    expect(pluginSource).toContain('const MAX_CONFIG_VALUE_BYTES = 16 * 1024');
    expect(pluginSource).toContain('uploadJar.value.size === 0');
    expect(pluginSource).toContain('configForm.value && !configForm.value.trim()');
    expect(pluginSource).toContain('expectedVersion: editing?.version ?? configTombstoneVersions.value.get(key) ?? 0');
    expect(pluginSource).toContain('const deletedVersion = await adminApi.plugins.deleteConfig(plugin.id, row.key, row.version)');
    expect(pluginSource).toContain('CONFIG_KEY_PATTERN.test(key)');
  });

  it('recovers deleted configuration generations and namespace usage', () => {
    expect(pluginSource).toContain('adminApi.plugins.configs(plugin.id, true)');
    expect(pluginSource).toContain('rows.filter((row) => !row.deleted)');
    expect(pluginSource).toContain('configTombstoneVersions.value = new Map(');
    expect(pluginSource).toContain('rows.filter((row) => row.deleted).map((row) => [row.key, row.version])');
    expect(pluginSource).toContain('configNamespaceCount.value = rows.length');
    expect(pluginSource).toContain('configTombstoneVersions.value.set(row.key, deletedVersion)');
    expect(pluginSource).not.toContain('configTombstoneVersions.value[key]');
  });

  it('does not reuse broad audit data for plugin-scoped history', () => {
    expect(pluginSource).not.toContain('adminApi.system.auditLogs');
    expect(pluginSource).not.toContain('detailJson');
    expect(pluginSource).not.toContain("'history'");
  });

  it('isolates concurrent loads and clears sensitive dialog state', () => {
    expect(pluginSource).toContain('sequence !== pluginLoadSequence');
    expect(pluginSource).toContain('sequence !== configLoadSequence || selectedPluginId.value !== plugin.id');
    expect(pluginSource).toContain('if (conflict) await loadPlugins(plugin.id)');
    expect(pluginSource).toContain('if (!visible) resetUploadForm()');
    expect(pluginSource).toContain('if (!visible) resetConfigForm()');
  });
});
