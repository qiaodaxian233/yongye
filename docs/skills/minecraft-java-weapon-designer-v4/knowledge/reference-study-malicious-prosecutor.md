# 参考模型工程研究：Voxel Models Pack Malicious Prosecutor v1.0

> 本文只记录对用户提供且声明已购买的资源包所做的工程分析。原始模型 JSON、贴图和配置没有包含在本 Skill 中。

## 包结构

该资源同时提供两套导出：

- CraftEngine
- ItemsAdder

两套导出的模型几何和 display 设计相同，主要差异是目录位置、配置格式和模型内纹理路径。

## 模型统计

| 模型 | elements | 旋转元素 | JSON 体积约 | 主要旋转 |
|---|---:|---:|---:|---|
| sword_1 | 286 | 0 | 528 KB | 无 |
| sword_2 | 475 | 475 | 854 KB | Z 轴 22.5° |
| sword_3 | 538 | 538 | 980 KB | Z 轴 22.5° / 45° |
| sword_4 | 540 | 539 | 984 KB | Z 轴 22.5° / 45° |
| sword_5 | 412 | 372 | 891 KB | Z 轴 22.5° |

结论：这是高到极高复杂度的显式体素模型，不是普通的 2D handheld 模型。

## 坐标与结构规律

- 大量使用小长方体形成像素化曲线和斜边。
- 部分形态所有体素共享统一 Z 轴旋转，形成整体倾斜的体素网格。
- 坐标范围明显超出 0–16，因此必须依赖每个视角独立的 display 调整。
- 一些模型省略部分不可见面；这比给所有元素固定生成六个面更节省。
- 没有保留 Blockbench/Cubik 的组层级，最终 JSON 是扁平 elements 列表。

## 贴图规律

观察到：

- 128×128 与 256×256 的静态图集
- 一个 128×1280 的纵向动画图集
- 动画图集由 10 个 128×128 帧组成
- `.png.mcmeta` 使用 `frametime: 1`
- 某一形态同时引用三个纹理槽，用静态图集、透明徽记和动画徽记叠加效果

这说明复杂武器的视觉效果可以拆为：

1. 几何主体
2. 静态图集
3. 透明装饰平面
4. 动态纹理平面

## Display 规律

所有模型都为多个显示场景单独设置参数。不同形态之间的 scale、translation 和 rotation 差异较大，说明 display 必须按轮廓调校。

特别观察：

- 大型模型在第三人称可能使用 2–4 倍缩放。
- GUI 会使用较大的 Z 轴旋转来突出武器轮廓。
- 某模型将左手视角缩放到 `0.0001`，达到近似隐藏效果。这应视为有意的特例，而不是通用模板。
- 部分形态提供 `head` 变换，允许作为头部展示物使用。

## CraftEngine 与 ItemsAdder 的关键差异

### CraftEngine

- 模型位于 `models/item/`
- 纹理位于 `textures/item/`
- 模型纹理引用含 `item/`，例如 `namespace:item/texture`
- YAML 中使用 namespaced item ID 与 `model.path`

### ItemsAdder

- 模型位于 `models/`
- 纹理位于 `textures/`
- 模型纹理引用不含 `item/`，例如 `namespace:texture`
- YAML 使用 `info.namespace`、`generate: false` 和 `model_path`

因此不能只复制同一个 JSON 到两个平台；至少要重写 textures 映射。

## 对 Skill 的改进

基于以上分析，2.0 版增加：

- 参考包静态分析流程
- 高复杂度 elements 统计
- 多平台路径转换
- 动画纹理识别
- display 调校检查
- 多形态武器工作流
- 性能风险分级
- 原创性与授权边界

## 未做出的推断

- 文件名不足以证明五个模型一定是同一武器的五个升级阶段。
- 未在实际 Minecraft 客户端、ItemsAdder 或 CraftEngine 中加载测试。
- README 内容与资源本体关联较弱，不能据此判断模型许可条款。
- `format_version` 与具体导出工具/游戏版本的兼容性仍需实机验证。
