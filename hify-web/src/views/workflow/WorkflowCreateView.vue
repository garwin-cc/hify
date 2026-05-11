<template>
  <div class="page-content">
    <PageHeader
      :title="isEditMode ? '编辑工作流' : '新建工作流'"
      description="通过画布配置节点和连接关系，保存后可被 Agent 绑定触发"
    >
      <template #actions>
        <el-button @click="goBack">
          <el-icon style="margin-right: 4px"><ArrowLeft /></el-icon>
          返回列表
        </el-button>
        <el-button @click="formatJson">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>
          同步 JSON
        </el-button>
        <el-button @click="autoLayout">
          <el-icon style="margin-right: 4px"><Rank /></el-icon>
          自动布局
        </el-button>
        <el-button :loading="runningWorkflow" @click="handleRun">
          <el-icon style="margin-right: 4px"><VideoPlay /></el-icon>
          试运行
        </el-button>
        <el-button :loading="creatingTemplate" @click="openTemplateDialog">
          <el-icon style="margin-right: 4px"><Collection /></el-icon>
          另存为模板
        </el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEditMode ? '保存' : '提交' }}
        </el-button>
      </template>
    </PageHeader>

    <div v-loading="loadingDetail" class="workflow-editor">
      <aside class="node-palette">
        <div class="panel-title">节点</div>
        <button
          v-for="item in nodeTypes"
          :key="item.type"
          class="palette-item"
          type="button"
          @click="addNode(item.type)"
        >
          <span class="palette-item__port palette-item__port--in"></span>
          <span class="palette-item__body">
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <span class="palette-item__port palette-item__port--out"></span>
        </button>
        <section class="validation-panel">
          <div class="panel-title">编排检查</div>
          <div v-if="validationIssues.length === 0" class="validation-empty">
            当前配置可保存
          </div>
          <button
            v-for="issue in validationIssues"
            :key="issue.key"
            class="validation-item"
            :class="`validation-item--${issue.level}`"
            type="button"
            @click="focusIssue(issue)"
          >
            <strong>{{ issue.level === 'error' ? '错误' : '提醒' }}</strong>
            <span>{{ issue.message }}</span>
          </button>
        </section>
      </aside>

      <main class="canvas-panel">
        <div class="canvas-toolbar">
          <el-form :model="form" inline class="workflow-meta">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" placeholder="请输入工作流名称" style="width: 240px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="workflowConfig.enabled" style="width: 120px">
                <el-option label="PUBLISHED" :value="1" />
                <el-option label="DRAFT" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" placeholder="可选" style="width: 280px" />
            </el-form-item>
          </el-form>
          <div class="toolbar-actions">
            <el-tag v-if="linkingFrom" type="primary">选择目标节点完成连线</el-tag>
            <el-button-group>
              <el-button size="small" @click="zoomOut">
                <el-icon><ZoomOut /></el-icon>
              </el-button>
              <el-button size="small" @click="resetZoom">{{ Math.round(canvasScale * 100) }}%</el-button>
              <el-button size="small" @click="zoomIn">
                <el-icon><ZoomIn /></el-icon>
              </el-button>
            </el-button-group>
            <el-button size="small" @click="resetExample">恢复示例</el-button>
          </div>
        </div>

        <div class="workflow-canvas" @click="clearSelection">
          <div class="canvas-board" :style="canvasBoardStyle">
            <svg class="edge-layer">
              <defs>
                <marker id="workflow-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                  <path d="M 0 0 L 8 4 L 0 8 z" fill="#8a95ad" />
                </marker>
              </defs>
              <g v-for="edge in workflowConfig.edges" :key="edgeKey(edge)">
                <path
                  class="edge-path"
                  :class="{ 'edge-path--selected': selectedEdge === edge }"
                  :d="edgePath(edge)"
                  marker-end="url(#workflow-arrow)"
                  @click.stop="selectEdge(edge)"
                />
                <text
                  v-if="edge.conditionExpression"
                  class="edge-label"
                  :x="edgeLabelPosition(edge).x"
                  :y="edgeLabelPosition(edge).y"
                >
                  {{ edge.conditionExpression }}
                </text>
              </g>
            </svg>

            <div
              v-for="node in workflowConfig.nodes"
              :key="node.nodeKey"
              class="workflow-node"
              :class="[
                `workflow-node--${node.nodeType.toLowerCase()}`,
                nodeRunClass(node.nodeKey),
                { 'workflow-node--selected': selectedNodeKey === node.nodeKey },
                { 'workflow-node--link-source': linkingFrom === node.nodeKey },
              ]"
              :style="{ left: `${node.positionX ?? 80}px`, top: `${node.positionY ?? 80}px` }"
              @click.stop="selectNode(node.nodeKey)"
              @pointerdown.stop="startDrag($event, node)"
            >
              <div class="workflow-node__head">
                <span>{{ nodeTypeLabel(node.nodeType) }}</span>
                <el-button link size="small" @click.stop="startLink(node.nodeKey)">连线</el-button>
              </div>
              <div class="workflow-node__name">{{ node.name }}</div>
              <div class="workflow-node__key">{{ node.nodeKey }}</div>
              <div v-if="nodeRunOf(node.nodeKey)" class="workflow-node__run">
                <span>{{ nodeRunOf(node.nodeKey)?.status }}</span>
                <span v-if="nodeRunOf(node.nodeKey)?.elapsedMs != null">
                  {{ nodeRunOf(node.nodeKey)?.elapsedMs }}ms
                </span>
              </div>
            </div>
          </div>
        </div>
      </main>

      <aside class="config-panel">
        <section class="run-panel">
          <div class="panel-title">试运行</div>
          <el-input
            v-model="runInput"
            type="textarea"
            :rows="3"
            placeholder="输入一条用户消息，例如：我要申请退款"
          />
          <el-button
            type="primary"
            :loading="runningWorkflow"
            class="run-panel__button"
            @click="handleRun"
          >
            {{ runningWorkflow ? '运行中' : '运行工作流' }}
          </el-button>
          <div v-if="latestRun" class="run-summary" :class="`run-summary--${latestRun.status.toLowerCase()}`">
            <div class="run-summary__header">
              <strong>{{ latestRun.status }}</strong>
              <span v-if="latestRun.elapsedMs != null">{{ latestRun.elapsedMs }}ms</span>
            </div>
            <div v-if="latestRun.workflowVersionId" class="run-summary__block">
              <span>版本快照</span>
              <p>#{{ latestRun.workflowVersionId }}</p>
            </div>
            <div v-if="latestRun.currentNodeKey && latestRun.status === 'RUNNING'" class="run-summary__block">
              <span>当前节点</span>
              <p>{{ latestRun.currentNodeKey }}</p>
            </div>
            <div v-if="latestRun.status === 'WAITING' && reviewTask" class="review-card">
              <div class="review-card__head">
                <div>
                  <span>待审批</span>
                  <strong>{{ reviewTask.title }}</strong>
                </div>
                <el-tag type="warning" size="small">{{ reviewTask.nodeKey }}</el-tag>
              </div>
              <div class="review-card__content">{{ reviewTask.content }}</div>
              <el-input
                v-if="reviewTask.allowEdit"
                v-model="reviewEditedContent"
                type="textarea"
                :rows="4"
                placeholder="可编辑评审内容"
              />
              <el-input
                v-model="reviewComment"
                type="textarea"
                :rows="2"
                placeholder="评审备注（可选）"
              />
              <div class="review-card__actions">
                <el-button
                  v-for="action in reviewTask.actions"
                  :key="action"
                  :type="action === 'APPROVE' ? 'primary' : 'danger'"
                  plain
                  @click="handleReviewAction(action)"
                >
                  {{ action === 'APPROVE' ? '通过' : action === 'REJECT' ? '拒绝' : action }}
                </el-button>
              </div>
            </div>
            <div v-if="latestRun.output" class="run-summary__block">
              <span>输出</span>
              <p>{{ latestRun.output }}</p>
            </div>
            <div v-if="latestRun.error" class="run-summary__block run-summary__block--error">
              <span>错误</span>
              <p>{{ latestRun.error }}</p>
            </div>
          </div>
        </section>

        <template v-if="selectedNode">
          <div class="panel-title">节点配置</div>
          <div class="node-actions">
            <el-button
              v-if="selectedNode.nodeType !== 'START'"
              size="small"
              @click="duplicateSelectedNode"
            >
              复制节点
            </el-button>
            <el-button
              v-if="selectedNode.nodeType === 'CONDITION'"
              size="small"
              type="primary"
              plain
              @click="createConditionBranches"
            >
              生成分支
            </el-button>
            <el-button
              v-if="canDebugSelectedNode"
              size="small"
              :loading="debuggingNode"
              @click="debugSelectedNode"
            >
              调试节点
            </el-button>
          </div>
          <div v-if="availableVariables.length" class="variable-panel">
            <div class="panel-title">变量引用</div>
            <button
              v-for="variable in availableVariables"
              :key="variable.value"
              class="variable-token"
              type="button"
              @click="insertVariable(variable.value)"
            >
              {{ variable.label }}
            </button>
          </div>
          <div v-if="selectedNodeRun" class="node-run-detail">
            <div class="node-run-detail__meta">
              <el-tag size="small" :type="runTagType(selectedNodeRun.status)">
                {{ selectedNodeRun.status }}
              </el-tag>
              <span v-if="selectedNodeRun.elapsedMs != null">{{ selectedNodeRun.elapsedMs }}ms</span>
            </div>
            <el-input
              :model-value="JSON.stringify(selectedNodeRun.outputs ?? {}, null, 2)"
              type="textarea"
              :rows="6"
              readonly
              class="json-preview"
            />
            <div v-if="selectedNodeRun.error" class="node-run-detail__error">
              {{ selectedNodeRun.error }}
            </div>
          </div>
          <div v-if="nodeDebugResult" class="node-run-detail">
            <div class="node-run-detail__meta">
              <el-tag size="small" :type="runTagType(nodeDebugResult.status)">
                调试 {{ nodeDebugResult.status }}
              </el-tag>
              <span v-if="nodeDebugResult.elapsedMs != null">{{ nodeDebugResult.elapsedMs }}ms</span>
            </div>
            <el-input
              :model-value="JSON.stringify(nodeDebugResult.outputs ?? {}, null, 2)"
              type="textarea"
              :rows="6"
              readonly
              class="json-preview"
            />
            <div v-if="nodeDebugResult.error" class="node-run-detail__error">
              {{ nodeDebugResult.error }}
            </div>
          </div>
          <el-form label-position="top" class="node-config-form">
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.name" />
            </el-form-item>
            <el-form-item label="节点 Key">
              <el-input v-model="selectedNode.nodeKey" disabled />
            </el-form-item>

            <template v-if="selectedNode.nodeType === 'LLM'">
              <el-form-item label="模型配置">
                <el-select
                  v-model="selectedConfig.modelConfigId"
                  placeholder="请选择模型"
                  filterable
                  style="width: 100%"
                  :loading="loadingModels"
                >
                  <el-option-group
                    v-for="group in modelGroups"
                    :key="group.providerId"
                    :label="group.providerName"
                  >
                    <el-option
                      v-for="model in group.models"
                      :key="model.id"
                      :label="`${model.name}（${model.modelId}）`"
                      :value="model.id"
                    />
                  </el-option-group>
                </el-select>
                <div
                  v-if="selectedConfig.modelConfigId && !modelNameOf(Number(selectedConfig.modelConfigId))"
                  class="field-warning"
                >
                  当前模型不存在或已禁用：{{ selectedConfig.modelConfigId }}
                </div>
              </el-form-item>
              <el-form-item label="Prompt">
                <el-input v-model="selectedConfig.prompt" type="textarea" :rows="7" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="answer" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.nodeType === 'CONDITION'">
              <el-form-item label="表达式">
                <el-input v-model="selectedConfig.expression" placeholder="'{{node.var}}' == 'value'" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="matched" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.nodeType === 'API_CALL'">
              <el-form-item label="URL">
                <el-input v-model="selectedConfig.url" />
              </el-form-item>
              <el-form-item label="Method">
                <el-select v-model="selectedConfig.method">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                </el-select>
              </el-form-item>
              <el-form-item label="Headers JSON">
                <el-input v-model="selectedConfig.headersText" type="textarea" :rows="4" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="response" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.nodeType === 'KNOWLEDGE'">
              <el-form-item label="知识库 ID">
                <el-input-number v-model="selectedConfig.knowledgeBaseId" :min="1" controls-position="right" />
              </el-form-item>
              <el-form-item label="查询">
                <el-input v-model="selectedConfig.query" placeholder="{{start.userMessage}}" />
              </el-form-item>
              <el-form-item label="Top K">
                <el-input-number v-model="selectedConfig.topK" :min="1" :max="20" controls-position="right" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="context" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.nodeType === 'HUMAN_REVIEW'">
              <el-form-item label="评审标题">
                <el-input v-model="selectedConfig.title" placeholder="人工审核" />
              </el-form-item>
              <el-form-item label="评审内容">
                <el-input v-model="selectedConfig.content" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item label="评审动作">
                <el-select
                  v-model="selectedConfig.actions"
                  multiple
                  allow-create
                  default-first-option
                  placeholder="APPROVE / REJECT"
                >
                  <el-option label="APPROVE" value="APPROVE" />
                  <el-option label="REJECT" value="REJECT" />
                </el-select>
              </el-form-item>
              <el-form-item label="允许编辑内容">
                <el-switch v-model="selectedConfig.allowEdit" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="result" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.nodeType === 'CODE_TASK'">
              <el-form-item label="任务描述">
                <el-input v-model="selectedConfig.task" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item label="执行器">
                <el-select v-model="selectedConfig.executor">
                  <el-option label="MCP Code Worker" value="MCP" />
                </el-select>
              </el-form-item>
              <el-form-item label="MCP Server ID">
                <el-input-number v-model="selectedConfig.mcpServerId" :min="1" controls-position="right" />
              </el-form-item>
              <el-form-item label="工具名称">
                <el-input v-model="selectedConfig.toolName" placeholder="code_worker" />
              </el-form-item>
              <el-form-item label="超时时间（秒）">
                <el-input-number v-model="selectedConfig.timeoutSeconds" :min="30" :max="1800" controls-position="right" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="result" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.nodeType === 'END'">
              <el-form-item label="输出变量">
                <el-input v-model="selectedConfig.outputVariable" placeholder="node.answer" />
              </el-form-item>
            </template>

            <el-button
              v-if="selectedNode.nodeType !== 'START'"
              type="danger"
              text
              @click="deleteSelectedNode"
            >
              删除节点
            </el-button>
          </el-form>
        </template>

        <template v-else-if="selectedEdge">
          <div class="panel-title">连接配置</div>
          <el-form label-position="top" class="node-config-form">
            <el-form-item label="源节点">
              <el-input :model-value="selectedEdge.sourceNodeKey" disabled />
            </el-form-item>
            <el-form-item label="目标节点">
              <el-input :model-value="selectedEdge.targetNodeKey" disabled />
            </el-form-item>
            <el-form-item label="边类型">
              <el-select v-model="selectedEdge.edgeType">
                <el-option label="DEFAULT" value="DEFAULT" />
                <el-option label="CONDITION" value="CONDITION" />
              </el-select>
            </el-form-item>
            <el-form-item label="条件表达式">
              <el-input v-model="selectedEdge.conditionExpression" placeholder="true / false，可留空" />
            </el-form-item>
            <el-button type="danger" text @click="deleteSelectedEdge">删除连接</el-button>
          </el-form>
        </template>

        <template v-else>
          <section v-if="workflowVersions.length" class="version-panel">
            <div class="panel-title">版本快照</div>
            <div
              v-for="version in workflowVersions"
              :key="version.id"
              class="version-item"
            >
              <div>
                <strong>v{{ version.versionNo }}</strong>
                <span>{{ version.changeSummary || '保存工作流' }}</span>
              </div>
              <el-button
                size="small"
                text
                type="primary"
                @click="restoreVersion(version.versionNo)"
              >
                恢复
              </el-button>
            </div>
          </section>
          <div class="panel-title">JSON 预览</div>
          <el-input
            :model-value="previewJson"
            type="textarea"
            :rows="24"
            readonly
            class="json-preview"
          />
        </template>
      </aside>
    </div>

    <el-dialog v-model="templateDialogVisible" title="另存为模板" width="520px">
      <el-form label-position="top">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="templateForm.category" maxlength="50" placeholder="通用" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="templateForm.tagsText" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="templateForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input v-model="templateForm.changelog" placeholder="初始版本" />
        </el-form-item>
        <el-checkbox v-model="templateForm.publish">创建后立即发布</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingTemplate" @click="handleCreateTemplate">
          创建模板
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Collection, MagicStick, Rank, VideoPlay, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createTemplateFromWorkflow,
  createWorkflow,
  debugWorkflowNode,
  getWorkflowDetail,
  getLatestWorkflowRun,
  getWorkflowReviewTask,
  getWorkflowRunDetail,
  getWorkflowVersions,
  restoreWorkflowVersion,
  startAsyncWorkflowRun,
  submitWorkflowReview,
  updateWorkflow,
  workflowRunEventsUrl,
  type WorkflowConfigJson,
  type WorkflowDetail,
  type WorkflowEdge,
  type WorkflowNode,
  type WorkflowNodeRun,
  type WorkflowNodeDebugResult,
  type WorkflowRunEvent,
  type WorkflowRun,
  type WorkflowReviewTask,
  type WorkflowVersion,
} from '@/api/workflow'
import { getModelGroups, type ModelGroup } from '@/api/agent'
import { notifySuccess } from '@/utils/notify'

