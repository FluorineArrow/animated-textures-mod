
# Animated Textures Mod / 动画纹理模组

> **Minecraft 1.21 | Fabric | Client-Side**
>
> 在资源包中使用 GIF 和 APNG 文件为方块、物品、药水效果图标添加逐帧动画。
>
> Use GIF and APNG files in resource packs to add frame-by-frame animation to blocks, items, and mob effect icons.

---

> **🤖 AI 辅助开发声明 / AI-Assisted Development Disclosure**
>
> 本项目的代码实现、架构设计及文档均由 **Claude (Anthropic)** 辅助生成。
> 核心创意、需求定义和功能决策由人类开发者完成，AI 负责代码编写、调试优化和技术文档撰写。
>
> The codebase, architecture, and documentation of this project were generated with the assistance of **Claude (Anthropic)**.
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
| **任意分辨率** | 支持 16×16、32×32、64×64 等任意分辨率纹理 | Any resolution textures (16×16, 32×32, 64×64, etc.) |
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
│ (钩子)   │ │ TickManager          │  ← 每 Tick 推进帧并上传 GPU
└──────────┘ └──────────────────────┘
```

### 核心流程 / Core Pipeline

#### 1. 资源发现与解码 / Resource Discovery & Decoding

Minecraft 的 `ResourceManager` 不会索引 `.gif` 和 `.png3` 这类非标准扩展名的文件。
本模组采用三步策略发现动画纹理：

Minecraft's `ResourceManager` does not index non-standard extensions like `.gif` or `.png3`.
This mod uses a three-step discovery strategy:

```
步骤 1: findResources("textures", *.png) → 获取所有 .png 纹理路径
步骤 2: 对每个路径尝试打开同名的 .gif 和 .png3
步骤 3: 独立扫描 .gif/.png3 文件（捕获无对应 .png 的动画纹理）
```

对于无法通过 `ResourceManager.getResource()` 访问的文件，模组会**直接从资源包** (`ResourcePack.open()`) 读取，绕过 Minecraft 的资源索引限制。

#### 2. GIF 解码 / GIF Decoding (`GifDecoder`)

纯 Java 实现的 GIF89a 解码器，支持：

Pure Java GIF89a decoder supporting:

- **LZW 压缩**：完整的变长编码解码，含安全计数器防止恶意文件死循环
- **局部/全局色表**：每帧可使用独立色表或全局色表
- **透明度**：支持透明色索引
- **帧处置模式**：`DISPOSE_OP_BACKGROUND`（清除区域）和 `DISPOSE_OP_PREVIOUS`（恢复上一帧）
- **隔行扫描**：支持 GIF 的 interlace 扫描模式
- **大小限制**：最大 50 MB，防止 OOM

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

#### 4. 纹理图集注入 / Texture Atlas Injection

本模组通过 **Mixin** 机制在两个层面注入动画帧：

This mod injects animation frames at **two levels** via Mixin:

**路径 A：`SpriteContentsAnimationMixin`（Mixin 路径）**

拦截 `SpriteContents.upload()` 方法。当 Minecraft 上传精灵帧到图集时，模组**替换精灵的像素数据**为当前动画帧，然后让 Minecraft 原生代码完成图集上传。

```
Minecraft 调用 SpriteContents.upload()
  → Mixin 拦截 @HEAD
  → 查找 AnimatedTextureRegistry 中是否有匹配的动画纹理
  → 有 → 获取当前帧并缩放到精灵尺寸 → 替换 uploadImages[0] 的像素
  → 标记为 mixinHandled（防止 TickManager 重复上传）
  → Minecraft 原生代码将修改后的精灵上传到图集
```

**路径 B：`AnimatedTextureTickManager`（Tick 驱动路径）**

每客户端 Tick（~50ms）运行，直接将动画帧上传到 GPU 纹理：

```
ClientTickEvents.END_CLIENT_TICK
  → 推进所有 AnimatedTexture 的帧指针（tick()）
  → 遍历已注册精灵（trackedSprites）
  → 跳过已被 Mixin 处理的精灵（避免双上传）
  → 获取当前帧 → 缩放到精灵尺寸 → 直接上传到图集 GPU 纹理
