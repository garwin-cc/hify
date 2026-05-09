<template>
  <div class="hify-table-wrapper">
    <el-table
      v-loading="loading"
      :data="records"
      :row-key="rowKey"
      :row-style="rowStyle"
      style="width: 100%"
    >
      <template v-for="col in columns" :key="col.prop ?? col.slot ?? col.label">
        <el-table-column
          :label="col.label"
          :prop="col.slot ? undefined : col.prop"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align ?? 'left'"
          :fixed="col.fixed"
          :sortable="col.sortable ?? false"
        >
          <template v-if="col.slot" #default="scope">
            <slot :name="col.slot" v-bind="scope" />
          </template>
        </el-table-column>
      </template>
    </el-table>

    <!-- 空状态 -->
    <div v-if="!loading && records.length === 0" class="hify-table__empty">
      <el-empty :description="emptyText" :image-size="80" />
    </div>

    <!-- 分页 -->
    <div v-if="showPagination && total > 0" class="hify-table__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        @change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
import { ref, onMounted } from 'vue'

// ── 类型 ──────────────────────────────────────────────────────────────

export interface HifyColumn {
  label: string
  prop?: string
  width?: string | number
  minWidth?: string | number
  slot?: string
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  sortable?: boolean
}

export interface PageData<R> {
  records: R[]
  total: number
  page: number
  size: number
}

// ── Props & Emits ─────────────────────────────────────────────────────

const props = withDefaults(defineProps<{
  columns: HifyColumn[]
  api: (page: number, pageSize: number) => Promise<PageData<T>>
  showPagination?: boolean
  rowKey?: string
  defaultPageSize?: number
  emptyText?: string
  immediate?: boolean
  rowStyle?: Record<string, string> | ((row: { row: T }) => Record<string, string>)
}>(), {
  showPagination: true,
  rowKey: 'id',
  defaultPageSize: 20,
  emptyText: '暂无数据',
  immediate: true,
  rowStyle: undefined,
})

// ── State ─────────────────────────────────────────────────────────────

const records     = ref<T[]>([])
const total       = ref(0)
const loading     = ref(false)
const currentPage = ref(1)
const pageSize    = ref(props.defaultPageSize)

// ── Data loading ──────────────────────────────────────────────────────

async function load() {
  loading.value = true
  try {
    const result = await props.api(currentPage.value, pageSize.value)
    records.value = result.records
    total.value   = result.total
  } finally {
    loading.value = false
  }
}

/** 从第 1 页重新加载（新增/删除后调用） */
function refresh() {
  currentPage.value = 1
  load()
}

onMounted(() => {
  if (props.immediate) load()
})

defineExpose({ refresh, load })
</script>

<style scoped>
.hify-table-wrapper {
  display: flex;
  flex-direction: column;
}

.hify-table__empty {
  padding: var(--space-10) 0;
}

.hify-table__pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--card-padding);
  border-top: 1px solid var(--border-color-light);
}
</style>
