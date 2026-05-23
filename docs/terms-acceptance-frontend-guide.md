# Terms Acceptance 功能 - 前端对接指南

> **后端版本**: `v0.25.1`  
> **完成日期**: 2026-05-08  
> **对接优先级**: P0（核心安全功能，影响用户注册/登录）

---

## 1. 功能概述

### 1.1 背景

- **法律合规**: GDPR/CCPA 要求证明用户同意了服务条款
- **版本管理**: 条款更新时需检查用户 `terms_version` 是否过期
- **审计追溯**: 记录用户同意的具体版本和时间

### 1.2 核心变更点

| 变更类型 | 影响范围 | 前端适配 |
|---------|---------|---------|
| **注册流程** | `POST /api/v1/auth/register` | 新增 `termsVersion` 参数 |
| **登录响应** | `POST /api/v1/auth/login` | 新增 `termsExpired` 字段 |
| **API 拦截** | 所有认证 API | 403 `USER_008` → 弹框重新同意 |
| **新端点** | `GET /api/v1/config/public` | 启动时获取当前条款版本 |
| **新端点** | `POST /api/v1/auth/terms/accept` | 重新同意条款后调用 |

---

## 2. API 变更详情

### 2.1 注册流程变更

**端点**: `POST /api/v1/auth/register`

**变更**: 新增必填参数 `termsVersion`

#### 旧版请求（v0.24.x）

```json
{
  "email": "user@example.com",
  "displayName": "User",
  "password": "SecurePass123!",
  "termsAccepted": true  // ❌ 已废弃
}
```

#### 新版请求（v0.25.1）

```json
{
  "email": "user@example.com",
  "displayName": "User",
  "password": "SecurePass123!",
  "termsVersion": "1.0.0"  // ✅ 新增必填
}
```

**关键点**:
- `termsAccepted` 字段已移除，改用 `termsVersion`
- `termsVersion` 必须与后端当前版本一致（版本不匹配返回 `USER_008`）
- 前端需在注册页显示条款 checkbox，用户勾选后传递版本号

---

### 2.2 登录响应变更

**端点**: `POST /api/v1/auth/login`

**变更**: 新增 `termsExpired` 字段

#### 新版响应

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "d4f5e6a7b8c9d0e1f2a3b4c5d6e7f8a9",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "termsExpired": false  // ✅ 新增字段
}
```

**字段含义**:
- `termsExpired: false` → 用户条款未过期，正常使用
- `termsExpired: true` → 用户条款已过期，需弹出同意框

---

### 2.3 新端点：获取公开配置

**端点**: `GET /api/v1/config/public`  
**认证**: 无需认证（公开端点）

#### 响应

```json
{
  "code": 20000,
  "message": "Success",
  "data": {
    "termsVersion": "1.0.0"
  }
}
```

**用途**: 
- 前端启动时调用，获取当前条款版本
- 注册页展示条款时使用此版本号
- 后端条款更新时，前端自动获取新版本

---

### 2.4 新端点：同意条款

**端点**: `POST /api/v1/auth/terms/accept`  
**认证**: 需要 JWT（`Authorization: Bearer <accessToken>`）

#### 请求

无请求体（用户 ID 从 JWT 中提取）

#### 响应

```json
{
  "code": 20000,
  "message": "Success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",  // ✅ 新 JWT（含新 termsVersion）
    "refreshToken": "d4f5e6a7b8c9d0e1f2a3b4c5d6e7f8a9",
    "expiresIn": 3600,
    "tokenType": "Bearer",
    "termsExpired": false
  }
}
```

**用途**: 
- 用户条款过期后，弹出同意框
- 用户勾选新条款后调用此端点
- 返回新 JWT，替换 localStorage 中的旧 JWT
- 重放之前被拦截的请求

---

## 3. 前端实现清单

### 3.1 注册页适配

**任务清单**:

| 优先级 | 任务 | 实现要点 |
|--------|------|---------|
| **P0** | 启动时获取版本 | `GET /api/v1/config/public` → 存储 `termsVersion` |
| **P0** | 条款 checkbox | 展示条款内容 + "我已阅读并同意" checkbox |
| **P0** | 注册请求改造 | 移除 `termsAccepted`，改传 `termsVersion` |
| **P1** | 版本不匹配处理 | 400 `USER_008` → 弹出提示"请刷新页面重试" |

**代码示例**（Vue.js）:

```vue
<template>
  <form @submit.prevent="handleRegister">
    <input v-model="form.email" type="email" placeholder="邮箱" />
    <input v-model="form.displayName" placeholder="用户名" />
    <input v-model="form.password" type="password" placeholder="密码" />
    
    <!-- ✅ 新增条款 checkbox -->
    <label>
      <input v-model="form.agreedTerms" type="checkbox" />
      我已阅读并同意 <a href="/terms" target="_blank">服务条款</a>
    </label>
    
    <button :disabled="!form.agreedTerms">注册</button>
  </form>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'