```

**双路径协同**：Mixin 路径处理 Minecraft 主动调用 upload 的场景，Tick 路径处理没有 mcmeta 动画定义的精灵。两者通过 `mixinHandledSprites` 集合协调，确保每帧只上传一次。

#### 5. 图集扫描与精灵匹配 / Atlas Scanning & Sprite Matching

`SpriteAtlasTextureMixin` 拦截图集上传完成事件，延迟扫描匹配精灵：

```
图集上传完成 (SpriteAtlasTexture.upload)
  → scheduleAtlasScan(atlas)  // 保存图集引用
  → onRegistryReady()         // 注册表就绪后扫描
    → 对每个已注册动画纹理:
      → getTargetTextureId()  // "minecraft:block/gold_ore"
      → 尝试从图集获取精灵
      → 失败则尝试 getMobEffectTargetId()  // "minecraft:fire_resistance"
      → 匹配成功 → registerSprite() → 加入跟踪列表
```

**Identifier 转换规则 / Identifier Mapping**：

| 资源包路径 (Source) | 图集精灵 ID (Target) | 说明 |
|---------------------|----------------------|------|
| `textures/block/gold_ore.gif` | `minecraft:block/gold_ore` | 去除 `textures/` 前缀和扩展名 |
| `textures/mob_effect/speed.png3` | `minecraft:mob_effect/speed` | 标准路径映射 |
| `textures/mob_effect/speed.png3` | `minecraft:speed` | 药水效果图集别名（prefix=""） |

#### 6. 帧缩放 / Frame Scaling

动画纹理可以是任意分辨率，模组会自动缩放到精灵在图集中的分配区域：

Animated textures can be any resolution; the mod auto-scales to the sprite's allocated atlas region:

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
│   ├── AnimatedTexturesClient.java         # 客户端入口，注册资源重载监听器
│   ├── AnimatedTexturesConfig.java         # 配置管理（JSON 持久化）
│   ├── AnimatedTexturesModMenu.java        # ModMenu 配置界面
│   └── AnimatedTextureReloadListener.java  # 资源重载：发现、解码、注册动画纹理
├── mixin/
│   ├── SpriteContentsAnimationMixin.java   # 拦截精灵上传，替换为动画帧
│   ├── SpriteAtlasTextureMixin.java        # 拦截图集上传完成，触发延迟扫描
│   ├── TextureManagerMixin.java            # 纹理管理器钩子，确保 TickManager 启动
│   └── AtlasSizeMixin.java                 # 占位：图集大小覆盖（待实现）
└── util/
    ├── AnimatedTexture.java                # 单个动画纹理：帧管理、缩放、ID 转换
    ├── AnimatedTextureRegistry.java        # 中央注册表（ConcurrentHashMap）
    ├── AnimatedTextureTickManager.java     # Tick 驱动的帧推进与 GPU 上传
    ├── AnimatedFrame.java                  # 单帧数据（像素数组 + 持续时间）
    ├── GifDecoder.java                     # 纯 Java GIF89a 解码器
    └── ApngDecoder.java                    # 纯 Java APNG 解码器
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
   将 `animated-textures-1.1.0.jar` 放入 `.minecraft/mods/` 目录。
   Place `animated-textures-1.1.0.jar` into `.minecraft/mods/` directory.

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

- 帧延迟最小 50ms（模组强制执行，即最高 20 FPS）
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

Config file: `.minecraft/config/animated_textures.json`. Also adjustable in-game via ModMenu.

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `scalingMode` | Enum | `BILINEAR` | 缩放模式：`NEAREST`（最近邻）/ `BILINEAR`（双线性） |
| `enableMipmaps` | Boolean | `true` | 是否为动画纹理生成 Mipmap |
| `atlasSize` | Integer | `0` | 图集大小覆盖。0 = Minecraft 默认值 |
| `logLevel` | Enum | `WARN` | 日志级别：`NONE` / `WARN` / `INFO` / `DEBUG` |

**配置文件示例 / Example config**：

```json
{
  "scalingMode": "BILINEAR",
  "enableMipmaps": true,
  "atlasSize": 0,
  "logLevel": "WARN"
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

# 生成示例资源包素材 / Generate example pack assets
./gradlew generateExamplePlaceholders

# 在开发环境中运行 Minecraft / Run Minecraft in dev
./gradlew runClient
```

### 输出文件 / Output

- 构建产物：`build/libs/animated-textures-1.1.0.jar`
- 示例资源包：`example_pack/`（由 Gradle task 自动生成）

---

## 兼容性 / Compatibility

| 模组/环境 | 兼容性 | 备注 |
|-----------|--------|------|
| **Sodium** | ✅ 兼容 | Mixin 优先级 1001，确保在 Sodium 之后执行 |
| **Iris** | ✅ 兼容 | 不影响着色器管线 |
| **OptiFine** | ⚠️ 未测试 | 与 Sodium 存在已知冲突时可能有问题 |
| **ModMenu** | ✅ 已集成 | 通过 ModMenu 提供配置界面 |
| **服务器** | ✅ 纯客户端 | `environment: "client"`，无需服务端安装 |

### Sodium 兼容细节 / Sodium Compatibility Details

- `SpriteAtlasTextureMixin` 使用 `priority = 1001`，确保在 Sodium 的 `MixinSpriteAtlasTexture`（默认 1000）之后执行
- 动画纹理通过直接 GPU 上传绕过 Sodium 的 "Animate Only Visible Textures" 可见性跟踪
- 即使 Sodium 优化了不可见纹理的动画，本模组的纹理仍会持续动画

---

## 常见问题 / FAQ

**Q: 为什么需要同名的 `.png` 文件？**
**Q: Why do I need a same-name `.png` file?**

A: Minecraft 的纹理图集系统只识别 `.png` 文件。`.png` 文件用于在图集中注册精灵（sprite），模组随后将精灵的像素数据替换为动画帧。没有 `.png` 文件，精灵不会被注册到图集，动画也就无从注入。

A: Minecraft's texture atlas system only recognizes `.png` files. The `.png` registers the sprite in the atlas; the mod then replaces the sprite's pixel data with animation frames. Without the `.png`, the sprite won't exist in the atlas.

---

**Q: 动画帧率最高多少？**
**Q: What's the maximum animation frame rate?**

A: 模组强制最低帧持续时间为 50ms，即最高约 20 FPS。这是为了避免过快的动画消耗过多 GPU 带宽。

A: The mod enforces a minimum frame duration of 50ms (~20 FPS max). This prevents overly fast animations from consuming excessive GPU bandwidth.

---

**Q: 支持自定义命名空间吗？**
**Q: Are custom namespaces supported?**

A: 支持。只需将文件放在 `assets/<你的命名空间>/textures/` 目录下即可。

A: Yes. Simply place files under `assets/<your_namespace>/textures/`.

---

**Q: 可以同时使用多个资源包的动画纹理吗？**
**Q: Can I use animated textures from multiple resource packs?**

A: 可以。Minecraft 的资源包优先级系统决定了同名纹理的覆盖顺序，高优先级包的动画纹理会覆盖低优先级的。

A: Yes. Minecraft's resource pack priority system determines which animated texture wins when multiple packs define the same texture.

---

**Q: `.png3` 是什么？为什么不直接用 `.apng`？**
**Q: What is `.png3`? Why not use `.apng`?**

A: Minecraft 的资源管理器 (`ResourceManager`) 只索引已知扩展名（如 `.png`、`.json`）。使用 `.png3` 这种变体扩展名是折中方案：既避免与原版 `.png` 冲突，又可以通过资源包直接访问读取。

A: Minecraft's `ResourceManager` only indexes known extensions (like `.png`, `.json`). Using `.png3` is a compromise: it avoids conflict with vanilla `.png` while still being directly accessible from resource packs.

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
