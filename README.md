# Animated Textures Mod / 动画纹理模组

> **Minecraft 1.21 | Fabric | Client-Side**
>
> 在资源包中使用 GIF 和 APNG 文件为方块、物品、药水效果图标添加逐帧动画。
>
> Use GIF and APNG files in resource packs to add frame-by-frame animation to blocks, items, and mob effect icons.

---

> **🤖 AI 辅助开发声明 / AI-Assisted Development Disclosure**
>
> 本项目的代码实现、架构设计及文档均通过 **Claude Code** 与 **MiMo** 模型辅助完成。核心创意、需求定义和功能决策由人类开发者完成，AI 负责代码编写、调试优化和技术文档撰写。
>
> The codebase, architecture, and documentation of this project were developed with the assistance of **Claude Code** and the **MiMo** model. Core ideas, requirements, and feature decisions were made by the human developer; AI handled code writing, debugging, optimization, and technical documentation.

---

## 目录 / Table of Contents

- [功能概述 / Features](#功能概述--features)
- [原理详解 / How It Works](#原理详解--how-it-works)
- [项目结构 / Project Structure](#项目结构--project-structure)
- [快速上手 / Quick Start](#快速上手--quick-start)
- [资源包制作指南 / Resource Pack Guide](#资源包制作指南--resource-pack-guide)
- [配置说明 / Configuration](#配置说明--configuration)
- [构建与开发 / Build & Development](#构建与开发--build--development)
- [兼容性 / Compatibility](#兼容性--compatibility)
- [常见问题 / FAQ](#常见问题--faq)
- [许可证 / License](#许可证--license)
- [致谢 / Credits](#致谢--credits)

---

## 功能概述 / Features

| 功能 Feature | 中文说明 | English Description |
|-------------|----------|---------------------|
| **GIF 支持** | 支持标准 GIF89a 格式动画，含多帧、透明度、帧间延迟 | Full GIF89a support with multi-frame, transparency, inter-frame delay |
| **APNG 支持** | 支持 `.png3` 扩展名的 APNG（动画 PNG）格式 | APNG (Animated PNG) via `.png3` extension |
| **可调画质** | 标准、高帧率、高分辨率和高质量四档，最高 60 FPS / 4K | Standard, high-FPS, high-resolution, and high-quality modes up to 60 FPS / 4K |
| **多图集支持** | 方块、物品、药水效果图标均可使用动画纹理 | Works with block, item, and mob effect atlases |
| **双线性缩放** | 可选双线性插值上采样，高分辨率资源包更平滑 | Optional bilinear upscaling for smooth high-res textures |
| **Sodium 兼容** | 兼容 Sodium 渲染优化模组 | Compatible with Sodium rendering optimization mod |
| **ModMenu 配置** | 通过 ModMenu 在游戏内调整设置 | In-game settings via ModMenu integration |
| **纯 Java 实现** | GIF/APNG 解码器完全自研，无外部依赖 | Pure Java decoders, zero external dependencies |

---

## 原理详解 / How It Works

### 总体架构 / Architecture Overview

```
资源包 (Resource Pack)
  │  .gif / .png3 文件
  ▼
┌─────────────────────────────────┐
│  AnimatedTextureReloadListener   │  ← 资源重载时扫描并解码
│  (资源重载监听器)                  │     Scan & decode on reload
└──────────┬──────────────────────┘
           │ 解码后的帧列表 / Decoded frame list
           ▼
┌─────────────────────────────────┐
│  AnimatedTextureRegistry         │  ← 中央注册表（按 Identifier 索引）
│  (动画纹理注册表)                  │     Central registry keyed by Identifier
└──────────┬──────────────────────┘
           │
     ┌─────┴─────┐
     ▼           ▼
┌──────────┐ ┌──────────────────────────┐
│ Mixin    │ │ AnimatedTexture           │  ← 每个动画纹理的帧管理器
│ (钩子)    │ │ TickManager               │     Per-animation frame manager
└──────────┘ │ (GPU 上传 / GPU upload)    │
             └──────────────────────────┘
```

### 核心流程 / Core Pipeline

#### 1. 资源发现与解码 / Resource Discovery & Decoding

Minecraft 的资源加载系统是纹理动画的单一数据源。模组在 `textures/` 目录下发现可见的 `.png` 回退文件后，通过 `ResourceManager` 解析同名的 `.gif` 和 `.png3` 资源。

Minecraft's resource loading system is the single source of truth for texture animations. The mod finds visible `.png` fallbacks under `textures/`, then resolves same-name `.gif` and `.png3` resources through `ResourceManager`.

```
Step 1: 查找可见的 textures/*.png 回退文件             → Find visible PNG fallbacks
Step 2: 检查同名的 GIF 和 APNG 资源层                  → Inspect same-name GIF/APNG layers
Step 3: 在解码前选择一个动画格式                        → Select one format before decoding
```

没有可见同名 `.png` 回退的动画会被忽略。资源包过滤器（filters）会被遵循。最高优先级的可见资源包胜出；若同一资源包的同一纹理同时提供两种格式，`.png3` APNG 优先于 `.gif`。若选中的动画文件损坏，则回退到静态 PNG，不会降级加载优先级较低的动画。

Animations without a visible same-name PNG fallback are ignored. Resource pack filters are honored. The highest-priority visible pack wins; if one winning pack supplies both formats, `.png3` APNG takes precedence over `.gif`. A corrupt selected animation falls back to its static PNG rather than loading a lower-priority animation.

解码器限制在分配像素缓冲区之前强制执行。标准和高级帧率模式允许：编码输入 16 MiB、单边 2,048 像素、每帧/画布 4,194,304 像素、256 帧、每个动画 16,777,216 个缓存解码像素。高分辨率和高画质模式允许：编码输入 32 MiB、单边 4,096 像素、每帧/画布 16,777,216 像素、每个动画 33,554,432 个缓存像素。

Decoder limits are enforced before pixel buffers are allocated. Standard and high-frame-rate modes allow 16 MiB encoded input, 2,048 pixels per side, 4,194,304 pixels per frame/canvas, 256 frames, and 16,777,216 retained decoded pixels per animation. High-resolution and high-quality modes allow 32 MiB input, 4,096 pixels per side, 16,777,216 pixels per frame/canvas, 256 frames, and 33,554,432 retained pixels.

#### 2. GIF 解码 / GIF Decoding (`GifDecoder`)

纯 Java 实现的 GIF89a 解码器，支持：

Pure Java GIF89a decoder supporting:

- **LZW 压缩 / LZW Compression**：完整的变长编码解码，含安全计数器防止恶意文件死循环 — Full variable-length-code decoding with a safety counter against malicious infinite loops
- **局部/全局色表 / Local & Global Color Tables**：每帧可使用独立色表或全局色表 — Per-frame local or global palette
- **透明度 / Transparency**：支持透明色索引 — Transparent color index support
- **帧处置模式 / Disposal Modes**：`DISPOSE_OP_BACKGROUND`（清除区域）和 `DISPOSE_OP_PREVIOUS`（恢复上一帧）
- **隔行扫描 / Interlacing**：支持 GIF 的 interlace 扫描模式
- **大小限制 / Size Limit**：最大 16 MiB，防止 OOM — Capped at 16 MiB to prevent OOM

```
GIF 文件 → 读取头部 → 解析扩展块 → LZW 解码像素 → 合成帧 → AnimatedFrame 列表
GIF file → Read header → Parse extension blocks → LZW decode pixels → Compose frames → AnimatedFrame list
```

#### 3. APNG 解码 / APNG Decoding (`ApngDecoder`)

APNG 是 PNG 的动画扩展，帧数据存储在 `fcTL`（帧控制）和 `fdAT`（帧数据）块中。普通 PNG 阅读器会忽略这些块。

APNG is a PNG animation extension. Frame data lives in `fcTL` (frame control) and `fdAT` (frame data) chunks that regular PNG readers ignore.

```
APNG 文件 → 验证 PNG 签名 → 解析 IHDR/acTL/fcTL/fdAT 块
  → 为每帧重建独立 PNG → ImageIO 解码 → 按 blendOp 合成到画布
  → 按 disposeOp 处理画布 → AnimatedFrame 列表

APNG file → Validate PNG signature → Parse IHDR/acTL/fcTL/fdAT chunks
  → Reconstruct per-frame standalone PNG → ImageIO decode → Blend to canvas per blendOp
  → Process canvas per disposeOp → AnimatedFrame list
```

支持的合成操作 / Supported blend & dispose operations:
- `blendOp`: `SOURCE`（覆盖 / overwrite）/ `OVER`（Alpha 混合 / alpha composite）
- `disposeOp`: `NONE` / `BACKGROUND`（清除 / clear）/ `PREVIOUS`（恢复 / restore）

#### 4. 图集动画上传 / Atlas Animation Uploads

`SpriteAtlasTextureMixin` 捕获每个图集缝合结果，包括缝合后的精灵映射和 mip 级别。动画管理器将每个匹配的精灵绑定到其实际图集并上传初始帧，之后仅在可见帧变化时重新上传。标准模式以客户端 tick 节奏运行；高帧率模式使用渲染循环，GPU 更新最高 60 Hz。

`SpriteAtlasTextureMixin` captures each atlas stitch result, including the stitched sprite map and mip level. The animation manager binds every matched sprite to its actual atlas and uploads an initial frame, then uploads again only when the visible frame changes. Standard modes run at client-tick cadence; high-frame-rate modes use the render loop with GPU updates capped at 60 Hz.

每一帧都会刷新所有图集 mip 级别。预处理的帧和 mip 链在每次资源重载时缓存在有界预算内；超大动画复用其基础缓冲区。这避免了重复的缩放内存分配，同时保持本地内存有界。

Every generated frame refreshes all atlas mip levels. Prepared frames and mip chains are cached within a bounded per-reload budget when a complete animation fits; oversized animations reuse their base buffer. This avoids repeated scaling allocations while keeping native memory bounded.

#### 5. 帧缩放 / Frame Scaling

动画纹理会缩放到精灵在图集中的分配区域。标准档单边最多 2,048 像素，高分辨率档单边最多 4,096 像素。同名的静态 PNG 必须使用目标显示尺寸，因为它决定了缝合后的精灵区域。

Animated textures are scaled to the sprite's allocated atlas region. Standard modes allow up to 2,048 pixels per side; high-resolution modes allow up to 4,096 pixels. The same-name static PNG must use the intended display size because it determines the stitched sprite region.

| 模式 Mode | 算法 Algorithm | 适用场景 Use Case |
|-----------|---------------|-------------------|
| **Nearest Neighbor** | 最近邻采样 / Nearest-neighbor sampling | 像素风格纹理，速度快 / Pixel-art textures, fast |
| **Bilinear** | 双线性插值（四邻域加权）/ Bilinear interpolation | 高分辨率资源包，效果平滑 / High-res packs, smooth results |

```
原始帧 (srcW × srcH) → 目标尺寸 (spriteW × spriteH)
  Nearest:  dst[x,y] = src[x * srcW/dstW, y * srcH/dstH]
  Bilinear: dst[x,y] = 加权平均 / weighted average of src[x0,y0], src[x1,y0], src[x0,y1], src[x1,y1]
```

---

## 项目结构 / Project Structure

```
src/main/java/com/animatedtextures/
├── client/
│   ├── AnimatedTexturesClient.java          # 客户端入口 & tick 注册
│   │                                         # Client entry point & tick registration
│   ├── AnimatedTexturesConfig.java          # 缩放 & 画质 JSON 配置
│   │                                         # Scaling & quality JSON configuration
│   ├── AnimatedTexturesModMenu.java         # ModMenu 配置界面
│   │                                         # ModMenu configuration screen
│   ├── AnimatedTextureReloadListener.java   # 可见资源发现与解码
│   │                                         # Visible-resource discovery & decoding
│   └── AnimatedResourceResolver.java        # 资源包优先级与格式选择
│                                             # Pack-priority & format selection
├── mixin/
│   ├── GameRendererMixin.java                # 高帧率渲染回调
│   │                                         # High-frame-rate render callback
│   ├── ReloadableResourceManagerMixin.java   # 资源重载生命周期
│   │                                         # Owns overall reload attempts
│   └── SpriteAtlasTextureMixin.java          # 捕获缝合后的图集区域
│                                             # Captures stitched atlas regions
└── util/
    ├── ActiveAnimationGeneration.java        # 每帧动画生成（帧选择 & 缩放）
    │                                         # Per-frame generation (frame selection & scaling)
    ├── AnimatedFrame.java                    # 不可变 ARGB 帧数据
    │                                         # Immutable ARGB frame data
    ├── AnimatedImageLimits.java              # 解码器输入/帧安全限制
    │                                         # Decoder input/frame safety limits
    ├── AnimatedTexture.java                  # 帧计时、缩放和精灵 ID 管理
    │                                         # Frame timing, scaling & sprite IDs
    ├── AnimatedTextureRegistry.java          # 规范化的选中动画注册表
    │                                         # Canonical selected-animation registry
    ├── AnimatedTextureRegistryBuilder.java   # 注册表构建器
    │                                         # Registry builder
    ├── AnimatedTextureRegistrySnapshot.java  # 注册表不可变快照
    │                                         # Immutable registry snapshot
    ├── AnimatedTextureReloadAttempt.java     # 单次资源重载尝试
    │                                         # Single reload attempt
    ├── AnimatedTextureReloadBudget.java      # 解码像素内存预算
    │                                         # Decoded-pixel memory budget
    ├── AnimatedTextureReloadCoordinator.java # 跨 atlas 重载协调器
    │                                         # Cross-atlas reload coordinator
    ├── AnimatedTextureTickManager.java       # 图感知 GPU 上传 & mipmap
    │                                         # Atlas-aware GPU uploads & mipmaps
    ├── AnimationFrameScheduler.java          # 高帧率帧调度器
    │                                         # High-FPS frame scheduler
    ├── AnimationQuality.java                 # 四档有界画质策略
    │                                         # Four bounded quality policies
    ├── ApngDecoder.java                      # 有界严格 APNG 解码器
    │                                         # Bounded strict APNG decoder
    ├── AtlasReloadGeneration.java            # 图集重载代数（生成标识）
    │                                         # Atlas reload generation ID
    ├── DecodedAnimation.java                 # 解码后的动画数据类型
    │                                         # Decoded animation data type
    ├── GifDecoder.java                       # 有界 GIF89a 解码器
    │                                         # Bounded GIF89a decoder
    ├── PreparedFrameCache.java               # 预处理帧 & mip 链缓存
    │                                         # Prepared-frame & mip-chain cache
    └── UploadRetryPolicy.java                # GPU 上传重试策略
                                              # GPU upload retry policy
```

---

## 快速上手 / Quick Start

### 安装 / Installation

1. **前置依赖 / Prerequisites**：
   - Minecraft 1.21
   - Fabric Loader ≥ 0.15.11
   - Fabric API
   - Java 21+

2. **安装模组 / Install the mod**：
   将 `animated-textures-1.2.0.jar` 放入 `.minecraft/mods/` 目录。
   Place `animated-textures-1.2.0.jar` into your `.minecraft/mods/` directory.

### 基本使用 / Basic Usage

1. 创建或打开资源包，在 `assets/<命名空间>/textures/` 目录下放置 `.gif` 或 `.png3` 文件
2. 文件名必须与原版纹理同名（扩展名不同），例如：
   - 替换金矿石 → `textures/block/gold_ore.gif`
   - 替换钻石 → `textures/item/diamond.png3`
   - 替换速度效果图标 → `textures/mob_effect/speed.gif`
3. 启动游戏，动画纹理将自动加载

---

1. Create or open a resource pack, place `.gif` or `.png3` files in `assets/<namespace>/textures/`
2. File names must match vanilla texture names (different extension), e.g.:
   - Replace gold ore → `textures/block/gold_ore.gif`
   - Replace diamond → `textures/item/diamond.png3`
   - Replace speed effect icon → `textures/mob_effect/speed.gif`
3. Launch the game — animated textures load automatically

---

## 资源包制作指南 / Resource Pack Guide

### 目录结构 / Directory Structure

```
my_animated_pack/
├── pack.mcmeta
└── assets/
    └── minecraft/
        └── textures/
            ├── block/
            │   ├── gold_ore.png      # 静态回退 / Static fallback（必须存在 / required）
            │   └── gold_ore.gif      # 动画版本 / Animated version（替换 .png / replaces .png）
            ├── item/
            │   ├── diamond.png       # 静态回退 / Static fallback
            │   └── diamond.png3      # APNG 动画版本 / APNG animated version
            └── mob_effect/
                ├── speed.png         # 静态回退 / Static fallback
                └── speed.png3        # 药水效果图标动画 / Mob effect icon animation
```

> **重要 / Important**：必须同时提供同名的 `.png` 静态文件作为回退。Minecraft 的纹理系统需要 `.png` 文件来注册精灵到图集中；模组随后替换其像素数据为动画帧。
>
> You must provide a same-name `.png` static fallback. Minecraft's texture system needs the `.png` to register the sprite in the atlas; the mod then replaces its pixel data with animation frames.

### 支持的文件格式 / Supported Formats

| 格式 Format | 扩展名 Extension | 特性 Features | 说明 Notes |
|-------------|-----------------|----------------|-------------|
| **GIF89a** | `.gif` | 多帧、透明度、帧延迟、循环 / Multi-frame, transparency, frame delay, looping | 最广泛使用的动画格式 / Most widely used animation format |
| **APNG** | `.png3` | 多帧、Alpha 通道、高色彩深度 / Multi-frame, alpha channel, high color depth | 比 GIF 质量更高，支持半透明 / Higher quality than GIF, supports semi-transparency |

### GIF 制作要点 / GIF Authoring Tips

- 标准档的帧延迟最小 50ms；高帧率和高质量档保留源时间轴并以最高 60 Hz 上传
  Standard modes enforce a minimum 50ms frame delay; high-FPS and high-quality modes preserve the source timeline and upload at up to 60 Hz
- 支持循环动画（NETSCAPE2.0 扩展）/ Looping animations supported (NETSCAPE2.0 extension)
- 支持透明度和帧间处置模式 / Transparency and inter-frame disposal modes supported
- 建议使用 16×16 或与目标精灵相同的分辨率 / Recommended: 16×16 or match the target sprite resolution

### APNG 制作要点 / APNG Authoring Tips

- 使用 `.png3` 扩展名（Minecraft 不索引 `.apng`）
  Use the `.png3` extension (Minecraft does not index `.apng`)
- 支持 `blendOp`（`SOURCE` / `OVER`）和 `disposeOp`（`NONE` / `BACKGROUND` / `PREVIOUS`）
- 适合需要半透明渐变效果的高保真动画
  Ideal for high-fidelity animations with semi-transparent gradients
- 帧延迟公式 / Frame delay formula：`duration = delayNum / delayDen` 秒

### 药水效果图标 / Mob Effect Icons

药水效果图标使用独立的图集（`textures/atlas/mob_effects.png`），尺寸为 **18×18** 像素。模组自动处理 mob_effect 纹理的特殊 ID 映射（bare registry name）。

Mob effect icons use a separate atlas at **18×18** pixels. The mod handles the special ID mapping (bare registry name) automatically.

---

## 配置说明 / Configuration

配置文件位于 `.minecraft/config/animated_textures.json`，也可通过 ModMenu 在游戏内修改。

The config file is at `.minecraft/config/animated_textures.json`. ModMenu edits a draft and writes it only through **Save & Done**. Invalid or missing values fall back to `BILINEAR` and `STANDARD`; legacy keys are ignored. Changing quality performs a resource reload so decoder limits, timing, atlas bindings, and native caches switch atomically.

| 参数 Parameter | 类型 Type | 默认值 Default | 说明 Description |
|---------------|-----------|---------------|------------------|
| `scalingMode` | Enum | `BILINEAR` | `NEAREST` — 保留像素风格的采样 / pixel-preserving sampling<br>`BILINEAR` — 平滑上采样 / smooth upscaling |
| `quality` | Enum | `STANDARD` | `STANDARD` / `HIGH_FRAME_RATE` / `HIGH_RESOLUTION` / `HIGH_QUALITY` |

| 画质 Quality | GPU 更新率 GPU Update Rate | 最大边长 Max Side | 每次重载的解码像素上限 Reload-wide Decoded Pixels |
|--------------|---------------------------|-------------------|--------------------------------------------------|
| `STANDARD` | ~20 Hz | 2,048 px | 16,777,216 (~64 MiB ARGB) |
| `HIGH_FRAME_RATE` | 最高 60 Hz / Up to 60 Hz | 2,048 px | 16,777,216 (~64 MiB ARGB) |
| `HIGH_RESOLUTION` | ~20 Hz | 4,096 px | 33,554,432 (~128 MiB ARGB) |
| `HIGH_QUALITY` | 最高 60 Hz / Up to 60 Hz | 4,096 px | 33,554,432 (~128 MiB ARGB) |

高分辨率动画会占用大量图集空间和 GPU 上传带宽。源帧短于 16.67ms 的帧在高帧率模式下保留原始时间轴，但可能因 60 Hz 上传上限而被跳过。所有图集 mip 级别在动画帧变化时自动更新。

High-resolution animations can consume substantial atlas space and GPU upload bandwidth. Source frames shorter than 16.67 ms keep their original timeline in high-frame-rate modes but may be skipped when sampled at the 60 Hz upload cap. All atlas mip levels are updated automatically whenever an animation frame changes.

```json
{
  "scalingMode": "BILINEAR",
  "quality": "STANDARD"
}
```

---

## 构建与开发 / Build & Development

### 环境要求 / Requirements

- JDK 21+
- Gradle 8.8（已包含 Wrapper / Wrapper included）

### 构建命令 / Build Commands

```bash
# 编译
# Compile
./gradlew build

# 生成并验证示例资源包 ZIP
# Generate and validate the example resource-pack ZIP
./gradlew verifyExampleResourcePack

# 运行解码器和状态回归测试
# Run decoder and state regression tests
./gradlew test

# 在开发环境中启动 Minecraft
# Run Minecraft in the development environment
./gradlew runClient
```

### 输出文件 / Output

- 模组产物 / Mod artifact: `build/libs/animated-textures-1.2.0.jar`
- 可安装示例包 / Installable example pack: `build/libs/animated-textures-example-pack-1.2.0.zip`
- 生成中间目录 / Generated staging directory: `build/generated/example-pack/`

---

## 兼容性 / Compatibility

| 模组/环境 Mod / Environment | 兼容性 Status | 备注 Notes |
|----------------------------|--------------|-------------|
| **Sodium** | ✅ 已验证兼容 / Verified | Minecraft 1.21 客户端已通过 Atlas 动画 & 资源重载验证 |
| **Iris** | ⚠️ 待验证 / Pending | Requires the same atlas regression pass |
| **OptiFine** | ⚠️ 未测试 / Untested | 可能与 Fabric 渲染器假设冲突 / May conflict with Fabric renderer assumptions |
| **ModMenu** | ✅ 已集成 / Integrated | 通过 ModMenu 提供配置界面 / In-game config via ModMenu |
| **服务器 / Server** | ✅ 纯客户端 / Client-only | `environment: "client"`，无需服务端安装 / No server-side install needed |

### Sodium 兼容细节 / Sodium Compatibility Details

- Minecraft 1.21 客户端已确认与 Sodium 兼容，包括动画 Atlas 上传和资源重载
  Sodium compatibility validated on the Minecraft 1.21 client path, including animated atlas uploads and resource reloads
- `SpriteAtlasTextureMixin` 使用 `priority = 1001`，确保在 Sodium 的 `MixinSpriteAtlasTexture`（默认 1000）之后执行
  `SpriteAtlasTextureMixin` uses `priority = 1001`, ensuring it runs after Sodium's `MixinSpriteAtlasTexture` (default 1000)
- 动画纹理通过直接 GPU 上传维护所有 mip 层，不依赖 Sodium 的原版动画可见性跟踪
  Animated textures maintain all mip levels via direct GPU upload, independent of Sodium's vanilla animation visibility tracking

---

## 常见问题 / FAQ

**Q: 为什么需要同名的 `.png` 文件？**
**Q: Why do I need a same-name `.png` file?**

A: Minecraft 的纹理图集系统只识别 `.png` 文件。`.png` 文件用于在图集中注册精灵（sprite），模组随后将精灵的像素数据替换为动画帧。没有 `.png` 文件，精灵不会被注册到图集，动画也就无从注入。

A: Minecraft's texture atlas system only recognizes `.png` files. The `.png` registers the sprite in the atlas; the mod then replaces the sprite's pixel data with animation frames. Without the `.png`, the sprite won't exist in the atlas.

---

**Q: 动画帧率最高多少？**
**Q: What's the maximum animation frame rate?**

A: 标准和高分辨率档最高约 20 FPS；高帧率和高质量档按渲染循环采样，GPU 更新最高 60 FPS。

A: Standard and high-resolution modes run at about 20 FPS. High-frame-rate and high-quality modes preserve source timing and update the GPU at up to 60 FPS.

---

**Q: 支持自定义命名空间吗？**
**Q: Are custom namespaces supported?**

A: 支持。只需将文件放在 `assets/<你的命名空间>/textures/` 目录下即可。

A: Yes. Simply place files under `assets/<your_namespace>/textures/`.

---

**Q: 可以同时使用多个资源包的动画纹理吗？**
**Q: Can I use animated textures from multiple resource packs?**

A: 可以。Minecraft 的资源包过滤器（filters）会被遵守。若同一目标纹理在多个可见资源包中都有动画，优先级最高的资源包胜出；若同一资源包同时包含两种格式，`.png3` APNG 优先于 `.gif`。若解码失败，不会降级加载低优先级的动画文件。

A: Yes. Minecraft resource pack filters are honored. For a target with multiple visible animations, the highest-priority pack wins; if that pack has both formats, `.png3` APNG wins over `.gif`. The chosen animation is not replaced by a lower-priority file if decoding fails.

---

**Q: `.png3` 是什么？为什么不直接用 `.apng`？**
**Q: What is `.png3`? Why not use `.apng`?**

A: `.png3` 将 APNG 资源与 Minecraft 的静态 `.png` 回退文件区分开，同时保持可被资源管理器寻址。每个动画纹理都必须有一个同名的 `.png` 回退文件。Minecraft 的资源索引系统不会将 `.apng` 识别为纹理资源，因此使用 `.png3` 作为替代扩展名。

A: `.png3` keeps APNG assets distinct from Minecraft's static `.png` fallback while remaining addressable as a resource through the resource manager. A same-name `.png` fallback is required for every animation. Minecraft's resource indexing system does not recognize `.apng` as a texture resource, so `.png3` is used as the alternative extension.

---

**Q: 会影响游戏性能吗？**
**Q: Does this affect game performance?**

A: 动画纹理的 GPU 上传在客户端 tick 或渲染帧中执行，CPU 开销主要来自帧缩放和 mipmap 生成。预处理帧缓存（PreparedFrameCache）会在资源重载时预计算帧和 mip 链，避免每帧重复缩放。标准档的内存预算约为 64 MiB ARGB，可通过画质设置调整。对大多数现代硬件影响很小。

A: Animated texture GPU uploads happen during client ticks or render frames. CPU overhead mainly comes from frame scaling and mipmap generation. The prepared frame cache pre-computes frames and mip chains at reload time, avoiding repeated per-frame scaling. Standard tier memory budget is ~64 MiB ARGB, adjustable via quality settings. Impact is minimal on most modern hardware.

---

**Q: 为什么我的动画没有播放？**
**Q: Why isn't my animation playing?**

A: 请检查以下几点：
1. 是否同时提供了同名的 `.png` 静态回退文件？
2. 文件是否放在了正确的路径下（`assets/<namespace>/textures/...`）？
3. 资源包的 `pack.mcmeta` 是否正确配置？
4. 是否使用了正确的扩展名（`.gif` 或 `.png3`）？
5. 资源包是否在游戏中被启用且优先级正确？

A: Check the following:
1. Did you provide a same-name `.png` static fallback?
2. Are files in the correct path (`assets/<namespace>/textures/...`)?
3. Is your resource pack's `pack.mcmeta` correctly configured?
4. Are you using the correct extension (`.gif` or `.png3`)?
5. Is the resource pack enabled in-game with the correct priority?

---

## 许可证 / License

MIT License — 详见 / see [LICENSE](LICENSE)

---

## 致谢 / Credits

- **Fabric** — Minecraft 模组加载器 / Mod loader
- **Fabric API** — Fabric 核心 API / Core API
- **ModMenu** — 模组配置界面 / Mod configuration UI
- **Mixin** — 字节码注入框架 / Bytecode injection framework
- **Yarn Mappings** — Minecraft 反混淆映射 / Deobfuscation mappings