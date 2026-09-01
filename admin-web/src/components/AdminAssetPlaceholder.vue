<script setup lang="ts">
import authHeroImage from '../assets/brand/auth-hero-admin.jpg';

defineProps<{
  assetId: string;
}>();
</script>

<template>
  <figure class="asset-hero" :aria-label="assetId">
    <img class="asset-hero-image" :src="authHeroImage" alt="" aria-hidden="true" />
  </figure>
</template>

<style scoped>
.asset-hero {
  position: relative;
  width: min(100%, 720px);
  aspect-ratio: 16 / 10;
  margin: 0;
  border: 1px solid var(--auth-line);
  border-radius: var(--radius-lg);
  background: var(--auth-panel);
  box-shadow: var(--auth-shadow);
  overflow: hidden;
}

.asset-hero-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
