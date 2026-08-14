<template>
  <div class="scroll-progress" :style="{ width: pct + '%' }" aria-hidden="true" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const pct = ref(0)

const onScroll = () => {
  const doc = document.documentElement
  const max = doc.scrollHeight - window.innerHeight
  pct.value = max > 0 ? Math.min(100, (window.scrollY / max) * 100) : 0
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
})
</script>

<style scoped>
.scroll-progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 3px;
  z-index: 1000;
  background: linear-gradient(90deg, #5b7cfa, #9d7bff, #ff7bd5, #5b7cfa);
  background-size: 300% 100%;
  animation: progress-shift 4s linear infinite;
  transition: width 0.1s linear;
  box-shadow: 0 0 12px rgba(125, 153, 255, 0.6);
}

@keyframes progress-shift {
  0% { background-position: 0% 0; }
  100% { background-position: 300% 0; }
}
</style>