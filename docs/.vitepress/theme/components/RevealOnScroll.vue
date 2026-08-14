<template>
  <div style="display: none" aria-hidden="true" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

let observer: IntersectionObserver | null = null
let mo: MutationObserver | null = null
let timer = 0

const scan = () => {
  if (!observer) return
  const sel = [
    '.vp-doc h2', '.vp-doc h3', '.vp-doc h4',
    '.vp-doc p', '.vp-doc li', '.vp-doc table',
    '.vp-doc blockquote', '.vp-doc img',
    '.vp-doc div[class*="language-"]', '.vp-doc .custom-block'
  ].join(', ')
  document.querySelectorAll(sel).forEach((el) => {
    if (el.classList.contains('revealed')) return
    el.classList.add('reveal')
    observer!.observe(el)
  })
}

const debouncedScan = () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(scan, 120)
}

onMounted(() => {
  if (!('IntersectionObserver' in window) || !('MutationObserver' in window)) return
  document.documentElement.classList.add('reveal-enabled')
  observer = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) {
          e.target.classList.add('revealed')
          observer!.unobserve(e.target)
        }
      }
    },
    { threshold: 0.05, rootMargin: '0px 0px -40px 0px' }
  )
  mo = new MutationObserver(debouncedScan)
  mo.observe(document.body, { childList: true, subtree: true })
  scan()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  mo?.disconnect()
  window.clearTimeout(timer)
  document.documentElement.classList.remove('reveal-enabled')
})
</script>