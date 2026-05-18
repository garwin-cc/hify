<template>
  <RouterView v-if="route.path === '/login'" />
  <AppShell v-else />
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'

const AppShell = defineAsyncComponent(() => import('@/components/layout/AppShell.vue'))

const route = useRoute()
</script>
