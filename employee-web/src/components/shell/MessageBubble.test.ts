import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const currentDir = dirname(fileURLToPath(import.meta.url));

describe('message dialog and bubble markup', () => {
  it('lets forward dialog use one visual panel', () => {
    const dialogSource = readFileSync(resolve(currentDir, '../ui/AppDialog.vue'), 'utf8');
    const messagesSource = readFileSync(resolve(currentDir, '../../views/sections/MessagesSection.vue'), 'utf8');

    expect(dialogSource).toContain('flush?: boolean');
    expect(dialogSource).toContain('wide?: boolean');
    expect(dialogSource).toContain(':class="{ flush: props.flush, wide: props.wide }"');
    expect(messagesSource).toContain('<AppDialog v-if="messageForwardOpen" flush>');
  });

  it('keeps sender name outside message bubble and hides it for current user messages', () => {
    const source = readFileSync(resolve(currentDir, 'MessageBubble.vue'), 'utf8');

    expect(source).toContain('class="bubble-stack"');
    expect(source).toContain('v-if="message.side !== \'right\'" class="bubble-meta"');
    expect(source.indexOf('class="bubble-meta"')).toBeLessThan(source.indexOf('class="bubble-content"'));
  });
});