type NodeType = 'START' | 'LLM' | 'CONDITION' | 'API_CALL' | 'KNOWLEDGE' | 'HUMAN_REVIEW' | 'CODE_TASK' | 'END'
type NodeConfig = Record<string, any>
type ValidationIssue = {
  key: string
  level: 'error' | 'warning'
  message: string
  nodeKey?: string
  edge?: WorkflowEdge
}

const router = useRouter()
const route = useRoute()
const submitting = ref(false)
const runningWorkflow = ref(false)
const loadingDetail = ref(false)
const workflowId = computed(() => Number(route.params.id))
const isEditMode = computed(() => Number.isFinite(workflowId.value) && workflowId.value > 0)
const selectedNodeKey = ref('')
const selectedEdge = ref<WorkflowEdge | null>(null)
const linkingFrom = ref('')
const runInput = ref('我要申请退款')
const latestRun = ref<WorkflowRun | null>(null)
const reviewTask = ref<WorkflowReviewTask | null>(null)
const reviewComment = ref('')
const reviewEditedContent = ref('')
const runPollingTimer = ref<number | null>(null)
const runEventSource = ref<EventSource | null>(null)
const lastRunEventSeq = ref(0)
const canvasScale = ref(1)
const modelGroups = ref<ModelGroup[]>([])
const loadingModels = ref(false)
const debuggingNode = ref(false)
const nodeDebugResult = ref<WorkflowNodeDebugResult | null>(null)
const workflowVersions = ref<WorkflowVersion[]>([])
const templateDialogVisible = ref(false)
const creatingTemplate = ref(false)
const templateForm = reactive({
  name: '',
  description: '',
  category: '通用',
  tagsText: '',
  changelog: '初始版本',
  publish: true,
})

