<template>
  <div class="dp-page">
    <div class="dp-card">
      <div class="dp-toolbar" style="margin-bottom: 0">
        <el-select v-model="projectId" placeholder="选择项目" style="width: 200px" @change="onProjectChange">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-select v-model="repositoryId" placeholder="全部仓库" clearable style="width: 200px">
          <el-option label="项目内全部仓库" :value="undefined" />
          <el-option v-for="r in repos" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-radio-group v-model="mode">
          <el-radio-button value="keyword">关键词</el-radio-button>
          <el-radio-button value="semantic">语义</el-radio-button>
          <el-radio-button value="hybrid">混合</el-radio-button>
        </el-radio-group>
        <el-input v-model="q" placeholder="类名 / 方法名 / 中文关键词 / 错误信息片段" style="flex: 1; min-width: 240px"
          @keyup.enter="doSearch" data-testid="search-input" />
        <el-button type="primary" :loading="loading" @click="doSearch()" data-testid="search-submit">检索</el-button>
      </div>
      <p class="dp-hint" v-if="result">
        命中 {{ result.total }} 条 ·
        <el-tag v-if="result.vectorEnabled" size="small" type="success" effect="plain">向量通道可用</el-tag>
        <el-tag v-else size="small" type="warning" effect="plain">向量通道不可用（仅关键词）</el-tag>
      </p>
    </div>

    <div class="dp-card" style="margin-top: 16px" v-loading="loading">
      <template v-if="result?.results?.length">
        <div class="dp-evidence" v-for="hit in result.results" :key="`${hit.repositoryId}-${hit.chunkId}`">
          <div class="dp-evidence-meta">
            <span class="dp-inline-code">{{ hit.path }}</span>
            <span>第 {{ hit.startLine }}-{{ hit.endLine }} 行</span>
            <el-tag v-if="hit.language" size="small" effect="plain">{{ hit.language }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ hit.channel }}</el-tag>
            <span>score {{ hit.score.toFixed(3) }}</span>
            <el-button link type="primary" size="small" @click="openFile(hit.fileId)">查看代码</el-button>
          </div>
          <div class="dp-code">{{ hit.snippet }}</div>
        </div>
        <el-pagination background layout="total, prev, pager, next" :total="result.total" :page-size="size"
          :current-page="page" @current-change="doSearch" style="margin-top: 8px" />
      </template>
      <el-empty v-else-if="searched && !loading" description="没有检索到相关代码：试试更具体的关键词，或确认仓库已完成索引" />
      <el-empty v-else-if="!searched" description="选择项目并输入关键词开始检索" />
    </div>

    <el-dialog v-model="previewVisible" :title="preview?.path" width="820">
      <div class="dp-code" style="max-height: 60vh">{{ preview?.content || '(无内容)' }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiProjects, apiRepositories, apiSearch, apiCodeFile } from '../api'
import type { CodeFileVO, ProjectVO, RepositoryVO, SearchResponse } from '../api/types'

const projects = ref<ProjectVO[]>([])
const repos = ref<RepositoryVO[]>([])
const projectId = ref<number>()
const repositoryId = ref<number | undefined>()
const mode = ref('keyword')
const q = ref('')
const page = ref(1)
const size = 10
const result = ref<SearchResponse | null>(null)
const loading = ref(false)
const searched = ref(false)
const previewVisible = ref(false)
const preview = ref<CodeFileVO | null>(null)

async function onProjectChange() {
  repositoryId.value = undefined
  repos.value = projectId.value ? (await apiRepositories(projectId.value, 1, 50)).data.data.list : []
}

async function doSearch(p = 1) {
  if (!projectId.value || !q.value.trim()) return
  page.value = p
  loading.value = true
  try {
    result.value = (
      await apiSearch({
        projectId: projectId.value,
        repositoryId: repositoryId.value,
        q: q.value.trim(),
        mode: mode.value,
        page: p,
        size,
      })
    ).data.data
    searched.value = true
  } finally {
    loading.value = false
  }
}

async function openFile(fileId?: number) {
  if (!fileId) return
  preview.value = (await apiCodeFile(fileId)).data.data
  previewVisible.value = true
}

onMounted(async () => {
  projects.value = (await apiProjects(1, 50)).data.data.list
})
</script>

<style scoped>
.dp-hint {
  margin: 10px 0 0;
  color: var(--dp-text-secondary);
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
