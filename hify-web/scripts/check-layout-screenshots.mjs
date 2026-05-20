import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(__dirname, '..')
const outputDir = path.join(rootDir, 'test-results', 'layout')
const port = Number(process.env.HIFY_WEB_LAYOUT_PORT || 5173)
const host = process.env.HIFY_WEB_LAYOUT_HOST || 'localhost'
const baseUrl = process.env.HIFY_WEB_LAYOUT_BASE_URL || `http://${host}:${port}`
const minGap = Number(process.env.HIFY_WEB_LAYOUT_MIN_GAP || 16)

let devServer

async function main() {
  await mkdir(outputDir, { recursive: true })
  if (!(await isServerReady())) {
    devServer = spawn('npm', ['run', 'dev', '--', '--host', host, '--port', String(port)], {
      cwd: rootDir,
      stdio: 'ignore',
      env: { ...process.env, BROWSER: 'none' },
    })
    await waitForServer()
  }

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    await page.addInitScript(() => {
      localStorage.setItem('hify.auth.token', 'layout-check-token')
    })
    await mockApi(page)

    await checkAnalytics(page)
    await checkLogs(page)
  } finally {
    await browser.close()
    if (devServer) {
      devServer.kill('SIGTERM')
    }
  }
}

async function mockApi(page) {
  await page.route('**/api/v1/**', route => fulfill(route, null))
  await page.route('**/api/v1/auth/me', route => fulfill(route, {
    id: 1,
    username: 'admin',
    displayName: 'Admin',
    role: 'ADMIN',
    status: 'ACTIVE',
  }))
  await page.route('**/api/v1/projects**', route => fulfill(route, [
    { id: 1, name: '默认项目', code: 'default', status: 'ACTIVE' },
  ]))
  await page.route('**/api/v1/ops/analytics/overview**', route => fulfill(route, analyticsOverview()))
  await page.route('**/api/v1/conversations/logs**', route => fulfill(route, conversationLogs()))
  await page.route('**/api/v1/workflow-runs**', route => fulfill(route, pageResult([])))
  await page.route('**/api/v1/mcp-tool-audits**', route => fulfill(route, pageResult([])))
  await page.route('**/api/v1/llm-usage**', route => fulfill(route, []))
}

async function checkAnalytics(page) {
  await page.goto(`${baseUrl}/analytics`)
  await page.getByRole('heading', { name: '运营分析' }).waitFor({ state: 'visible', timeout: 10_000 })
  await page.screenshot({ path: path.join(outputDir, 'analytics.png'), fullPage: true })

  const gap = await cardGap(page, '错误原因排行', '慢 LLM 调用')
  assertGap('analytics error-to-risk row gap', gap)
}

async function checkLogs(page) {
  await page.goto(`${baseUrl}/logs`)
  await page.getByRole('heading', { name: '日志中心' }).waitFor({ state: 'visible', timeout: 10_000 })
  await page.screenshot({ path: path.join(outputDir, 'logs.png'), fullPage: true })

  const gap = await page.evaluate(() => {
    const header = document.querySelector('.page-header')
    const card = document.querySelector('.log-tabs')
    if (!header || !card) return null
    const headerRect = header.getBoundingClientRect()
    const cardRect = card.getBoundingClientRect()
    return Math.round(cardRect.top - headerRect.bottom)
  })
  assertGap('logs header-to-tabs gap', gap)
}

async function cardGap(page, fromTitle, toTitle) {
  return page.evaluate(([from, to]) => {
    const cards = Array.from(document.querySelectorAll('.hify-card'))
    const fromCard = cards.find(card => card.textContent?.includes(from))
    const toCard = cards.find(card => card.textContent?.includes(to))
    if (!fromCard || !toCard) return null
    return Math.round(toCard.getBoundingClientRect().top - fromCard.getBoundingClientRect().bottom)
  }, [fromTitle, toTitle])
}

function assertGap(name, gap) {
  if (gap === null || gap < minGap) {
    throw new Error(`${name} expected >= ${minGap}px, actual=${gap}`)
  }
  console.log(`${name}: ${gap}px`)
}

function fulfill(route, data) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'ok', data, ok: true }),
  })
}

function pageResult(records) {
  return {
    records,
    total: records.length,
    page: 1,
    size: 20,
  }
}

function conversationLogs() {
  return {
    records: [
      {
        traceId: 'layout-trace-1',
        agentName: '医疗 agent',
        modelId: 'deepseek-v4-flash',
        ragTriggered: true,
        mcpTriggered: false,
        status: 'DONE',
        startedAt: '2026-05-20T10:00:00',
      },
    ],
    hasMore: false,
  }
}

function analyticsOverview() {
  return {
    summary: {
      conversationCount: 16,
      failedConversationCount: 3,
      conversationFailureRate: 0.1875,
      totalTokens: 99833,
      ragTriggeredCount: 11,
      ragHitCount: 10,
      ragHitRate: 0.9091,
      workflowRunCount: 3,
      workflowSuccessCount: 0,
      workflowSuccessRate: 0,
      mcpCallCount: 8,
      mcpFailureCount: 0,
      mcpFailureRate: 0,
    },
    agents: [
      { agentId: 1, agentName: '医疗 agent', conversationCount: 11, totalTokens: 73298, failureRate: 0.1818 },
      { agentId: 2, agentName: 'agent测试', conversationCount: 4, totalTokens: 13280, failureRate: 0.25 },
    ],
    models: [
      { providerType: 'DEEPSEEK', modelId: 'deepseek-v4-flash', callCount: 9, totalTokens: 99833, avgLatencyMs: 27245 },
    ],
    workflows: [
      { workflowName: '知识问答', runCount: 3, successCount: 0, failureCount: 3, successRate: 0 },
    ],
    mcpTools: [
      { toolName: 'maps_search_detail', callCount: 4, failureCount: 0, failureRate: 0, lastErrorSummary: '' },
    ],
    errors: [
      { sourceType: 'CONVERSATION', errorCode: 'LLM_CALL_ERROR', errorMessage: 'LLM 请求失败', count: 2 },
    ],
    slowLlmCalls: [
      { traceId: 'layout-trace-2', modelId: 'deepseek-v4-flash', latencyMs: 50220, totalTokens: 13000, errorCode: 'LLM_TIMEOUT' },
    ],
    riskConversations: [
      { traceId: 'layout-trace-2', agentName: '医疗 agent', status: 'ERROR', ragTriggered: true, ragHit: false, errorMessage: 'LLM 请求失败' },
    ],
  }
}

async function isServerReady() {
  try {
    const response = await fetch(baseUrl)
    return response.ok
  } catch {
    return false
  }
}

async function waitForServer() {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    if (await isServerReady()) {
      return
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`Vite dev server did not start at ${baseUrl}`)
}

main().catch(error => {
  console.error(error)
  process.exit(1)
})
