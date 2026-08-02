# DragonCore、萌芽风格资源与 MythicMobs 人物关联

## 典型关联

```text
客户端人物
├─ geometry.json
├─ animation.json
├─ texture.png
├─ glow_texture.png
└─ sound/*.ogg

服务端
├─ DragonCore/EntityModel/<entity>.yml
├─ DragonCore/ItemModel.yml
└─ MythicMobs/
   ├─ Mobs/<entity>.yml
   ├─ Items/<items>.yml
   └─ Skills/<skills>.yml
```

## 检查原则

- EntityModel 的 model、animation、texture、glowTexture 应能定位到客户端文件。
- MythicMobs 的 `animation{name=...}` 必须存在于动画 JSON。
- `sound{s=...}` 应能定位到实际 OGG，并检查名称与文件是否语义对应。
- 投射物或特效 Mob 使用的 Items 必须存在，Items 的显示名/模型编号必须能被 ItemModel 映射。
- 客户端和服务端可能分别使用中文路径；迁移到 Linux 时要检查大小写和 Unicode 规范化。
- 同一资源在多个平台目录中重复时，用哈希检测漂移；不要只看文件大小。

## 时间同步表

| 事件 | 秒 | tick | 说明 |
|---|---:|---:|---|
| 动画开始 | 0 | 0 | 锁定朝向或开始 GCD |
| 起手音效 | 0.1 | 2 | 可选 |
| 命中判定 | 0.4 | 8 | 与武器接触帧一致 |
| 后摇结束 | 0.7 | 14 | 解除 GCD |

数值仅为通用模板，必须根据实际动画重新测量。
