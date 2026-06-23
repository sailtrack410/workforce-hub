<script setup lang="ts">
import { computed, unref } from 'vue';
import {
  ChevronLeft,
  BadgeCheck,
  Pencil,
  MessageCircle,
  MessageSquareText,
  UserRoundCheck,
} from 'lucide-vue-next';
import { useContactsSectionContext } from '../employee-shell-context';
import { displayField, employmentStatusLabel } from '@/utils/employee-format';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppTag from '../../components/ui/AppTag.vue';

const {
  shell,
  activeContact,
  backToList,
  startPrivateConversationFromContact,
  openContactRemarkEditor,
} = useContactsSectionContext();

const selectedContact = computed(() => unref(activeContact) ?? shell.filteredContacts[0] ?? shell.contacts[0] ?? null);

function presenceStatusLabel(status?: string | null) {
  if (status === 'ONLINE') return '在线';
  if (status === 'BUSY') return '忙碌';
  if (status === 'AWAY') return '离开';
  if (status === 'DND') return '勿扰';
  return '未设置';
}
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">通讯录</h2>
        <div v-if="selectedContact" class="workspace-meta-line">
          <span>{{ selectedContact.name }}</span>
          <span>{{ selectedContact.relation }}</span>
        </div>
      </div>
    </div>
  </header>

  <section class="content-stack">
    <article v-if="selectedContact" class="detail-card">
      <div class="contact-profile-head">
        <AppAvatar :label="selectedContact.name" :image="selectedContact.avatarUrl" tone="primary" size="lg" />
        <div class="contact-profile-copy">
          <div class="title-line">
            <h3>{{ selectedContact.name }}</h3>
            <AppTag tone="primary">{{ selectedContact.relation }}</AppTag>
          </div>
          <p>{{ selectedContact.role || '未设置岗位' }}<span v-if="selectedContact.department"> · {{ selectedContact.department }}</span></p>
          <div class="contact-profile-badges">
            <span>
              <UserRoundCheck :size="13" />
              {{ employmentStatusLabel(selectedContact.employmentStatus) }}
            </span>
            <span v-if="selectedContact.presenceStatus">
              <BadgeCheck :size="13" />
              {{ presenceStatusLabel(selectedContact.presenceStatus) }}
            </span>
          </div>
        </div>
      </div>
      <div class="contact-action-row">
        <AppButton v-if="selectedContact.userId && selectedContact.relation !== '本人'" variant="primary" size="sm" @click="startPrivateConversationFromContact(selectedContact)">
          <MessageCircle :size="15" />
          发消息
        </AppButton>
        <AppButton v-if="selectedContact.userId" variant="secondary" size="sm" @click="openContactRemarkEditor(selectedContact)">
          <Pencil :size="15" />
          备注
        </AppButton>
      </div>
      <p v-if="selectedContact.signature" class="contact-signature">
        <MessageSquareText :size="15" />
        <span>{{ selectedContact.signature }}</span>
      </p>
      <dl class="field-grid">
        <div>
          <dt>工号</dt>
          <dd>{{ displayField(selectedContact.employeeNo) }}</dd>
        </div>
        <div>
          <dt>部门</dt>
          <dd>{{ displayField(selectedContact.department) }}</dd>
        </div>
        <div>
          <dt>岗位</dt>
          <dd>{{ displayField(selectedContact.role) }}</dd>
        </div>
        <div>
          <dt>直属上级</dt>
          <dd>{{ displayField(selectedContact.managerName) }}</dd>
        </div>
        <div>
          <dt>账号状态</dt>
          <dd>{{ displayField(selectedContact.status) }}</dd>
        </div>
        <div>
          <dt>组织关系</dt>
          <dd>{{ selectedContact.relation }}</dd>
        </div>
      </dl>
    </article>
    <AppEmptyState v-if="!selectedContact" title="暂无联系人" description="暂无联系人" />
  </section>
</div>
</template>