const nodeTypes = [
  { type: 'START' as NodeType, label: '开始', short: 'S', description: '用户输入入口' },
  { type: 'LLM' as NodeType, label: 'LLM', short: 'L', description: '调用模型生成内容' },
  { type: 'CONDITION' as NodeType, label: '条件', short: 'C', description: '根据表达式分支' },
  { type: 'KNOWLEDGE' as NodeType, label: '知识库', short: 'K', description: '检索 RAG 内容' },
  { type: 'API_CALL' as NodeType, label: 'API 调用', short: 'A', description: '请求外部接口' },
  { type: 'HUMAN_REVIEW' as NodeType, label: '人工审核', short: 'H', description: '暂停等待人工确认' },
  { type: 'CODE_TASK' as NodeType, label: '代码任务', short: 'C', description: '调用 Code Worker 实现' },
  { type: 'END' as NodeType, label: '结束', short: 'E', description: '输出最终结果' },
]

const exampleConfig: WorkflowConfigJson = {
  status: 'PUBLISHED',
  enabled: 1,
  startNodeKey: 'start',
  nodes: [
    { nodeKey: 'start', nodeType: 'START', name: '开始', config: {}, positionX: 80, positionY: 180 },
    {
      nodeKey: 'classify_intent',
      nodeType: 'LLM',
      name: '识别用户意图',
      config: {
        modelConfigId: 1,
        prompt: '你是智能客服意图分类器。请根据用户消息判断意图，只返回 refund、order_status、human、other 之一。用户消息：{{start.userMessage}}',
        outputVariable: 'intent',
      },
      positionX: 320,
      positionY: 180,
    },
    {
      nodeKey: 'is_refund',
      nodeType: 'CONDITION',
      name: '是否退款',
      config: { expression: "'{{classify_intent.intent}}' == 'refund'", outputVariable: 'matched' },
      positionX: 580,
      positionY: 180,
    },
    {
      nodeKey: 'refund_reply',
      nodeType: 'LLM',
      name: '退款回复',
      config: {
        modelConfigId: 1,
        prompt: '用户想办理退款。请礼貌说明退款所需信息，并引导用户提供订单号。用户消息：{{start.userMessage}}',
        outputVariable: 'answer',
      },
      positionX: 840,
      positionY: 90,
    },
    {
      nodeKey: 'general_reply',
      nodeType: 'LLM',
      name: '通用回复',
      config: {
        modelConfigId: 1,
        prompt: '你是智能客服。请根据用户消息给出简洁、礼貌、可执行的回复。用户消息：{{start.userMessage}}',
        outputVariable: 'answer',
      },
      positionX: 840,
      positionY: 280,
    },
    {
      nodeKey: 'end_refund',
      nodeType: 'END',
      name: '结束（退款）',
      config: { outputVariable: 'refund_reply.answer' },
      positionX: 1100,
      positionY: 90,
    },
    {
      nodeKey: 'end_general',
      nodeType: 'END',
      name: '结束（通用）',
      config: { outputVariable: 'general_reply.answer' },
      positionX: 1100,
      positionY: 280,
    },
  ],
  edges: [
    { sourceNodeKey: 'start', targetNodeKey: 'classify_intent', edgeType: 'DEFAULT', sortOrder: 0 },
    { sourceNodeKey: 'classify_intent', targetNodeKey: 'is_refund', edgeType: 'DEFAULT', sortOrder: 0 },
    { sourceNodeKey: 'is_refund', targetNodeKey: 'refund_reply', edgeType: 'CONDITION', conditionExpression: 'true', sortOrder: 0 },
    { sourceNodeKey: 'is_refund', targetNodeKey: 'general_reply', edgeType: 'CONDITION', conditionExpression: 'false', sortOrder: 1 },
    { sourceNodeKey: 'refund_reply', targetNodeKey: 'end_refund', edgeType: 'DEFAULT', sortOrder: 0 },
    { sourceNodeKey: 'general_reply', targetNodeKey: 'end_general', edgeType: 'DEFAULT', sortOrder: 0 },
  ],
}

const form = reactive({
  name: '智能客服分类工作流',
  description: '识别用户意图，按退款/通用路径生成客服回复',
})

const workflowConfig = reactive<WorkflowConfigJson>(copyConfig(exampleConfig))

const selectedNode = computed(() =>
  workflowConfig.nodes.find((node) => node.nodeKey === selectedNodeKey.value) ?? null,
)
const selectedNodeRun = computed(() =>
  selectedNode.value ? nodeRunOf(selectedNode.value.nodeKey) : null,
)
const selectedConfig = computed<NodeConfig>(() => {
  if (!selectedNode.value) return {}
  if (!selectedNode.value.config) selectedNode.value.config = {}
  if (selectedNode.value.nodeType === 'API_CALL') {
    const config = selectedNode.value.config as NodeConfig
    if (config.headersText == null) {
      config.headersText = JSON.stringify(config.headers ?? {}, null, 2)
    }
  }
  return selectedNode.value.config as NodeConfig
})
const previewJson = computed(() => JSON.stringify(normalizedConfig(), null, 2))
const canvasBoardStyle = computed(() => ({
  width: `${canvasSize.value.width}px`,
  height: `${canvasSize.value.height}px`,
  transform: `scale(${canvasScale.value})`,
}))
const canvasSize = computed(() => {
  const maxX = Math.max(1600, ...workflowConfig.nodes.map((node) => (node.positionX ?? 80) + 260))
  const maxY = Math.max(900, ...workflowConfig.nodes.map((node) => (node.positionY ?? 80) + 180))
  return {
    width: maxX,
    height: maxY,
  }
})
const availableVariables = computed(() => buildVariableOptions(selectedNode.value?.nodeKey))
const validationIssues = computed<ValidationIssue[]>(() => buildValidationIssues())
const canDebugSelectedNode = computed(() =>
  !!selectedNode.value && !['START', 'END'].includes(selectedNode.value.nodeType),
)

function copyConfig<T>(config: T): T {
  return JSON.parse(JSON.stringify(config))
}

