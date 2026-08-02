# 人物、NPC 与 Boss 检查清单

## 授权
- [ ] 已记录商品来源、版本和许可范围
- [ ] 未把购买素材嵌入公开 Skill 或模板
- [ ] 保留作者 credit

## 几何与骨骼
- [ ] geometry identifier 唯一
- [ ] 骨骼名唯一
- [ ] parent 全部存在且无循环
- [ ] 根骨骼用途清楚
- [ ] 左右臂、腿、翅膀命名一致
- [ ] 武器和特效挂点父级正确
- [ ] hitbox/locator/空骨骼没有被误删
- [ ] UV 没有越界
- [ ] texture size 与 PNG 一致
- [ ] visible bounds 覆盖所有动作

## 动画
- [ ] 动画名与技能调用大小写一致
- [ ] loop 状态合理
- [ ] animation_length 与末关键帧一致
- [ ] 没有引用不存在的骨骼
- [ ] idle/walk 首尾连续
- [ ] attack 命中帧已记录
- [ ] death 与实体移除时间一致
- [ ] Catmull-Rom 没有明显过冲

## 服务端同步
- [ ] EntityModel 文件引用存在
- [ ] MythicMobs Mob 调用的 Skills 存在
- [ ] Skills 调用的动画存在
- [ ] Skills 调用的音效存在
- [ ] 投射物 Mob 和 Item 存在
- [ ] ItemModel 映射能命中显示名或标识
- [ ] GCD、delay、伤害帧和音效帧已同步

## 渲染和碰撞
- [ ] glowTexture 是预期遮罩
- [ ] 透明平面无明显 Z-fighting
- [ ] 翅膀和剑气双面显示正常
- [ ] 模型 scale 与服务端碰撞盒匹配
- [ ] hitbox 骨骼与真实碰撞盒职责清楚
- [ ] 大幅动作不被裁切

## 性能
- [ ] 多个 Boss 同屏测试
- [ ] 高频 timer 技能测试
- [ ] 投射物/盔甲架及时清理
- [ ] 音效和粒子没有重复触发
- [ ] 客户端和服务端副本哈希一致
