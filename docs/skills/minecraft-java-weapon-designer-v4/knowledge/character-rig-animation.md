# 人物骨骼与动画工程知识

## 骨骼树

人物骨架应按功能而不是视觉碎片拆分。常见层级：

```text
body
├─ upper_body
│  └─ chest_or_upper_body2
│     ├─ head
│     ├─ right_arm → right_wrist → right_hand_attachment
│     ├─ left_arm → left_wrist → left_hand_attachment
│     ├─ right_wing → right_wing_end
│     └─ left_wing → left_wing_end
├─ right_leg → right_ankle
└─ left_leg → left_ankle
```

命中盒、粒子起点、眼睛位置和武器挂点可以作为独立根骨骼或空骨骼。没有 cube 并不代表无用。

## 动画质量

- idle 和 walk 应循环，并在首尾姿态连续。
- attack 和 cast 通常不循环，末帧应与 animation_length 对齐。
- 动作时长与服务端 delay 需要按 tick 对齐。
- 使用 Catmull-Rom 时要检查过冲，尤其是长武器、翅膀和头部。
- 大幅动作要扩大 visible bounds，避免客户端裁切。
- 左右镜像动作不能只改动画名；手腕、武器骨骼和伤害方向也要同步。

## 性能

人物性能不能只看 cube 数量，还要看：

- 同屏实体数量
- 每段动画涉及的骨骼与关键帧数量
- 大尺寸透明图集
- 发光层和双面平面
- 粒子、音效、投射物与盔甲架数量
- MythicMobs 高频 timer 和 totem 检测

先修复引用错误，再做几何或贴图优化。
