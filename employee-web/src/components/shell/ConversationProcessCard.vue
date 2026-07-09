<script setup lang="ts">
import { computed } from 'vue';
import type { ConversationMessage, ConversationProcessCard as ProcessCard } from '../../stores/shell';
import AppBadge from '../ui/AppBadge.vue';
import AppButton from '../ui/AppButton.vue';
import { statusText } from '../../utils/employee-format';

const emit = defineEmits<{
  open: [];
}>();

const props = defineProps<{
  message: ConversationMessage;
}>();

const processCard = computed(() => props.message.processCard as ProcessCard);

function toneOf(statusTone: ProcessCard['statusTone']) {
  if (statusTone === 'success') return 'success';
  if (statusTone === 'warning') return 'warning';
  if (statusTone === 'info') return 'primary';
  return 'neutral';
}
</script>

<template>
  <div class="process-message">
    <div class="message-meta">
      <strong>{{ props.message.author }}</strong>
      <span v-if="props.message.role">{{ props.message.role }}</span>
      <span v-if="props.message.time">{{ props.message.time }}</span>
    </div>

    <button class="process-card" type="button" @click="emit('open')">
      <div class="card-main">
        <div class="header">
          <strong>{{ processCard.title }}</strong>
          <AppBadge :tone="toneOf(processCard.statusTone)">
            {{ statusText(processCard.status) }}
          </AppBadge>
        </div>

        <p v-if="processCard.body" class="body">{{ processCard.body }}</p>

        <div v-if="processCard.fields?.length" class="fields">
          <span v-for="field in processCard.fields" :key="field[0]">
            <small>{{ field[0] }}</small>
            <b>{{ field[1] }}</b>
          </span>
        </div>

        <div v-if="processCard.checks?.length" class="checks">
          <span v-for="check in processCard.checks" :key="check.label" :class="{ passed: check.passed }">
            <i />
            <span>
              <strong>{{ check.label }}</strong>
              <small>{{ check.result }}</small>
            </span>
          </span>
        </div>

        <div v-if="processCard.file" class="file-row">
          <span class="file-type">{{ processCard.file.type }}</span>
          <span class="file-copy">
            <strong>{{ processCard.file.name }}</strong>
            <small>{{ processCard.file.size }} · {{ statusText(processCard.file.status) }}</small>
          </span>
        </div>
      </div>

      <div class="card-action">
        <AppButton variant="secondary" size="sm">
          {{ processCard.actionLabel }}
        </AppButton>
      </div>
    </button>
  </div>
</template>

<style scoped>
.process-message {
  display: grid;
  gap: var(--space-8);
  width: min(100%, 43rem);
}

.message-meta {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.message-meta strong {
  color: var(--text-secondary);
  font-weight: 700;
}

.process-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-20);
  align-items: center;
  width: 100%;
  padding: var(--space-16);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-12);
  background: var(--surface-panel);
  color: var(--text-primary);
  text-align: left;
  box-shadow: none;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.process-card:hover,
.process-card:focus-visible {
  outline: none;
  border-color: color-mix(in srgb, var(--primary-300) 58%, var(--border-default));
  box-shadow: var(--shadow-sm);
}

.card-main {
  display: grid;
  gap: var(--space-12);
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
}

.header strong {
  font-size: var(--text-subtitle-sm);
}

.card-action {
  display: flex;
  align-items: center;
}

.body {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
  line-height: 1.65;
}

.fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-8) var(--space-16);
}

.fields span,
.checks > span {
  min-width: 0;
}

.fields small,
.checks small,
.file-copy small {
  display: block;
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.fields b {
  display: block;
  margin-top: var(--space-4);
  font-size: var(--text-body-sm);
  overflow-wrap: anywhere;
}

.checks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-12);
}

.checks > span {
  display: flex;
  align-items: flex-start;
  gap: var(--space-8);
  padding: var(--space-12);
  border-radius: var(--radius-8);
  background: color-mix(in srgb, var(--surface-panel-muted) 86%, transparent);
}

.checks i {
  display: grid;
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-panel);
}

.checks > span.passed i {
  border-color: var(--success-500);
  background: var(--success-500);
}

.checks strong,
.file-copy strong {
  display: block;
  font-size: var(--text-caption-md);
}

.file-row {
  display: grid;
  grid-template-columns: 3rem minmax(0, 1fr);
  gap: var(--space-12);
  align-items: center;
}

.file-type {
  display: inline-grid;
  place-items: center;
  min-height: 2.25rem;
  border-radius: var(--radius-8);
  background: var(--surface-panel-strong);
  color: var(--text-accent);
  font-size: var(--text-caption-md);
  font-weight: 700;
}

@media (max-width: 920px) {
  .process-card {
    grid-template-columns: 1fr;
  }

  .checks,
  .fields {
    grid-template-columns: 1fr;
  }

  .card-action {
    justify-content: flex-start;
  }
}
</style>