function normalizedConfig(): WorkflowConfigJson {
  return {
    enabled: workflowConfig.enabled ?? 1,
    status: workflowConfig.enabled === 0 ? 'DRAFT' : 'PUBLISHED',
    startNodeKey: workflowConfig.startNodeKey,
    nodes: workflowConfig.nodes.map((node) => ({
      ...node,
      config: normalizeNodeConfig(node),
    })),
    edges: workflowConfig.edges ?? [],
  }
}

function normalizeNodeConfig(node: WorkflowNode) {
  const config = { ...((node.config ?? {}) as NodeConfig) }
  if (node.nodeType === 'API_CALL') {
    const headersText = typeof config.headersText === 'string' ? config.headersText : '{}'
    try {
      config.headers = JSON.parse(headersText || '{}')
    } catch {
      config.headers = {}
    }
    delete config.headersText
  }
  return config
}

function nodeTypeLabel(type: string) {
  return nodeTypes.find((item) => item.type === type)?.label ?? type
}

function nodeRunOf(nodeKey: string): WorkflowNodeRun | null {
  return latestRun.value?.nodeRuns?.find((run) => run.nodeKey === nodeKey) ?? null
}

function nodeRunClass(nodeKey: string) {
  const run = nodeRunOf(nodeKey)
  return run ? `workflow-node--run-${run.status.toLowerCase()}` : ''
}

function runTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'TIMEOUT') return 'warning'
  if (status === 'WAITING') return 'warning'
  if (status === 'CANCELED') return 'info'
  return 'primary'
}

function modelNameOf(modelConfigId: number) {
  for (const group of modelGroups.value) {
    const model = group.models.find((item) => item.id === modelConfigId)
    if (model) return `${group.providerName} / ${model.name}（${model.modelId}）`
  }
  return ''
}

async function loadModelGroups() {
  loadingModels.value = true
  try {
    modelGroups.value = await getModelGroups()
  } catch {
    modelGroups.value = []
  } finally {
    loadingModels.value = false
  }
}

function buildVariableOptions(currentNodeKey?: string) {
  const options = [{ label: 'start.userMessage', value: '{{start.userMessage}}' }]
  workflowConfig.nodes.forEach((node) => {
    if (node.nodeKey === currentNodeKey) return
    const config = (node.config ?? {}) as NodeConfig
    if (typeof config.outputVariable === 'string' && config.outputVariable.trim()) {
      const key = `${node.nodeKey}.${config.outputVariable.trim()}`
      options.push({ label: key, value: `{{${key}}}` })
    }
  })
  return options
}

function buildValidationIssues(): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  const nodes = workflowConfig.nodes
  const edges = workflowConfig.edges ?? []
  const keys = new Set<string>()
  const duplicateKeys = new Set<string>()

  nodes.forEach((node) => {
    if (!node.nodeKey?.trim()) {
      issues.push({
        key: `empty-key-${node.name}`,
        level: 'error',
        message: `节点「${node.name || '未命名'}」缺少节点 Key`,
        nodeKey: node.nodeKey,
      })
      return
    }
    if (keys.has(node.nodeKey)) duplicateKeys.add(node.nodeKey)
    keys.add(node.nodeKey)
  })

  duplicateKeys.forEach((nodeKey) => {
    issues.push({ key: `duplicate-${nodeKey}`, level: 'error', message: `节点 Key 重复：${nodeKey}`, nodeKey })
  })

  const startNode = nodes.find((node) => node.nodeKey === workflowConfig.startNodeKey)
  if (!workflowConfig.startNodeKey || !startNode) {
    issues.push({ key: 'missing-start', level: 'error', message: '缺少有效的开始节点' })
  }

  if (!nodes.some((node) => node.nodeType === 'END')) {
    issues.push({ key: 'missing-end', level: 'warning', message: '建议配置结束节点，明确最终输出' })
  }

  edges.forEach((edge, index) => {
    if (!keys.has(edge.sourceNodeKey) || !keys.has(edge.targetNodeKey)) {
      issues.push({
        key: `dangling-edge-${index}`,
        level: 'error',
        message: `连接 ${edge.sourceNodeKey} → ${edge.targetNodeKey} 指向不存在的节点`,
        edge,
      })
    }
    if (edge.sourceNodeKey === edge.targetNodeKey) {
      issues.push({
        key: `self-edge-${index}`,
        level: 'error',
        message: `节点 ${edge.sourceNodeKey} 不能连接到自身`,
        edge,
      })
    }
  })

  nodes.forEach((node) => {
    const config = (node.config ?? {}) as NodeConfig
    if (node.nodeType === 'LLM') {
      if (!config.modelConfigId) {
        issues.push({ key: `llm-model-${node.nodeKey}`, level: 'error', message: `LLM 节点「${node.name}」缺少模型配置 ID`, nodeKey: node.nodeKey })
      }
      if (!String(config.prompt ?? '').trim()) {
        issues.push({ key: `llm-prompt-${node.nodeKey}`, level: 'error', message: `LLM 节点「${node.name}」缺少 Prompt`, nodeKey: node.nodeKey })
      }
      if (!String(config.outputVariable ?? '').trim()) {
        issues.push({ key: `llm-output-${node.nodeKey}`, level: 'error', message: `LLM 节点「${node.name}」缺少输出变量`, nodeKey: node.nodeKey })
      }
    }
    if (node.nodeType === 'CONDITION') {
      const outgoing = edges.filter((edge) => edge.sourceNodeKey === node.nodeKey)
      const hasTrue = outgoing.some((edge) => edge.conditionExpression === 'true')
      const hasFalse = outgoing.some((edge) => edge.conditionExpression === 'false')
      if (!String(config.expression ?? '').trim()) {
        issues.push({ key: `condition-expression-${node.nodeKey}`, level: 'error', message: `条件节点「${node.name}」缺少表达式`, nodeKey: node.nodeKey })
      }
      if (!hasTrue || !hasFalse) {
        issues.push({ key: `condition-branches-${node.nodeKey}`, level: 'warning', message: `条件节点「${node.name}」建议配置 true / false 两条分支`, nodeKey: node.nodeKey })
      }
    }
    if (node.nodeType === 'KNOWLEDGE' && !config.knowledgeBaseId) {
      issues.push({ key: `knowledge-id-${node.nodeKey}`, level: 'error', message: `知识库节点「${node.name}」缺少知识库 ID`, nodeKey: node.nodeKey })
    }
    if (node.nodeType === 'HUMAN_REVIEW') {
      if (!String(config.content ?? '').trim()) {
        issues.push({ key: `review-content-${node.nodeKey}`, level: 'error', message: `人工审核节点「${node.name}」缺少评审内容`, nodeKey: node.nodeKey })
      }
      if (!String(config.outputVariable ?? '').trim()) {
        issues.push({ key: `review-output-${node.nodeKey}`, level: 'error', message: `人工审核节点「${node.name}」缺少输出变量`, nodeKey: node.nodeKey })
      }
    }
    if (node.nodeType === 'API_CALL') {
      if (!String(config.url ?? '').trim()) {
        issues.push({ key: `api-url-${node.nodeKey}`, level: 'error', message: `API 节点「${node.name}」缺少 URL`, nodeKey: node.nodeKey })
      }
      try {
        JSON.parse(String(config.headersText ?? '{}') || '{}')
      } catch {
        issues.push({ key: `api-headers-${node.nodeKey}`, level: 'error', message: `API 节点「${node.name}」的 Headers JSON 不合法`, nodeKey: node.nodeKey })
      }
    }
    if (node.nodeType === 'CODE_TASK') {
      if (!String(config.task ?? '').trim()) {
        issues.push({ key: `code-task-${node.nodeKey}`, level: 'error', message: `代码任务节点「${node.name}」缺少任务描述`, nodeKey: node.nodeKey })
      }
      if (!config.mcpServerId) {
        issues.push({ key: `code-mcp-server-${node.nodeKey}`, level: 'error', message: `代码任务节点「${node.name}」缺少 MCP Server ID`, nodeKey: node.nodeKey })
      }
      if (!String(config.toolName ?? '').trim()) {
        issues.push({ key: `code-tool-${node.nodeKey}`, level: 'error', message: `代码任务节点「${node.name}」缺少工具名称`, nodeKey: node.nodeKey })
      }
    }
    if (node.nodeType === 'END' && !String(config.outputVariable ?? '').trim()) {
      issues.push({ key: `end-output-${node.nodeKey}`, level: 'warning', message: `结束节点「${node.name}」未指定输出变量`, nodeKey: node.nodeKey })
    }
  })

  if (startNode) {
    const reachable = collectReachableNodeKeys(startNode.nodeKey)
    nodes
      .filter((node) => !reachable.has(node.nodeKey))
      .forEach((node) => {
        issues.push({ key: `unreachable-${node.nodeKey}`, level: 'warning', message: `节点「${node.name}」无法从开始节点到达`, nodeKey: node.nodeKey })
      })
  }

  return issues
}

