# DragonCore 目录与映射知识

## 已观察到的布局

DragonCore 资源包并不总是只有一种目录结构。常见形式包括客户端/服务端分离、`DragonCoreResource` 根目录以及配置与资源同级。

核心关系不是目录名字本身，而是：

```text
ItemModel.yml 的 path
        ↓
models/items/<path>/model.json
        ↓
模型 textures 字段
        ↓
PNG 与 PNG.mcmeta
```

## 模型文件类型

- `model.json`：通常为主显示模型。
- 其他自定义名称 JSON：可能是阻挡、蓄力、开箱、投掷或其他状态。
- 没有 `parent` 但有完整 `elements` 的模型可以是有效的自包含 Blockbench 模型。

## 纹理引用类型

1. 当前目录短名：`"blade"`
2. 相对子目录：`"effects/glow"`
3. 命名空间：`"namespace:path/texture"`

分析时必须分别解析，不能把所有引用都当成原版 `assets/<namespace>/textures` 路径。

## YAML 注意点

`match` 的具体触发含义依赖用户服务器上的 DragonCore 版本与配置，不应仅凭参考包猜测。静态工具只验证 YAML 结构和 `path` 是否能找到模型。
