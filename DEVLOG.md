# 开发记录（DEVLOG）

> 本项目从一份玩法设计文档起步，通过多轮迭代逐步落地。下面按里程碑整理开发历程与每一步的关键决策，便于回溯「为什么这么做」。
> 工作流：代码在沙箱内编写 + 静态自检 → 用户在本地 IDEA（JDK 21）`./gradlew build` 验证 → 报错回传精确修复 → push 到 `main`。

---

## 里程碑 1 — Phase 0 + Phase 1（工程骨架 + 核心成长循环）
- 从零搭 Fabric Loom 1.21.1 工程：内置 Gradle 8.10.2 wrapper、`fabric.mod.json`、mixin 配置占位、JSON 配置系统、五套注册框架（物品/组件/附着/物品组/配方序列化器）。
- 落地核心循环：怪物基础增强、套装血量、血量技能书 V1~65535、同级合成、随机掉落品质表、8 种稀有材料。
- PIL 生成首版物品贴图与图标。
- 首次 push 成功，建立 `main` 分支。

## 里程碑 2 — Phase 2（精英怪 + Boss 翻倍）
- `EliteHandler`：概率精英化 + 发光名牌、精英骷髅一秒五箭、精英女巫一秒五喷、瞬移、召援、精英专属掉落。
- `BossHandler`：识别五种 Boss，属性·掉落翻倍。
- 全程零 mixin，事件 + tick 驱动。

## 里程碑 3 — Phase 3 + Phase 4 一次性落地
- Phase 3：永夜五级 + 赎夜（`NightfallManager`，存档持久化）、随机任务带 Boss 血条（`QuestManager`）、追杀 AI 锁定/挖墙/爬墙（`PursuitHandler`，纯 tick）。
- Phase 4：10 种背包神器（`ArtifactManager`）、高血量反制（`HighHpCounterHandler`）、`/yongye` 指令、10 种神器资源。

## 里程碑 4 — 首次本地编译，逐轮修复 1.21.1 API
按用户 build 报错依次修复（详见 HANDOVER「踩过的坑」）：
1. `ServerEntityEvents` 包名（`entity.event.v1` → `event.lifecycle.v1`）。
2. `CraftingRecipeInput.size()` → `getSize()`；`SpecialRecipeSerializer` 顶层类；删除已移除的 `setPunch`。
3. 主动自查发现 `AFTER_DAMAGE` 是 1.21.2 才有 → 反制改用 `ALLOW_DAMAGE`。
4. `build.gradle` 的 `archivesName` → `project.base.archivesName.get()`。
- 最终 **BUILD SUCCESSFUL**，产出 `yongye-0.1.0.jar`。

## 里程碑 5 — 实机反馈修复 + 美术接入
- 修复：怪物爬墙（循环提到每 2 tick + 正前方墙判定持续上推）、精英瞬移（主动感知 48 格锁定 + 太远/卡住触发）、任务崩端（逐条 try/catch + 日志兜底）、前 5 分钟无任务宽限、任务循序渐进（永夜<2 只派可达成）、精英血量 5→3 倍。
- 澄清「变创造」非本 mod 所为（全工程无 setGameMode；追杀会跳过创造玩家，故创造下测不到追杀）。
- 接入用户用 GPT 制作的 18 个材料/神器图标，升级到 64×64 高清。

## 里程碑 6 — 精英专属皮肤（客户端渲染）
- `EliteSkinFeatureRenderer`：客户端给生物渲染器挂叠层，**仅名字带「精英」的怪** 显示 mod 内自定义贴图，不覆盖原版。确认 1.21.1 为渲染重构前旧体系（`FeatureRenderer`、`int` 颜色参数）。

## 里程碑 7 — 技能书扩展 + HUD
- 新增 6 本属性技能书（攻击/护甲/恢复/闪避/反伤/抗性）：物品 + 学习 + 同级合成 + 指令 + 创造栏 + 中英文。
- HUD 紧凑显示（客户端 mixin，本项目首个 mixin）：血量/护甲过大时改画「图标 ×数值」，解决高血量心形铺满屏幕挡视线。

## 里程碑 8 — 技能书掉落 + 成长面板
- 6 本新书接入掉落：普通 2%（永夜加成）/ 精英 60% / Boss 必掉，补齐「合成需先有 2 本」的获取缺口。
- 成长面板（无指令）：背包左上「成长」按钮 → 自定义网络包同步已学技能 → `StatsScreen` 列出等级与实际效果。

## 里程碑 9 — 灾厄核心
- `CatastropheCoreManager` + 自定义方块：永夜≥2 自然生成、持续刷精英、摧毁掉落并降一级永夜；掘墓人罗盘优先指向核心；新增「清除灾厄核心」任务。不用 BlockEntity，服务端管理器 + GSON 持久化。

## 里程碑 10 — Boss 专属机制 + 核心赎夜闭环
- `BossAbilityHandler`：在翻倍之上叠加 持续减伤 / 锁定 / 狂暴(<50%) / 召唤援军 / 冲击波击退，Boss 从沙包变灾变事件。
- 摧毁任意灾厄核心降一级永夜，形成「失败升永夜 ↔ 清核压回」的小闭环。

