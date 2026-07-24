
# Animated Textures Mod / 动画纹理模组

> **Minecraft 1.21 | Fabric | Client-Side**
>
> 在资源包中使用 GIF 和 APNG 文件为方块、物品、药水效果图标添加逐帧动画。
>
> Use GIF and APNG files in resource packs to add frame-by-frame animation to blocks, items, and mob effect icons.

---

> **🤖 AI 辅助开发声明 / AI-Assisted Development Disclosure**
>
> 本项目的代码实现、架构设计及文档均通过 **Claude Code** 与 **MiMo** 模型辅助完成。
> 核心创意、需求定义和功能决策由人类开发者完成，AI 负责代码编写、调试优化和技术文档撰写。
>
> The codebase, architecture, and documentation of this project were developed with the assistance of **Claude Code** and the **MiMo** model.
> Core ideas, requirements, and feature decisions were made by the human developer; AI handled code writing, debugging, optimization, and technical documentation.

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

---

## 功能概述 / Features

| 功能 | 中文说明 | English Description |
|------|----------|---------------------|
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
┌─────────────────────────────┐
│  AnimatedTextureReloadListener  │  ← 资源重载时扫描并解码
│  (资源重载监听器)             │
└──────────┬──────────────────┘
           │ 解码后的帧列表
           ▼
┌─────────────────────────────┐
│  AnimatedTextureRegistry     │  ← 中央注册表 (按 Identifier 索引)
│  (动画纹理注册表)             │
└──────────┬──────────────────┘
           │
     ┌─────┴─────┐
     ▼           ▼