function collectReachableNodeKeys(startKey: string) {
  const edges = workflowConfig.edges ?? []
  const visited = new Set<string>()
  const queue = [startKey]
  while (queue.length) {
    const key = queue.shift()!
    if (visited.has(key)) continue
    visited.add(key)
    edges
      .filter((edge) => edge.sourceNodeKey === key)
      .forEach((edge) => {
        if (!visited.has(edge.targetNodeKey)) queue.push(edge.targetNodeKey)
      })
  }
  return visited
}

function focusIssue(issue: ValidationIssue) {
  if (issue.nodeKey) selectNode(issue.nodeKey)
  if (issue.edge) selectEdge(issue.edge)
}

function defaultConfig(type: NodeType): NodeConfig {
  if (type === 'LLM') return { modelConfigId: 1, prompt: '{{start.userMessage}}', outputVariable: 'answer' }
  if (type === 'CONDITION') return { expression: 'true', outputVariable: 'matched' }
  if (type === 'API_CALL') return { url: '', method: 'GET', headersText: '{}', outputVariable: 'response' }
  if (type === 'KNOWLEDGE') return { knowledgeBaseId: undefined, query: '{{start.userMessage}}', topK: 3, outputVariable: 'context' }
  if (type === 'HUMAN_REVIEW') return { title: '人工审核', content: '{{start.userMessage}}', actions: ['APPROVE', 'REJECT'], allowEdit: false, outputVariable: 'result' }
  if (type === 'CODE_TASK') return { task: '{{start.userMessage}}', executor: 'MCP', mcpServerId: undefined, toolName: 'code_worker', timeoutSeconds: 600, outputVariable: 'result' }
  if (type === 'END') return { outputVariable: '' }
  return {}
}

function uniqueNodeKey(type: NodeType) {
  const prefix = type.toLowerCase()
  let index = workflowConfig.nodes.filter((node) => node.nodeType === type).length + 1
  let key = `${prefix}_${index}`
  while (workflowConfig.nodes.some((node) => node.nodeKey === key)) {
    index += 1
    key = `${prefix}_${index}`
  }
  return key
}

function addNode(type: NodeType) {
  const count = workflowConfig.nodes.filter((node) => node.nodeType === type).length + 1
  const key = uniqueNodeKey(type)
  const item = nodeTypes.find((nodeType) => nodeType.type === type)
  const node: WorkflowNode = {
    nodeKey: key,
    nodeType: type,
    name: `${item?.label ?? type}${count}`,
    config: defaultConfig(type),
    positionX: 120 + (workflowConfig.nodes.length % 4) * 230,
    positionY: 100 + Math.floor(workflowConfig.nodes.length / 4) * 150,
  }
  workflowConfig.nodes.push(node)
  if (type === 'START') workflowConfig.startNodeKey = key
  selectNode(key)
}

function duplicateSelectedNode() {
  if (!selectedNode.value || selectedNode.value.nodeType === 'START') return
  const source = selectedNode.value
  const key = uniqueNodeKey(source.nodeType as NodeType)
  const clone: WorkflowNode = {
    ...source,
    nodeKey: key,
    name: `${source.name} 副本`,
    config: copyConfig(source.config ?? {}),
    positionX: (source.positionX ?? 80) + 40,
    positionY: (source.positionY ?? 80) + 40,
  }
  workflowConfig.nodes.push(clone)
  selectNode(key)
}

function createConditionBranches() {
  if (!selectedNode.value || selectedNode.value.nodeType !== 'CONDITION') return
  const condition = selectedNode.value
  const baseX = (condition.positionX ?? 80) + 260
  const baseY = condition.positionY ?? 80
  const trueNodeKey = uniqueNodeKey('LLM')
  const falseNodeKey = uniqueNodeKey('LLM')
  const trueEndKey = uniqueNodeKey('END')
  const falseEndKey = uniqueNodeKey('END')
  const trueNode: WorkflowNode = {
    nodeKey: trueNodeKey,
    nodeType: 'LLM',
    name: '满足条件回复',
    config: {
      modelConfigId: 1,
      prompt: '请根据用户消息生成满足条件时的回复。用户消息：{{start.userMessage}}',
      outputVariable: 'answer',
    },
    positionX: baseX,
    positionY: Math.max(20, baseY - 90),
  }
  const falseNode: WorkflowNode = {
    nodeKey: falseNodeKey,
    nodeType: 'LLM',
    name: '不满足条件回复',
    config: {
      modelConfigId: 1,
      prompt: '请根据用户消息生成不满足条件时的回复。用户消息：{{start.userMessage}}',
      outputVariable: 'answer',
    },
    positionX: baseX,
    positionY: baseY + 120,
  }
  const trueEnd: WorkflowNode = {
    nodeKey: trueEndKey,
    nodeType: 'END',
    name: '结束（true）',
    config: { outputVariable: `${trueNodeKey}.answer` },
    positionX: baseX + 260,
    positionY: Math.max(20, baseY - 90),
  }
  const falseEnd: WorkflowNode = {
    nodeKey: falseEndKey,
    nodeType: 'END',
    name: '结束（false）',
    config: { outputVariable: `${falseNodeKey}.answer` },
    positionX: baseX + 260,
    positionY: baseY + 120,
  }
  workflowConfig.nodes.push(trueNode, falseNode, trueEnd, falseEnd)
  workflowConfig.edges = [
    ...(workflowConfig.edges ?? []).filter((edge) => edge.sourceNodeKey !== condition.nodeKey),
    { sourceNodeKey: condition.nodeKey, targetNodeKey: trueNodeKey, edgeType: 'CONDITION', conditionExpression: 'true', sortOrder: 0 },
    { sourceNodeKey: condition.nodeKey, targetNodeKey: falseNodeKey, edgeType: 'CONDITION', conditionExpression: 'false', sortOrder: 1 },
    { sourceNodeKey: trueNodeKey, targetNodeKey: trueEndKey, edgeType: 'DEFAULT', sortOrder: 0 },
    { sourceNodeKey: falseNodeKey, targetNodeKey: falseEndKey, edgeType: 'DEFAULT', sortOrder: 0 },
  ]
  selectNode(trueNodeKey)
}

function selectNode(nodeKey: string) {
  if (linkingFrom.value && linkingFrom.value !== nodeKey) {
    addEdge(linkingFrom.value, nodeKey)
    linkingFrom.value = ''
  }
  selectedNodeKey.value = nodeKey
  selectedEdge.value = null
  nodeDebugResult.value = null
}

function selectEdge(edge: WorkflowEdge) {
  selectedNodeKey.value = ''
  selectedEdge.value = edge
  linkingFrom.value = ''
  nodeDebugResult.value = null
}

function clearSelection() {
  selectedNodeKey.value = ''
  selectedEdge.value = null
  linkingFrom.value = ''
  nodeDebugResult.value = null
}

function startLink(nodeKey: string) {
  linkingFrom.value = linkingFrom.value === nodeKey ? '' : nodeKey
}

function addEdge(sourceNodeKey: string, targetNodeKey: string) {
  const exists = workflowConfig.edges?.some(
    (edge) => edge.sourceNodeKey === sourceNodeKey && edge.targetNodeKey === targetNodeKey,
  )
  if (exists) return
  workflowConfig.edges = workflowConfig.edges ?? []
  workflowConfig.edges.push({
    sourceNodeKey,
    targetNodeKey,
    edgeType: 'DEFAULT',
    sortOrder: workflowConfig.edges.length,
  })
}

function insertVariable(value: string) {
  if (!selectedNode.value) return
  const config = selectedConfig.value
  if (selectedNode.value.nodeType === 'LLM') {
    config.prompt = `${config.prompt ?? ''}${value}`
  } else if (selectedNode.value.nodeType === 'CONDITION') {
    config.expression = `${config.expression ?? ''}${value}`
  } else if (selectedNode.value.nodeType === 'KNOWLEDGE') {
    config.query = `${config.query ?? ''}${value}`
  } else if (selectedNode.value.nodeType === 'API_CALL') {
    config.url = `${config.url ?? ''}${value}`
  } else if (selectedNode.value.nodeType === 'HUMAN_REVIEW') {
    config.content = `${config.content ?? ''}${value}`
  } else if (selectedNode.value.nodeType === 'CODE_TASK') {
    config.task = `${config.task ?? ''}${value}`
  } else if (selectedNode.value.nodeType === 'END') {
    config.outputVariable = value.replace(/^\{\{|\}\}$/g, '')
  }
}