const form = ref({
  email: '',
  displayName: '',
  password: '',
  agreedTerms: false
})

const currentTermsVersion = ref('')

// ✅ P0: 启动时获取版本
onMounted(async () => {
  const res = await api.get('/api/v1/config/public')
  currentTermsVersion.value = res.data.termsVersion
})

// ✅ P0: 注册请求改造
const handleRegister = async () => {
  try {
    const payload = {
      email: form.value.email,
      displayName: form.value.displayName,
      password: form.value.password,
      termsVersion: currentTermsVersion.value  // ✅ 传递版本号
    }
    await api.post('/api/v1/auth/register', payload)
    // 注册成功逻辑...
  } catch (error) {
    if (error.code === 'USER_008') {
      // ✅ P1: 版本不匹配处理
      alert('条款版本已更新，请刷新页面后重新注册')
    }
  }
}
</script>
```

---

### 3.2 登录响应适配

**任务清单**:

| 优先级 | 任务 | 实现要点 |
|--------|------|---------|
| **P0** | 解析 `termsExpired` | 登录成功后检查此字段 |
| **P0** | 弹框逻辑 | `termsExpired: true` → 弹出条款同意框 |
| **P1** | 缓存逻辑 | 暂不处理（后端未实现 cache 字段） |

**代码示例**（Vue.js）:

```vue
<script setup>
import { ref } from 'vue'
import api from '@/api'
import TermsDialog from '@/components/TermsDialog.vue'

const showTermsDialog = ref(false)
const pendingRequests = ref([])

const handleLogin = async () => {
  try {
    const res = await api.post('/api/v1/auth/login', {
      email: form.value.email,
      password: form.value.password
    })
    
    // ✅ P0: 检查条款是否过期
    if (res.data.termsExpired) {
      showTermsDialog.value = true
      // 暂存 JWT（过期状态），用户同意后替换
      localStorage.setItem('accessToken', res.data.accessToken)
    } else {
      // 正常登录逻辑
      localStorage.setItem('accessToken', res.data.accessToken)
      router.push('/dashboard')
    }
  } catch (error) {
    // 登录失败处理...
  }
}
</script>
```

---

### 3.3 Axios 拦截器适配

**任务清单**:

| 优先级 | 任务 | 实现要点 |
|--------|------|---------|
| **P0** | 拦截 403 `USER_008` | 全局拦截条款过期错误 |
| **P0** | 缓存失败请求 | 拦截后暂存请求到队列 |
| **P0** | 弹出同意框 | 显示 TermsDialog |
| **P0** | 调用同意端点 | `POST /api/v1/auth/terms/accept` |
| **P0** | 替换 JWT | 新 JWT 替换 localStorage |
| **P0** | 重放请求 | 队列中的请求用新 JWT 重发 |

**代码示例**（Axios 拦截器）:

```javascript
// src/api/interceptors.js
import axios from 'axios'
import TermsDialog from '@/components/TermsDialog.vue'

const pendingRequests = []
let isShowingTermsDialog = false

axios.interceptors.response.use(
  response => response,
  async error => {
    const { response, config } = error
    
    // ✅ P0: 拦截条款过期错误
    if (response?.status === 403 && response?.data?.code === 'USER_008') {
      // 缓存失败请求
      pendingRequests.push(config)
      
      // 如果未显示弹框，则显示
      if (!isShowingTermsDialog) {
        isShowingTermsDialog = true
        showTermsDialog()
      }
      
      return Promise.reject(error)
    }
    
    return Promise.reject(error)
  }
)

