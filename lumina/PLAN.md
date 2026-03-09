# Lumina — Compose 实现计划

> 本文档描述如何在 `lumina` 模块中使用 **Compose Multiplatform** 实现 3D 粒子效果库，
> 包含架构设计、模块分层、分阶段任务和关键 API 草案。

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    调用方（composeApp）                   │
│   LuminaParticleView(config, inputSource, modifier)      │
└───────────────────────┬─────────────────────────────────┘
                        │ Composable API
┌───────────────────────▼─────────────────────────────────┐
│                  commonMain（核心层）                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ ParticleEngine│  │ InputSource  │  │ RenderPipeline│  │
│  │  粒子物理模拟  │  │  输入抽象层  │  │  Canvas 渲染  │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │ expect/actual
        ┌───────────────┼───────────────┐
   androidMain      iosMain          jvmMain / jsMain / wasmJsMain
  触摸事件适配     UITouch 适配      鼠标/触控板适配
  视频帧解码       AVFoundation      HTML5 Video / AWT
```

---

## 二、模块分层（commonMain 包结构）

```
com.hrm.lumina/
├── core/
│   ├── Particle.kt              # 粒子数据类（位置、速度、颜色、生命周期）
│   ├── ParticlePool.kt          # 对象池，避免频繁 GC
│   ├── ParticleEngine.kt        # 粒子物理更新（每帧 tick）
│   └── ParticleConfig.kt        # 粒子系统配置（数量、大小、颜色、力场）
│
├── projection/
│   ├── Camera3D.kt              # 透视相机（fov、near、far、position）
│   └── Projection.kt            # 3D → 2D 透视投影工具函数
│
├── input/
│   ├── InputSource.kt           # sealed interface：手势 / 视频 / 自动
│   ├── GestureInputSource.kt    # 手势输入（PointerInputScope 封装）
│   └── VideoInputSource.kt      # 视频帧输入（expect/actual 跨平台）
│
├── render/
│   ├── ParticleRenderer.kt      # DrawScope 扩展，负责将粒子投影并绘制
│   └── BlendMode.kt             # 粒子混合模式枚举（Add / Screen / Normal）
│
└── ui/
    ├── LuminaParticleView.kt    # 对外暴露的顶层 Composable
    └── LuminaState.kt           # remember 状态持有，驱动重组
```

---

## 三、分阶段实施计划

### Phase 1 — 基础粒子渲染（里程碑：能在 Canvas 上看到运动的粒子）

| 任务 | 文件 | 说明 |
|------|------|------|
| 定义粒子数据结构 | `core/Particle.kt` | `data class Particle(x,y,z, vx,vy,vz, color, life, maxLife, size)` |
| 实现对象池 | `core/ParticlePool.kt` | 预分配固定数量粒子，复用避免 GC |
| 实现粒子引擎 | `core/ParticleEngine.kt` | `fun tick(deltaMs: Float)` 更新位置、衰减生命值 |
| 透视投影 | `projection/Projection.kt` | `fun project(x,y,z, camera): Offset` |
| Canvas 渲染 | `render/ParticleRenderer.kt` | `DrawScope.drawParticles(particles, camera)` |
| 顶层 Composable | `ui/LuminaParticleView.kt` | `Canvas` + `LaunchedEffect` 驱动帧循环 |

**帧循环方案（commonMain）：**

```kotlin
// LuminaParticleView.kt
@Composable
fun LuminaParticleView(
    config: ParticleConfig,
    modifier: Modifier = Modifier
) {
    val state = rememberLuminaState(config)

    // 使用 withFrameMillis 驱动每帧更新
    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            withFrameMillis { frameTime ->
                val delta = if (lastTime == 0L) 16f else (frameTime - lastTime).toFloat()
                lastTime = frameTime
                state.engine.tick(delta)
            }
        }
    }

    Canvas(modifier = modifier) {
        drawParticles(state.engine.particles, state.camera)
    }
}
```

---

### Phase 2 — 跟手交互（里程碑：手指/鼠标拖动驱动粒子）

| 任务 | 文件 | 说明 |
|------|------|------|
| 定义输入抽象 | `input/InputSource.kt` | `sealed interface InputSource` |
| 手势输入实现 | `input/GestureInputSource.kt` | 包装 `pointerInput`，输出归一化坐标流 |
| 引擎接入输入 | `core/ParticleEngine.kt` | `fun applyForce(x, y, radius, strength)` |
| Composable 接入 | `ui/LuminaParticleView.kt` | `Modifier.pointerInput` 收集手势并传入引擎 |

**InputSource 设计：**

```kotlin
sealed interface InputSource {
    /** 跟手模式：手势/鼠标驱动 */
    data object Gesture : InputSource

