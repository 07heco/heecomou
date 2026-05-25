# HeecoMou — 智能语音输入法

## 1. 项目概述

**HeecoMou** 是一个面向中文用户的**智能语音输入法系统**，支持云端高精度 ASR 与端侧离线推理双引擎，提供词库管理、语音识别文本纠错、多端（桌面客户端 + Android 输入法 + Web 管理后台）同步等功能。

| 属性       | 说明                                                |
| -------- | ------------------------------------------------- |
| 项目类型     | 全栈语音输入法系统（6 组件）                                   |
| 编程语言     | Go / Java / Kotlin / Python / TypeScript          |
| 部署目标     | 云服务器 (Linux) + 桌面端 (Windows) + 移动端 (Android 8.0+) |
| 持久化存储    | MySQL 8.0 (MariaDB)                               |
| 缓存 / 中间件 | Redis 7                                           |
| 核心依赖     | [声明见 §7 第三方库对照表](#7-原创功能与第三方库对照)                  |

***

## 2. 核心需求

| 编号  | 需求                                        | 实现状态  |
| --- | ----------------------------------------- | ----- |
| R1  | 语音录制并实时转写为中文文本                            | ✅ 已完成 |
| R2  | 支持云端高精度 ASR 与端侧离线 ONNX 推理两种模式             | ✅ 已完成 |
| R3  | 根据网络状况、电量、隐私上下文自动路由 ASR 引擎                | ✅ 已完成 |
| R4  | 用户词库管理（增删改查、搜索、云端同步）                      | ✅ 已完成 |
| R5  | 词库数据为 ASR 识别提供热词注入，提升专有名词准确率              | ✅ 已完成 |
| R6  | 识别结果可编辑，支持用户提交纠错反馈形成闭环                    | ✅ 已完成 |
| R7  | 多端一致性：Desktop / Android / Web 使用同一账号体系与数据 | ✅ 已完成 |
| R8  | JWT 认证 + RefreshToken 无感刷新 + 登出 Token 黑名单 | ✅ 已完成 |
| R9  | 接口限流保护（令牌桶算法）                             | ✅ 已完成 |
| R10 | 提供 Swagger 在线 API 文档                      | ✅ 已完成 |

***


## 3. 系统架构

### 3.1 组件部署拓扑

```
┌──────────────────────────────────────────────────────────────────┐
│                    Client Layer（客户端层）                        │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐    │
│  │ Desktop Client   │  │ Android IME      │  │ Web Frontend │    │
│  │ (Compose/Kotlin) │  │ (kotlin/ONNX)    │  │ (React/TS)   │    │
│  └───┬──────────┬───┘  └───┬──────────┬───┘  └──────┬───────┘    │
│      │WebSocket │HTTP      │WebSocket │HTTP         │HTTP        │
└──────┼──────────┼──────────┼──────────┼─────────────┼───────────┘
       │          │          │          │             │
       ▼          ▼          ▼          ▼             ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Server Layer（服务端层）                      │
│                                                                  │
│  ┌────────────────────┐    ┌──────────────────────────────────┐  │
│  │ Gateway (Go :8080) │    │ Backend (Java/Spring :8081)      │  │
│  │ ─────────────────  │    │ ─────────────────────────────    │  │
│  │ • WebSocket 音频流  │    │ • 用户认证 (JWT/Security)        │  │
│  │ • VAD 语音活动检测  │    │ • 词库 CRUD + 增量同步           │  │
│  │ • 噪声抑制 (Wiener) │    │ • 纠错记录管理                   │  │
│  │ • ASR 引擎路由决策  │    │ • MyBatis-Plus ORM              │  │
│  │    (Cloud/Local)   │    │ • RateLimit 令牌桶限流           │  │
│  └────────┬───────────┘    │ • Swagger API 文档              │  │
│           │                └──────────────────────────────────┘  │
│           │ HTTP POST /api/v1/asr/recognize                      │
│           ▼                                                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ ASR Service (Python/FastAPI :8082)                         │  │
│  │ ──────────────────────────────────                         │  │
│  │ • Whisper 语音识别模型 (openai/whisper-small)               │  │
│  │ • ONNX Runtime 边端推理优化                                 │  │
│  │ • 词汇注入 (VocabInjector)                                  │  │
│  │ • 文本后处理 (纠错/标点/格式化)                              │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
       │                │
       ▼                ▼
┌──────────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer（基础设施层）                  │
│  ┌─────────────────────────┐   ┌─────────────────────────────┐   │
│  │ MySQL/MariaDB (:3306)   │   │ Redis (:6379)               │   │
│  │ user / vocabulary /     │   │ Token 黑名单 / RefreshToken  │   │
│  │ correction_history      │   │ 令牌桶限流计数器             │   │
│  └─────────────────────────┘   └─────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 语音识别完整链路

```
客户端麦克风 → PCM 音频流 → WebSocket → Gateway (:8080)
    ├── VAD (语音活动检测) 分割语音段
    ├── Wiener (维纳滤波) 噪声抑制
    ├── 保存 .wav 文件到磁盘
    └── 会话结束 → Base64 编码 → HTTP POST → ASR (:8082)
         ├── Whisper 模型推理
         ├── VocabInjector 词库热词注入 + 常见错误纠正
         ├── Punctuator 标点恢复
         └── Formatter 文本格式化
              └── 识别文本 → WebSocket 回传 → 客户端展示
```

### 3.3 ASR 引擎路由策略

| 优先级     | 条件                          | 决策         |
| ------- | --------------------------- | ---------- |
| Level 1 | 电量 ≤ 15% 或 离线状态             | **本地端侧推理** |
| Level 2 | 蜂窝信号 < 0.5 或 WiFi 信号 < 0.3  | **本地端侧推理** |
| Level 3 | 敏感隐私上下文 (如密码输入)             | **本地端侧推理** |
| Level 4 | WiFi > 0.7 或以太网 + 噪音 < 50dB | **云端高精度**  |
| Level 5 | 默认                          | **云端高精度**  |

### 3.4 数据流向

```
用户注册/登录 → Backend 验证密码 → 签发 JWT (accessToken + refreshToken)
    ↓
客户端缓存 JWT → 每次 API 调用携带 Bearer Token
    ↓
语音识别 → 云端 ASR → 注入用户词库热词 → 返回纠正后文本
    ↓
用户编辑纠错 → POST /api/v1/corrections → correction_history 表
    ↓ (未来闭环)
纠错数据 → 反馈到词库/ASR 模型微调
```

***

## 4. 组件技术栈与依赖

### 4.1 Gateway (Go 语音网关)

| 属性        | 值                            |
| --------- | ---------------------------- |
| 端口        | `8080` (环境变量 `GATEWAY_PORT`) |
| 语言        | Go 1.22                      |
| WebSocket | gorilla/websocket v1.5.3     |

**功能模块：**

| 模块           | 路径                          | 说明                            |
| ------------ | --------------------------- | ----------------------------- |
| `websocket/` | 音频 WebSocket 处理器            | 接收客户端实时音频流，分 session 管理       |
| `audio/`     | VAD / FFT / Wiener / Writer | 语音活动检测、快速傅里叶变换、维纳滤波降噪         |
| `asr/`       | Forwarder / gRPC Client     | Base64 编码音频后转发到 Python ASR 服务 |
| `router/`    | 规则路由引擎                      | 根据设备上下文 (电量/网络/噪音/隐私) 决策云端或端侧 |

**第三方依赖：**

| 依赖                                                        | 版本     | 用途             | 许可           |
| --------------------------------------------------------- | ------ | -------------- | ------------ |
| [gorilla/websocket](https://github.com/gorilla/websocket) | v1.5.3 | WebSocket 协议实现 | BSD-2-Clause |

***

### 4.2 Backend (Java 业务后端)

| 属性     | 值                                    |
| ------ | ------------------------------------ |
| 端口     | `8081`                               |
| JDK    | Java 17                              |
| 框架     | Spring Boot 3.2.5                    |
| 构建     | Maven (pom.xml)                      |
| ORM    | MyBatis-Plus 3.5.7                   |
| API 文档 | SpringDoc OpenAPI 2.5.0 (Swagger UI) |
| 数据库迁移  | Flyway                               |

**第三方依赖：**

| 依赖                             | GroupId                  | 版本        | 用途              | 许可                |
| ------------------------------ | ------------------------ | --------- | --------------- | ----------------- |
| Spring Boot Starter Web        | org.springframework.boot | 3.2.5     | Web 框架          | Apache-2.0        |
| Spring Boot Starter Security   | org.springframework.boot | 3.2.5     | 认证鉴权            | Apache-2.0        |
| Spring Boot Starter Validation | org.springframework.boot | 3.2.5     | 参数校验            | Apache-2.0        |
| Spring Boot Starter Data Redis | org.springframework.boot | 3.2.5     | Redis 集成        | Apache-2.0        |
| Spring Boot Starter AOP        | org.springframework.boot | 3.2.5     | 切面编程（限流）        | Apache-2.0        |
| MyBatis-Plus                   | com.baomidou             | 3.5.7     | ORM 框架          | Apache-2.0        |
| MySQL Connector/J              | com.mysql                | (managed) | MySQL 驱动        | GPLv2             |
| jjwt (API + Impl + Jackson)    | io.jsonwebtoken          | 0.12.5    | JWT 签发与验证       | Apache-2.0        |
| Flyway Core + MySQL            | org.flywaydb             | (managed) | 数据库版本迁移         | Apache-2.0        |
| SpringDoc OpenAPI              | org.springdoc            | 2.5.0     | Swagger UI 接口文档 | Apache-2.0        |
| H2 Database                    | com.h2database           | (managed) | 测试用内存数据库        | EPL-1.0 / MPL-2.0 |

***

### 4.3 ASR (Python 语音识别引擎)

| 属性     | 值                                              |
| ------ | ---------------------------------------------- |
| 端口     | `8082`                                         |
| Python | ≥ 3.10                                         |
| 框架     | FastAPI 0.110.3 + Uvicorn 0.27.1               |
| ASR 模型 | `openai/whisper-small` (可通过 `ASR_MODEL_ID` 配置) |

**功能模块：**

| 模块                         | 路径              | 说明                |
| -------------------------- | --------------- | ----------------- |
| `inference/engine.py`      | Whisper 推理引擎    | 模型加载 / 语音转文本 / 卸载 |
| `inference/onnx_engine.py` | ONNX Runtime 推理 | 端侧 ONNX 模型导出与推理加速 |
| `nlp/vocab_injector.py`    | 词汇注入器           | 用户词库拼音匹配 + 常见错误纠正 |
| `nlp/punctuator.py`        | 标点恢复            | 无标点文本自动添加标点符号     |
| `nlp/formatter.py`         | 文本格式化           | 数字/日期等格式化处理       |
| `nlp/context_corrector.py` | 上下文纠错           | 基于语言模型的上下文理解纠错    |
| `grpc_server.py`           | gRPC 服务         | gRPC 协议语音识别接口     |
| `local_onnx_server.py`     | 本地 ONNX 服务      | 端侧 ONNX 推理部署脚本    |

**第三方依赖：**

| 依赖                                                          | 版本       | 用途                   | 许可           |
| ----------------------------------------------------------- | -------- | -------------------- | ------------ |
| [fastapi](https://github.com/tiangolo/fastapi)              | 0.110.3  | HTTP API 框架          | MIT          |
| [uvicorn](https://github.com/encode/uvicorn)                | 0.27.1   | ASGI 服务器             | BSD-3-Clause |
| [torch](https://pytorch.org/)                               | ≥ 2.6.0  | 深度学习框架               | BSD-3-Clause |
| [transformers](https://github.com/huggingface/transformers) | ≥ 4.48.0 | Whisper 模型加载         | Apache-2.0   |
| [onnxruntime](https://github.com/microsoft/onnxruntime)     | 1.17.3   | ONNX 模型推理引擎          | MIT          |
| [grpcio](https://grpc.io/)                                  | 1.62.2   | gRPC 通信框架            | Apache-2.0   |
| grpcio-tools                                                | 1.62.2   | gRPC protobuf 代码生成   | Apache-2.0   |
| protobuf                                                    | 5.26.1   | Protocol Buffers     | BSD-3-Clause |
| numpy                                                       | 1.26.4   | 科学计算                 | BSD-3-Clause |
| soundfile                                                   | 0.12.1   | 音频文件读写               | BSD-3-Clause |
| scipy                                                       | 1.13.1   | 科学计算（信号处理）           | BSD-3-Clause |
| httpx                                                       | 0.27.2   | HTTP 客户端（调用后端词库 API） | BSD-3-Clause |
| zhconv                                                      | ≥ 1.4.0  | 中文简繁转换               | MIT          |
| pydantic                                                    | ≥ 2.0.0  | 数据校验                 | MIT          |
| pypinyin                                                    | ≥ 0.50.0 | 汉字转拼音                | MIT          |
| pytest                                                      | 8.3.3    | 测试框架                 | MIT          |

***

### 4.4 Frontend (React Web 管理后台)

| 属性    | 值                         |
| ----- | ------------------------- |
| 端口    | `5173` (dev)              |
| Node  | ≥ 18                      |
| 框架    | React 18 + TypeScript 5.8 |
| 构建    | Vite 6.3                  |
| UI 样式 | Tailwind CSS 3.4          |
| 状态管理  | Zustand 5.0               |

**功能页面：**

| 页面     | 文件                    | 说明                 |
| ------ | --------------------- | ------------------ |
| 首页     | `Home.tsx`            | 项目入口 / 导航          |
| 登录     | `LoginPage.tsx`       | 用户名密码登录            |
| 注册     | `RegisterPage.tsx`    | 新用户注册（注册即返回 Token） |
| 仪表盘    | `DashboardPage.tsx`   | 用户数据概览仪表盘          |
| 词库管理   | `VocabularyPage.tsx`  | 增删改查 + 搜索 + 手动同步   |
| 纠错记录   | `CorrectionsPage.tsx` | 纠错历史查看 + 手动提交纠错    |
| 个人资料   | `ProfilePage.tsx`     | 个人信息编辑             |
| 修改密码   | `PasswordPage.tsx`    | 密码修改               |
| API 文档 | `public/docs.html`    | 离线 API 接口文档页       |

**第三方依赖：**

| 依赖                                                          | 版本      | 用途             | 许可         |
| ----------------------------------------------------------- | ------- | -------------- | ---------- |
| [react](https://react.dev/)                                 | 18.3.1  | UI 框架          | MIT        |
| [react-dom](https://react.dev/)                             | 18.3.1  | React DOM 渲染   | MIT        |
| [react-router-dom](https://reactrouter.com/)                | 7.3.0   | 客户端路由          | MIT        |
| [axios](https://axios-http.com/)                            | 1.7.0   | HTTP 客户端       | MIT        |
| [zustand](https://github.com/pmndrs/zustand)                | 5.0.3   | 状态管理           | MIT        |
| [react-hook-form](https://react-hook-form.com/)             | 7.54.0  | 表单处理           | MIT        |
| [lucide-react](https://lucide.dev/)                         | 0.511.0 | SVG 图标库        | ISC        |
| [tailwindcss](https://tailwindcss.com/)                     | 3.4.17  | 原子化 CSS 框架     | MIT        |
| [clsx](https://github.com/lukeed/clsx)                      | 2.1.1   | CSS Class 拼接工具 | MIT        |
| [tailwind-merge](https://github.com/dcastil/tailwind-merge) | 3.0.2   | Tailwind 类名合并  | MIT        |
| [vite](https://vitejs.dev/)                                 | 6.3.5   | 构建工具           | MIT        |
| [typescript](https://www.typescriptlang.org/)               | 5.8.3   | 类型系统           | Apache-2.0 |
| [eslint](https://eslint.org/)                               | 9.25.0  | 代码规范检查         | MIT        |
| [postcss](https://postcss.org/)                             | 8.5.3   | CSS 后处理        | MIT        |
| autoprefixer                                                | 10.4.21 | CSS 自动前缀       | MIT        |

***

### 4.5 Desktop Client (Compose Desktop)

| 属性    | 值                                     |
| ----- | ------------------------------------- |
| 语言    | Kotlin (JVM) 1.9.22                   |
| UI 框架 | JetBrains Compose Multiplatform 1.6.0 |
| 构建    | Gradle 8.5                            |
| 目标平台  | Windows (MSI/EXE)                     |
| 主类    | `com.heecomou.desktop.MainKt`         |

**源码文件清单 (15 个)：**

| 包          | 文件                       | 说明                                                 |
| ---------- | ------------------------ | -------------------------------------------------- |
| `hotkey/`  | `GlobalHotkeyManager.kt` | 全局快捷键注册 (Ctrl+Shift+V 触发 / Esc 取消)                 |
| `auth/`    | `TokenManager.kt`        | JWT Token 本地持久化（`%LOCALAPPDATA%\HeecoMou\`）        |
| `auth/`    | `AuthApiService.kt`      | 登录/注册 API 调用 (OkHttp)                              |
| `auth/`    | `AuthModels.kt`          | LoginRequest / RegisterRequest / LoginResponse DTO |
| `asr/`     | `AsrRouter.kt`           | 调用 Gateway 路由 API 决策云端/端侧                          |
| `asr/`     | `CloudAsrClient.kt`      | WebSocket 云端 ASR 客户端                               |
| `asr/`     | `LocalAsrClient.kt`      | 端侧 ONNX 推理客户端                                      |
| `network/` | `VocabApiService.kt`     | 词汇 CRUD + 同步 API 调用                                |
| `network/` | `VocabModels.kt`         | 词汇 + 纠错数据模型                                        |
| `network/` | `LocalAsrApiService.kt`  | 本地 ASR HTTP 接口                                     |
| `vocab/`   | `VocabSyncManager.kt`    | 增量同步引擎（version 游标翻页）                               |
| `vocab/`   | `LocalVocabStore.kt`     | SQLite 本地词库存储                                      |
| `ui/`      | `LoginWindow.kt`         | 登录/注册窗口 (Compose UI)                               |
| `ui/`      | `FloatingVoiceWindow.kt` | 浮动语音输入窗口 + 识别结果编辑 + 纠错提交                           |
| `ui/`      | `TextOutputManager.kt`   | 识别文本输出管理                                           |

**第三方依赖：**

| 依赖                | GroupId/ArtifactId                       | 版本       | 用途                | 许可         |
| ----------------- | ---------------------------------------- | -------- | ----------------- | ---------- |
| JetBrains Compose | org.jetbrains.compose                    | 1.6.0    | 跨平台 Desktop UI 框架 | Apache-2.0 |
| OkHttp            | com.squareup.okhttp3                     | 4.12.0   | HTTP 客户端          | Apache-2.0 |
| OkHttp Logging    | com.squareup.okhttp3:logging-interceptor | 4.12.0   | HTTP 请求日志         | Apache-2.0 |
| Gson              | com.google.code.gson                     | 2.10.1   | JSON 序列化/反序列化     | Apache-2.0 |
| jnativehook       | com.github.kwhat                         | 2.2.2    | 全局键盘钩子 (热键)       | LGPL-3.0   |
| SQLite JDBC       | org.xerial                               | 3.45.1.0 | 本地嵌入式数据库          | Apache-2.0 |
| JUnit Jupiter     | org.junit.jupiter                        | 5.10.2   | 测试框架              | EPL-2.0    |

***

### 4.6 Android Client (Android IME)

| 属性                 | 值                                          |
| ------------------ | ------------------------------------------ |
| 包名                 | `com.heecomou.ime`                         |
| minSdk / targetSdk | 26 (Android 8.0) / 34                      |
| 语言                 | Kotlin 1.9.22                              |
| 构建                 | Gradle 8.2.0 + Android Gradle Plugin 8.2.0 |

**源码文件清单 (25 个)：**

| 包          | 文件数 | 核心功能                                                               |
| ---------- | --- | ------------------------------------------------------------------ |
| `asr/`     | 9   | ASR 引擎路由 / 云端客户端 / 本地 ONNX 推理 / 噪声检测 / 方言 / 平滑切换                   |
| `audio/`   | 2   | 音频采集管理 / 客户端 VAD                                                   |
| `network/` | 6   | Retrofit API (Auth / User / Vocab) / TokenManager / CloudAsrClient |
| `model/`   | 3   | 通用模型 / 词汇模型 / 纠错反馈模型                                               |
| `ui/`      | 4   | 启动页 / 登录页 / 语音输入面板 / 声波纹视图                                         |
| 根包         | 1   | `HeecoMouIME.kt` 输入法服务入口                                           |

**第三方依赖：**

| 依赖                      | GroupId/ArtifactId                       | 版本     | 用途             | 许可         |
| ----------------------- | ---------------------------------------- | ------ | -------------- | ---------- |
| AndroidX Core KTX       | androidx.core:core-ktx                   | 1.12.0 | Android 核心扩展   | Apache-2.0 |
| AndroidX AppCompat      | androidx.appcompat:appcompat             | 1.6.1  | Android 兼容库    | Apache-2.0 |
| Material Design         | com.google.android.material              | 1.11.0 | Material UI 组件 | Apache-2.0 |
| ConstraintLayout        | androidx.constraintlayout                | 2.1.4  | 约束布局           | Apache-2.0 |
| Retrofit                | com.squareup.retrofit2                   | 2.9.0  | HTTP 客户端       | Apache-2.0 |
| Retrofit Gson Converter | com.squareup.retrofit2:converter-gson    | 2.9.0  | JSON 转换器       | Apache-2.0 |
| OkHttp                  | com.squareup.okhttp3                     | 4.12.0 | HTTP 引擎        | Apache-2.0 |
| OkHttp Logging          | com.squareup.okhttp3:logging-interceptor | 4.12.0 | 网络日志           | Apache-2.0 |
| Gson                    | com.google.code.gson                     | 2.10.1 | JSON 解析        | Apache-2.0 |
| Kotlin Coroutines       | org.jetbrains.kotlinx                    | 1.7.3  | 异步协程           | Apache-2.0 |
| Lifecycle KTX           | androidx.lifecycle:lifecycle-runtime-ktx | 2.7.0  | 生命周期管理         | Apache-2.0 |
| ONNX Runtime Android    | com.microsoft.onnxruntime                | 1.17.3 | 端侧 AI 推理引擎     | MIT        |
| JUnit                   | junit:junit                              | 4.13.2 | 单元测试           | EPL-1.0    |
| Mockito                 | org.mockito                              | 5.8.0  | Mock 测试框架      | MIT        |
| Mockito Kotlin          | org.mockito.kotlin                       | 5.2.1  | Kotlin Mock 扩展 | MIT        |

***


## 5. 多技术栈选型理由

HeecoMou 采用了 **Go + Java + Kotlin + Python + TypeScript** 五种编程语言组合开发，这不是随意堆砌，而是基于各技术栈的核心优势、擅长领域，进行**优势互补、各司其职**的工程决策。

### 5.1 选型全景

| 技术栈 | 组件 | 擅长领域 | 在本项目承担的角色 |
|--------|------|---------|------------------|
| **Go** | Gateway | 高并发网络 I/O、低内存开销、单二进制部署 | WebSocket 实时音频流网关，处理数千路并发连接 |
| **Java (Spring)** | Backend | 企业级事务管理、安全框架成熟度、ORM 生态 | 业务逻辑核心：认证鉴权、词库 CRUD、事务一致性 |
| **Python (PyTorch)** | ASR Engine | AI/ML 生态统治力，HuggingFace 模型库 | 语音识别推理引擎，NLP 后处理 |
| **TypeScript (React)** | Frontend Web | 组件化 UI、类型安全、开发效率 | Web 管理后台，面向运营和管理的交互界面 |
| **Kotlin (Compose)** | Desktop Client | JVM 跨平台、声明式 UI、系统级 API 调用 | Windows 桌面客户端，快捷键体系、音频设备操控 |
| **Kotlin (Android)** | Android IME | Android 原生支持、ONNX Runtime 移动端推理 | Android 输入法，端侧 AI 推理 |

### 5.2 逐技术栈深入分析

#### Go — 语音网关（高并发网关层）

**选用理由：**

- **天然适合 I/O 密集型高并发**：Goroutine 轻量协程（每个仅 2KB 栈空间），数千路 WebSocket 并发音频流仅需几十 MB 内存。传统 Java 线程模型（默认 1MB/线程）在同等并发量下资源消耗悬殊
- **编译为单一静态二进制**：无运行时依赖，scp 拷贝即部署，运维极简。Python 需要安装整个解释器和依赖包
- **标准库网络能力强大**：`net/http` 原生支持 WebSocket 升级，无需引入重量级框架
- **显式错误处理**：音频流处理对稳定性要求极高，Go 的 `if err != nil` 模式迫使开发者关注每一处可能的失败点

**解决的问题：** 低延迟、高吞吐的实时音频流转发，连接数上千时仍保持毫秒级响应。

#### Java (Spring Boot) — 业务后端（企业级业务层）

**选用理由：**

- **Spring Security 认证体系**：开箱即用的 JWT 过滤器链、Method Security、BCrypt 密码编码器，是业界最成熟的认证鉴权框架
- **MyBatis-Plus ORM 与事务管理**：复杂业务逻辑（词库增量同步、纠错记录关联）需要声明式事务保证数据一致性（`@Transactional`）
- **Spring AOP 横切关注点**：`@RateLimit` 限流注解、日志切面等非业务功能零侵入织入
- **Flyway 数据库迁移**：版本化管理 DDL，多环境部署时自动保持表结构一致
- **Swagger/OpenAPI 自动文档生成**：Controller 注解一键生成交互式 API 文档

**解决的问题：** 复杂业务规则的事务一致性、安全认证的标准实现、接口文档自动化。

#### Python (FastAPI + PyTorch) — 语音识别引擎（AI 层）

**选用理由：**

- **AI/ML 生态绝对统治力**：PyTorch、Transformers、HuggingFace 模型库全部以 Python 为第一语言。Whisper 模型的加载、推理、微调全部基于 Python 生态
- **HuggingFace 模型仓库**：一行代码加载 `openai/whisper-small`，无需模型格式转换
- **NLP 工具链丰富**：`pypinyin`（汉字转拼音）、`zhconv`（简繁转换）等中文 NLP 工具仅 Python 有成熟实现
- **FastAPI 高性能异步**：基于 Starlette + Pydantic，async/await 原生支持，性能媲美 Node.js/Go

**解决的问题：** 深度学习模型推理的唯一可行技术栈。其他语言调用 Whisper 只能通过 Python 子进程或 ONNX 导出，但 ONNX 有模型兼容性损失。

#### TypeScript + React — Web 管理后台（交互层）

**选用理由：**

- **组件化 UI 开发**：React 的函数式组件 + Hooks 模式，页面与状态逻辑清晰分离
- **TypeScript 类型安全**：编译期发现 API 数据结构不匹配，避免运行时 `undefined is not a function`
- **Zustand 轻量状态管理**：相比 Redux 减少 90% 样板代码，适合中型管理后台
- **Tailwind CSS 原子化样式**：无需切换文件写 CSS，直接在 JSX 中构建界面

**解决的问题：** 快速构建管理后台的交互界面，类型系统保证前后端数据结构对齐。

#### Kotlin (Compose Desktop) — 桌面客户端（跨平台层）

**选用理由：**

- **JVM 生态复用**：可与 Java Backend 共享 DTO 定义，减少跨服务数据模型重复定义
- **JetBrains Compose 声明式 UI**：与 Android Jetpack Compose 同源，桌面端和移动端 UI 代码思维模型一致
- **jnativehook 全局键盘钩子**：JVM 可以通过 JNI 调用系统级 API，实现 Ctrl+Shift+V 全局快捷键
- **SQLite 嵌入式数据库**：本地词库缓存无需额外安装数据库，sqlite-jdbc 一行依赖即可

**解决的问题：** 桌面端原生体验（全局热键、系统托盘、音频设备操控），同时保持与 Android 端 UI 框架的一致性。

#### Kotlin (Android) — 移动端输入法（端侧推理层）

**选用理由：**

- **Android InputMethodService**：Kotlin 是 Android 官方推荐语言，IME 框架 API 原生支持
- **ONNX Runtime Android 官方包**：`com.microsoft.onnxruntime:onnxruntime-android` 专为 ARM 移动芯片优化，支持 NPU 加速
- **Retrofit + OkHttp 网络栈**：Android 端普遍使用，社区成熟
- **Kotlin Coroutines**：用同步语法写异步代码，避免回调地狱

**解决的问题：** Android 端侧离线语音识别 + 系统级输入法集成。

### 5.3 技术栈协同优势

```
┌──────────────────────────────────────────────────────────────────────┐
│                        技术栈协同矩阵                                   │
├──────────────┬──────────┬──────────┬──────────┬──────────┬───────────┤
│              │ Go       │ Java     │ Python   │ Kotlin   │ TypeScript│
├──────────────┼──────────┼──────────┼──────────┼──────────┼───────────┤
│ 高并发I/O    │  ★★★★★   │  ★★★    │  ★★     │  ★★     │  ★★       │
│ 事务/安全    │  ★★     │  ★★★★★   │  ★      │  ★★     │  ★★       │
│ AI/ML 推理   │  ★      │  ★      │  ★★★★★   │  ★★★    │  ★        │
│ 跨平台 UI    │  ★      │  ★★     │  ★      │  ★★★★★   │  ★★★★★    │
│ 类型安全     │  ★★★    │  ★★★★   │  ★★     │  ★★★★   │  ★★★★     │
│ 部署简便     │  ★★★★★   │  ★★★    │  ★★     │  ★★★    │  ★★★      │
├──────────────┼──────────┼──────────┼──────────┼──────────┼───────────┤
│ 承担角色     │ 实时网关  │ 业务核心  │ AI 推理  │ 客户端   │ Web 管理  │
└──────────────┴──────────┴──────────┴──────────┴──────────┴───────────┘
```

**核心协同关系：**

1. **Go ↔ Python**：Go 网关负责高并发音频接收，Python 专注模型推理。Go 通过轮询负载均衡将音频分发到多实例 Python ASR，实现计算密集型（AI 推理）与 I/O 密集型（网络连接）的解耦
2. **Java ↔ 所有客户端**：Java Backend 是唯一的数据真相源（Single Source of Truth），所有客户端（Desktop/Android/Web）通过 REST API 与 Java 后端交互，数据模型由 Java 端 DTO 定义
3. **Kotlin Desktop ↔ Kotlin Android**：共享 ASR 路由逻辑和词库同步协议，两端代码可以相互参考实现
4. **Python ASR ↔ Java Backend**：ASR 推理完成后，Python 通过 `VocabInjector` 调用 Java Backend 的词库 API 获取热词，实现推理结果的质量增强

### 5.4 如果单一技术栈会怎样

| 如果全用... | 最大的损失 |
|------------|----------|
| 全 Go | 缺少成熟的企业级认证/事务框架，AI 推理生态基本空白 |
| 全 Java | ASR 推理只能用 ONNX 导出的次优模型，Python NLP 工具链完全不可用 |
| 全 Python | 高并发 WebSocket 网关性能不足（GIL 限制），桌面端全局热键无法实现 |
| 全 Kotlin | AI/ML 生态薄弱，中文 NLP 工具链缺失，高并发网关内存开销大 |
| 全 TypeScript (Node.js) | ASR 推理生态不成熟，桌面端系统级 API 调用困难 |

**结论：多技术栈组合不是过度工程化，而是每层选择该领域最优解后自然形成的结果。** 组件之间通过标准的 HTTP/WebSocket/gRPC 协议解耦，技术栈差异不会形成沟通障碍。


---

## 6. 数据库设计

### 6.1 user 表

```sql
CREATE TABLE user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,    -- 用户名（登录凭证）
    password_hash VARCHAR(255) NOT NULL,           -- BCrypt 加密密码
    nickname      VARCHAR(50),                     -- 昵称
    email         VARCHAR(100),                    -- 邮箱
    phone         VARCHAR(20),                     -- 手机号
    avatar_url    VARCHAR(500),                    -- 头像 URL
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 6.2 vocabulary 表

```sql
CREATE TABLE vocabulary (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT       NOT NULL,               -- 外键 → user.id
    word      VARCHAR(100) NOT NULL,               -- 词汇
    pinyin    VARCHAR(200),                        -- 拼音
    category  VARCHAR(50),                         -- 分类标签
    frequency INT          NOT NULL DEFAULT 1,     -- 使用频率
    version   BIGINT       NOT NULL DEFAULT 1,     -- 版本号（增量同步游标）
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_id (user_id),
    KEY idx_user_id_version (user_id, version),    -- 增量同步复合索引
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 6.3 correction\_history 表

```sql
CREATE TABLE correction_history (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,          -- 外键 → user.id
    original_text  TEXT         NOT NULL,          -- ASR 原始识别文本
    corrected_text TEXT         NOT NULL,          -- 用户纠正后文本
    source         VARCHAR(50)  NOT NULL DEFAULT 'manual',  -- 来源: desktop / android / web
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

> 数据库迁移由 Flyway 自动管理，迁移脚本位于 `backend-java/src/main/resources/db/migration/`，按版本号递增执行。

***

## 7. API 接口清单

所有 API 路径统一以 `/api/v1` 为前缀，JWT Bearer Token 鉴权。

### 7.1 健康检查

| 方法  | 路径               | 鉴权 | 说明                 |
| --- | ---------------- | -- | ------------------ |
| GET | `/api/v1/health` | 无需 | 后端 + DB + Redis 状态 |

### 7.2 认证模块 (Auth)

| 方法   | 路径                      | 鉴权 | 限流      | 说明             |
| ---- | ----------------------- | -- | ------- | -------------- |
| POST | `/api/v1/auth/register` | 无需 | 10次/60s | 用户注册（返回 JWT）   |
| POST | `/api/v1/auth/login`    | 无需 | 10次/60s | 用户登录（返回 JWT）   |
| POST | `/api/v1/auth/refresh`  | 需  | 无       | 刷新 Token       |
| POST | `/api/v1/auth/logout`   | 需  | 无       | 登出（Token 入黑名单） |

### 7.3 用户模块 (User)

| 方法  | 路径                      | 鉴权 | 说明       |
| --- | ----------------------- | -- | -------- |
| GET | `/api/v1/user/me`       | 需  | 获取当前用户信息 |
| PUT | `/api/v1/user/profile`  | 需  | 更新个人资料   |
| PUT | `/api/v1/user/password` | 需  | 修改密码     |

### 7.4 词库模块 (Vocabulary)

| 方法     | 路径                          | 鉴权 | 限流      | 说明                          |
| ------ | --------------------------- | -- | ------- | --------------------------- |
| GET    | `/api/v1/vocabulary`        | 需  | 无       | 分页列表 (page, size)           |
| GET    | `/api/v1/vocabulary/search` | 需  | 无       | 关键词搜索 (keyword, page, size) |
| GET    | `/api/v1/vocabulary/{id}`   | 需  | 无       | 词汇详情                        |
| POST   | `/api/v1/vocabulary`        | 需  | 30次/60s | 添加词汇                        |
| PUT    | `/api/v1/vocabulary/{id}`   | 需  | 无       | 更新词汇                        |
| DELETE | `/api/v1/vocabulary/{id}`   | 需  | 无       | 删除词汇                        |
| POST   | `/api/v1/vocabulary/sync`   | 需  | 无       | 增量同步 (version, limit)       |

### 7.5 纠错模块 (Correction)

| 方法   | 路径                    | 鉴权 | 说明     |
| ---- | --------------------- | -- | ------ |
| GET  | `/api/v1/corrections` | 需  | 纠错历史列表 |
| POST | `/api/v1/corrections` | 需  | 提交纠错记录 |

### 7.6 网关 API (非业务接口)

| 方法        | 路径              | 端口   | 说明         |
| --------- | --------------- | ---- | ---------- |
| GET       | `/health`       | 8080 | 网关健康检查     |
| WebSocket | `/ws/audio`     | 8080 | 实时音频流上传    |
| POST      | `/api/v1/route` | 8080 | ASR 引擎路由决策 |

***

## 8. 原创功能与第三方库对照

### 8.1 第三方库 / 框架（非原创）

| 库 / 框架                   | 所在组件                            | 用途                 |
| ------------------------ | ------------------------------- | ------------------ |
| Spring Boot 3.2.5        | backend-java                    | Java Web 应用框架      |
| Spring Security          | backend-java                    | 认证与鉴权基础框架          |
| MyBatis-Plus 3.5.7       | backend-java                    | ORM 数据库映射          |
| jjwt 0.12.5              | backend-java                    | JWT Token 签发与签名验证  |
| Flyway                   | backend-java                    | 数据库版本迁移管理          |
| SpringDoc OpenAPI 2.5.0  | backend-java                    | Swagger UI 接口文档生成  |
| Redis (Lettuce)          | backend-java                    | 缓存与 Token 黑名单      |
| FastAPI 0.110.3          | asr-python                      | Python HTTP API 框架 |
| PyTorch                  | asr-python                      | 深度学习推理框架           |
| HuggingFace Transformers | asr-python                      | Whisper 模型加载与推理    |
| ONNX Runtime 1.17.3      | asr-python / client-android     | 端侧模型推理加速           |
| gorilla/websocket v1.5.3 | gateway-go                      | WebSocket 协议       |
| React 18 + TypeScript    | frontend-web                    | 前端 UI 框架           |
| Vite 6.3                 | frontend-web                    | 前端构建工具             |
| Tailwind CSS 3.4         | frontend-web                    | 原子化 CSS 框架         |
| Zustand 5.0              | frontend-web                    | 前端状态管理             |
| Axios 1.7                | frontend-web                    | HTTP 客户端           |
| React Router v7          | frontend-web                    | 前端路由               |
| JetBrains Compose 1.6.0  | client-desktop                  | Desktop 跨平台 UI 框架  |
| OkHttp 4.12.0            | client-desktop / client-android | HTTP 网络请求          |
| Gson 2.10.1              | client-desktop / client-android | JSON 序列化           |
| jnativehook 2.2.2        | client-desktop                  | 全局键盘钩子 (热键)        |
| SQLite JDBC 3.45         | client-desktop                  | 本地嵌入式数据库           |
| Retrofit 2.9.0           | client-android                  | Android HTTP 客户端   |
| Kotlin Coroutines 1.7.3  | client-android                  | 异步协程框架             |
| AndroidX                 | client-android                  | Android 官方支持库      |

### 8.2 原创功能模块

| 模块                     | 所在组件                           | 原创内容                                                                                |
| ---------------------- | ------------------------------ | ----------------------------------------------------------------------------------- |
| **ASR 引擎路由**           | gateway-go, Desktop, Android   | 基于设备上下文 (电量/网络/噪音/隐私) 的五级优先级路由规则引擎，自主决策云端 vs 端侧推理                                   |
| **VAD 语音活动检测**         | gateway-go                     | 基于短时能量和过零率的双阈值端点检测，配合 FFT 频谱分析的客户端/服务端协同 VAD                                        |
| **Wiener 维纳滤波降噪**      | gateway-go                     | 自实现的频域维纳滤波算法，估计噪声功率谱进行自适应降噪                                                         |
| **VocabInjector 词汇注入** | asr-python                     | 用户词库最长匹配替换 + 常见中文 ASR 错别字纠正表，将语音识别文本进行后处理纠错 (原创纠错规则表)                               |
| **增量同步引擎**             | backend-java, Desktop, Android | 基于 `version` 字段的游标式增量同步协议，客户端维护 `maxVersion`，服务端按 `version ASC` 分页返回增量数据            |
| **Token 生命周期管理**       | backend-java                   | JWT accessToken + refreshToken 双令牌体系，登出时将 Token 写入 Redis 黑名单，refreshToken 支持单个/全部吊销 |
| **RateLimit 令牌桶限流**    | backend-java                   | 基于 Redis Lua 脚本的令牌桶限流切面，通过 `@RateLimit` 注解按 IP+方法 限流                                |
| **词库同步管理器**            | client-desktop                 | 桌面端自动启动同步 + 每 120 秒周期同步，SQLite 本地高速缓存，支持离线读写                                        |
| **语音输入浮动窗口**           | client-desktop                 | Compose Desktop 实现的独立悬浮窗口，支持语音输入状态可视化、实时识别结果展示、文本编辑 + 纠错提交                          |
| **纠错反馈闭环**             | Desktop + Frontend + Backend   | 用户编辑 ASR 识别结果 → 提交原始/纠正文本 → 存储到 `correction_history` → 后续可回馈词库提升准确率                 |
| **全局热键系统**             | client-desktop                 | 基于 jnativehook 的 Ctrl+Shift+V 全局快捷键触发语音输入，Esc 取消                                    |
| **Android IME**        | client-android                 | 基于 Android InputMethodService 的自定义语音输入法，集成 ONNX 端侧推理                                |
| **数据库迁移脚本**            | backend-java                   | 4 个 Flyway 版本迁移脚本 (V1\~V4)，从零构建完整的 user / vocabulary / correction\_history 表结构      |
| **API 离线文档页**          | frontend-web                   | 手写的 `docs.html`，独立于 Swagger 的离线 API 文档页面                                            |

***

## 9. 核心功能说明

### 9.1 用户认证与授权 (JWT + Security)

- 用户注册：BCryptPasswordEncoder 加密密码存储 → 返回 accessToken + refreshToken
- 用户登录：验证密码 → 生成双 Token → Redis 记录 refreshToken
- Token 刷新：用 refreshToken 换新 accessToken → 吊销旧 refreshToken → 签发新 refreshToken
- 登出：accessToken 加入 Redis 黑名单 → 吊销所有 refreshToken
- 认证过滤器：`JwtAuthenticationFilter` 拦截所有请求，校验 Bearer Token 的有效性和黑名单状态

### 9.2 词库增量同步协议

```
客户端                           服务端
  │                                │
  ├── POST /sync {version:0,limit:500} ──→
  │                                ├── SELECT WHERE version > 0 ORDER BY version ASC LIMIT 501
  │                                ├── hasMore = (results.size() > limit)
  │                                └── return {items, hasMore, maxVersion}
  ←── {items:[...], hasMore:true, maxVersion:V500}
  │
  ├── POST /sync {version:V500,limit:500} ──→
  ←── {items:[...], hasMore:false}
  │
  └── 本地 SQLite INSERT OR REPLACE 写入
```

### 9.3 纠错反馈闭环

```
语音识别 → 原始文本展示
         ↓ 用户编辑修改
        点击「提交纠错」
         ↓ HTTP POST /api/v1/corrections
        服务端存储 {original_text, corrected_text, source}
         ↓ (后续)
        纠错数据聚合 → 更新词库优先级 → 反馈到 ASR 词库提升准确率
```

### 9.4 接口限流机制

- 实现方式：Spring AOP + `@RateLimit` 注解 + Redis Lua 脚本
- 算法：令牌桶 (Token Bucket)，Redis 原子性保证
- 限流 key：`rate_limit:<IP>:<ClassName>:<MethodName>`
- 超限响应：`429 Too Many Requests`
- 当前生效接口：注册 (10次/60s)、登录 (10次/60s)、添加词汇 (30次/60s)

***

## 10. 部署方式

### 10.1 基础设施

```bash
# 启动 MySQL + Redis
docker compose -f docker-compose.yml up -d
```

| 服务        | 容器名            | 端口              | 账号                 |
| --------- | -------------- | --------------- | ------------------ |
| MySQL 8.0 | heecomou-mysql | 3306 (映射 13306) | root / heecomou123 |
| Redis 7   | heecomou-redis | 6379            | (无密码)              |

### 10.2 部署辅助脚本 (`scripts/`)

| 脚本                     | 说明                                 |
| ---------------------- | ---------------------------------- |
| `deploy-server.sh`     | 云服务器全量部署 (backend + gateway + asr) |
| `deploy.sh`            | 通用部署脚本                             |
| `build-for-cloud.sh`   | 云环境构建脚本                            |
| `manage.sh`            | 服务管理 (启动/停止/重启)                    |
| `api-test.sh`          | API 回归测试                           |
| `e2e-test.sh`          | 端到端全链路测试                           |
| `dual-platform-e2e.sh` | Desktop + Android 双平台 E2E 测试       |

### 10.3 环境变量

| 变量                 | 作用              | 默认值                     |
| ------------------ | --------------- | ----------------------- |
| `GATEWAY_PORT`     | Go 网关监听端口       | `8080`                  |
| `AUDIO_OUTPUT_DIR` | 音频文件存储目录        | `./audio_sessions`      |
| `ASR_SERVERS`      | ASR 服务地址 (逗号分隔) | `http://localhost:8082` |
| `ASR_MODEL_ID`     | Whisper 模型 ID   | `openai/whisper-small`  |
| `ASR_SKIP_LOAD`    | 跳过启动时模型预加载      | `false`                 |
| `BACKEND_URL`      | ASR 调用后端的地址     | `http://localhost:8081` |

***

## 11. 构建与运行

### 11.1 前置环境

| 环境      | 最低版本                 |
| ------- | -------------------- |
| JDK     | 17 (Temurin/OpenJDK) |
| Go      | 1.22                 |
| Python  | 3.10+                |
| Node.js | 18+                  |
| Maven   | 3.8+                 |
| Gradle  | 8.5+ (wrapper 自带)    |
| Docker  | 20+ (仅基础设施)          |

### 11.2 构建命令

```bash
# === Backend (Java) ===
cd backend-java
mvn clean package -DskipTests
# 产出: target/heecomou-backend-0.1.0-SNAPSHOT.jar

# === Gateway (Go) ===
cd gateway-go
go build -o gateway ./cmd/gateway/

# === ASR (Python) ===
cd asr-python
pip install -r requirements.txt
# 启动: python -m src.main (监听 :8082)

# === Frontend (Web) ===
cd frontend-web
npm install
npm run build          # 生产构建
npm run dev            # 开发服务器 (监听 :5173)

# === Desktop Client ===
cd client-desktop
./gradlew run          # 开发运行
./gradlew packageMsi   # 打包 MSI
./gradlew packageExe   # 打包 EXE

# === Android Client ===
cd client-android
./gradlew assembleDebug   # Debug APK
```

### 11.3 运行测试

```bash
# Backend
cd backend-java && mvn test

# Gateway
cd gateway-go && go test ./...

# ASR
cd asr-python && python -m pytest

# Frontend
cd frontend-web && npm run check

# Desktop
cd client-desktop && ./gradlew test

# Android
cd client-android && ./gradlew test
```




***

## 12. Demo 视频

HeecoMou 功能演示视频：[《HeecoMou Demo 视频》](https://www.yuque.com/u49119058/azmmfs/xyftrcv2gueqwf8o?singleDoc#)

> 点击上方链接查看 HeecoMou 产品的完整功能演示，包含语音输入、词库管理、纠错反馈等核心流程。

***

> 本文档由 HeecoMou 开发团队维护，随项目迭代持续更新。


***