// ✅ P0: 显示条款同意框
async function showTermsDialog() {
  const agreed = await TermsDialog.show()
  
  if (agreed) {
    // 调用同意端点
    const res = await axios.post('/api/v1/auth/terms/accept')
    const newAccessToken = res.data.data.accessToken
    
    // 替换 JWT
    localStorage.setItem('accessToken', newAccessToken)
    
    // 重放所有缓存的请求
    pendingRequests.forEach(config => {
      config.headers.Authorization = `Bearer ${newAccessToken}`
      axios.request(config)
    })
    pendingRequests.length = 0
  }
  
  isShowingTermsDialog = false
}
```

---

### 3.4 TermsDialog 组件

**任务清单**:

| 优先级 | 任务 | 实现要点 |
|--------|------|---------|
| **P0** | 条款内容展示 | 从 i18n 文件读取（`locales/zh.json`, `locales/en.json`） |
| **P0** | 同意 checkbox | "我已阅读并同意新条款" |
| **P0** | 调用同意端点 | 用户勾选后调用 `POST /api/v1/auth/terms/accept` |
| **P1** | 多语言支持 | i18n 管理条款内容 |

**关键点**:
- **条款内容存储位置**: 前端 i18n 文件（后端不存储条款内容）
- **版本查询**: `GET /api/v1/config/public` 返回当前版本号
- **多语言**: 前端控制，后端只校验版本号

---

## 4. 错误码处理

| 错误码 | HTTP 状态 | 含义 | 前端处理 |
|--------|----------|------|---------|
| `USER_008` | 400 | 注册时条款版本不匹配 | 弹出提示"请刷新页面重试" |
| `USER_008` | 403 | 访问 API 时条款已过期 | 弹出 TermsDialog，用户同意后重放请求 |
| `AUTH_019` | 403 | 条款版本已过期（旧版错误码） | 同 USER_008 处理（已合并） |

**注意**: 后端统一使用 `USER_008` 表示条款相关问题。

---

## 5. 测试场景

### 5.1 注册流程测试

| 场景 | 操作 | 预期结果 |
|------|------|---------|
| 正常注册 | 勾选条款 → 传 `termsVersion: "1.0.0"` | 注册成功，200 |
| 版本不匹配 | 传旧版本 `termsVersion: "0.9.0"` | 400 `USER_008`，提示刷新页面 |
| 未勾选条款 | 未勾选 checkbox | 前端禁用提交按钮 |
| OAuth 注册 | GitHub OAuth 登录 | 自动设置 `termsVersion`，返回 `termsExpired: false` |

### 5.2 登录流程测试

| 场景 | 操作 | 预期结果 |
|------|------|---------|
| 条款未过期 | 正常登录 | `termsExpired: false`，正常跳转 dashboard |
| 条款已过期 | 登录 | `termsExpired: true`，弹出 TermsDialog |
| 同意新条款 | 弹框中勾选 → 点击同意 | 调用 `/terms/accept`，返回新 JWT，跳转 dashboard |

### 5.3 API 拦截测试

| 场景 | 操作 | 预期结果 |
|------|------|---------|
| 条款过期后访问 API | 用户条款过期 → 请求 `/api/v1/stats` | 403 `USER_008`，弹出 TermsDialog |
| 同意后重放请求 | 弹框同意 → 新 JWT → 重放 `/stats` | 200 正常返回数据 |
| 多个请求并发过期 | 3个请求同时触发 403 | 弹框只显示一次，同意后重放所有请求 |

---

| 决策点 | 后端选择 | 前端影响 |
|--------|---------|---------|
| **条款内容存储** | 前端 i18n | 前端完全控制内容，后端不存储 |
| **当前版本号** | 后端配置 `application.yaml` | 前端从 `GET /config/public` 获取 |
| **是否建 terms 表** | ❌ 不建 | 无需复杂 DB 查询 |
| **termsAccepted 字段** | ❌ 移除 | 改用 `termsVersion` |
| **JWT claims** | ✅ 使用 `termsVersion` claim | 前端无需解析 JWT，后端拦截器处理 |
| **同意后返回** | ✅ 完整 JWT 对 | 前端需替换 localStorage |

---

## 7. 后端版本信息

- **当前版本**: `v0.25.1`
- **API 文档**: http://localhost:8080/swagger-ui.html
- **Git 提交**: `feat(terms): add Terms Acceptance feature tests and documentation` (9697d76)
- **Git 提交**: `chore: bump version to 0.25.1` (fa8c74e)
- **分支**: `develop` / `master`（已同步）

---

## 8. 联系方式

如有对接问题，请联系：
- **后端开发**: AhogeK
- **文档位置**: `docs/developer-handbook.md`（USER_008/AUTH_019 错误码详情）

---

**祝对接顺利！如有问题随时沟通。**