function zoomIn() {
  canvasScale.value = Math.min(1.5, Number((canvasScale.value + 0.1).toFixed(1)))
}

function zoomOut() {
  canvasScale.value = Math.max(0.6, Number((canvasScale.value - 0.1).toFixed(1)))
}

function resetZoom() {
  canvasScale.value = 1
}

function autoLayout() {
  const nodes = workflowConfig.nodes
  if (nodes.length === 0) return
  const startKey = workflowConfig.startNodeKey || nodes[0].nodeKey
  const levelMap = new Map<string, number>()
  const queue = [{ key: startKey, level: 0 }]
  while (queue.length) {
    const current = queue.shift()!
    const known = levelMap.get(current.key)
    if (known != null && known <= current.level) continue
    levelMap.set(current.key, current.level)
    ;(workflowConfig.edges ?? [])
      .filter((edge) => edge.sourceNodeKey === current.key)
      .forEach((edge) => queue.push({ key: edge.targetNodeKey, level: current.level + 1 }))
  }
  nodes.forEach((node) => {
    if (!levelMap.has(node.nodeKey)) {
      levelMap.set(node.nodeKey, Math.max(0, levelMap.size))
    }
  })
  const groups = new Map<number, WorkflowNode[]>()
  nodes.forEach((node) => {
    const level = levelMap.get(node.nodeKey) ?? 0
    groups.set(level, [...(groups.get(level) ?? []), node])
  })
  Array.from(groups.entries()).forEach(([level, group]) => {
    group.forEach((node, index) => {
      node.positionX = 80 + level * 260
      node.positionY = 80 + index * 150
    })
  })
  notifySuccess('布局已整理')
}

function deleteSelectedNode() {
  if (!selectedNode.value || selectedNode.value.nodeType === 'START') return
  const key = selectedNode.value.nodeKey
  workflowConfig.nodes = workflowConfig.nodes.filter((node) => node.nodeKey !== key)
  workflowConfig.edges = (workflowConfig.edges ?? []).filter(
    (edge) => edge.sourceNodeKey !== key && edge.targetNodeKey !== key,
  )
  selectedNodeKey.value = ''
}

function deleteSelectedEdge() {
  if (!selectedEdge.value) return
  const target = selectedEdge.value
  workflowConfig.edges = (workflowConfig.edges ?? []).filter((edge) => edge !== target)
  selectedEdge.value = null
}

function edgeKey(edge: WorkflowEdge) {
  return `${edge.sourceNodeKey}-${edge.targetNodeKey}-${edge.conditionExpression ?? ''}`
}

function nodeCenter(nodeKey: string) {
  const node = workflowConfig.nodes.find((item) => item.nodeKey === nodeKey)
  return {
    x: (node?.positionX ?? 80) + 90,
    y: (node?.positionY ?? 80) + 42,
  }
}

function edgePath(edge: WorkflowEdge) {
  const source = nodeCenter(edge.sourceNodeKey)
  const target = nodeCenter(edge.targetNodeKey)
  const midX = (source.x + target.x) / 2
  return `M ${source.x} ${source.y} C ${midX} ${source.y}, ${midX} ${target.y}, ${target.x} ${target.y}`
}

function edgeLabelPosition(edge: WorkflowEdge) {
  const source = nodeCenter(edge.sourceNodeKey)
  const target = nodeCenter(edge.targetNodeKey)
  return { x: (source.x + target.x) / 2, y: (source.y + target.y) / 2 - 8 }
}

function startDrag(event: PointerEvent, node: WorkflowNode) {
  const startX = event.clientX
  const startY = event.clientY
  const originX = node.positionX ?? 80
  const originY = node.positionY ?? 80
  const target = event.currentTarget as HTMLElement
  target.setPointerCapture(event.pointerId)

  const move = (moveEvent: PointerEvent) => {
    node.positionX = Math.max(20, originX + moveEvent.clientX - startX)
    node.positionY = Math.max(20, originY + moveEvent.clientY - startY)
  }
  const up = () => {
    target.removeEventListener('pointermove', move)
    target.removeEventListener('pointerup', up)
    target.removeEventListener('pointercancel', up)
  }
  target.addEventListener('pointermove', move)
  target.addEventListener('pointerup', up)
  target.addEventListener('pointercancel', up)
}

function validateWorkflow(): boolean {
  if (!form.name.trim()) {
    ElMessage.error('工作流名称不能为空')
    return false
  }
  if (!workflowConfig.startNodeKey) {
    ElMessage.error('请选择开始节点')
    return false
  }
  if (workflowConfig.nodes.length === 0) {
    ElMessage.error('至少需要一个节点')
    return false
  }
  const firstError = validationIssues.value.find((issue) => issue.level === 'error')
  if (firstError) {
    ElMessage.error(firstError.message)
    focusIssue(firstError)
    return false
  }
  const invalidApiNode = workflowConfig.nodes.find((node) => {
    if (node.nodeType !== 'API_CALL') return false
    const config = (node.config ?? {}) as NodeConfig
    try {
      JSON.parse(config.headersText || '{}')
      return false
    } catch {
      return true
    }
  })
  if (invalidApiNode) {
    ElMessage.error(`节点「${invalidApiNode.name}」的 Headers JSON 不合法`)
    selectNode(invalidApiNode.nodeKey)
    return false
  }
  return true
}

function formatJson() {
  ElMessage.success('JSON 已根据画布同步')
}

function goBack() {
  router.push('/workflows')
}

function toConfigJson(detail: WorkflowDetail): WorkflowConfigJson {
  return {
    status: detail.enabled === 1 ? 'PUBLISHED' : 'DRAFT',
    enabled: detail.enabled,
    startNodeKey: detail.startNodeKey,
    nodes: detail.nodes ?? [],
    edges: detail.edges ?? [],
  }
}

function applyConfig(config: WorkflowConfigJson) {
  workflowConfig.enabled = config.enabled ?? (config.status === 'DRAFT' ? 0 : 1)
  workflowConfig.status = workflowConfig.enabled === 1 ? 'PUBLISHED' : 'DRAFT'
  workflowConfig.startNodeKey = config.startNodeKey
  workflowConfig.nodes = config.nodes ?? []
  workflowConfig.edges = config.edges ?? []
}

function resetExample() {
  applyConfig(copyConfig(exampleConfig))
  form.name = '智能客服分类工作流'
  form.description = '识别用户意图，按退款/通用路径生成客服回复'
  selectedNodeKey.value = ''
  selectedEdge.value = null
  latestRun.value = null
}

async function loadWorkflowDetail() {
  if (!isEditMode.value) return
  loadingDetail.value = true
  try {
    const detail = await getWorkflowDetail(workflowId.value)
    form.name = detail.name ?? ''
    form.description = detail.description ?? ''
    applyConfig(toConfigJson(detail))
    latestRun.value = await getLatestWorkflowRun(workflowId.value).catch(() => null)
    await loadWorkflowVersions()
  } catch {
    router.push('/workflows')
  } finally {
    loadingDetail.value = false
  }
}

async function loadWorkflowVersions() {
  if (!isEditMode.value) {
    workflowVersions.value = []
    return
  }
  workflowVersions.value = await getWorkflowVersions(workflowId.value).catch(() => [])
}

async function debugSelectedNode() {
  if (!selectedNode.value || !canDebugSelectedNode.value) return
  const id = await saveWorkflowBeforeRun()
  if (!id) return
  debuggingNode.value = true
  nodeDebugResult.value = null
  try {
    nodeDebugResult.value = await debugWorkflowNode(id, selectedNode.value.nodeKey, {
      userMessage: runInput.value.trim(),
      variables: latestRun.value?.nodeRuns?.find((run) => run.nodeKey === selectedNode.value?.nodeKey)?.outputs,
    })
  } finally {
    debuggingNode.value = false
  }
}

async function restoreVersion(versionNo: number) {
  if (!isEditMode.value) return
  try {
    const detail = await restoreWorkflowVersion(workflowId.value, versionNo)
    form.name = detail.name ?? ''
    form.description = detail.description ?? ''
    applyConfig(toConfigJson(detail))
    await loadWorkflowVersions()
    notifySuccess(`已恢复到 v${versionNo}`)
  } catch {
    // request interceptor has shown the error message
  }
}

