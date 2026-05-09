<template>
  <el-dialog
    v-model="visible"
    :title="computedTitle"
    :width="width"
    destroy-on-close
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="model"
      :rules="rules"
      :label-width="labelWidth"
      label-position="right"
    >
      <slot :is-edit="isEdit" :data="editData" />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ cancelText }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ submitText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

// ── Props & Emits ─────────────────────────────────────────────────────

const props = withDefaults(defineProps<{
  /** v-model 外部控制显隐（可选；open()/close() 也可直接控制） */
  modelValue?: boolean
  /** 新增模式标题 */
  title: string
  /** 编辑模式标题，不传则复用 title */
  editTitle?: string
  width?: string
  /** 绑定到 el-form 的响应式数据对象 */
  model: Record<string, unknown>
  rules?: FormRules
  labelWidth?: string
  submitText?: string
  cancelText?: string
}>(), {
  width: '480px',
  labelWidth: '80px',
  submitText: '确认',
  cancelText: '取消',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 父组件处理 API 调用，完成后调用 done(true) 关闭；done(false) 保持打开 */
  'submit': [done: (success?: boolean) => void]
  /** 对话框打开时通知父组件，携带编辑数据（新增时为 undefined） */
  'open': [data: unknown]
}>()

// ── State ─────────────────────────────────────────────────────────────

const formRef   = ref<FormInstance>()
const submitting = ref(false)
const isEdit    = ref(false)
const editData  = ref<unknown>(undefined)

// ── Visibility ────────────────────────────────────────────────────────

const visible = computed({
  get: () => props.modelValue ?? _visible.value,
  set: (v) => {
    _visible.value = v
    emit('update:modelValue', v)
  },
})
const _visible = ref(false)

// ── Title ─────────────────────────────────────────────────────────────

const computedTitle = computed(() =>
  isEdit.value ? (props.editTitle ?? props.title) : props.title
)

// ── Methods ───────────────────────────────────────────────────────────

/**
 * 打开对话框。
 * @param data 传入已有数据则为编辑模式，不传为新增模式。
 *             父组件在调用前自行填充 model（或监听 @open 事件后填充）。
 */
function open(data?: unknown) {
  isEdit.value   = data !== undefined
  editData.value = data
  visible.value  = true
  emit('open', data)
}

function close() {
  visible.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  emit('submit', (success = true) => {
    submitting.value = false
    if (success) visible.value = false
  })
}

function handleClosed() {
  formRef.value?.clearValidate()
  submitting.value = false
}

defineExpose({ open, close })
</script>
