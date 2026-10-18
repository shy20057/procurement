# 异步寻宝动画网站实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个以 `async/await` 驱动、带分支剧情和动画展示的独立寻宝网页，并部署到 Vercel。

**Architecture:** 在 `treasure-adventure` 子目录中创建零依赖 ES Module 静态站点。`treasure.js` 负责可测试的异步剧情引擎与流程编排，`app.js` 负责 DOM、用户选择 Promise 和动画状态，HTML/CSS 负责响应式舞台；Vercel 直接托管该目录。

**Tech Stack:** HTML5、CSS3、原生 JavaScript ES Modules、Node.js 内置测试运行器、Vercel CLI

**Spec:** `docs/superpowers/specs/2026-10-18-treasure-adventure-design.md`

## Global Constraints

- 独立静态站点，不修改现有采购系统前后端代码。
- 主寻宝流程必须使用 `async/await`、`try/catch/finally`，不得使用 Promise `.then()` 链编排。
- 用户选择通过 Promise 等待按钮事件。
- 支持中途重新开始并取消旧流程。
- 尊重 `prefers-reduced-motion`。
- 不引入运行时依赖、数据库或用户数据采集。
- 当前工作区不是 Git 仓库，因此不执行提交步骤。

---

### Task 1: 异步剧情引擎

**Files:**
- Create: `treasure-adventure/package.json`
- Create: `treasure-adventure/treasure.js`
- Create: `treasure-adventure/tests/treasure.test.js`

**Interfaces:**
- Consumes: `AbortSignal`、由 UI 提供的 `choose(prompt)` 与 `onEvent(event)` 回调。
- Produces: `TreasureMap`、`TempleChallengeError`、`findTreasure({ choose, onEvent, signal, delayScale })`。

- [x] **Step 1: 创建测试入口并写失败测试**

```json
{
  "name": "starlight-treasure-adventure",
  "private": true,
  "type": "module",
  "scripts": { "test": "node --test" }
}
```

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { findTreasure } from '../treasure.js';

test('按 async/await 顺序完成萤火小径寻宝', async () => {
  const events = [];
  const answers = ['firefly', 'moon'];
  const result = await findTreasure({
    choose: async () => answers.shift(),
    onEvent: event => events.push(event.type),
    delayScale: 0
  });
  assert.equal(result.title, '星潮之心');
  assert.deepEqual(events.slice(-3), ['chest', 'treasure', 'settled']);
});

test('谜题答错后可异步重试并完成旅程', async () => {
  const events = [];
  const answers = ['secret', 'sun', 'moon'];
  await findTreasure({
    choose: async () => answers.shift(),
    onEvent: event => events.push(event.type),
    delayScale: 0
  });
  assert.ok(events.includes('riddle-error'));
  assert.equal(events.at(-2), 'treasure');
  assert.equal(events.at(-1), 'settled');
});

test('取消信号会终止旧旅程', async () => {
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(
    findTreasure({ choose: async () => 'firefly', onEvent() {}, signal: controller.signal, delayScale: 0 }),
    error => error.name === 'AbortError'
  );
});
```

- [x] **Step 2: 运行测试并确认因模块缺失而失败**

Run: `node --test treasure-adventure/tests/treasure.test.js`

Expected: FAIL，提示无法导入 `treasure.js`。

- [x] **Step 3: 实现最小异步剧情引擎**

```js
export class TempleChallengeError extends Error {
  constructor(message) {
    super(message);
    this.name = 'TempleChallengeError';
  }
}

export async function findTreasure({ choose, onEvent, signal, delayScale = 1 }) {
  try {
    const clue = await TreasureMap.getInitialClue({ signal, delayScale });
    onEvent({ type: 'clue', message: clue });
    const location = await TreasureMap.decodeAncientScript(clue, { signal, delayScale });
    onEvent({ type: 'decoded', message: location });
    const route = await choose({ type: 'route' });
    await TreasureMap.crossWhisperingForest(route, { signal, delayScale });
    onEvent({ type: 'temple', route });
    let solved = false;
    while (!solved) {
      try {
        const answer = await choose({ type: 'riddle' });
        await TreasureMap.solveTempleRiddle(answer, { signal, delayScale });
        solved = true;
      } catch (error) {
        if (!(error instanceof TempleChallengeError)) throw error;
        onEvent({ type: 'riddle-error', message: error.message });
      }
    }
    const relics = await TreasureMap.gatherRelics(route, { signal, delayScale });
    onEvent({ type: 'chest', relics });
    const treasure = await TreasureMap.openTreasureBox(relics, { signal, delayScale });
    onEvent({ type: 'treasure', treasure });
    return treasure;
  } catch (error) {
    if (error.name !== 'AbortError') onEvent({ type: 'error', message: error.message });
    throw error;
  } finally {
    onEvent({ type: 'settled' });
  }
}
```

`TreasureMap` 的六个静态方法统一调用支持 `AbortSignal` 的 `wait(ms, signal, delayScale)`，返回完整中文剧情数据；`solveTempleRiddle` 对非 `moon` 答案抛出 `TempleChallengeError`。

- [x] **Step 4: 运行引擎测试并确认通过**

Run: `node --test treasure-adventure/tests/treasure.test.js`

Expected: 3 tests PASS，无警告和未处理拒绝。

### Task 2: 动画舞台与浏览器交互

**Files:**
- Create: `treasure-adventure/index.html`
- Create: `treasure-adventure/styles.css`
- Create: `treasure-adventure/app.js`
- Create: `treasure-adventure/tests/site-structure.test.js`

**Interfaces:**
- Consumes: `findTreasure()` 事件、浏览器 DOM、按钮点击。
- Produces: `waitForChoice(prompt, signal)`、`renderEvent(event)`、`startAdventure()` 和完整可访问页面。

- [x] **Step 1: 写静态结构失败测试**

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('页面包含地图、剧情日志、选择区和开始按钮', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  for (const id of ['adventure-map', 'story-log', 'choice-panel', 'start-button']) {
    assert.match(html, new RegExp(`id=["']${id}["']`));
  }
  assert.match(html, /<script type="module" src="\.\/app\.js"><\/script>/);
});