async function saveWorkflowBeforeRun(): Promise<number | null> {
  if (!validateWorkflow()) return null
  const config = normalizedConfig()
  const payload = {
    name: form.name.trim(),
    description: form.description.trim(),
    enabled: config.enabled ?? 1,
    startNodeKey: config.startNodeKey,
    nodes: config.nodes,
    edges: config.edges ?? [],
  }
  if (isEditMode.value) {
    await updateWorkflow(workflowId.value, payload)
    await loadWorkflowVersions()
    return workflowId.value
  }
  const created = await createWorkflow(payload) as WorkflowDetail
  await router.replace(`/workflows/${created.id}/edit`)
  return created.id
}

async function handleRun() {
  if (!runInput.value.trim()) {
    ElMessage.error('请输入试运行消息')
    return
  }
  stopRunPolling()
  stopRunEventStream()
  runningWorkflow.value = true
  latestRun.value = null
  clearReviewTask()
  lastRunEventSeq.value = 0
  try {
    const id = await saveWorkflowBeforeRun()
    if (!id) {
      runningWorkflow.value = false
      return
    }
    latestRun.value = await startAsyncWorkflowRun(id, runInput.value.trim())
    await syncReviewTaskIfWaiting(latestRun.value)
    persistRunResumeState()
    notifySuccess('工作流已开始执行')
    if (latestRun.value.status === 'WAITING') {
      runningWorkflow.value = false
      return
    }
    if (isTerminalRun(latestRun.value.status)) {
      runningWorkflow.value = false
      clearRunResumeState()
      return
    }
    startRunEventStream(latestRun.value.id, lastRunEventSeq.value)
  } catch {
    if (isEditMode.value) {
      latestRun.value = await getLatestWorkflowRun(workflowId.value).catch(() => null)
    }
    runningWorkflow.value = false
  }
}

function isTerminalRun(status?: string) {
  return ['SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED'].includes(status ?? '')
}

async function syncReviewTaskIfWaiting(run: WorkflowRun | null) {
  if (!run || run.status !== 'WAITING') {
    clearReviewTask()
    return
  }
  try {
    reviewTask.value = await getWorkflowReviewTask(run.id)
    reviewEditedContent.value = reviewTask.value.content ?? ''
  } catch {
    clearReviewTask()
  }
}

function clearReviewTask() {
  reviewTask.value = null
  reviewComment.value = ''
  reviewEditedContent.value = ''
}

async function handleReviewAction(action: string) {
  if (!latestRun.value) return
  runningWorkflow.value = true
  try {
    latestRun.value = await submitWorkflowReview(latestRun.value.id, {
      action,
      comment: reviewComment.value.trim(),
      editedContent: reviewEditedContent.value,
    })
    clearReviewTask()
    if (isTerminalRun(latestRun.value.status)) {
      runningWorkflow.value = false
      clearRunResumeState()
      return
    }
    lastRunEventSeq.value = 0
    persistRunResumeState()
    startRunEventStream(latestRun.value.id, lastRunEventSeq.value)
  } catch {
    runningWorkflow.value = false
  }
}

function startRunEventStream(runId: number, afterEventSeq = 0) {
  stopRunEventStream()
  runEventSource.value = new EventSource(workflowRunEventsUrl(runId, afterEventSeq))
  runEventSource.value.addEventListener('workflow-run-event', async (message) => {
    try {
      const event = JSON.parse((message as MessageEvent).data) as WorkflowRunEvent
      lastRunEventSeq.value = Math.max(lastRunEventSeq.value, event.eventSeq ?? 0)
      persistRunResumeState()
      const detail = await getWorkflowRunDetail(runId)
      latestRun.value = detail
      await syncReviewTaskIfWaiting(detail)
      if (detail.status === 'WAITING') {
        runningWorkflow.value = false
        stopRunEventStream()
        return
      }
      if (isTerminalRun(detail.status)) {
        runningWorkflow.value = false
        stopRunEventStream()
        clearRunResumeState()
      }
    } catch {
      startRunPolling(runId)
    }
  })
  runEventSource.value.onerror = () => {
    stopRunEventStream()
    startRunPolling(runId)
  }
}

function stopRunEventStream() {
  if (runEventSource.value) {
    runEventSource.value.close()
    runEventSource.value = null
  }
}

function startRunPolling(runId: number) {
  runPollingTimer.value = window.setInterval(async () => {
    try {
      const detail = await getWorkflowRunDetail(runId)
      latestRun.value = detail
      await syncReviewTaskIfWaiting(detail)
      if (detail.status === 'WAITING') {
        stopRunPolling()
        runningWorkflow.value = false
        return
      }
      if (isTerminalRun(detail.status)) {
        stopRunPolling()
        runningWorkflow.value = false
      }
    } catch {
      stopRunPolling()
      runningWorkflow.value = false
    }
  }, 2000)
}

function stopRunPolling() {
  if (runPollingTimer.value != null) {
    window.clearInterval(runPollingTimer.value)
    runPollingTimer.value = null
  }
}

function runResumeStorageKey() {
  return isEditMode.value ? `hify.workflow.run.${workflowId.value}` : ''
}

function persistRunResumeState() {
  const key = runResumeStorageKey()
  if (!key || !latestRun.value || isTerminalRun(latestRun.value.status)) {
    return
  }
  sessionStorage.setItem(key, JSON.stringify({
    runId: latestRun.value.id,
    lastEventSeq: lastRunEventSeq.value,
  }))
}

function clearRunResumeState() {
  const key = runResumeStorageKey()
  if (key) sessionStorage.removeItem(key)
}

async function restoreRunningWorkflow() {
  const key = runResumeStorageKey()
  if (!key) return
  const raw = sessionStorage.getItem(key)
  if (!raw) return
  try {
    const state = JSON.parse(raw) as { runId?: number; lastEventSeq?: number }
    if (!state.runId) return
    const detail = await getWorkflowRunDetail(state.runId)
    latestRun.value = detail
    await syncReviewTaskIfWaiting(detail)
    lastRunEventSeq.value = state.lastEventSeq ?? 0
    if (detail.status === 'WAITING') {
      runningWorkflow.value = false
      return
    }
    if (isTerminalRun(detail.status)) {
      runningWorkflow.value = false
      clearRunResumeState()
      return
    }
    runningWorkflow.value = true
    startRunEventStream(detail.id, lastRunEventSeq.value)
  } catch {
    clearRunResumeState()
  }
}

async function handleSubmit() {
  if (!validateWorkflow()) return

  submitting.value = true
  try {
    const config = normalizedConfig()
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      enabled: config.enabled ?? 1,
      startNodeKey: config.startNodeKey,
      nodes: config.nodes,
      edges: config.edges ?? [],
    }
    if (isEditMode.value) {
      await updateWorkflow(workflowId.value, payload)
      await loadWorkflowVersions()
      notifySuccess('工作流已更新')
    } else {
      await createWorkflow(payload)
      notifySuccess('工作流已创建')
    }
    router.push('/workflows')
  } catch {
    // request interceptor has shown the error message
  } finally {
    submitting.value = false
  }
}

function openTemplateDialog() {
  templateForm.name = form.name ? `${form.name}模板` : '工作流模板'
  templateForm.description = form.description || ''
  templateForm.category = '通用'
  templateForm.tagsText = ''
  templateForm.changelog = '初始版本'
  templateForm.publish = true
  templateDialogVisible.value = true
}

async function handleCreateTemplate() {
  if (!templateForm.name.trim()) {
    ElMessage.error('模板名称不能为空')
    return
  }
  if (!validateWorkflow()) return

  creatingTemplate.value = true
  try {
    const id = await saveWorkflowBeforeRun()
    if (!id) return
    const tags = templateForm.tagsText
      .split(/[,，]/)
      .map((item) => item.trim())
      .filter(Boolean)
    await createTemplateFromWorkflow({
      workflowId: id,
      name: templateForm.name.trim(),
      description: templateForm.description.trim(),
      category: templateForm.category.trim() || '通用',
      icon: 'workflow',
      tags,
      publish: templateForm.publish,
      changelog: templateForm.changelog.trim() || '初始版本',
    })
    templateDialogVisible.value = false
    notifySuccess('模板已创建')
    router.push('/workflow-templates')
  } catch {
    // request interceptor has shown the error message
  } finally {
    creatingTemplate.value = false
  }
}

onMounted(() => {
  loadModelGroups()
  loadWorkflowDetail()
  restoreRunningWorkflow()
})

onBeforeUnmount(() => {
  stopRunPolling()
  stopRunEventStream()
})
</script>