## 里程碑 11 — 长门（佩恩）Boss
- `PainBossHandler`：以 Husk 为载体套长门皮，四技能（神罗天征/万象牵引/地爆天星/轮回天生）。
- 处理皮肤：长门皮 64×32 旧格式转 64×64（补左肢）；僵尸 boss 皮替换精英僵尸占位。
- 放弃了 GeckoLib + Blockbench 自定义模型路线，改用标准皮肤套 vanilla 模型，免前置 mod、更稳。

## 里程碑 12 — 长门终局闭环
- 永夜达 IV(灾变)级后，长门作为「六道之痛」按概率**自然降临（全局唯一）**；**击败 → 永夜直降 2 级**。
- 至此终局节奏成立：苟住 → 永夜逐级升 → 灾变级招来长门 → 拼死击杀 → 压回黑暗。

## 里程碑 13 — 装备无限强化 + 品质系统
- `EquipmentEnhancer` + `EquipmentEnhanceRecipe`：武器/盔甲 + 材料(碎片+1/结晶+10/核心+100/血核+1000,每格 1 个)→ 强化等级**无上限**,加成写进 `AttributeModifiersComponent`(跟随物品)+ 耐久按级提升。
- `WeaponQuality`(普通→至尊 9 阶):由强化等级换算,含颜色/稀有度字母/暴击率/攻速;`WeaponCombatHandler` 用 `AttackEntityCallback` 实现武器暴击(先打再清无敌帧叠加)。
- 富 tooltip(品质+稀有度+强化等级);背包「装备」按钮打开 `WeaponInfoScreen` 武器介绍界面;`/yongye enhance <等级>`。
- 踩坑:确认 1.21.1 `EntityAttributeModifier(Identifier,double,Operation)`、`AttributeModifiersComponent.with(...)`、`ItemTooltipCallback` 4 参回调、`LivingEntity.timeUntilRegen` 字段。

## 里程碑 14 — 主动武器技能
- 三技能按品质解锁:混沌斩(稀有,锥形斩击+击退)、深渊吞噬(史诗,范围吸血)、终焉降临(神器,大范围+上抛)。
- 客户端 `KeyBinding`(默认 R/G/V)+ tick 轮询 → C2S `SkillUsePayload` → 服务端 `WeaponSkillManager.use` 结算 + 冷却(`Map<UUID,long[]>`)。
- 武器介绍界面增设技能区(解锁状态 + 冷却);加 `key.categories.yongye` 等按键语言条目。

## 里程碑 15 — 怪物随进度递增
- 此前怪物只有出生固定倍率,玩家变态后怪沦为沙包。
- `MobEnhancementHandler` 增设递增缩放:出生时按 **永夜等级 + 游戏天数 + 附近玩家最大生命** 计算进度倍率,提升怪物**血量(+攻击按比例同步)**,有上限。怪与玩家一起变强。

## 里程碑 16 — 混沌之刃专属武器 + 硬核开局生存包
- **混沌之刃**(`ChaosBladeItem`):固定高基础属性(攻击≈+30、攻速、2500 耐久),三大主动技能**无需品质解锁**即可施放(`WeaponSkillManager` 特判 CHAOS_BLADE);GPT 贴图转 64×64;创造栏可取,长门 15% 掉落;仍可继续强化。
- **硬核开局生存包**(`HardcoreSurvivalHandler`,对应设计前 8 条,全可配):睡觉不跳夜(`playersSleepingPercentage=101`)、食物紧张(持续饥饿)、火把不安全(夜晚无视亮度伏击)、洞穴危险(地下刷怪+失明/挖掘疲劳)、木石矿难采(挖掘疲劳+额外饥饿)。
- 设计 15 条核对:9–15 已全做;1–8 由本包补齐(村庄不安全/石器时代以通用夜袭+资源难采近似)。

## 里程碑 17 — 逻辑漏洞修复(深度审查)
编译通过后做了一轮逻辑审查,修掉 8 个非表面问题:
1. **技能误伤友方**:混沌斩/深渊吞噬/终焉降临原本打所有 LivingEntity(含村民/宠物/玩家)→ 改为只打敌对(`Monster` 或我方标记 Boss/长门,`isHostileTarget`)。
2. **禁疗漏吸血**:深渊吞噬吸血未检查 `NO_HEAL_UNTIL` → 现禁疗时不回血。
3. **暴击无视蓄力**:连点也能暴击 → 加 `getAttackCooldownProgress>=0.9` 判定。
4. **吸血可一次回满**:治疗上限改为最大生命百分比(`skillDevourHealMaxPct=0.25`)。
5. **内存只增不减**:技能冷却表 / 高血量压制表加玩家断开清理(`ServerPlayConnectionEvents.DISCONNECT`)。
6. **夜袭对地下玩家空放**:玩家在地下时改用其所在高度刷怪。
7. **强化免费修耐久**:`withLevel` 不再清零损耗,改为保留(提升上限但不修复)。
8. **长门重启可能重复**:自然降临前扫描在线玩家附近已存在的长门并认领,避免重启后再刷一个。

---

> 后续待办、已知边界与可做方向见 **[HANDOVER.md](HANDOVER.md)** 第 6 节。