┌──────────┐ ┌──────────────────────┐
│ Mixin    │ │ AnimatedTexture      │  ← 每个动画纹理的帧管理器
│ (钩子)   │ │ TickManager          │  ← 按质量档推进帧并上传 GPU
└──────────┘ └──────────────────────┘
```

### 核心流程 / Core Pipeline

#### 1. 资源发现与解码 / Resource Discovery & Decoding

Minecraft resource loading is the source of truth for animations. The mod finds visible `.png` fallbacks under `textures/`, then resolves same-name `.gif` and `.png3` resources through `ResourceManager`.

```
Step 1: find visible textures/*.png fallbacks
Step 2: inspect visible same-name GIF and APNG resource layers
Step 3: select one animation before decoding
```

Animations without a visible same-name PNG fallback are ignored. Resource filters are honored. The highest-priority visible pack wins; if one winning pack supplies both formats, `.png3` APNG takes precedence over `.gif`. A corrupt selected animation falls back to its static PNG rather than loading a lower-priority animation.

Decoder limits are enforced before pixel buffers are allocated. Standard and high-frame-rate modes allow 16 MiB encoded input, 2,048 pixels per side, 4,194,304 pixels per frame/canvas, 256 frames, and 16,777,216 retained decoded pixels per animation. High-resolution and high-quality modes allow 32 MiB input, 4,096 pixels per side, 16,777,216 pixels per frame/canvas, and 33,554,432 retained pixels.

#### 2. GIF 解码 / GIF Decoding (`GifDecoder`)

纯 Java 实现的 GIF89a 解码器，支持：

Pure Java GIF89a decoder supporting:

- **LZW 压缩**：完整的变长编码解码，含安全计数器防止恶意文件死循环
- **局部/全局色表**：每帧可使用独立色表或全局色表
- **透明度**：支持透明色索引
- **帧处置模式**：`DISPOSE_OP_BACKGROUND`（清除区域）和 `DISPOSE_OP_PREVIOUS`（恢复上一帧）
- **隔行扫描**：支持 GIF 的 interlace 扫描模式
- **大小限制**：最大 16 MiB，防止 OOM

```
GIF 文件 → 读取头部 → 解析扩展块 → LZW 解码像素 → 合成帧 → AnimatedFrame 列表
```

#### 3. APNG 解码 / APNG Decoding (`ApngDecoder`)

APNG 是 PNG 的动画扩展，帧数据存储在 `fcTL`（帧控制）和 `fdAT`（帧数据）块中。普通 PNG 阅读器会忽略这些块。

APNG is a PNG animation extension. Frame data lives in `fcTL` (frame control) and `fdAT` (frame data) chunks that regular PNG readers ignore.

```
APNG 文件 → 验证 PNG 签名 → 解析 IHDR/acTL/fcTL/fdAT 块
  → 为每帧重建独立 PNG → ImageIO 解码 → 按 blendOp 合成到画布
  → 按 disposeOp 处理画布 → AnimatedFrame 列表
```

支持的合成操作 / Supported blend/dispose operations:
- `blendOp`: SOURCE（覆盖）/ OVER（Alpha 混合）
- `disposeOp`: NONE / BACKGROUND（清除）/ PREVIOUS（恢复）

#### 4. Atlas Animation Uploads

`SpriteAtlasTextureMixin` captures each atlas stitch result, including the stitched sprite map and mip level. The animation manager binds every matched sprite to its actual atlas and uploads an initial frame, then uploads again only when the visible frame changes. Standard modes run at client-tick cadence; high-frame-rate modes use the render loop with GPU updates capped at 60 Hz.

Every generated frame refreshes all atlas mip levels. Prepared frames and mip chains are cached within a bounded per-reload budget when a complete animation fits; oversized animations reuse their base buffer. This avoids repeated scaling allocations while keeping native memory bounded.

#### 5. Frame Scaling

动画纹理会缩放到精灵在图集中的分配区域。标准档单边最多 2,048 像素，高分辨率档单边最多 4,096 像素。

Animated textures are scaled to the sprite's allocated atlas region. Standard modes allow up to 2,048 pixels per side; high-resolution modes allow up to 4,096 pixels. The same-name static PNG must use the intended display size because it determines the stitched sprite region.

| 模式 | 算法 | 适用场景 |
|------|------|----------|
| **Nearest Neighbor** | 最近邻采样 | 像素风格纹理，速度快 |
| **Bilinear** | 双线性插值（四邻域加权） | 高分辨率资源包，效果平滑 |

```
原始帧 (srcW × srcH) → 目标尺寸 (spriteW × spriteH)
  Nearest: dst[x,y] = src[x * srcW/dstW, y * srcH/dstH]
  Bilinear: dst[x,y] = 加权平均(src[x0,y0], src[x1,y0], src[x0,y1], src[x1,y1])
```

---

## 项目结构 / Project Structure

```
src/main/java/com/animatedtextures/
├── client/
│   ├── AnimatedTexturesClient.java         # Client entry point and tick registration
│   ├── AnimatedTexturesConfig.java         # Scaling and quality JSON configuration
│   ├── AnimatedTexturesModMenu.java        # ModMenu configuration screen
│   ├── AnimatedTextureReloadListener.java  # Visible-resource discovery and decoding
│   └── AnimatedResourceResolver.java       # Pack-priority and format selection
├── mixin/
│   ├── GameRendererMixin.java                # High-frame-rate render callback
│   ├── ReloadableResourceManagerMixin.java   # Owns overall reload attempts
│   └── SpriteAtlasTextureMixin.java          # Captures stitched atlas regions
└── util/
    ├── AnimationQuality.java               # Four bounded quality policies
    ├── AnimatedImageLimits.java            # Decoder input/frame safety limits
    ├── AnimatedTexture.java                # Frame timing, scaling, and sprite IDs
    ├── AnimatedTextureRegistry.java        # Canonical selected animations
    ├── AnimatedTextureTickManager.java     # Atlas-aware GPU uploads and mipmaps
    ├── AnimatedFrame.java                  # Immutable ARGB frame data
    ├── GifDecoder.java                     # Bounded GIF89a decoder
    └── ApngDecoder.java                    # Bounded strict APNG decoder
```

---

## 快速上手 / Quick Start

### 安装 / Installation

1. **前置依赖 / Prerequisites**：
   - Minecraft 1.21
   - Fabric Loader ≥ 0.15.11
   - Fabric API
   - Java 21+

2. **安装模组 / Install mod**：
   将 `animated-textures-1.2.0.jar` 放入 `.minecraft/mods/` 目录。
   Place `animated-textures-1.2.0.jar` into `.minecraft/mods/` directory.

### 基本使用 / Basic Usage

1. 创建或打开资源包，在 `assets/<命名空间>/textures/` 目录下放置 `.gif` 或 `.png3` 文件
2. 文件名必须与原版纹理同名（扩展名不同），例如：
   - 替换金矿石 → `textures/block/gold_ore.gif`
   - 替换钻石 → `textures/item/diamond.png3`
   - 替换速度效果图标 → `textures/mob_effect/speed.gif`
3. 启动游戏，动画纹理将自动加载

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
            │   ├── gold_ore.png      # 静态回退（必须存在）
            │   └── gold_ore.gif      # 动画版本（替换 .png）
            ├── item/
            │   ├── diamond.png       # 静态回退
            │   └── diamond.png3      # APNG 动画版本
            └── mob_effect/
                ├── speed.png         # 静态回退
                └── speed.png3        # 药水效果图标动画
```

> **重要 / Important**：必须同时提供同名的 `.png` 静态文件作为回退。Minecraft 的纹理系统需要 `.png` 文件来注册精灵到图集中；模组随后替换其像素数据为动画帧。
>
> You must provide a same-name `.png` static fallback. Minecraft's texture system needs the `.png` to register the sprite in the atlas; the mod then replaces its pixel data with animation frames.

### 支持的文件格式 / Supported Formats

| 格式 | 扩展名 | 特性 | 说明 |
|------|--------|------|------|
| **GIF89a** | `.gif` | 多帧、透明度、帧延迟、循环 | 最广泛使用的动画格式 |
| **APNG** | `.png3` | 多帧、Alpha 通道、高色彩深度 | 比 GIF 质量更高，支持半透明 |

### GIF 制作要点 / GIF Tips

- 标准档的帧延迟最小 50ms；高帧率和高质量档保留源时间轴并以最高 60 Hz 上传
- 支持循环动画（NETSCAPE2.0 扩展）
- 支持透明度和帧间处置模式
- 建议使用 16×16 或与目标精灵相同的分辨率

### APNG 制作要点 / APNG Tips

- 使用 `.png3` 扩展名（Minecraft 不索引 `.apng`）
- 支持 `blendOp`（SOURCE / OVER）和 `disposeOp`（NONE / BACKGROUND / PREVIOUS）
- 适合需要半透明渐变效果的高保真动画
- 帧延迟公式：`duration = delayNum / delayDen` 秒

### 药水效果图标 / Mob Effect Icons

药水效果图标使用独立的图集（`textures/atlas/mob_effects.png`），尺寸为 **18×18** 像素。
模组自动处理 mob_effect 纹理的特殊 ID 映射（bare registry name）。

Mob effect icons use a separate atlas at **18×18** pixels. The mod handles the special ID mapping automatically.

---

## 配置说明 / Configuration

配置文件位于 `.minecraft/config/animated_textures.json`，也可通过 ModMenu 在游戏内修改。

Config file: `.minecraft/config/animated_textures.json`. ModMenu edits a draft and writes it only through **Save & Done**. Invalid or missing values fall back to `BILINEAR` and `STANDARD`; legacy keys are ignored. Changing quality performs a resource reload so decoder limits, timing, atlas bindings, and native caches switch atomically.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `scalingMode` | Enum | `BILINEAR` | `NEAREST` for pixel-preserving sampling or `BILINEAR` for smooth upscaling |
| `quality` | Enum | `STANDARD` | `STANDARD`, `HIGH_FRAME_RATE`, `HIGH_RESOLUTION`, or `HIGH_QUALITY` |

| Quality | GPU update rate | Maximum side | Reload-wide decoded pixels |
|---------|-----------------|--------------|----------------------------|
| `STANDARD` | ~20 Hz | 2,048 px | 16,777,216 (~64 MiB ARGB) |
| `HIGH_FRAME_RATE` | Up to 60 Hz | 2,048 px | 16,777,216 (~64 MiB ARGB) |
| `HIGH_RESOLUTION` | ~20 Hz | 4,096 px | 33,554,432 (~128 MiB ARGB) |
| `HIGH_QUALITY` | Up to 60 Hz | 4,096 px | 33,554,432 (~128 MiB ARGB) |

High-resolution animations can consume substantial atlas space and GPU upload bandwidth. Source frames shorter than 16.67 ms keep their original timeline in high-frame-rate modes but may be skipped when sampled at the 60 Hz upload cap.

All atlas mip levels are updated automatically whenever an animation frame changes.

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
- Gradle 8.8（已包含 Wrapper）

### 构建命令 / Build Commands

```bash
# 编译 / Compile
./gradlew build

# Generate and validate the example resource-pack ZIP
./gradlew verifyExampleResourcePack

# Run decoder and state regressions
./gradlew test

# Run Minecraft in the development environment
./gradlew runClient
```

### 输出文件 / Output

- Mod artifact: `build/libs/animated-textures-1.2.0.jar`
- Installable example pack: `build/libs/animated-textures-example-pack-1.2.0.zip`
- Generated staging directory: `build/generated/example-pack/`

---

## 兼容性 / Compatibility

| 模组/环境 | 兼容性 | 备注 |
|-----------|--------|------|
| **Sodium** | ✅ 已验证兼容 | Minecraft 1.21 客户端已验证 Atlas 动画与资源重载 |
| **Iris** | ⚠️ Client validation pending | Requires the same atlas regression pass |
| **OptiFine** | ⚠️ Untested | May conflict with Fabric renderer assumptions |
| **ModMenu** | ✅ 已集成 | 通过 ModMenu 提供配置界面 |
| **服务器** | ✅ 纯客户端 | `environment: "client"`，无需服务端安装 |

### Sodium 兼容细节 / Sodium Compatibility Details

- Minecraft 1.21 客户端已确认与 Sodium 兼容，包括动画 Atlas 上传和资源重载
- `SpriteAtlasTextureMixin` 使用 `priority = 1001`，确保在 Sodium 的 `MixinSpriteAtlasTexture`（默认 1000）之后执行
- 动画纹理通过直接 GPU 上传维护所有 mip 层，不依赖 Sodium 的原版动画可见性跟踪

Sodium compatibility has been validated on the Minecraft 1.21 client path. Iris remains pending separate validation.

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

A: Yes. Minecraft resource filters are honored. For a target with multiple visible animations, the highest-priority pack wins; if that pack has both formats, `.png3` APNG wins over `.gif`. The chosen animation is not replaced by a lower-priority file if decoding fails.

---

**Q: `.png3` 是什么？为什么不直接用 `.apng`？**
**Q: What is `.png3`? Why not use `.apng`?**

A: `.png3` keeps APNG assets distinct from Minecraft's static `.png` fallback while remaining addressable as a resource. A same-name `.png` fallback is required for every animation.

---

## 许可证 / License

MIT License

---

## 致谢 / Credits

- **Fabric** — Minecraft 模组加载器 / Mod loader
- **Fabric API** — Fabric 核心 API
- **ModMenu** — 模组配置界面 / Mod configuration UI
- **Mixin** — 字节码注入框架 / Bytecode injection framework
- **Yarn Mappings** — Minecraft 反混淆映射 / Deobfuscation mappings
