<script setup lang="ts">
import { useData } from 'vitepress'

const { page } = useData()
</script>

<template>
  <h1 v-if="page.title" class="page-gradient-title">{{ page.title }}</h1>
</template>