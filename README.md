# 亡途荒夜 (Yongye)

极难灾变生存玩法 · Minecraft **Fabric 1.21.1**

> 白天跑图，夜晚逃命，永夜追杀；靠**装备血量 + 技能书(V65535)+ 背包神器 + 随机掉落**反向变强。

本仓库当前进度:**Phase 0(工程骨架)+ Phase 1(核心成长循环)**。

---

## 一、当前已实现(Phase 0 + Phase 1)

### Phase 0 — 工程骨架
- Fabric Loom 工程(Gradle 8.10.2 wrapper 已内置)
- `fabric.mod.json`、mixin 配置(Phase 1 暂无 mixin,配置已就位供后续使用)
- 配置系统:`config/yongye.json`,所有平衡数值可调,无需改代码
- 注册框架:物品 / 数据组件 / 数据附着 / 物品组 / 配方序列化器

### Phase 1 — 核心成长循环
| 系统 | 说明 | 关键文件 |
|---|---|---|
| 怪物基础增强 | 敌对怪出生即加血/攻/速/击退抗性/感知范围,按概率附带随机正面药水,只增强一次 | `system/MobEnhancementHandler` |
| 套装血量加成 | 穿齐同材质整套盔甲给额外最大生命(皮革+5 / 锁链+8 / 铁+10 / 金+20 / 钻+30 / 下界合金+40) | `system/ArmorHealthHandler` |
| 血量强化技能书 | V1~V65535,右键学习永久 +等级×10 最大生命,跨死亡保留 | `item/HealthSkillBookItem`、`system/PlayerSkillManager` |
| 技能书同级合成 | 工作台内 2 本同级书(+按阶段需材料)→ 高一级书,封顶 V65535 | `recipe/HealthBookCombineRecipe` |
| 随机掉落 | 敌对怪死亡按品质表(普通/实用/稀有/史诗/神级 = 60/25/10/4/1%)掉落,稀有以上掺入技能书与材料 | `system/LootHandler` |
| 超稀有材料 | 生命碎片/结晶/核心/灾变血核/永夜之尘/裂界残片/深渊魂晶/终焉神髓(8 种) | `registry/ModItems` |

**技能书合成材料门槛**(默认值,配置可调):合成到 V10+ 需生命结晶,V100+ 需生命核心,V1000+ 需灾变血核。

---

## 二、构建

需要 **JDK 21**。

```bash
./gradlew build
```

产物在 `build/libs/yongye-0.1.0.jar`。把它(以及 Fabric API、Fabric Loader)丢进 `mods/` 即可。

> ⚠️ 说明:本工程在沙箱内编写,沙箱网络未放行 Fabric/Minecraft 的 Maven 源,因此**未能在沙箱实际跑通 `./gradlew build`**。代码按 1.21.1 + Fabric API 的正确 API 编写并逐项自检,但首次本地构建若遇到个别版本/映射不匹配,按 `gradle.properties` 顶部注释微调 `yarn_mappings` / `fabric_version` 即可(参考 https://fabricmc.net/develop )。

---

## 三、配置

首次运行生成 `config/yongye.json`,可调项包括:

- 总开关:`enableMobEnhancement` / `enableArmorHealthBonus` / `enableRandomLoot`
- 怪物增强:血量/攻击/移速倍率、击退抗性、感知范围、随机药水概率
- 套装加成:6 档材质的额外最大生命
- 技能书:封顶等级、三档材料门槛
- 随机掉落:5 档品质概率、生命碎片掉率、是否要求玩家击杀

---

## 四、后续路线

- **Phase 2**:精英怪(一秒五箭骷髅 / 一秒五喷女巫等)、Boss 翻倍
- **Phase 3**:永夜五级(暗潮/猎杀/围城/灾变/灭世)+ 赎夜、随机任务、追杀 AI(挖墙/爬墙/瞬移)
- **Phase 4**:背包神器系统、高血量反制(百分比/真实伤害/最大生命压制/禁疗)

详见设计文档 `亡途荒夜玩法设计.md`(20 章)。

---

## 许可

All Rights Reserved · © qiaodaxian233
