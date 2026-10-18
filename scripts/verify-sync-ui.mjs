// Uses Node 22+ and an isolated, headless Chromium/Edge started on the supplied CDP port.
// Execute only after approval; writes one clearly labelled record to the mock SAP source via the UI.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { setTimeout as pause } from 'node:timers/promises'
import { randomUUID } from 'node:crypto'

const baseUrl = process.env.SYNC_UI_URL || 'http://127.0.0.1:5196'
const debugUrl = process.env.SYNC_CDP_URL || 'http://127.0.0.1:9336'
const adminKey = process.env.SYNC_ADMIN_KEY
assert(adminKey, 'Set SYNC_ADMIN_KEY for this approved UI validation session')
const pageInfo = await (await fetch(`${debugUrl}/json/new?${encodeURIComponent(`${baseUrl}/integration/sync`)}`, { method: 'PUT' })).json()
const socket = new WebSocket(pageInfo.webSocketDebuggerUrl)
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
let messageId = 0
const waiting = new Map()
const runtimeErrors = []
socket.onmessage = event => {
  const message = JSON.parse(event.data)
  if (message.method === 'Runtime.exceptionThrown') runtimeErrors.push(message.params.exceptionDetails.text)
  const pending = waiting.get(message.id)
  if (pending) { clearTimeout(pending.timeout); waiting.delete(message.id); message.error ? pending.reject(new Error(JSON.stringify(message.error))) : pending.resolve(message.result) }
}
function command(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++messageId
    const timeout = setTimeout(() => { waiting.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 20000)
    waiting.set(id, { resolve, reject, timeout })
    socket.send(JSON.stringify({ id, method, params }))
  })
}
async function evaluate(expression) {
  const response = await command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails))
  return response.result.value
}
async function until(expression, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) { if (await evaluate(expression)) return; await pause(300) }
  throw new Error(`UI timeout: ${label}`)
}
async function click(selector, text) {
  const clicked = await evaluate(`(() => { const target = [...document.querySelectorAll(${JSON.stringify(selector)})].find(el => el.innerText.replace(/\\s+/g,'').includes(${JSON.stringify(text.replace(/\s+/g, ''))})); if (!target || target.disabled) return false; target.click(); return true })()`)
  assert(clicked, `Clickable control: ${text}`)
}
async function screenshot(file) {
  await pause(700) // Let UI transitions settle before inspecting the captured result.
  const image = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  const directory = resolve('.codex-runtime')
  await mkdir(directory, { recursive: true })
  const path = resolve(directory, file)
  await writeFile(path, Buffer.from(image.data, 'base64'))
  console.log(`SCREENSHOT: ${path}`)
}

try {
  await command('Runtime.enable')
  await command('Page.enable')
  await command('Emulation.setDeviceMetricsOverride', { width: 1536, height: 1050, deviceScaleFactor: 1, mobile: false })
  await command('Page.navigate', { url: `${baseUrl}/integration/sync` })
  await until("document.body.innerText.includes('主数据同步中心') && document.body.innerText.includes('SAP 成本中心')", 'dashboard and real job definitions')
  await evaluate(`sessionStorage.setItem('integration-admin-key', ${JSON.stringify(adminKey)})`)
  assert(await evaluate("document.querySelectorAll('.job-card').length === 2"), 'two backend job cards rendered')
  await screenshot('sync-center-preview.png')
  console.log('PASS: dashboard, backend metrics, two job cards and navigation rendered')

  await click('.el-tabs__item', 'SAP源端模拟（开发）')
  await until("document.body.innerText.includes('开发模拟源') && document.body.innerText.includes('新增源记录')", 'mock source panel')
  await click('button', '新增源记录')
  await until("document.querySelector('.el-dialog')?.innerText.includes('成本中心编码')", 'source editor')
  const code = `UI_${randomUUID().slice(0, 8).toUpperCase()}`
  const formResult = await evaluate(`(() => {
    const set = (label, value) => {
      const item = [...document.querySelectorAll('.el-dialog .el-form-item')].find(el => el.querySelector('label')?.innerText.includes(label));
      const input = item?.querySelector('input'); if (!input) return false;
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
      input.dispatchEvent(new Event('input', { bubbles: true })); return true;
    };
    return set('成本中心编码', ${JSON.stringify(code)}) && set('成本中心名称', '页面演示成本中心');
  })()`)
  assert(formResult, 'source form inputs are present')
  await click('.el-dialog button', '保存源端变化')
  await until("!document.querySelector('.el-overlay-dialog .el-dialog') || !document.querySelector('.el-overlay-dialog .el-dialog').getBoundingClientRect().height", 'source save completed')
  await click('.job-card button', '拉取增量')
  await until("document.querySelector('.el-drawer')?.innerText.includes('已完成')", 'actual asynchronous run completion')
  await screenshot('sync-run-preview.png')
  await evaluate("document.querySelector('.el-drawer__close-btn')?.click()")
  await click('.el-tabs__item', 'SRM本地主数据')
  await evaluate(`(() => { const input = document.querySelector('.table-toolbar .el-input input'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(code)}); input.dispatchEvent(new Event('input', { bubbles: true })); input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true })); })()`)
  await until(`document.querySelector('.workspace .el-table')?.innerText.includes(${JSON.stringify(code)})`, 'source change arrived in local target table')
  assert(await evaluate("document.querySelector('.workspace .el-table').innerText.includes('页面演示成本中心')"), 'local target contains the source editor value')
  await screenshot('sync-incremental-preview.png')
  console.log(`PASS: UI source creation -> incremental trigger -> successful run -> SRM local record (${code})`)
  assert.equal(runtimeErrors.length, 0, `browser runtime errors: ${runtimeErrors.join('; ')}`)
  console.log('PASS: no JavaScript runtime exceptions')
} finally {
  await command('Browser.close').catch(() => {})
  socket.close()
}
