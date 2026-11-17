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

  it('shows conversation list mention and reply notices before subtitle', () => {
    const source = readFileSync(resolve(currentDir, '../../views/MessagesView.vue'), 'utf8');

    expect(source).toContain('function conversationNoticeLabel(conversation: ConversationRecord)');
    expect(source).toContain('[有人@我]');
    expect(source).toContain('[有人回复我]');
    expect(source).toContain('class="conversation-notice-label"');
  });

  it('shows a blue mention preview above the composer input', () => {
    const source = readFileSync(resolve(currentDir, '../../views/sections/MessagesSection.vue'), 'utf8');

    expect(source).toContain('draftMentionNames');
    expect(source).toContain('class="composer-mention-preview"');
  });

  it('keeps sender name outside message bubble and hides it for current user messages', () => {
    const source = readFileSync(resolve(currentDir, 'MessageBubble.vue'), 'utf8');

    expect(source).toContain('class="bubble-stack"');
    expect(source).toContain('v-if="message.side !== \'right\'" class="bubble-meta"');
    expect(source.indexOf('class="bubble-meta"')).toBeLessThan(source.indexOf('class="bubble-content"'));
  });

  it('renders quoted content as an inline section inside the message bubble', () => {
    const source = readFileSync(resolve(currentDir, 'MessageBubble.vue'), 'utf8');

    expect(source).toContain('class="bubble-quote-rail"');
    expect(source).toContain('<strong class="bubble-quote-author">{{ quote.author }}：</strong>');
    expect(source.indexOf('class="bubble-quote"')).toBeGreaterThan(source.indexOf('class="bubble-content"'));
  });

  it('renders mentions as blue clickable text and exposes sender mention insertion', () => {
    const source = readFileSync(resolve(currentDir, 'MessageBubble.vue'), 'utf8');

    expect(source).toContain('senderMention?: MessageMention | null');
    expect(source).toContain('class="bubble-author-mention"');
    expect(source).toContain('class="bubble-mention-token"');
    expect(source).toContain("insertMention: [mention: MessageMention]");
    expect(source).toContain("mention: [mention: MessageMention]");
  });
});
