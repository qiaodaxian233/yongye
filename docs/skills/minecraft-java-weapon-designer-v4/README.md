# Minecraft Java Weapon & Character Designer Skill 4.0

用于指导 AI 设计、分析和实现 Minecraft Java 版武器、人物、NPC、Boss 与技能特效。

## 4.0 新能力

- Bedrock Geometry 人物骨架与层级检查
- Bedrock Animation 动画时长、关键帧、骨骼引用与插值检查
- DragonCore EntityModel 与萌芽/Germ 风格实体配置识别
- MythicMobs Mobs / Items / Skills 跨文件引用图
- 动画、GCD、delay、命中帧、音效和投射物同步检查
- 独立武器、投射物、剑气与人物内置武器骨骼的关联分析
- visible bounds、实体碰撞盒和模型 hitbox 骨骼区分
- 发光遮罩、UV 越界、透明平面与图集利用率检查
- OGG 音频存在性、基础元数据与错误别名检查
- 人物模型包只学习工程方法、不嵌入购买素材的规则

## 主要文件

- `SKILL.md`：完整技能规则
- `knowledge/character-rig-animation.md`：人物骨骼与动画知识
- `knowledge/dragoncore-mythicmobs-character.md`：客户端/服务端关联方法
- `knowledge/reference-study-archangel-character.md`：从用户购买包中提炼的非表达性工程结论
- `checklists/character-validation.md`：人物模型发布前检查表
- `templates/character/`：原创通用人物项目模板
- `tools/analyze_character_pack.py`：只读人物模型包分析工具
- `tools/analyze_model_pack.py`：武器和批量资源分析工具

## 使用分析工具

```bash
python tools/analyze_character_pack.py 人物包.zip \
  --output character-report.json \
  --markdown-output character-report.md
```

工具不会执行压缩包内脚本。Pillow、PyYAML 和系统中的 ffprobe 属于可选能力；缺少它们时，部分深度检查会跳过。

## 版权与授权

Skill 4.0 不包含用户购买模型包的原始模型、贴图、动画、音效或完整配置。参考包仅用于提炼工程方法、检查规则与通用模板。
