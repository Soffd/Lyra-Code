<template>
  <canvas ref="canvas" class="hero-particles" aria-hidden="true" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvas = ref<HTMLCanvasElement | null>()
let raf = 0

onMounted(() => {
  const c = canvas.value
  if (!c) return
  const ctx = c.getContext('2d')
  if (!ctx) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)

  let w = 0
  let h = 0
  let particles: { x: number; y: number; r: number; vx: number; vy: number; a: number }[] = []

  const resize = () => {
    const rect = c.parentElement?.getBoundingClientRect()
    w = rect?.width || window.innerWidth
    h = rect?.height || 400
    c.width = w * dpr
    c.height = h * dpr
    c.style.width = w + 'px'
    c.style.height = h + 'px'
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    const count = Math.min(55, Math.max(18, Math.floor((w * h) / 22000)))
    particles = Array.from({ length: count }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      r: Math.random() * 1.8 + 0.4,
      vx: (Math.random() - 0.5) * 0.3,
      vy: (Math.random() - 0.5) * 0.3,
      a: Math.random() * Math.PI * 2
    }))
  }

  const tick = () => {
    ctx.clearRect(0, 0, w, h)
    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy
      if (p.x < 0 || p.x > w) p.vx *= -1
      if (p.y < 0 || p.y > h) p.vy *= -1
      p.a += 0.02
      const alpha = 0.35 + Math.sin(p.a) * 0.28
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(125, 153, 255, ${alpha.toFixed(3)})`
      ctx.fill()
    }
    raf = requestAnimationFrame(tick)
  }

  resize()
  tick()
  window.addEventListener('resize', resize)
  onBeforeUnmount(() => {
    cancelAnimationFrame(raf)
    window.removeEventListener('resize', resize)
  })
})
</script>

<style scoped>
.hero-particles {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}
</style>