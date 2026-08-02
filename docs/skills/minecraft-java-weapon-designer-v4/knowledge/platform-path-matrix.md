# 平台路径矩阵

| 项目 | 原版资源包 | CraftEngine | ItemsAdder |
|---|---|---|---|
| 模型目录 | `assets/ns/models/item/` | `.../assets/ns/models/item/` | `.../assets/ns/models/` |
| 贴图目录 | `assets/ns/textures/item/` | `.../assets/ns/textures/item/` | `.../assets/ns/textures/` |
| 常见纹理引用 | `ns:item/name` | `ns:item/name` | `ns:name` |
| 外部模型 | 直接加载 | `model.path` | `generate: false` + `model_path` |
| 分类配置 | 无统一格式 | `categories.yml` | category YAML |

该矩阵是当前参考包观察结果，不代替目标插件版本的官方文档和实机测试。
