<template>
  <div class="dp-page" style="text-align: center; padding-top: 80px">
    <h1 style="font-size: 64px; color: var(--dp-primary)">404</h1>
    <p style="color: var(--dp-text-secondary)">页面不存在</p>
    <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
  </div>
</template>

<script setup lang="ts"></script>
