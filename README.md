# Lumina

**Lumina** 是一个基于 Kotlin Multiplatform + Compose Multiplatform 的 **3D 粒子效果库**，支持 Android、iOS、Web、Desktop (JVM) 多平台。

## ✨ 功能特性

- 🎇 **3D 粒子渲染** — 基于 Compose Canvas 实现高性能 3D 粒子系统，支持深度感知、透视投影与粒子生命周期管理
- 👆 **跟手交互** — 粒子效果实时响应触摸/鼠标输入，支持拖拽、滑动、多点触控等手势驱动粒子运动
- 🎬 **视频行为反馈** — 解析视频帧的运动信息（光流/像素差分），将视频中的动态区域映射为粒子爆发、扩散等视觉反馈效果
- 🌐 **跨平台** — 单一代码库，覆盖 Android / iOS / Web (JS & Wasm) / Desktop (JVM)

## 📦 模块结构

```
Lumina/
├── lumina/          # 核心库模块（发布为 KMP 库）
│   ├── commonMain   # 跨平台粒子系统核心逻辑
│   ├── androidMain  # Android 平台适配（触摸事件、Camera/视频解码）
│   ├── iosMain      # iOS 平台适配
│   ├── jvmMain      # Desktop 平台适配
│   ├── jsMain       # JS Web 平台适配
│   └── wasmJsMain   # Wasm Web 平台适配
└── composeApp/      # 演示应用（展示各平台粒子效果）
```