<template>
  <el-tooltip content="Mock 为服务端基于真实检索结果的模板化演示输出，非真实大模型" placement="top">
    <el-tag :type="mode === 'mock' ? 'warning' : 'success'" size="small" effect="dark">
      {{ mode === 'mock' ? '演示模式 Mock' : '真实模型' }}
    </el-tag>
  </el-tooltip>
</template>

<script setup lang="ts">
defineProps<{ mode?: string }>()
</script>