    /** 视频模式：视频帧运动向量驱动 */
    data class Video(val provider: VideoFrameProvider) : InputSource

    /** 自动模式：内置动画曲线驱动（无需外部输入） */
    data class Auto(val preset: AnimationPreset = AnimationPreset.Wave) : InputSource
}
```

---

### Phase 3 — 视频行为反馈（里程碑：视频动态区域触发粒子爆发）

| 任务 | 文件 | 说明 |
|------|------|------|
| 定义帧提供者接口 | `input/VideoFrameProvider.kt` | `expect interface VideoFrameProvider` |
| Android 实现 | `androidMain/.../VideoFrameProvider.kt` | `MediaMetadataRetriever` / `MediaPlayer` 逐帧解码 |
| iOS 实现 | `iosMain/.../VideoFrameProvider.kt` | `AVAssetImageGenerator` |
| JVM 实现 | `jvmMain/.../VideoFrameProvider.kt` | FFMPEG-KMP 或 AWT `BufferedImage` |
| 运动检测算法 | `input/MotionDetector.kt` | 像素差分法，输出热力图（归一化 0~1 二维数组） |
| 引擎接入 | `core/ParticleEngine.kt` | `fun applyMotionMap(map: Array<FloatArray>)` |

**运动检测流程：**

```
视频帧 N-1  ──┐
              ├──► 像素差分 ──► 高斯模糊 ──► 归一化热力图 ──► applyMotionMap()
视频帧 N    ──┘
```

---

### Phase 4 — 配置与扩展性（里程碑：对外 API 稳定，可发布 0.1.0）

| 任务 | 说明 |
|------|------|
| `ParticleConfig` DSL | `luminaConfig { count = 500; color = ... }` 构建器风格 |
| 预设效果 | `Preset.Firework / Preset.Snow / Preset.Galaxy` |
| 性能分级 | 根据设备性能自动降级粒子数量 |
| 单元测试 | `commonTest` 覆盖 `ParticleEngine.tick()` 和 `Projection` |
| 发布配置 | `mavenPublish` 插件已就绪，补全 POM 信息 |

---

## 四、关键技术决策

| 问题 | 决策 | 原因 |
|------|------|------|
| 帧循环驱动 | `withFrameMillis`（Compose 内置） | 与 Compose 重组时钟同步，无需额外线程 |
| 粒子存储 | `FloatArray` 替代 `List<Particle>` | 减少对象分配，提升 GC 友好性 |
| 3D 投影 | 纯软件透视投影（不依赖 OpenGL） | 保持跨平台一致性，Canvas 即可实现 |
| 视频解码 | `expect/actual` 各平台独立实现 | 各平台视频 API 差异大，无法统一 |
| 状态管理 | `remember` + `mutableStateOf` | 最小化重组范围，仅 Canvas 重绘 |

---

## 五、依赖补充（lumina/build.gradle.kts）

Phase 1~2 只需在 `commonMain` 中添加：

```kotlin
commonMain {
    dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)  // Canvas、pointerInput
        implementation(libs.compose.ui)
    }
}
```

Phase 3 视频解码在各平台 sourceSet 中按需添加平台特定依赖。

---

## 六、里程碑时间线（参考）

```
Phase 1  ████████░░░░░░░░░░░░  基础粒子渲染
Phase 2  ░░░░░░░░████████░░░░  跟手交互
Phase 3  ░░░░░░░░░░░░░░░░████  视频行为反馈
Phase 4  ░░░░░░░░░░░░░░░░░░██  配置 & 发布
```