<style scoped>
.workflow-editor {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 340px;
  gap: 12px;
  height: calc(100vh - 170px);
  min-height: 620px;
}

.node-palette,
.canvas-panel,
.config-panel {
  min-height: 0;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.node-palette,
.config-panel {
  padding: 14px;
  overflow: auto;
}

.run-panel {
  padding-bottom: 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
}

.run-panel__button {
  width: 100%;
  margin-top: 10px;
}

.run-summary {
  padding: 10px;
  margin-top: 10px;
  border: 1px solid #d9e0ef;
  border-radius: 8px;
  background: #f8fafc;
}

.run-summary--success {
  border-color: #b7ebc6;
  background: #f6fff8;
}

.run-summary--failed {
  border-color: #ffd0d0;
  background: #fff7f7;
}

.run-summary--timeout {
  border-color: #ffe1a6;
  background: #fffaf0;
}

.run-summary--waiting {
  border-color: #ffe1a6;
  background: #fffaf0;
}

.run-summary__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.run-summary__block {
  margin-top: 8px;
}

.run-summary__block span {
  color: var(--text-tertiary);
  font-size: var(--text-xs);
}

.run-summary__block p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.5;
  white-space: pre-wrap;
}

.run-summary__block--error p {
  color: #e5484d;
}

.review-card {
  display: grid;
  gap: 8px;
  padding-top: 10px;
}

.review-card__title {
  color: var(--text-primary);
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
}

.review-card__head {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.review-card__head span,
.review-card__head strong {
  display: block;
}

.review-card__head span {
  color: var(--text-tertiary);
  font-size: var(--text-xs);
}

.review-card__head strong {
  margin-top: 2px;
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.review-card__content {
  max-height: 150px;
  overflow: auto;
  padding: 8px;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.5;
  white-space: pre-wrap;
  background: #fff;
  border: 1px solid #f0dca8;
  border-radius: 6px;
}

.review-card p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.5;
  white-space: pre-wrap;
}

.review-card__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.panel-title {
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
}

.palette-item {
  position: relative;
  display: grid;
  grid-template-columns: 8px 1fr 8px;
  width: 100%;
  min-height: 76px;
  gap: 8px;
  align-items: center;
  padding: 12px 8px;
  margin-bottom: 12px;
  text-align: center;
  cursor: pointer;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
  border: 1px solid #d9e0ef;
  border-radius: 8px;
  box-shadow: 0 6px 14px rgb(15 23 42 / 6%);
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.palette-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 10px 22px rgb(67 97 238 / 12%);
  transform: translateY(-1px);
}

.palette-item__body {
  min-width: 0;
}

.palette-item__port {
  display: block;
  width: 8px;
  height: 8px;
  background: var(--primary-color);
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #b9c5ff;
}

.palette-item__port--in {
  justify-self: start;
}

.palette-item__port--out {
  justify-self: end;
}

.palette-item strong,
.palette-item small {
  display: block;
}

.palette-item strong {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.25;
}

.palette-item small {
  margin-top: 8px;
  color: #8a95ad;
  font-size: 13px;
  line-height: 1.3;
}

.validation-panel {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--border-light);
}

.validation-empty {
  padding: 10px;
  color: #2b9950;
  font-size: var(--text-sm);
  background: #f6fff8;
  border: 1px solid #b7ebc6;
  border-radius: 8px;
}

.validation-item {
  display: block;
  width: 100%;
  padding: 10px;
  margin-bottom: 8px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d9e0ef;
  border-radius: 8px;
}

.validation-item strong,
.validation-item span {
  display: block;
}

.validation-item strong {
  margin-bottom: 4px;
  font-size: 12px;
}

.validation-item span {
  color: var(--text-secondary);
  font-size: var(--text-xs);
  line-height: 1.45;
}

.validation-item--error {
  border-color: #ffd0d0;
  background: #fff7f7;
}

.validation-item--error strong {
  color: #e5484d;
}

.validation-item--warning {
  border-color: #ffe1a6;
  background: #fffaf0;
}

.validation-item--warning strong {
  color: #b7791f;
}

.canvas-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.canvas-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-light);
}

.workflow-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.workflow-meta :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 0;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.workflow-canvas {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: auto;
  background-color: #f8fafc;
  background-image:
    linear-gradient(#e7ecf5 1px, transparent 1px),
    linear-gradient(90deg, #e7ecf5 1px, transparent 1px);
  background-size: 24px 24px;
}

.canvas-board {
  position: relative;
  min-width: 100%;
  min-height: 100%;
  transform-origin: 0 0;
}

.edge-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.edge-path {
  pointer-events: stroke;
  cursor: pointer;
  fill: none;
  stroke: #8a95ad;
  stroke-width: 2;
}

.edge-path--selected {
  stroke: var(--primary-color);
  stroke-width: 3;
}

.edge-label {
  fill: var(--primary-color);
  font-size: 12px;
  font-weight: 600;
  pointer-events: none;
}

.workflow-node {
  position: absolute;
  z-index: 2;
  width: 180px;
  min-height: 84px;
  padding: 10px;
  cursor: grab;
  user-select: none;
  background: #fff;
  border: 1px solid #d9e0ef;
  border-radius: 8px;
  box-shadow: 0 8px 18px rgb(15 23 42 / 8%);
}

.workflow-node:active {
  cursor: grabbing;
}

.workflow-node--selected {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgb(67 97 238 / 14%);
}

.workflow-node--link-source {
  border-color: #0ea5e9;
}

.workflow-node--run-success {
  border-color: #32c45f;
  box-shadow: 0 0 0 3px rgb(50 196 95 / 14%);
}

.workflow-node--run-failed {
  border-color: #ff4d4f;
  box-shadow: 0 0 0 3px rgb(255 77 79 / 14%);
}

.workflow-node--run-canceled {
  border-color: #a8b1c4;
  box-shadow: 0 0 0 3px rgb(148 163 184 / 14%);
}

.workflow-node--run-running {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgb(67 97 238 / 14%);
}

.workflow-node--run-waiting {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgb(245 158 11 / 14%);
}

.workflow-node__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-tertiary);
  font-size: var(--text-xs);
}

.workflow-node__name {
  margin-top: 8px;
  color: var(--text-primary);
  font-weight: var(--font-semibold);
  line-height: 1.35;
}

.workflow-node__key {
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-node__run {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  padding-top: 6px;
  color: var(--text-tertiary);
  font-size: 11px;
  border-top: 1px solid #eef2f7;
}

.workflow-node--start,
.workflow-node--end {
  background: #f8fff9;
}

.workflow-node--condition {
  background: #fffaf0;
}

.workflow-node--knowledge {
  background: #f4fbff;
}

.workflow-node--human_review {
  background: #fffaf0;
}

.workflow-node--code_task {
  background: #f7f7ff;
}

.workflow-node--reply {
  background: #f8fafc;
}

.node-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.variable-panel {
  padding: 10px;
  margin-bottom: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8fafc;
}

.variable-panel .panel-title {
  margin-bottom: 8px;
  font-size: var(--text-xs);
}

.variable-token {
  display: block;
  width: 100%;
  padding: 6px 8px;
  margin-bottom: 6px;
  overflow: hidden;
  color: var(--primary-color);
  font-family: var(--font-mono);
  font-size: 12px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d9e0ef;
  border-radius: 6px;
}

.variable-token:hover {
  border-color: var(--primary-color);
}

.node-config-form :deep(.el-input-number),
.node-config-form :deep(.el-select) {
  width: 100%;
}

.field-warning {
  margin-top: 6px;
  color: #e5484d;
  font-size: var(--text-xs);
  line-height: 1.4;
}

.node-run-detail {
  padding: 10px;
  margin-bottom: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8fafc;
}

.node-run-detail__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--text-tertiary);
  font-size: var(--text-xs);
}

.node-run-detail__error {
  margin-top: 8px;
  color: #e5484d;
  font-size: var(--text-sm);
  line-height: 1.45;
}

.version-panel {
  padding: 10px;
  margin-bottom: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8fafc;
}

.version-item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-top: 1px solid #eef2f7;
}

.version-item:first-of-type {
  border-top: 0;
}

.version-item strong,
.version-item span {
  display: block;
}

.version-item strong {
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.version-item span {
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: var(--text-xs);
}

.json-preview :deep(.el-textarea__inner) {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 1180px) {
  .workflow-editor {
    grid-template-columns: 180px minmax(520px, 1fr);
    grid-template-rows: minmax(520px, 1fr) auto;
  }

  .config-panel {
    grid-column: 1 / -1;
    max-height: 420px;
  }
}
</style>
