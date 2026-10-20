<script setup>
import AppHeader from './components/AppHeader.vue'
import AiAssistant from './components/AiAssistant.vue'
</script>

<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <AppHeader />
    <main class="flex-1 w-full max-w-7xl mx-auto px-4 py-6">
      <RouterView />
    </main>
    <footer class="py-4 text-center text-xs text-gray-400 border-t border-gray-200">
      校园二手交易平台 · 软件工程课程实验
    </footer>
    <AiAssistant />
  </div>
</template>