test('样式支持减少动态效果偏好', async () => {
  const css = await readFile(new URL('../styles.css', import.meta.url), 'utf8');
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
});

test('浏览器控制器使用 await 调用寻宝流程', async () => {
  const source = await readFile(new URL('../app.js', import.meta.url), 'utf8');
  assert.match(source, /await\s+findTreasure/);
});
```

- [x] **Step 2: 运行结构测试并确认页面文件缺失**

Run: `node --test treasure-adventure/tests/site-structure.test.js`

Expected: FAIL，提示 `index.html`、`styles.css` 或 `app.js` 不存在。

- [x] **Step 3: 创建语义化页面骨架**

`index.html` 包含跳过链接、标题区、六站 SVG/CSS 地图、探险者标记、进度条、`aria-live` 剧情日志、隐藏的选择面板、开始/重开按钮和宝藏弹层。所有交互按钮使用明确中文标签，脚本以 ES Module 引入。

- [x] **Step 4: 实现响应式视觉和动画**

`styles.css` 定义夜蓝、古金、魔法青设计令牌；实现星尘、雾层、发光路径、角色移动、卡片入场、神庙开门、宝箱摇晃和宝藏光爆动画。`@media (max-width: 820px)` 切换为单列，`@media (prefers-reduced-motion: reduce)` 把动画和滚动时间降到接近零。

- [x] **Step 5: 实现 DOM 控制器和可取消选择 Promise**

```js
function waitForChoice(prompt, signal) {
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      choicePanel.replaceChildren();
      choicePanel.hidden = true;
      signal?.removeEventListener('abort', abort);
    };
    const abort = () => {
      cleanup();
      reject(new DOMException('旅程已重新开始', 'AbortError'));
    };
    for (const option of prompt.options) {
      const button = document.createElement('button');
      button.textContent = option.label;
      button.addEventListener('click', () => {
        cleanup();
        resolve(option.value);
      }, { once: true });
      choicePanel.append(button);
    }
    choicePanel.hidden = false;
    signal?.addEventListener('abort', abort, { once: true });
  });
}

async function startAdventure() {
  activeController?.abort();
  activeController = new AbortController();
  resetScene();
  try {
    await findTreasure({
      choose: prompt => waitForChoice(prompt, activeController.signal),
      onEvent: renderEvent,
      signal: activeController.signal
    });
  } catch (error) {
    if (error.name !== 'AbortError') renderFatalError(error);
  }
}
```

- [x] **Step 6: 运行全部本地自动测试**

Run: `npm test --prefix treasure-adventure`

Expected: 6 tests PASS。

### Task 3: Vercel 配置、部署与线上验证

**Files:**
- Create: `treasure-adventure/vercel.json`
- Create: `treasure-adventure/README.md`
- Create or update by Vercel CLI: `treasure-adventure/.vercel/project.json`

**Interfaces:**
- Consumes: 完成的静态站点、当前 Vercel 登录会话。
- Produces: Vercel 项目、生产部署 URL、部署说明。

- [x] **Step 1: 添加静态部署配置和项目说明**

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "cleanUrls": true,
  "trailingSlash": false
}
```

`README.md` 先记录剧情、`async/await` 教学点、本地打开方式和测试命令；部署成功后追加真实生产 URL。

- [x] **Step 2: 确认 Vercel 身份和项目上下文**

Run from `treasure-adventure`: `vercel whoami --format json`

Expected: 返回当前 Vercel 用户/团队；若要求登录，停止并由用户完成设备授权。

Run from `treasure-adventure`: `vercel project inspect --non-interactive`

Expected: 对首次项目返回 `link_required`；不得误用现有项目。

- [x] **Step 3: 创建或链接独立 Vercel 项目并生产部署**

Run from `treasure-adventure`: `vercel --prod --yes`

Expected: stdout 返回新的 `https://...vercel.app` 生产 URL。

- [x] **Step 4: 检查部署状态和线上首页**

Run from `treasure-adventure`: `vercel inspect <deployment-url>`

Expected: 部署状态为 Ready。

Run from `treasure-adventure`: `vercel curl <deployment-url>`

Expected: HTTP 成功并返回包含“星潮遗迹”的 HTML。

- [x] **Step 5: 更新 README 并返回网页链接**

将真实生产 URL 写入 `treasure-adventure/README.md`，在聊天最终结果中以可点击 Markdown 链接返回。
