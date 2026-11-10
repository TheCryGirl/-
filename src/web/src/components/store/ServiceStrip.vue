<script setup>
import { servicePromises } from '../../data/showcase'
import StoreIcon from './StoreIcon.vue'
</script>

<template>
  <ul class="jd-promises">
    <li v-for="item in servicePromises" :key="item.title" class="jd-promises__item">
      <span class="jd-promises__icon">
        <StoreIcon :name="item.icon" :size="20" />
      </span>
      <span class="jd-promises__text">
        <strong>{{ item.title }}</strong>
        <small>{{ item.desc }}</small>
      </span>
    </li>
  </ul>
</template>

<style scoped>
.jd-promises {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}
.jd-promises__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease;
}
.jd-promises__item:hover {
  transform: translateY(-3px);
  box-shadow: var(--jd-shadow-card);
}
.jd-promises__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 12px;
  background: var(--jd-red-soft);
  color: var(--jd-red);
}
.jd-promises__text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.jd-promises__text strong {
  font-size: 13px;
  color: var(--jd-ink);
}
.jd-promises__text small {
  font-size: 11px;
  color: var(--jd-ink-4);
}
@media (max-width: 760px) {
  .jd-promises {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>