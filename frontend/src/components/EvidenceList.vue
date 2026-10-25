<template>
  <div>
    <div class="dp-evidence" v-for="(ev, i) in evidence" :key="ev.chunkId">
      <div class="dp-evidence-meta">
        <el-tag size="small" type="primary" effect="plain">E{{ i + 1 }}</el-tag>
        <span class="dp-inline-code">{{ ev.path }}</span>
        <span>第 {{ ev.startLine }}-{{ ev.endLine }} 行</span>
        <el-tag v-if="ev.language" size="small" effect="plain">{{ ev.language }}</el-tag>
        <span>相关度 {{ ev.score.toFixed(3) }}</span>
        <el-button link type="primary" size="small" @click="$emit('view', ev)">查看代码</el-button>
      </div>
      <div class="dp-code">{{ ev.snippet }}</div>
    </div>
    <el-empty v-if="!evidence?.length" description="本条回答没有携带代码证据" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
import type { AgentEvidence } from '../api/types'

defineProps<{ evidence?: AgentEvidence[] }>()
defineEmits<{ view: [ev: AgentEvidence] }>()
</script>
