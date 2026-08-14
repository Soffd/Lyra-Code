<template>
  <canvas ref="canvas" class="global-stars" aria-hidden="true" />
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

  let w = window.innerWidth
  let h = window.innerHeight
  let stars: { x: number; y: number; r: number; a: number; s: number }[] = []

  const resize = () => {
    w = window.innerWidth
    h = window.innerHeight
    c.width = w * dpr
    c.height = h * dpr
    c.style.width = w + 'px'
    c.style.height = h + 'px'
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    const count = Math.min(90, Math.max(40, Math.floor((w * h) / 15000)))
    stars = Array.from({ length: count }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      r: Math.random() * 1.8 + 1.0,
      a: Math.random() * Math.PI * 2,
      s: Math.random() * 0.22 + 0.08
    }))
  }

  const tick = () => {
    ctx.clearRect(0, 0, w, h)
    // 星云连线：距离近的粒子连成线
    for (let i = 0; i < stars.length; i++) {
      for (let j = i + 1; j < stars.length; j++) {
        const dx = stars[i].x - stars[j].x
        const dy = stars[i].y - stars[j].y
        const d2 = dx * dx + dy * dy
        if (d2 < 8100) {
          const t = 1 - d2 / 8100
          ctx.beginPath()
          ctx.moveTo(stars[i].x, stars[i].y)
          ctx.lineTo(stars[j].x, stars[j].y)
          ctx.strokeStyle = `rgba(150, 170, 255, ${(0.16 * t).toFixed(3)})`
          ctx.lineWidth = 0.6
          ctx.stroke()
        }
      }
    }
    for (const st of stars) {
      st.y -= st.s
      if (st.y < -6) {
        st.y = h + 6
        st.x = Math.random() * w
      }
      st.a += 0.02
      const alpha = 0.5 + Math.sin(st.a) * 0.3
      ctx.beginPath()
      ctx.arc(st.x, st.y, st.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(170, 190, 255, ${alpha.toFixed(3)})`
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
.global-stars {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 1;
}

@media (prefers-color-scheme: light) {
  .global-stars {
    opacity: 0.65;
  }
}
</style>