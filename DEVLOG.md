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

## 里程碑 18 — 挖掘减速(极难生存核心)
- 之前「木头难获取」是砍完给个挖掘疲劳(等级0/3秒),基本无感 → 弃用。
- 改为 `MiningSpeedMixin` 注入 `PlayerEntity#getBlockBreakingSpeed` 返回值,**直接对木头/石头/矿物乘减速系数**(默认 0.3 ≈ 耗时 3.3 倍),只影响挖方块、不碰攻速。
- 配置:`hcMiningSlowdown` 开关 / `hcMiningSpeedMultiplier` 系数 / `hcMiningSlowAll`(是否所有方块都减速)。AFTER 事件去掉会拖慢攻速的挖掘疲劳,仅保留挖矿额外扣体力。

## 里程碑 19 — 追杀防白嫖(卡住传送)
- 发现漏洞:用船卡住怪、或隔着水/岩浆/挖不动的墙,怪就够不到玩家,「永夜锁定/挖墙追杀」被白嫖。
- `PursuitHandler` 加卡住跟踪(记录追击中距玩家的最小距离及取得时刻):**骑乘载具(船)/ 长时间无进展且前方有墙 / 泡在水或岩浆里** 时,传送到玩家附近可站立的安全点(相近高度、脚下实心、两格空气、无流体),附末影传送粒子+音效。
- 受 `世界之锚` 神器与配置 `pursuitTeleportStuck` 约束;参数 `pursuitStuckTicks/pursuitTeleportRadius/pursuitTeleportMinDist` 可调。

## 里程碑 20 — 佩恩音效 + HIM 突脸惊吓
- **音效**:`ModSounds` 注册 4 个事件;仪礼=遭遇 BGM(降临 `playSoundToPlayer`、死亡发 `StopSoundS2CPacket` 收住),神罗天征/万象天引/地爆天星=各自技能音效(技能触发时在佩恩处播放)。MP3 经 ffmpeg 转 OGG。
- **HIM**(`HimJumpscareHandler`):极低概率(夜晚/黑暗)在玩家正前方出现静止人形,约 1.75s 后冒烟消失;**无 AI、无敌、零伤害**,已从增强/精英/追杀系统排除;名「HIM」走套皮渲染 `textures/entity/him.png`。
- 注:若仓库中 OGG 为静音占位,表示用户需重新上传 4 个 MP3 后再换入真音频。

## 里程碑 21 — 修复:精英发光触发渲染mod崩溃
- 玩家崩溃报告:NPE 在第三方 `Accelerated Rendering`(误装 1.20.1 版于 1.21.1)的实体描边 mixin,根因是其未对发光描边 framebuffer 判空。
- 触发源是本 mod 给精英怪挂的**永久 GLOWING**(及精英箭的 GLOWING)。
- 修复:精英发光改配置项 `eliteGlowing`(默认 false,精英已有金色名牌识别);精英箭 GLOWING 换为 NAUSEA。本 mod 不再触发该描边路径。

## 里程碑 22 — 混沌之刃合成配方 + 直给命令
- 新增 shaped 配方 `data/yongye/recipe/chaos_blade.json`:无尽夜尘×2 + 终焉神髓 + 深渊之魂结晶×2 + 下界合金剑 + 裂隙碎片×2 + 灾变血核 → 混沌之刃(物品默认属性来自 Item.Settings,配方无需 NBT)。
- 新增命令 `/yongye chaosblade` 直接给一把(测试用,OP)。

## 里程碑 23 — 佩恩血量提升
- `painBossMaxHealth` 默认 400 → 1000(佩恩不吃怪物缩放,血量为固定值)。旧配置文件需手动改或重生成。

## 里程碑 24 — 盔甲强化识别兜底
- `EquipmentEnhancer.kindOf` 增加兜底:`item instanceof ArmorItem` 也判定为盔甲(防止个别盔甲未在 attribute_modifiers 组件暴露 generic.armor 时被判成不可强化)。修复部分盔甲无法强化/「装备」按钮打不开。

## 里程碑 25 — 饱食度技能/任务死亡不判败/前期压制/怪物调强/嵌墙传送/精英缴械
- **饱食度强化技能**(SkillType.SATIETY):每秒补充饱食度+饱和度,等级越高越不会饿;自动有物品/命令/合成,贴图+中英文+模型齐。
- **任务·守住据点死亡不判败**:撑到时间即成功(本就不因死亡失败);修复死亡重生后 Boss 血条失效——每 tick 重新挂回当前玩家。
- **前期技能书压制**:前 `skillBookEarlyGameDays`(默认3)游戏日内,普通怪技能书爆率 ×`skillBookEarlyGameChance`(默认0.2)。
- **怪物调强**:基础血 ×2→×3、攻击 ×1.5→×2.2、移速×1.2;缩放每永夜+80%、每日+6%、攻击比0.4。
- **嵌墙怪传送**:追杀半径内敌对怪整只卡进实心方块(shouldSuffocate)→ 立刻传送到玩家附近(不要求在追你)。
- **精英缴械**(eliteCanDisarm/Chance/Cooldown):精英命中玩家概率夺走主手武器、自己装上,死亡掉落(击杀夺回);带冷却。

## 里程碑 26 — 搜集任务 + 多人失败广播
- 新增任务 **GATHER 搜集物资**:限时内集齐随机指定物品(铁/金/钻/煤/骨/腐肉/火药/线/末影珠/红石/黏液球/皮革/生命碎片之一),到点没集齐即失败。命令 `/yongye quest gather`,已入轮换(前/后期均可派)。
- **多人失败广播**:任意玩家任务失败 → 向全服广播是谁未完成 + 全局永夜 +1(强化协作压力)。

## 里程碑 27 — 守护附魔书(防缴械)
- 新增组件 `DISARM_PROOF`(Boolean,挂武器上)。
- 新增物品 **守护附魔书 `ward_book`**(模型复用原版附魔书外观):一只手持武器、另一只手右键本书 → 武器获得「无法被夺取」,精英缴械判定跳过该武器。
- 武器 tooltip 显示「⚔ 无法被夺取」;命令 `/yongye wardbook` 直给;已入创造栏。

## 里程碑 28 — 防卡死 + 缴械漏洞修复 + 配置重置
- **防卡死**:新增全局怪量预算 `globalMaxHostilesNearby`(默认60,半径 `globalHostileRadius`28)——附近敌对生物过多时,夜袭/洞穴/灾厄核心不再额外刷怪;追杀/嵌墙传送加每 tick 上限 `pursuitMaxTeleportsPerTick`(默认3),避免一口气把大量怪传到玩家身边。
- **缴械漏洞修复**:被精英夺走武器后,该精英 `setPersistent()` 不再自然消失,确保玩家能击杀夺回武器。
- **配置重置命令** `/yongye config reset`:一键把配置重置为默认值并写盘(省去手改/删配置文件)。

## 里程碑 29 — HUD:护甲与简易血量同排、修复护甲浮动
- 紧凑血量接管时,护甲值改为画在**同一排(血量右侧)**;并取消原版护甲条(原版护甲会被多行心数顶得往上"飘")。血量正常但护甲>20 时仍单独紧凑显示在固定位置。

## 里程碑 30 — 抢夺技能(玩家版,可强化到 65535)
- 新增技能书 **抢夺强化 `skill_book_steal`**(SkillType.STEAL):命中怪物时按等级概率(`skillStealChancePerLevel` 每级+0.5%,上限 `skillStealMaxChance` 0.9)夺取怪物主手物品并给玩家(背包满则掉落)。可升到 65535(到达上限概率封顶)。与精英缴械互为反制:可把被抢的武器从精英手里抢回。
- 命令 `/yongye skillbook steal <等级>`;贴图暂为占位(复制 thorns),待替换。

## 里程碑 31 — 任务全面加难 + 掉落收紧
- **限时缩短**:questTimeLimitTicks 3600→1800。
- **目标量提升+随永夜**:猎杀=击杀N只精英(questHuntEliteCount,+永夜/2);逃离距离 50→questFleeDistance120(随永夜+);搜集数量翻倍且随永夜×。
- **新任务 SLAY 屠戮**:限时内击杀 N 只怪物(questSlayCount,+永夜×5);猎杀/屠戮血条实时显示 X/N。
- **完成奖励重做**:不再保底堆钻石/金苹果;改保底血量书(等级随永夜)+ 按概率给生命结晶/核心/顶级材料(终焉神髓等)/附魔金苹果,概率与品质随永夜走高(更稀更值)。
- **掉落收紧**:普通怪掉落概率整体压低(common .60→.35 等,余下落空);稀有材料更难爆(生命碎片 .05→.02、精英结晶/核心 .5/.2→.25/.08);技能书更难得(普通 .02→.008、精英 .6→.3、前期压制 6天×0.1)。

## 里程碑 32 — 复活满血 / 创造转生存 / 取消挖掘限制 / 任务按人数加难 / 强化石爆率
- **复活满血**:AFTER_RESPAWN 在重应用最大生命后 `setHealth(maxHealth)`,不再 20 血复活。
- **创造转生存**:`forceSurvival`(默认开)创造模式每秒自动切回生存;`forceSurvivalExemptOp`(默认开)OP 豁免。
- **取消挖掘限制**:`hcMiningSlowdown` 默认关(砍树/挖矿恢复正常速度);洞穴 debuff 去掉挖掘疲劳,仅保留致盲。
- **任务按人数加难**:`questPlayerScaling`(默认0.5),每多一名在线玩家,猎杀/屠戮/搜集目标量倍率增加。
- **强化石(生命碎片)爆率**:lifeShardDropChance 0.02→0.01。

## 里程碑 33 — 饰品栏(自定义容器,无外部依赖)
- 从零实现 4 格饰品栏(不依赖 Trinkets/Cardinal Components):`AccessoryScreenHandler`(只接受神器)+ `AccessoryScreen`(纯填充背景)+ `ModScreens` 注册 ScreenHandlerType + `ACCESSORIES` NBT 附件存档 + `OpenAccessoryPayload` C2S 开界面 + 背包「饰品」按钮。
- 神器效果(ArtifactManager.getActiveLevel)同时扫描饰品栏,放进去即生效、不占背包格。
- 说明:Trinkets 为 MIT 开源但依赖 Cardinal Components,不内嵌源码;此为自带轻量实现。**此功能体量大、未能本地编译,待联调。**

## 里程碑 34 — 饰品栏扩容/自定义背景 + 面板补全
- **饰品栏 4→10 格**(2 行 5 列):刚好放下全部 10 种神器,可全部同时生效。
- **饰品栏自定义背景**:界面改用贴图 `textures/gui/accessory_gui.png`(176×158),已放占位图;槽位描边始终绘制,换图不影响。用户可用 GPT 出 176×158 的图替换。
- **成长面板补全**:NAMES/descs 从 6 项补到 8 项(加 饱食强化、抢夺强化),不再漏显示新技能(注:技能等级同步用变长数组,回血数值本身无错位)。

## 里程碑 35 — 饱食跳动修复 / 强化材料爆率 / HIM失明 / 神器配方
- **饱食跳动修复**:applySatiety 改为直接钉满饱食度(20)+留足饱和度缓冲+清零耗竭,食物条不再抖;饱食充盈时缓慢回血(尊重禁疗)。
- **强化材料爆率**:生命碎片必掉(1.0)、生命结晶常掉(0.65)、生命核心稀有(0.05),精英结晶/核心翻倍。
- **HIM 失明预警**:HIM 出现前玩家先失明 5 秒(PENDING 延迟生成)。
- **神器合成配方**:为全部 10 种神器各加一个主题 shaped 配方(合成即 1 级,可用 artifact_upgrade 升级)。

## 里程碑 36 — 时间进度系统(类「惊变」)
- 新增 `ProgressionManager` + 配置(enableProgression 等),按游戏天数驱动:
  - **第一天长白天**:首个白天放慢到 firstDayMinutes(24)分钟(仅主世界,总时间 0..12000)。
  - **新手保护**:第一天白天不刷额外怪(洞穴/夜袭跳过)。
  - **精英按天解锁**:第3天前无精英;第3~4天小概率(×0.3);第5天起 +65%(×1.65)。接入 EliteHandler 刷新判定。
  - **每10天进化**:evolutionMultiplier(每阶段 +50%)乘入 MobEnhancement 缩放。
  - **早期怪不挖方块**:PursuitHandler 挖掘按 mobDigStartDay(5)解锁。
- 怪物随永夜/天数渐强逻辑保留(MobEnhancement),配合进化倍率叠加。

## 里程碑 37 — 职业系统(B,第一版)
- 6 职业 PlayerClass(肉盾/战士/术士/剑客/武僧/刺客),属性修饰由 ClassManager 每秒应用(最大生命/护甲/攻击/移速/方块·实体交互距离)。
- **获得**:精英概率掉落职业书 `class_book_<id>`(classBookDropChance 0.15),右键学习;`/yongye classbook <type>` 给书。
- **等级门控**:第一职业需 classLevel1(50)、第二职业需 classLevel2(100);**降级跌破即失去该职业**(消失,需重新学)。最多 2 职业。
- **特性**:武僧空手击杀生物 → 永久 +1 拳击伤害(空手时生效);刺客夜视。
- 附件 LEARNED_CLASSES(有序,copyOnDeath)+ MONK_FIST_BONUS。
- **未完(待下轮)**:武僧武器耐久×2(需 mixin)、刺客暴击专属机制(暂用攻击加成代替)、术士远程伤害专属加成(暂用攻击+交互距离代替)、职业选择 GUI。**整套未本地编译,待联调;交互距离属性字段名是最可能的编译点。**

## 里程碑 38 — 修复佩恩早期降临 + /yongye level
- **佩恩自然降临加游戏天数门**:painSpawnMinDay(默认5),早期无论永夜多高都不降临。
- **任务防雪崩**:新手期不派任务;第3天(eliteStartDay)前不派「猎杀精英/屠戮/清核心」等做不到的任务——避免连续失败把永夜快速顶到IV触发佩恩。
- **新增 `/yongye level <n>`**:直接设玩家经验等级(方便测职业,免去 /xp points 报错)。

## 里程碑 39 — 守住据点死亡判败 / 饱食每tick钉住 / 稀有材料进神器配方 / 交接文档完成度
- **守住据点(SURVIVE)死亡即判败**:玩家死亡时若有该任务则失败(原为死亡不判败);标题改「死亡判败!」。
- **饱食每 tick 钉住**:新增每 tick 修正(食物=20/耗竭=0/饱和度≥5),解决秒间被原版缓慢扣减的「慢掉」问题。
- **稀有材料用途扩展**:5 种稀有材料原即混沌之刃合成料;现再各掺入一个高阶神器配方(生命神像←灾变血核、不灭余烬←终焉神髓、永夜之眼←无尽之夜尘、世界之锚←裂隙碎片、巫毒净瓶←深渊之魂)。神器配方(10 个)已于 m35 完成。
- **HANDOVER.md 更新**:项目完成度≈85% + 最近几轮新增待办(职业深化/专属机制/选职GUI/编译点/美术占位)。

## 里程碑 40 — 天赋树系统(第一版,命令驱动)
- **新增 `system/TalentManager`**:6 职业各 5 个天赋节点(共 30),每职业 4 个属性节点 + 1 个「技能」节点(持续状态增益),带前置(prereq)形成小树。
- **发点规则**:等级达到 `talentStartLevel`(默认 50,与首职业门槛对齐)后,每升 1 级发 `talentPointsPerLevel`(默认 1)点;附件 `TALENT_CLAIMED_LEVEL` 记最高已发等级,**掉级不重复发、已得点不回收(死亡保留)**。存量角色首次进服按区间补发。
- **加点命令**:玩家可用的 `/talent`(**不需 OP**——与权限2的 `/yongye` 分开注册):`/talent` 总览、`/talent list` 列表、`/talent learn <id>` 投点、`/talent reset` 全额返还、`/talent info <id>`。单点消耗 1,带满级/前置/点数校验。
- **生效链路**:天赋的属性修饰 / 持续增益挂进 `ClassManager.applyClasses` 每 20 tick 重刷,**仅对当前够等级生效的职业应用**;职业因掉级休眠时天赋点保留但不生效。修饰用稳定 Identifier `talent_<cls>_<node>_<attr>` 先清后加,杜绝叠加。
- **技能节点**先以持续状态效果落地(等级=rank-1):守护者→抗性、狂战/剑气→力量、急速咏唱→急迫、吐纳→生命恢复、疾风步→迅捷。真·主动/触发技能 + mixin 机制留下一里程碑。
- 新增附件 `TALENT_POINTS / TALENT_CLAIMED_LEVEL / TALENTS(Map)`(均 persistent + copyOnDeath);新增配置 `enableTalents / talentStartLevel / talentPointsPerLevel`。
- **编译点(IDEA 注意)**:`TalentManager.ATTRS` 新用 `GENERIC_ATTACK_SPEED / GENERIC_KNOCKBACK_RESISTANCE / GENERIC_ARMOR_TOUGHNESS / GENERIC_LUCK`,1.21.1 若字段名不符则改;m37 两交互距离属性已随本轮 build 验证通过。

## 里程碑 41 — 职业专属技能(触发型,纯事件实现)
- **新增 `system/ClassSkillHandler`**:六职业签名机制,全部用 Fabric 事件(`AttackEntityCallback` / `ServerLivingEntityEvents.ALLOW_DAMAGE` / 服务器 tick)实现,**不依赖 mixin**;追加伤害沿用 `WeaponCombatHandler` 的「`target.damage(...)` 后 `timeUntilRegen=0`」叠加法。
- **战士**:吸血(近战命中按攻击力比例回血)+ 斩杀(目标生命 ≤20% 且非 Boss/玩家时,追加 最大生命×50% 伤害)。
- **坦克**:嘲讽(每 40t 把半径内怪物目标拉到自己)+ 护盾(每秒续吸收 II);**减伤%** 由 m40 守护者天赋(抗性)覆盖。
- **刺客**:背刺(从背后命中追加伤害)+ 闪避(概率完全免疫一次实体攻击,`ALLOW_DAMAGE` 否决)+ 脱战加速(脱离战斗 5s 后迅捷)。
- **术士**:潜行近战 → 牺牲生命(默认 3 心)对目标周围造成范围魔法伤害(高风险高回报)。
- **武僧**:空手连击(连续命中同一目标叠伤,封顶 5 层)+ 缴械(概率打掉持械怪物主手);拳意见 m37。
- **剑客**:剑气波(持剑命中时对前方区域附带伤害)+ 格挡反击(举盾被近战命中时否决并反伤)。
- 全部受职业等级门控(`ClassManager.isActive` 纯查询,掉级即失效);连击/脱战用瞬态内存表,无新增持久化。新增配置段 `enableClassSkills` + 各职业数值(伤害/概率/半径/消耗皆可调)。
- **编译点(IDEA 注意)**:主要看 `ServerLivingEntityEvents.ALLOW_DAMAGE` 在该 Fabric API 版本是否存在/签名一致(闪避·格挡反击用它否决伤害);其余 API 均与 `WeaponCombatHandler`/`ClassManager` 既有用法一致。数值未实测,后续按手感调。

## 里程碑 42 — 职业专属武器
- **新增 `item/ClassWeaponItem`**(统一类,6 实例,仿 ClassBookItem/ArtifactItem):战士 巨阙、坦克 镇魂、刺客 影刺、术士 噬魂杖、武僧 鬼神拳套、剑客 流光。EPIC 稀有度、`maxDamage` 2000。
- **差异化主手基础属性**:攻击/攻速用 `BASE_ATTACK_DAMAGE/SPEED_MODIFIER_ID`(tooltip 显示为总值),其余走 MAINHAND 槽 Identifier 修饰——战士高攻慢重+生命;坦克中攻+护甲+击退抗性+生命;刺客快攻+移速;术士高攻+交互距离+幸运但 -生命(玻璃大炮);武僧极快+击退抗性;剑客均衡+交互距离。
- **专属协同**(手持且本职业生效,`ClassSkillHandler` 读 `ClassWeaponItem.held`):战士斩杀阈值↑、吸血×2;坦克护盾+1 级、嘲讽半径×1.5;刺客背刺×2、闪避+12%;术士 AoE 半径+2、伤害×1.5、耗血-2;武僧拳套视为空手可连击、连击封顶+3、每层×1.5;剑客剑气波范围+2、伤害×1.5、格挡反击×1.5。普通玩家可当高属性武器用,但吃不到协同。
- 注册进 ModItems(EnumMap)+ 创意标签;给予命令 `/yongye classweapon <id>`;资源:6 物品模型(暂 parent 到贴合的原版物品当占位——剑/锤/法杖/拳套等)+ 中英文名。
- **美术**:6 把已配 AI 生成像素贴图(白底抠透明、裁剪居中、缩 32×32),模型改为 `handheld`+`layer0`(斜握姿势)。**待续**:获取途径(拟精英/Boss 稀有掉落或稀有材料合成,目前仅创造/命令)、数值平衡。

## 里程碑 43 — 开局选职业(出生定本命职业)
- **进度模型**(用户拍板):出生即选定**本命职业**(第一职业,0 级即生效、**不因掉级失去**);**职业书**改为习得**第二职业**(仍需 classLevel2)。
- **流程**:登录时若 `STARTING_CLASS_CHOSEN` 为假且无任何职业 → 服务端发 S2C `OpenClassSelectPayload`,客户端进入世界后弹 `ClassSelectScreen`(六按钮,强制选、屏蔽 ESC),点击发 C2S `ChooseClassPayload(classId)`,服务端 `ClassManager.chooseStartingClass` 授为第一职业。老玩家(已有职业)只补标记不弹窗。客户端用 `pendingClassSelect` 标记 + tick 延迟弹出,避开登录过场覆盖。
- **ClassManager**:`isActive` 第一职业 0 级即生效、第二职业需 classLevel2;`enforceAndGet` 去掉对第一职业的掉级剥夺(本命永久),仅第二职业掉级失去;新增 `chooseStartingClass`(防重复/防刷,可选附赠专属武器)。
- 新增附件 `STARTING_CLASS_CHOSEN`;配置 `enableStartingClassSelect`(默认开)、`startingClassGiveWeapon`(默认**关**,避免出生白嫖 EPIC 破坏高难)。选职卡图已切 6 张存 `textures/gui/class_card_<id>.png`(160px)备用。
- **编译点(IDEA 注意)**:纯客户端/网络代码,沙箱编译不了;均照 `StatsScreen`/`SkillUsePayload`/`OpenAccessoryPayload`/`YongyeClient` 既有写法。重点看 `ClassSelectScreen` 的 Screen API 与两个 Payload 的 codec。
- **本版选职界面为按钮版**(纯文字+按钮);卡图渲染(`DrawContext.drawTexture` 的 1.21.1 签名)留作下一小步贴上去。

## 里程碑 44 — 坦克专属盾·磐盾
- **新增 `item/TankShieldItem`**(继承原版 `ShieldItem`,自带举盾格挡);副手装备时 +6 护甲 / +4 韧性 / +0.3 击退抗性 / +10 生命(OFFHAND 槽)。EPIC、maxDamage 1500。与主手镇魂成套(锤+盾)。
- **协同**(坦克副手持磐盾,`ClassSkillHandler`):护盾(吸收)再 +1 级(与镇魂叠加,全套最高 base+2);格挡被近战命中时反震 `tankShieldReflect`(默认 4;不否决,叠在原版格挡减伤之上)。
- 配上金边蓝宝石盾图(抠白底缩 32×32),`item/generated` 平面模型。注册进 ModItems + 创意标签;`/yongye tankshield` 给予。新增配置 `tankShieldReflect`。
- **编译点(IDEA 注意)**:`TankShieldItem extends net.minecraft.item.ShieldItem`(构造与 `appendTooltip` 覆写照 ChaosBlade 风格);1.21.1 若 ShieldItem 构造签名不符再调。盾在手里先是平面图标,立体盾面(`builtin/entity` 渲染)精修留后续。

## 里程碑 45 — 补齐职业原始设定两缺口(纯事件,不依赖 mixin)
- **武僧「任何武器耐久消耗×2」**:此前未做(本拟 mixin 拦 `ItemStack.damage`,但盲写 mixin 签名错=启动崩溃,风险过高)。改为在已有 `AttackEntityCallback` 里,武僧持(非拳套)可损耗武器攻击时额外 `setDamage(getDamage()+1)`——照搬 `EquipmentEnhancer` 的耐久 API,编译期可查、不碰 mixin。覆盖攻击磨损(正合「逼你用拳」的设计本意);若日后要连挖矿等全用途也翻倍,再上 mixin。开关 `monkWeaponDurabilityPenalty`。
- **刺客「更容易出现暴击」**:此前仅用 +攻击 近似。补为真·职业暴击——`AttackEntityCallback` 里按 `assassinCritChance`(持影刺再 +15%)掷骰,命中则追加 攻击力×`assassinCritBonusFraction` 伤害(复用 `WeaponCombatHandler` 的「追加后清无敌帧叠加」模式)。
- 至此**六职业原始设定全部落地**(肉盾/战士/术士/剑客之前已完全吻合;武僧、刺客本轮补齐)。
- **开发守则置顶**:应用户要求,将「八荣八耻 + 八条」开发守则置顶进 `HANDOVER.md`(所有协作者/AI 助手必守:不瞎猜接口、先查现有用法、拿不准标注待验证等)。本轮即按此守则:不盲写 mixin、改用编译可查的事件方案。

## 里程碑 46 — 职业武器·磐盾的获取途径(合成配方)
- 给 6 把职业专属武器 + 磐盾各加一个合成配方(`data/yongye/recipe/`,**纯数据、Fabric 自动加载、零 Java**,照抄 `chaos_blade.json` 格式)。
- 统一 3×3 形(`MRM/RBR/MRM`):中心基底 + 主料R/副料M 按职业区分——战士 life_core+life_shard(底:下界合金剑)、肉盾 life_crystal+life_shard(链锤)、刺客 rift_fragment+life_shard(铁剑)、术士 abyss_soul_crystal+life_shard(烈焰棒)、武僧 life_core+life_crystal(下界锭)、剑客 life_crystal+life_core(下界合金剑)、磐盾 catastrophe_blood_core+life_core(盾)。
- 稀有材料本就由 Boss/精英掉落 → 武器/盾被合理地卡在刷材料之后(契合极难基调)。
- **待续(可选)**:Boss/精英直接稀有掉落职业武器(需改 `EliteHandler`/`BossHandler` 的 Java;本轮按守则只做零风险纯配方,要做再上)。

## 里程碑 47 — 真·主动技能(职业大招)+ 选职界面卡图化 + 平衡微调
**真·主动技能(职业大招)**:复用现有按键+网络模式——新增按键「职业大招」(默认 X,`key.yongye.ultimate`)→ C2S `ClassUltimatePayload`(空包)→ 服务端 `system/ClassUltimateManager.use`,施放**本命职业**(第一职业)的主动技能,带冷却(`ultimateCooldownTicks` 默认 30s),纯事件不依赖 mixin:
- 战士 旋风斩(周身 AoE)、坦克 不动如山(抗性III+吸收IV+嘲讽全场)、刺客 影遁(隐身+迅捷III)、术士 灭世(大范围魔法,献祭生命)、武僧 百裂拳(周身重击+击退)、剑客 万剑归一(前方大范围剑气)。全部 `ult*` 数值可调。

**选职界面卡图化**:`ClassSelectScreen` 由按钮版改为**卡图版**——六张职业卡 3×2 排布,`mouseClicked` 命中判定点选,悬停金色描边(`ctx.fill`);卡图用 `AccessoryScreen` 确认过的 `ctx.drawTexture(id,x,y,0,0,w,h,texW,texH)` 签名原尺寸绘制(卡图重导为统一 96×132 透明留边)。

**平衡微调(轻量首版)**:刺客暴击略降(`assassinCritChance` 0.25→0.20、`assassinCritBonusFraction` 0.6→0.5,此前与背刺+武器品质暴击叠加偏高)。其余保持(全 config 可调),真·平衡待实测手感再调。

- **编译点(IDEA 注意)**:客户端/网络代码沙箱编译不了;`ClassSelectScreen`(drawTexture/fill/mouseClicked 照 `AccessoryScreen`)、大招按键(照 skillKeys)、`ClassUltimatePayload`(照 OpenAccessoryPayload)。`ClassUltimateManager` 唯一非项目既有用法是 `LivingEntity.takeKnockback(double,double,double)`(标准 API),若报错告知即调。

## 里程碑 48 — 开局送武器默认开 + 背包显示当前职业
- **`startingClassGiveWeapon` 默认 false→true**:选本命职业即附赠该职业专属武器(EPIC)。已选过职的老角色不补发,用 `/yongye classweapon <id>` 取。
- **背包显示当前职业**:职业 id 经 `StatsPayload`(新增 `className` 字段;`sendStats` 取本命=第一职业;`chooseStartingClass` 后即时 `sendStats`)同步到客户端 `ClientStats`;背包界面复用现有 `ScreenEvents.AFTER_INIT`+`Screens.getButtons` 模式,新增「本命·<职业>」标签按钮(点开成长面板)。
- **编译点**:`StatsPayload` 加字段已同步改全部构造/接收处(均 3 参);职业标签按钮照搬现有 成长/装备/饰品 按钮写法,均为已确认 API。

## 里程碑 49 — 掉落规则细化 + 守护附魔书需精英材料合成
**掉落规则**(`LootHandler` + `YongyeConfig`):
- **普通怪**:必爆 1 个生命碎片;20% 生命结晶;**绝不掉生命核心及以上**。
- **精英怪**:1~2 生命碎片;结晶几率翻倍;**生命核心(`lifeCoreDropChance` 默认 50%)+ 灾厄血核(`bloodCoreDropChanceElite` 默认 10%)为精英专属**(普通怪不掉)。
- 配置变更:`lifeCrystalDropChance` 0.65→0.20;`lifeCoreDropChance` 语义改为"仅精英"(默认 0.50);新增 `bloodCoreDropChanceElite`(默认 0.10)。
**守护附魔书**:此前仅命令获取、无配方。新增 `recipe/ward_book.json`——**需精英材料**:生命核心×4 + 灾厄血核×1 + 书。门槛压在精英 farming 之后,杜绝轻易合成。
**JEI 排查结论**:10 个神器**合成配方**均为标准 `crafting_shaped`、材料/产物 id 全部有效、目录正确——结构上应能在 JEI 显示(若没显示,查 latest.log 的配方加载报错)。但 `artifact_upgrade`/`equipment_enhance`/书合成是 `SpecialRecipeSerializer` 动态配方,且**项目无 JEI 插件**,故这几类 JEI 无法显示(属预期,需写 JEI 集成才行)。

## 里程碑 50 — 修复全部配方加载失败(致 JEI 不显示 + 无法合成的真因)
- **真相**(由玩家日志 `minecraft-exported-logs-...18-49` 定位):**全部 19 个 yongye 合成配方解析失败**(`com.google.gson.JsonParseException`),被 MC 跳过——所以神器配方一直不在 JEI 显示、也合不出来;连早就存在的 `chaos_blade` 和 10 个 `artifact_*` 都是坏的(从写下时就错,只是之前没人开 JEI 看)。
- **根因**:1.21.1 配方 `key` 的材料**不接受裸字符串** `"yongye:life_core"`,必须是对象 `{"item": "yongye:life_core"}`(标签则 `{"tag": "..."}`)。产物 `{"id": ...}` 写法是对的(未报错),仅 `key` 错。
- **修复**:批处理把所有 19 个含 `key` 的配方材料由裸字符串改为 `{"item": <id>}`(`chaos_blade`、`artifact_*` ×10、`class_weapon_*` ×6、`tank_shield`、`ward_book`);4 个特殊配方(`artifact_upgrade`/`equipment_enhance`/书合成,无 `key`)不受影响。纯数据,零 Java。
- **教训**:之前我说"神器配方从代码层面挑不出毛病"是**没核实 1.21.1 的 ingredient 格式**就下的结论,错了;玩家日志才是判据。

## 里程碑 51 — 掉落率实时命令热调
- 新增 `/yongye loot ...`(OP):`show` 看当前值;`shard/crystal/core/bloodcore <0~1>` 分别热调生命碎片/结晶/核心/灾厄血核掉率;`enable <true|false>` 总开关。
- 复用现有配置系统:`LootHandler` 每次怪死都读 `YongyeConfig.get()`,故改完**下一只怪即生效**;每次设置后调 `YongyeConfig.save()` 写盘 `config/yongye.json`,**重启也保留**。无需重进世界。

---

> 后续待办、已知边界与可做方向见 **[HANDOVER.md](HANDOVER.md)** 第 6 节。

## 里程碑 52 — 天赋树 GUI + 打怪掉职业武器
**天赋 GUI(#1)**:背包新增「天赋」按钮 → `client/TalentScreen`,按已习得职业逐行展示各自 5 个节点(读 `TalentManager.treeView` 树结构 + `ClientTalents` 同步的点数/已点等级);点击"可加点"节点 → C2S `TalentLearnPayload` → 服务端 `TalentManager.learn` 校验加点 → S2C `TalentSyncPayload` 回传 → 界面下一帧即时刷新。节点按状态着色(满级金/可点绿/锁灰/悬停金框),底部显示悬停说明。新增 `TalentManager.NodeView` + `treeView()`(只读暴露,通用代码客户端可直接读)、`client/ClientTalents`、`TalentSyncPayload`(S2C)/`TalentLearnPayload`(C2S)、`YongyeNet.sendTalents`(登录 + 发点 + 加点后推送)。纯 Screen + 已确认 API,不依赖 mixin。
**打怪掉职业武器(#2)**:`BossHandler` Boss 必掉 1 把随机职业专属武器(随 Boss 倍率放大);`LootHandler` 精英按 `classWeaponDropChanceElite`(默认 4%)概率掉随机职业武器。配合 m46 合成配方,职业武器获取途径齐全。
- **编译点(IDEA 注意)**:客户端/网络代码沙箱编译不了;`TalentScreen` 照 `StatsScreen`/`ClassSelectScreen`、两个 payload 照 `ChooseClassPayload`/`StatsPayload`、`ClientTalents` 照 `ClientStats`、背包按钮照现有 成长/装备/饰品。`drawTextWithShadow` 已确认 `WeaponInfoScreen` 用过。

---

## 里程碑 53 — 磐盾握持姿势(立体化,纯 JSON)
- **澄清**:磐盾原本就是 `item/generated`——MC 会按贴图透明轮廓把它挤出成有厚度的盾形立体块,并非纸片;显"平"是因为握持姿势是普通物品拿法。
- **改动**:给 `models/item/tank_shield.json` 加 `display` 块——第一/三人称手持改为**盾牌姿势**(放大约 1.6×、举臂、盾面朝外),背包/GUI 图标保持正面平展不变。纯数据、零 Java、零崩溃风险;姿势数值为合理起点,可按实机截图再微调。
- **未做(需另议)**:原版那种**弧形盾面**需自定义 Java 物品渲染器(`BuiltinItemRendererRegistry` / 1.21.1 渲染管线),较大且版本敏感,留作单独评估,不在本轮冒险。

---

## 里程碑 54 — 坦克真·%减伤(mixin) + 武僧耐久×2全用途(事件)
**#5 坦克真·百分比减伤**:新增 `mixin/TankDefenseMixin`,注入 `LivingEntity#modifyAppliedDamage` 的 RETURN,对当前生效的坦克玩家按 `tankTrueDamageReduction`(配置,默认 0.15、上限 0.9)削减最终承受伤害;写法严格对齐项目唯一现成的 `MiningSpeedMixin`(@Inject + RETURN + cancellable + cir.setReturnValue),已注册进 `yongye.mixins.json`。
**#6 武僧耐久×2 全用途**:在 m45「攻击磨损翻倍」之外,于 `ClassSkillHandler.register()` 新增 `PlayerBlockBreakEvents.AFTER` 处理器——武僧持(非拳套)可损耗武器破坏方块时额外 `setDamage(+1)`,与攻击磨损合并即"任何武器全用途耐久×2"。纯 Fabric 事件、不依赖 mixin、编译期可查。
- **验证点(关键)**:`modifyAppliedDamage` 在 1.21.1 的映射名我无法在沙箱编译验证;`require=0` 保证即便名字不符,该注入器只会被静默跳过、**不会崩游戏**。**请启动后看日志**——若注入没挂上,日志会提示该 mixin 注入器找不到目标(届时把日志发我改方法名);并实测:坦克挨打看伤害是否按比例下降、武僧挖矿看耐久是否掉得翻倍。

---

---

## 里程碑 55 — 解除 1024 属性上限 + 镇魂攻防双修强化 + 武器强化窗口 + 天赋同步补漏
**#1 解除原版属性 1024 硬上限(核心机制级)**:原版 `generic.max_health` / `attack_damage` / `armor` 等都是 `ClampedEntityAttribute`,上限硬编码在 **1024**(再多也只按 1024 生效,wiki 已确认)——导致血量书堆到一百多级、武器强化堆攻击到一千多就失效。新增 **accessor mixin** `mixin/ClampedEntityAttributeAccessor` 把私有 `maxValue` 暴露为可写,`Yongye.raiseAttributeCaps()` 初始化时把 `max_health/attack_damage/armor/armor_toughness` 上限抬到 **100 万**(攻速 1024 够用不动)。已注册进 `yongye.mixins.json`。**待验证**:字段名 `maxValue` 是 1.21.1 Yarn 约定名,无法在沙箱编译——但 accessor 字段名由 IDEA 的 fabric mixin 注解处理器在【编译期】校验,名字不符会直接编译失败并报 "Unable to locate field maxValue",拿真实名替换即可(不会运行崩)。

**#2 修复镇魂强化不加攻击 + 攻防双修(B 方案)**:根因——镇魂(坦克职业武器)同时带 `GENERIC_ARMOR` 和 `GENERIC_ATTACK_DAMAGE`,旧 `EquipmentEnhancer.kindOf` **先判护甲**→被误判成盔甲→强化只加护甲/韧性/生命、完全不碰攻击。修复:`kindOf` 新增 **HYBRID**(攻击+护甲兼具),`withLevel` 加 HYBRID 分支——攻击按 `enhanceHybridDamageFraction`(默认 **0.5**,即攻击/级减半,"加得少些")打折,护甲/韧性/生命照盔甲成长一起涨,全挂主手槽。`isWeapon`/`critBonusDamage` 纳入 HYBRID(可暴击,暴击额外伤害同比例打折)。只有镇魂受影响(其它职业武器只攻击无护甲;磐盾只护甲无攻击仍算盔甲)。新配置 `enhanceHybridDamageFraction`。

**#3 新功能·武器强化窗口(一键升级)**:背包新增「强化」按钮 → C2S `OpenEnhancePayload` → 服务端 `openHandledScreen(EnhanceScreenHandler)`。窗口=装备槽(收任意可强化装备)+ 材料槽(收强化材料,可整组)+「升级」按钮;点升级(C2S `EnhanceApplyPayload`)→ **升级级数 = 材料数量 × 单值**(生命碎片×1 / 结晶×10 / 核心×100 / 血核×1000),材料整组消耗——**一组生命碎片一键 +64 级**。临时容器,关闭归还槽内物品。新增 `screen/EnhanceScreenHandler`、`client/EnhanceScreen`、`ModScreens.ENHANCE`、`OpenEnhancePayload`/`EnhanceApplyPayload`,全部照 `AccessoryScreenHandler`/`AccessoryScreen`/`OpenAccessoryPayload` 既有模式抄,不依赖 mixin。

**顺带修 m52 天赋同步漏调**:`ClassManager.chooseStartingClass`/`learn` 改职业后只调了 `sendStats`(刷新背包职业显示)、漏了 `sendTalents`(刷新天赋面板)——导致选完本命职业后天赋面板仍显示"你还没有职业",要重进才好。两处各补一行 `sendTalents`。证据:玩家背包显示「本命·肉盾」但天赋面板说没职业,正是此漏调症状。

- **编译点(IDEA 注意)**:① `ClampedEntityAttributeAccessor` 的 `maxValue`(编译期校验,见上);② 强化窗口的客户端/网络/容器代码沙箱编译不了,均照 `AccessoryScreenHandler`/`AccessoryScreen`/各 Payload 既有写法;③ `EnhanceScreenHandler` 用到 `PlayerInventory.offerOrDrop` / `ScreenHandler.sendContentUpdates` 为标准 API,若报错告知即调。数值(`enhanceHybridDamageFraction` 0.5、上限 100 万)全可调。

---

## 里程碑 56 — 神器远古/终焉可见合成表 + 永夜 V5 解封顶(深渊线性增怪血)
**神器远古/终焉缺合成表(根因:升阶配方隐形)**:升阶配方 `ArtifactUpgradeRecipe` 是 `SpecialCraftingRecipe`,**不进合成书/JEI**;每个神器的基础 JSON 表只产**残破**(结果不带 ARTIFACT_LEVEL 组件 = 默认 1 级)。所以玩家在合成表里只看得到残破,远古/终焉像"没有"。「终焉神髓」本就可获得(佩恩/Boss 掉落 + 任务),材料不缺,只是路径看不见。
- **修复**:为 10 个神器各加**直接可见**的远古(3级)+ 终焉(6级)shaped 表,共 **20 张**。纯材料合成、**不吃神器当材料**(避免与升阶特殊配方在某些等级撞配方)。规则:远古 = 残破图案、**中心换生命核心**、结果 `components.artifact_level=3`;终焉 = 残破图案、**4 角换终焉神髓 + 保留招牌中心**(辨识度)、结果 `artifact_level=6`。`ARTIFACT_LEVEL` 用 `Codec.INT` 注册,故可在 JSON 结果里直接带等级。
- 脚本批量生成;已校验 30 张神器 shaped 表**无材料布局冲突**(初版终焉换"4角+中心"导致骨箭/掘墓罗盘撞表,改为"只换4角保留中心"解决)。
- **待验证**:result `components` 的 JSON 语法(沙箱无法编译/测),若 1.21.1 略有出入,该表加载失败会在日志报数据包错误(不崩游戏),拿报错来修。材料/成本(远古=8主题+1生命核心;终焉=4主题边+招牌中心+4终焉神髓)全可调。

**永夜 V5 不再是终点 + 深渊线性增怪血**:`NightfallManager` 原 `level` 双重封顶(等级 ≤5 且怪物缩放被 `mobScalingMaxMultiplier` 夹住)。
- **解封顶**:`setLevel`/`load` 的 `Math.min(5,…)` 改 `nightfallMaxLevel`(默认 99,近似无尽);`getLevelName` 对 >5 生成「永夜 · 深渊 N 层」。
- **深渊增血**:`MobEnhancementHandler` 新增独立项——永夜 >5 时按 `(level-5) × nightfallBeyondHpPerLevel`(默认 0.5:V6=+50%、V7=+100%…)**线性叠加怪物最大生命**(仅血量,独立于既有缩放封顶,不动 ≤5 的平衡),在补满血之前应用。精英概率/锁定半径数组对 >5 自动钳在第 5 档(最高),无需改。
- 失败→升永夜的触发在 `QuestManager`(挑战失败 `escalate`)。新配置 `nightfallMaxLevel` / `nightfallBeyondHpPerLevel`,均可调。

> 本轮纯 JSON + 服务端 Java,无新 mixin。改动 Java 文件(YongyeConfig/NightfallManager/MobEnhancementHandler)括号已配平;20 张配方 JSON 已校验合法且无冲突。

---

## 里程碑 57(热修)— 饰品栏神器死亡后消失
**根因**:`ModAttachments.ACCESSORIES`(饰品栏 NBT)虽 `.persistent(...)`(存档/重进保留),但**漏了 `.copyOnDeath()`**。而模组里其它所有"已获得成长"(LEARNED_HEALTH/SKILLS/CLASSES、MONK_FIST_BONUS、EMBER_READY_AT、TALENT_*…)全有 copyOnDeath。于是玩家**一死,饰品附件被重置为空**(initializer = new NbtCompound),里面的神器既不掉落也不保留,直接蒸发。用户日志里大量死亡记录 + 聊天"放在那个饰品里了/东西没了"印证。
**修复**:给 `ACCESSORIES` 加 `.copyOnDeath()`,死亡保留饰品栏神器(与全模组成长一致)。一行改动,零风险。
> 备选:若希望死亡时神器"掉落可捡"(硬核惩罚)而非直接保留,需另写死亡事件读附件、生成掉落物、清空附件——按需再说。

---

## 里程碑 58 — 调试 / 运营菜单(/yongye debug)+ 修 nightfall 参数上限
**调试菜单**:把常用的 /yongye 命令做成一屏分组按钮,点一下即执行,免去手敲(尤其方便实机验证 m55-57)。
- 入口走「服务端命令 → S2C 开界面」:`/yongye debug`(OP)→ 新增 `network/OpenDebugPayload`(S2C 空包,照 `OpenClassSelectPayload`)→ 客户端 `YongyeClient` 收到即 `setScreen(new DebugScreen())`。
- `client/DebugScreen`(纯 `Screen`,照 `StatsScreen` 写法):6 组按钮(永夜/节奏、成长道具、职业/武器、神器、事件Boss任务、运维),每个按钮 = `client.player.networkHandler.sendCommand("yongye …")`(命令串不带斜杠)。命令仍在服务端按权限 2 执行,故能开菜单的 OP 点按钮才有效,权限边界天然一致。`shouldPause()=false`,点完命令可立刻观察效果。
- 纯客户端 + 命令复用,不依赖 mixin、不新增服务端逻辑;全 UI 文案为 `Text.literal` 硬编码中文,无需 lang key。
**附带修复(m56 遗留)**:`/yongye nightfall` 的参数仍是 `IntegerArgumentType.integer(0, 5)`,而 m56 已把封顶移到 `nightfallMaxLevel`(99)——导致 `/yongye nightfall 6+` 被 Brigadier 拒绝、深渊层无法用命令触达。改为 `integer(0)`(上界放开,`setLevel` 内部已钳到 99)。调试菜单「永夜·深渊7」按钮即依赖此修复。
- **编译点(IDEA 注意)**:客户端/网络代码沙箱编译不了;`DebugScreen` 的 Screen API(`renderBackground`/`drawCenteredTextWithShadow`/`drawTextWithShadow`/`ButtonWidget.builder`/`shouldPause`)均为 `StatsScreen`/`WeaponInfoScreen` 已 build 同款;`networkHandler.sendCommand(String)` 已查 Yarn 1.21.1 文档确认存在;新包 + 命令发包照 `OpenClassSelectPayload`/`YongyeNet` 既有写法。

---

## 里程碑 59 — 精英怪光环特效 + /yongye elite 测试命令
**精英光环特效**(应需求加):精英怪周身常显幽蓝魂火光环——脚下一圈随时间旋转的 `SOUL_FIRE_FLAME` + 少量上升 `SOUL` 粒子,作"被诅咒的强敌"标识,与金色名牌一样常显。
- 实现:`EliteHandler.tickElite` 里每 `eliteAuraIntervalTicks`(默认 4)tick 调 `spawnAura`,用服务端 `ServerWorld.spawnParticles`(自动广播给附近玩家)。**纯服务端粒子,不走发光描边**——规避 m21 那类第三方渲染mod对实体描边崩溃的风险(`eliteGlowing` 仍默认关)。
- 配置:`eliteAuraEffect`(开关,默认开)、`eliteAuraIntervalTicks`(间隔,默认 4≈每秒5次,越小越密越费)。
**/yongye elite 测试命令**:把玩家附近 16 格内、尚未精英化的敌对怪物就地变精英(`EliteHandler.makeNearbyElite` 复用 `makeElite`),免去干等 4% 概率刷新 + 第3天解锁,方便实机查看光环/属性。调试菜单「怪物/Boss/事件」组加「精英化附近」按钮(与「长门降临」并排,一键召出特殊怪)。
- **澄清(非 bug)**:用户反映"没见到 BOSS"——本模组 Boss = ① 被增强的原版 Boss(凋灵/监守者/远古守卫/末影龙/袭击队长,只在各自原生场景出现,非主世界随机刷);② 自定义长门·佩恩,仅在永夜 ≥ IV(`painSpawnMinNightfall=4`)且游戏 ≥ 第5天(`painSpawnMinDay=5`)按概率(`painNaturalSpawnChance=0.25`)自然降临。新档低永夜/早期自然不会刷,属设计预期。即时查看用 `/yongye painboss`(或调试菜单「长门降临」)。
- **编译点**:`spawnParticles`/`getHeight`/`getEntitiesByClass`/`Box.expand`/`SOUL_FIRE_FLAME`/`SOUL` 均为项目已 build 同款;无新 mixin、无新依赖。

---

## 里程碑 60 — 普通怪 BOSS 版 + 搭方块爬塔(反躲塔)
应需求三连:① 普通怪也出 BOSS 版、② 第 10 天起刷、③ 怪搭方块爬上躲在单格高塔上的玩家。

**① 普通怪 BOSS 版(新增 MobBossHandler)**:第 `mobBossStartDay`(默认 10)天起,普通敌对怪按 `mobBossChance`(默认 0.8%)"BOSS化"。
- 做法 = 打 `IS_BOSS` 标记 + 大属性(血×12 / 攻×4 / 速×1.25 / 抗击退+0.9)+ 体型放大(GENERIC_SCALE ×1.6)+ 红色 ServerBossBar + 【BOSS】名牌。
- **关键复用**:带 `IS_BOSS` 即自动继承全项目 Boss 待遇,无需重写——BossAbilityHandler 全套能力(减伤/狂暴/召援/冲击波/锁定)、BossHandler 死亡掉落、PursuitHandler Boss 档挖墙、HighHpCounter 高血量反制、LootHandler 跳过普通掉落表。
- 另用独立 `IS_MOB_BOSS`(持久)区分原版 Boss 与怪物BOSS版,仅后者挂自定义血条;血条每 tick 更新血量% + 同步 `mobBossBarRadius`(48格)内玩家可见,死亡/移除即 clearPlayers;重载分支补回 IS_BOSS + 恢复血条。
- 注册置于 EliteHandler 之前,且 EliteHandler 加 `IS_BOSS` 跳过 → 怪物BOSS版不会被二次精英化。
- 测试:`/yongye mobboss`(或调试菜单「BOSS化附近」)就地把附近 16 格怪变 BOSS,免等概率刷。

**② 搭方块爬塔(PursuitHandler)**:反制"玩家造单格高塔躲在怪够不着的正上方"。
- 追杀中,玩家近乎正上方(水平距 ≤ `pillarMaxHorizontal`=2.5)且高出 `pillarMinHeightDiff`(3)格、怪在地面时,每 `pillarCooldownTicks`(8t)搭一格:先上移 1 格再在原脚位填方块(`pillarBlock`,默认圆石),逐格垒到玩家高度。`pillared` 标记优先于普通爬墙;受 世界之锚 + `canMobsDig`(第5天门控)+ `mobPillarUp` 开关约束。

**待编译验证**:`EntityAttributes.GENERIC_SCALE` —— 证据强(SCALE 属性 1.20.5 引入即带 GENERIC_ 前缀;1.21.2 才改名裸 SCALE;本项目 1.21.1、全程用 GENERIC_ 前缀且已 build 通过)。这是本轮**唯一**编译风险点;若 build 报 `cannot find symbol GENERIC_SCALE`,把 `MobBossHandler.makeMobBoss` 里那一行属性换成 `EntityAttributes.SCALE` 即可(其余不动)。其余 API(ServerBossBar 全套 / world.setBlockState / Registries.BLOCK / refreshPositionAndAngles / spawnParticles)均为项目已 build 同款。
- 84 个 Java 文件(+1 MobBossHandler)。

---

## 里程碑 61 — HIM 突脸:自定义音效 + 传送闪现登场
应需求:给 HIM 突脸换上用户上传的自定义音效(`突脸惊吓.mp3`)+ 传送闪现登场 + 更"突然"。
- **自定义音效**:`突脸惊吓.mp3` → ffmpeg 转 OGG Vorbis(`assets/yongye/sounds/him_jumpscare.ogg`,立体声 22.05kHz,14.3s),**复用项目既有音效管线**(同长门 pain_* 那套):`sounds.json` 加 `him_jumpscare`(category hostile,stream true)+ `ModSounds` 加 `HIM_JUMPSCARE = register("him_jumpscare")`(随 `ModSounds.init` 自动注册)。HIM 登场处把 `SoundEvents.ENTITY_ENDERMAN_STARE` 换成 `ModSounds.HIM_JUMPSCARE`(`playSoundToPlayer` 收 SoundEvent,与长门同款)。
- **传送闪现**:登场喷 50 颗 `ParticleTypes.PORTAL`(紫色末影门粒子)+ 原烟雾,营造"啪地闪到面前"。配置 `himTeleportFlash`(默认开)。
- **更突然**:失明铺垫从硬编码 100t(5秒)改为可配 `himBlindnessTicks`(默认 20t≈1秒),越短越突兀;想要旧的 5 秒慢压迫感就设回 100。
- **已知**:音效本身 14.3 秒,而 HIM 只停留 `himDurationTicks`(35t≈1.75s),声音会在 HIM 消失后继续放完——要贴合可裁短 mp3 或调长停留。
- API 全部复用项目已 build 同款(ModSounds.register / SoundEvent.of / playSoundToPlayer / spawnParticles),无新依赖、无 mixin、无待编译验证点。

---

## 里程碑 62 — 精英+ 额外经验(加快升级)
应需求:升级慢 → 精英及以上的怪死亡掉额外经验。
- 新增 `BonusXpHandler`:`AFTER_DEATH` 时按档掉经验,用原版 `ExperienceOrbEntity.spawn(world, pos, amount)`(自动拆成若干小球)。分档**取最高适用**(先判 IS_MOB_BOSS 再判通用 IS_BOSS,避免怪物BOSS被算成原版档):长门 `xpBonusPain`(500)> 怪物BOSS版 `xpBonusMobBoss`(150)> 原版Boss `xpBonusVanillaBoss`(200,叠加在原版自带经验上)> 精英 `xpBonusElite`(25)。
- 配置 `enableBonusXp` + 四档数值,升级快慢直接调。注册在 EliteHandler 前(顺序无关,纯死亡事件)。无 mixin、无新依赖、无待验证点。
- 85 个 Java 文件(+1)。
- **未完**:用户同批还要"材质包应用进去 / 切换默认皮肤 / 音效"——因 `minecraft.zip` 未实际上传到沙箱(uploads 目录为空)而搁置,待重传 zip 后做(预定 m63)。

---

## 里程碑 63 — 应用整套材质/音效资源包(默认皮肤 + 音效)
应需求:把用户的材质包应用进 mod、默认生效(切换默认皮肤 + 音效)。
- 用户把资源包做成 7z 分卷(`minecraft.7z.001/.002`)直接提交进仓库(`e0699af`)。本里程碑解开它(341 贴图 + 784 音效 + 43 models/blockstates/lang + splash),整套并入 mod 的 `src/main/resources/assets/minecraft/`。
- **原理**:Fabric mod 的 jar 资源在资源栈里盖过原版默认(但低于玩家手动装的资源包),所以装了 mod 就自动应用这套贴图/怪物皮肤/音效,无需手动挂资源包。音效无 `sounds.json`、靠同路径 ogg 覆盖原版(标准做法,生效)。
- 删除根目录的 `minecraft.7z.001/.002`(raw 压缩包不进 build,资产已正确落位,留着是 18MB 死重)。
- 体积:`assets/minecraft` 约 37MB,build 出的 jar 会相应变大(用户要整套,属预期)。
- 无 Java 改动、无 mixin。若日后想做成"可在资源包菜单里开关"的内置包(而非强制默认),再转 `registerBuiltinResourcePack` + `DEFAULT_ENABLED`。

---

## 里程碑 64 — 材质包只留怪物皮肤(去掉方块等非怪物贴图)
应需求"只留怪物皮肤,方块皮肤不要":从 m63 并入的整套包里删除所有非怪物视觉资产——`textures/{block,item,environment,painting,particle,models}` + `models/`(方块模型) + `blockstates/` + `lang/` + `texts/`,共 168 个文件。
- 保留:`textures/entity/`(217 怪物/实体皮肤)+ `sounds/`(784 音效,上轮要过,未动)。
- 注:items / lang(改名)等也一并去掉(按"只留怪物皮肤"从严理解);若其实想保留物品贴图或怪物改名文案,说一声加回。
- 音效是体积大头(没要求删),jar 仍较大,属预期。无 Java 改动。

---

## 里程碑 65 — 改名:显示名「亡途荒夜」→「永夜」
应需求把 mod 显示名改为「永夜」(本就是 mod_id `yongye` 的本名/拼音)。全局把字符串 `亡途荒夜` → `永夜`:`fabric.mod.json` 的 name(纯"永夜")、所有日志前缀 `[永夜]`、调试菜单标题、lang(物品组名 + 按键分类名)、注释、文档。
- **内部 id / 包名 `com.yongye` / 资源命名空间 `assets/yongye` / 配置文件 / 存档键一律未动**——这些动了会毁存档、资源与配置;且 `yongye` 本就是"永夜"的拼音,与新显示名天然一致。
- 无 Java 逻辑改动;fabric.mod.json 与 lang JSON 校验合法,85 文件括号配平。

---

## 里程碑 66 — 材料兑换按钮(10 碎片→结晶→核心→血核)
应需求:背包加兑换按钮,10:1 升级材料,扣背包物品。
- **比例固定 10:1**,与四材料的强化值等值(碎片+1 / 结晶+10 / 核心+100 / 血核+1000),兑换前后**等值不溢出**,故 10 是唯一合理比例,不做成可配。
- **链路**:背包新增「兑换」按钮 → 客户端 `ExchangeScreen`(三行:碎片→结晶 / 结晶→核心 / 核心→血核,各含"兑换 10→1"与"全部兑换",并**实时显示背包内各材料数量**)→ C2S `ExchangePayload(tier, all)` → 服务端 `MaterialExchange` 扫背包数料、`decrement` 扣料、`offerOrDrop` 给产物、发聊天反馈(材料不足时红字提示)。
- 纯事件 + 网络,无 mixin。复用现成范式:`SkillUsePayload` 的带字段 codec、`OpenAccessory/Enhance` 的 C2S 接线、背包按钮 `ScreenEvents.AFTER_INIT`、`offerOrDrop`。配置 `enableMaterialExchange`。
- 88 个 Java 文件(+3:ExchangePayload / MaterialExchange / ExchangeScreen)。无待验证点(API 全为项目已 build 同款)。

---

## 里程碑 67 — 开局赠礼:每人首次进入发一个下界合金背包
应需求:所有人开局获得一个下界合金背包(Sophisticated Backpacks)。
- 新增 `StartingKitHandler`:`ServerPlayConnectionEvents.JOIN` 时,若未领过(持久标记 `GOT_STARTING_KIT`,死亡保留防刷)则 `giveItemStack` 发一个并打标记,**每人仅一次**。
- **软依赖**:背包是独立 mod,**不硬依赖**——按字符串 id(配置 `startingBackpackItem`,默认 `sophisticatedbackpacks:netherite_backpack`)在 `Registries.ITEM` 查;查不到(未装该 mod / id 错)**静默跳过且不打标记**(玩家日后装上该 mod、下次登录可补发),不崩。
- 老玩家(尚无标记)下次登录也会补发一个 → "所有人"最终都拿到一个。配置 `giveStartingBackpack` 开关。
- 89 个 Java 文件(+1 StartingKitHandler)。无待验证点。

---

## 里程碑 68 — 佩恩强化 + 通用配置命令 + 调试菜单调参
应需求四项:
- **① 佩恩数值**:血量 1000→**20000**、攻击 12→**2000**(配置默认值)。
- **② 佩恩按时间线增强**(此前缺失):生成时复用怪物缩放公式——`MobEnhancementHandler.progressionMultiplier` 改 `public`,佩恩血量 ×进度倍率、攻击按 `mobScalingAttackRatio` 比例缩放,封顶 `mobScalingMaxMultiplier`;受 `enableMobScaling` 开关控制。倍率随永夜等级 + 游戏天数 + 附近玩家强度 + 进化阶段上升,与普通怪同一套。
- **③ 通用配置命令**:`/yongye config set <字段> <值>`、`get <字段>`、`list`。**反射读写 `YongyeConfig` 任意 public 实例字段**(boolean/int/long/double/String;数组只读),改完 `YongyeConfig.save()` 写盘,大多即时生效(部分需重进世界)。这是"所有功能进调试可设"的通用入口——任意配置都能游戏内改,不用编辑 json。
- **④ 调试菜单"调参/配置"组**:技能书爆率(精英 `skillBookDropChanceElite` / 普通 `skillBookDropChanceNormal`,本就有字段)+ 佩恩血/攻 快捷按钮(点即 `config set`);更多字段用命令。
- 89 个 Java 文件,无新增文件。`config set` 用反射(`getField`/`setX`),无新依赖。

---

## 里程碑 69 — 深渊层血量过低修复 + 技能书/碎片爆率压制
应玩家反馈(永夜 92 层怪血仅 ~2000、技能书与碎片爆率过高):
- **诊断**:① 三个血量倍率(基础 ×3、缩放 ×60 封顶、超 V5 增血)全是 `ADD_MULTIPLIED_BASE`(**相加**),僵尸 20×(1+2+59+43.5)=**2110**;缩放 `prog` 在 92×0.8 早撞 `mobScalingMaxMultiplier=60` 顶,只剩超 V5 项还涨却被加法稀释。② 技能书普通怪爆率 `0.008×(1+永夜×0.5)` **无封顶**,92 层 =37.6%。③ 碎片掉落**无条件必掉**(根本没用 `lifeShardDropChance`)。
- **修复①**:超 V5 增血改 `addMultiplierTotal`(`ADD_MULTIPLIED_TOTAL`,在基础×缩放之上**再乘**)。92 层(perLevel 0.5)→ 20×62×44.5 ≈ **55180**(26×);`nightfallBeyondHpPerLevel=2` → ≈ **217000**。
- **修复②**:技能书永夜倍率封顶 `min(1+nf×0.5, skillBookNightfallMaxMult=3)`;精英默认 0.3→**0.15**。
- **修复③**:碎片接上 `lifeShardDropChance` 概率判定;默认 1.0→**0.3**。
- 调试菜单"调参"组按钮改为合理预设(技书·精英0.15 / 普通0.008、碎片0.3、永夜增血/级2)。
- **注意**:默认值改动只影响新配置;既有存档 `config.json` 里的旧值(碎片 1.0、上次点按钮设的高技能书爆率)需 `config set` 才更新。逻辑改动(血量乘法、碎片接概率、技能书封顶 + 新字段默认)重建即生效。89 个 Java 文件,无新增文件。

---

## 里程碑 70 — 平衡大改:爆率压制 / 深渊倍增 / 精英装备格挡 / 永夜尸潮 / 追杀微调
应需求一次性 8 项:
- **① 技能书爆率 → 千分之一**:`skillBookDropChanceNormal/Elite` 默认 →0.001;精英原"保底必掉血量书"改为按 `skillBookDropChanceElite` 概率掉(不再无条件)。
- **② 碎片 → 10%**:`lifeShardDropChance` 默认 →0.10(注:掉落逻辑 m69 已接上该概率)。
- **③ 精英高级材料等比减半**:生命核心 `lifeCoreDropChance` →0.05、灾变血核 `bloodCoreDropChanceElite` →0.025、新增**终焉神髓** `endingEssenceDropChanceElite` →0.0125(精英掉落链补一档)。
- **④ 永夜尸潮**(新 `NightfallHordeHandler`):永夜 ≥1 在每个玩家周围维持高密度敌对怪,出生即锁定玩家蜂拥追杀。目标量 = min(`nightfallHordeBase`×永夜等级, `nightfallHordeMax`)——**V1=100、V2 翻倍=200**,封顶 200 护 TPS;`nightfallHordeBatch` 平滑补刷;世界锚石范围内不刷。成分=僵尸/尸壳/蜘蛛(显式构造)。
- **⑤ 超 V5 血量+攻击倍增 2/4/6/8/10**:`nightfallBeyondHpPerLevel` 默认 →2.0,公式改 `(nf-5)×step` 并用 `ADD_MULTIPLIED_TOTAL` 乘法叠在基础×缩放之上,**HP 与攻击都乘**(新增 `ID_NIGHTFALL_ATK`)。V6 ×2、V7 ×4…V92 ×174。
- **⑥ 第 5 天起精英持武器 + 盾牌 + 格挡**:`makeElite` 中 gameDay≥`eliteEquipStartDay`(5)时,主手为空则给随机铁/钻剑斧、副手给盾牌(均不掉落);新增 `ALLOW_DAMAGE` 处理器:持盾精英按 `eliteBlockChance`(0.30)**完全格挡一次"来自实体的攻击"**(环境/穿透伤害不挡),带盾击音效 + 暴击粒子。
- **⑦ 追杀寻路改**:墙后卡住**不再瞬移**(`pursuitTeleportWallStuck` 默认 false,嵌墙兜底与墙后卡死兜底都改靠挖墙脱困;水/船卡住仍由 `pursuitTeleportStuck` 传送);新增**起跳翻越** `pursuitJumpWalls`(撞 1~2 格低墙且在地面给一次起跳冲量,配合挖墙/搭塔)。
- 90 个 Java 文件(+1 NightfallHordeHandler)。
- **注意**:默认值改动只影响新配置;既有 `config.json` 的旧值(技能书/碎片/核心/血核/beyond 步长)需 `/yongye config reset`(一次到位全套新默认)或逐项 `config set` 才更新;新增字段(尸潮/精英装备/终焉神髓/追杀开关)不在旧 json 中,会自动取新默认、重建即生效。
- **性能提醒**:尸潮 100~200 只寻路怪对 TPS 压力大,卡顿可调 `nightfallHordeMax / nightfallHordeBatch / nightfallHordeIntervalTicks`。

---

## 里程碑 71 — 追杀瞬移回归(与挖墙/起跳组合判定) + 任务奖励调低
应需求:
- **① 追杀组合判定**:`pursuitTeleportWallStuck` 默认 false→**true**。墙后卡住时三者结合——**能在玩家身边找到安全落点(`teleportNear` 成功)就瞬移过去;找不到(返回 false)则挖墙脱困 + 撞低墙起跳翻越**。挖/跳即时进行,卡住 ~3s 仍无进展且有墙时才尝试瞬移(且仅在有合法落点时成功);嵌墙兜底同理。
- **② 任务奖励调低**:原 reward 随永夜**无封顶暴涨**(92 层保底 ~V187 血量书 + 几乎必出结晶/核心/顶级材料,与"技能书千分之一"严重冲突)。改:永夜加成封顶 `min(nf,5)`;保底血量书降到 **V2~V9**;生命结晶 20~35%×1、生命核心 8~18%、顶级材料 3~8%、金苹果 8%(均大幅下调)。
- 90 个 Java 文件,无新增文件。**注**:`pursuitTeleportWallStuck` 是 m70 既有字段,旧 config.json 若存为 false 需 `config set pursuitTeleportWallStuck true`(或 reset);任务奖励是纯逻辑,重建即生效。

---

## 里程碑 72 — 技能按攻击力 / 佩恩失目标传送 / 抢装备与找回
应需求:
- **① 武器技能按攻击力**:混沌斩/深渊吞噬/终焉降临伤害额外 `+ 玩家攻击力 × skillXAttackRatio`(1.5/1.0/2.5),武器越强技能越强。
- **② 佩恩技能按攻击力**:神罗天征/万象牵引/地爆天星伤害改按 `佩恩攻击力 × painXAttackRatio`(0.30/0.15/0.50);攻击随时间线缩放,技能随之变强(地爆天星伤害在登记爆心时算好存入 PainState)。
- **③ 佩恩失目标传送**:`painLostTeleportTicks`(默认 1200=60s)无目标 → `maybeRelocatePain` 把佩恩传到同世界随机玩家身边并锁定追杀(在某玩家 160 格内找到加载着的佩恩才能传)。
- **④ 强化装备无法破坏**:`EquipmentEnhancer.withLevel` 对 level>0 设 `UNBREAKABLE`,保护投入、被夺也不被打坏。**[待编译验证:`UnbreakableComponent` 构造]**
- **⑤ 精英抢护甲**:缴械除武器外,按 `eliteStealArmorChance`(0.25)抢一件穿戴护甲并穿到精英身上(死亡掉落归还);`STOLE_GEAR` 标记防一只怪累计抢多人装备;被夺武器强化等级记入 `LOST_WEAPON_ENHANCE`。
- **⑥ 武器找回** `/yongye recover`:把 `LOST_WEAPON_ENHANCE` 的 2/3(`weaponRecoverKeepFraction`)转移到手持武器(损失 1/3),清记录。
- 90 个 Java 文件,无新增文件。新增附着 `LOST_WEAPON_ENHANCE`(int)/`STOLE_GEAR`(bool)。

---

## 里程碑 73 — 精英词缀 / 佩恩阶段化 / 存活排行(推荐功能 ①⑤⑥)
- **① 精英词缀**:`enableEliteAffix`,按 `eliteAffixChance`(0.5)随机带 1~2 个,名牌红字显示。爆裂=死亡 4 格 AoE(magic 伤害,不破坏地形)+ 粒子音效;分裂=死亡刷 2 只僵尸;嗜血=命中玩家按 `eliteLifestealRatio` 回血;剧毒=光环每 40t 给 4 格内玩家中毒;召唤=每 120t(有目标时)刷 1 援军。位掩码存 `ELITE_AFFIX` 附着;行为分布于 makeElite(分配/命名)、tickElite(光环/召唤,按 age 错峰)、缴械钩子(嗜血)、`AFTER_DEATH`(爆裂/分裂)。
- **⑤ 佩恩阶段化**:`enablePainPhases`,血量 >66% / 33~66% / <33% 分 3 阶段;进阶即叠加力量+速度(amp 随阶段)、抗性、粒子、广播,并立即施法(`nextAbility=now`)。PainState 加 `phase`。
- **⑥ 存活排行**:`SurvivalRankHandler` 每 5s 记录在线玩家终身最高永夜层 / 最高天数(`BEST_NIGHTFALL`/`BEST_DAY` 附着,死亡保留);`/yongye top` 列在线排行(按永夜层→天数)。
- 91 个 Java 文件(+1 SurvivalRankHandler)。新增附着 ELITE_AFFIX/BEST_NIGHTFALL/BEST_DAY。
- 推荐功能 **②永夜天象 / ③据点防御 / ⑦材料商人** 留待后续(分别需:服务端天象事件设计、新方块+材质+配方、交易 API)。

---

## 里程碑 74 — 永夜天象(推荐功能 ②;血月/酸雨/流星雨)
- 新增 `NightfallWeatherHandler`:永夜 ≥1 时每 `weatherCheckIntervalTicks`(30s)按 `weatherTriggerChance`(0.2)检定,无进行中天象则随机降下一种(持续 `weatherEventDurationTicks` 60s),按永夜等级解锁:
  - **血月**(≥`bloodMoonMinNightfall` 2):每 40t 给玩家 48 格内所有敌对怪叠加 力量II+速度I,怪群狂暴。
  - **酸雨**(≥3):`setWeather` 强制下雨;每 20t 对露天(`isSkyVisible` 头顶可见天)玩家造成 `acidRainDamage`;结束时恢复晴天。
  - **流星雨**(≥4):每 15t 在每名玩家 `meteorRadius` 内随机落点,爆炸/火焰粒子 + 爆炸音 + 落点 `meteorImpactRadius` 内 `meteorDamage` 的 magic AoE(不破坏地形)。
  - 起止全服广播。纯服务端——血月红天/浓雾等客户端渲染未做(用广播 + 玩法效果替代)。
- 92 个 Java 文件(+1 NightfallWeatherHandler)。setWeather/isSkyVisible/spawnParticles 均原版稳定 API。
- 推荐功能 ③据点防御(新方块)/ ⑦商人(交易API)按用户要求**不做**。

---

## 里程碑 75 — 永夜 HUD:屏幕中上显示当前阶段
应需求:开启永夜(等级≥1)时,在屏幕中上显示当前阶段。
- **同步**:新增 S2C `NightfallSyncPayload(int level, String name)`;`NightfallManager.setLevel` 变更后向全体玩家下发,`ServerPlayConnectionEvents.JOIN` 时也下发(`YongyeNet.sendNightfall`)。阶段名复用现成的 `NightfallManager.getLevelName()`(永夜 I·暗潮 … V·灭世 / 深渊 N 层)。
- **客户端**:`YongyeClient` 存 `nightfallLevel/nightfallName`,收到包即更新;`HudRenderCallback` 在 `level≥1` 时把阶段名(深红加粗)居中绘制在屏幕顶部(y=4,boss 血条上方);`hudHidden`(F1)时不画。
- 93 个 Java 文件(+1 NightfallSyncPayload)。**[待编译验证:`HudRenderCallback` 在 1.21.1 的 `(DrawContext, RenderTickCounter)` 签名]**,其余为项目现成 S2C/JOIN 范式。

---

## 里程碑 76 — 永夜剥视(沉浸感:黑暗压缩视野)
应需求:永夜开启即剥夺视线、视野变短,增强沉浸感。
- 新增 `NightfallVisionHandler`:永夜 ≥ `nightfallDarknessMinLevel`(默认 1)时,每 2 秒给非创造/旁观玩家续一次 100t 的「黑暗」(`StatusEffects.DARKNESS`)。屏幕外圈黑暗向内吞噬,有效视野骤缩——"永夜里看不清远处"的恐怖感。
- 续期 40t < 时长 100t,始终有富余,不触发到期淡出 → 稳定持续而非周期闪烁;不显示图标/粒子,保持沉浸。纯服务端施加,客户端自动渲染黑暗叠层,无需 mixin。配置 `enableNightfallDarkness`。
- 94 个 Java 文件(+1 NightfallVisionHandler)。
- 备注:这是"吞噬式黑暗"(视野收拢),并非真正的渲染距离雾;若想要"只能看见 N 格"的距离雾,需客户端 fog mixin(版本敏感,另议)。

---

## 里程碑 77 — 血量 HUD 重做(实时血量 + 回血速率 + 底衬)
应需求:血量 HUD 的回血不实时、看不见回了多少血、数字难看。
- **根因**:`HudCompactMixin.renderHealthBar` 画血量用的是原版传入的动画形参 `health`(带受伤抖动 + 回血心跳延迟,阶梯跳变、不实时);且只有小字无底衬,亮背景下看不清;无任何回血速率提示。
- **新增 `HealthRateTracker`(客户端)**:逐 tick 把 `player.getHealth()` 写入 21 槽(≈1s)带 tick 戳的环形缓冲,扫描窗口内最旧样本算「最近一秒净血量变化 / 秒」(正=回血、负=掉血,<0.1 归零)。挂在 `YongyeClient` 已有的 `END_CLIENT_TICK` 回调里;离开世界 `reset()` 防陈旧样本。纯客户端、零网络。
- **`HudCompactMixin` 重写绘制**:① 血量改读实时 `getHealth()`/`getMaxHealth()`/`getAbsorptionAmount()`(回血时数字平滑上涨);② 整排横向布局 = 红心+血量(白字) · 护甲图标+护甲(蓝白) · 速率(绿/红「+X.X/s」,静止不显示);③ 半透明深色底衬 `fill(...,0x90000000)` 提升可读性。阈值 `YONGYE_HEALTH_THRESHOLD`(60)以下仍走原版。`renderArmor` 取消逻辑不变。
- 95 个 Java 文件(+1 HealthRateTracker)。用到的 `getHealth/getMaxHealth/getAbsorptionAmount/getArmor`(本 mixin 已用)、`DrawContext.fill`(项目多处用,带 ARGB 透明度)、`drawGuiTexture/drawTextWithShadow`(原版本就用)均项目/原版稳定 API,改的是既有 mixin 方法体(签名不动),无新待验证点。

---

## 里程碑 78 — 天象视觉:血月红月贴图 + 酸雨绿雨贴图 + 流星雨真·下落
应需求(上轮"血月能换图吗/酸雨能改雨色吗/流星雨怎么实现"):用户用 GPT 生成红月 + 绿雨贴图,走**资源包永久换贴图**路;流星雨补真下落动画。
- **血月红月**(贴图):用户红月相图(1774×887,2:1,RGB)→ 重采样 1024×512(每月相格 256×256,整数边界防串色)+ 按 `max(R,G,B)` 生成 alpha(黑角透明/红晕半透成光晕/月盘不透),存 `assets/minecraft/textures/environment/moon_phases.png`。**注意:永久红月**(所有夜晚都红,非仅血月事件);要"仅血月时红"需天空渲染 mixin。**[待实机验证:月亮渲染 blend 下 alpha 观感]**
- **酸雨绿雨**(贴图):用户绿雨图(512×2048,RGBA)直接存 `assets/minecraft/textures/environment/rain.png`(雨平铺采样,尺寸宽容)。**注意:永久绿雨**(所有雨都绿);要"仅酸雨时绿"需 WorldRenderer 天气渲染 mixin。
- **流星雨真下落**(`NightfallWeatherHandler`):原来只在地面凭空炸。新增在途流星列表 + `Meteor` 内部类:`spawnMeteor` 在落点上方 45~60 格 + 水平偏移 24 处生成(斜线),`tickMeteors`(register 顶端每 tick 调用,始终执行保证清理)推进位置 + 喷 FLAME/LAVA/LARGE_SMOKE 火尾,~1~1.6s 落地调现有 `impact()`(爆炸+范围伤害)。上限 64 颗护栏。METEOR 分支改 `impact`→`spawnMeteor`。纯服务端粒子,无 mixin。
- 95 个 Java 文件(无新增 Java,仅改 NightfallWeatherHandler)+ 2 贴图。粒子 API 全原版稳定;贴图为资源覆盖,无编译影响。
- 备注:血月红色屏幕叠层(HUD,需小 S2C)未做——用户选了换月亮贴图;要的话可加。

---

## 里程碑 79 — 主菜单「永夜」暗黑化(标题 + splash + 压暗)
应需求:主菜单标题改「永夜」、splash 换永夜主题、背景暗黑风。
- **splash 文字**:新增 `assets/minecraft/texts/splashes.txt`(30 条永夜主题,资源包覆盖原版),替掉黄色 "Hard to label!"。零风险。
- **标题 + 背景**:新增客户端 `TitleScreenMixin`(注册进 mixins.json `client`)。在 `TitleScreen.render` 的 **TAIL 纯叠加绘制**(不取消原版 logo/全景图/按钮的原生渲染,兼容性最稳):① 全屏 `fill(0x66000000)` 压暗(暗黑氛围);② 顶部 86px 深色横幅 `fill(0xD2120006)` 遮住原版 MINECRAFT logo;③ 矩阵放大 4.5× 画「永夜」血红大字 + 副标题 "ETERNAL NIGHT · 活下去"。原版 logo 是像素图集塞不进中文,故标题用文字重画。
- **[待编译验证:`TitleScreen.render(DrawContext,int,int,float)` 签名 + `DrawContext.getMatrices()`/`MatrixStack.scale/translate` + `DrawContext.drawText(...,boolean)` 在 1.21.1]**——TitleScreen 渲染属版本敏感区,以本地 build 为准。
- 96 个 Java 文件(+1 TitleScreenMixin)+ splash 文本。
- 备注:背景目前是"压暗"非"换图";要整张末日全景图需用户提供 6 面立方体贴图(`panorama_0..5.png`)或单张背景图(再接 mixin)。按钮暗黑主题未做(需替换全局 widget 贴图,会影响所有界面)。横幅高度 86 是估值,某些 GUI 缩放下若露出原 logo/压到按钮,调该值即可。

---

## 里程碑 80 — 主菜单永夜大字重做(修原 logo 穿帮 + 加辉光)
应需求(上轮成品截图\"不好看,原 MINECRAFT logo 透在永夜后面\"):
- **根因**:m79 顶部横幅用半透明 `0xD2120006`(82% 不透明),原版 logo 从底下透出来,显得乱。
- **修法**(仅改 `TitleScreenMixin` 绘制方法体,无新文件/无新 API):① 横幅改**完全不透明** `0xFF0A0306` 彻底盖死原 logo;② 横幅下方加 3 段递减透明 fill 做**渐变过渡**(避免硬边)+ 一条 `0xFF8B0000` **血红下边线**(把边缘变成有意设计);③「永夜」大字放大 5×,先画四向偏移的暗红 `0xFF4A0000` **辉光描边**再叠亮血红 `0xFFE01515` 主体,更醒目;④ 全屏压暗加深到 `0x88000000`;⑤ 副标题改字距拉开的 \"E T E R N A L   N I G H T\"。
- 待验证项同 m79(TitleScreen 渲染签名/矩阵/drawText 属版本敏感区,以本地 build 为准)——本轮未引入新接口。
- 96 个 Java 文件(无增减,仅改 TitleScreenMixin 方法体)。
- 仍未做:背景换整张末日图(需用户提供全景/背景图)、按钮全局暗黑主题、"仅事件时"红月/绿雨(需渲染 mixin)。

---

## 里程碑 81 — 灾厄核心提示增强 + 修僵尸一跳一跳
应需求:① 核心刷新除聊天外加 音效+屏幕中央标题+HUD 方向箭头;② 修"僵尸一跳一跳"。
- **修僵尸跳(`PursuitHandler`)**:根因——`wallAhead` 用 `!isAir()` 判墙,把草/花/雪层/麦子等**无碰撞植被**也当成墙,怪走在草地上每 tick 触发 m70 的"起跳翻越"→ 原地一跳一跳。改为新 helper `hasCollision`(`getCollisionShape(world,pos).isEmpty()` 判真实碰撞箱),植被不再误判为墙。仅改判定,挖墙/爬墙/搭塔逻辑不变。
- **核心提示·音效+标题**(`CatastropheCoreManager.spawnCore`):刷新时除原暗红聊天外,给 `coreSpawnNotifyRadius`(120)内玩家发 `TitleFadeS2CPacket(10,60,20)`+`TitleS2CPacket("灾厄核心降临")`+`SubtitleS2CPacket(坐标)` + `playSoundToPlayer(ENTITY_WITHER_SPAWN)`。开关 `coreSpawnTitle`。
- **核心提示·HUD 方向箭头**(像 boss 指示):新增 S2C `CoreLocatorPayload(has,x,y,z)`;`CatastropheCoreManager` 每 2s `sendLocators` 给各玩家下发 `coreLocatorRange`(220)内最近核心坐标(无则 has=false);客户端 `YongyeClient` 存坐标 + 新 `HudRenderCallback`:用玩家当前 yaw + 核心坐标逐帧算相对方位角,`RotationAxis.POSITIVE_Z.rotationDegrees` 旋转「▲」指向核心 + 下方「灾厄核心 N 格」距离。开关 `enableCoreLocator`。
- 配置 +4(coreSpawnTitle/coreSpawnNotifyRadius/enableCoreLocator/coreLocatorRange)。+1 文件(CoreLocatorPayload,97)。
- **[待编译验证]**:① TitleS2CPacket/SubtitleS2CPacket/TitleFadeS2CPacket(已查 yarn 1.21 确存在于 net.minecraft.network.packet.s2c.play,构造 (Text)/(Text)/(int,int,int))② 客户端 `RotationAxis.POSITIVE_Z.rotationDegrees` + `MatrixStack.multiply(Quaternionf)`(箭头旋转)。playSoundToPlayer/HudRenderCallback/getCollisionShape 均项目已用或稳定。箭头旋转方向若实机感觉镜像,把 bearingDeg 取负即可。

---

## 里程碑 82 — 按钮移左侧 + 结晶爆率再降 + 永夜暗角改"固定不闪"
应需求(截图标注左侧竖条 + "结晶还高" + "限视野一直闪"):
- **按钮移到背包左侧竖条**(`YongyeClient` AFTER_INIT):原来是面板上方 2 列网格(挡合成格)。改为面板**左缘外**(guiLeft-bw-4)竖排 7 个:成长/装备/饰品/天赋/强化/兑换/本命职业,bw=54、行距 19,从 guiTop+5 起;落在用户画框的空白竖条里。
- **生命结晶爆率再降**(`LootHandler`+`YongyeConfig`):① 删掉精英分支里**写死的 25% 额外结晶**(与下方统一规则重复,是精英结晶过多主因);② `lifeCrystalDropChance` 0.20→**0.05**(普通5%/精英10%)。**存量 config.json 需 `config set lifeCrystalDropChance 0.05`**;删写死那条重建即生效。
- **永夜限视野"一闪一闪"→固定**:根因——`StatusEffects.DARKNESS`(监守者黑暗)**客户端自带呼吸式脉动**,续期改不掉,天生就闪。改方案:① `NightfallVisionHandler` 默认**不再施加** DARKNESS(gate 新配置 `nightfallDarknessEffect`,默认 false;想要原版脉动可开);② 改用**客户端恒定暗角**:`NightfallSyncPayload` 加 `vision` 字段(服务端按 enableNightfallDarkness+minLevel+等级算强度下发),`YongyeClient` 新 HudRenderCallback 按 vision 画**纯静态边缘压暗**(12 段平方衰减 fill,无任何时间变量→亮度固定绝不闪),vision 越大越暗越收窄(封顶防全黑)。
- 配置 +1(nightfallDarknessEffect);NightfallSyncPayload 记录 +1 字段(2参→3参,构造/codec/读取已同步)。无新增文件(97)。
- **[待验证]**:暗角观感(强度/收窄是否合适,可调 reachX/Y 与 edgeAlpha);按钮在不同 GUI 缩放下是否都落在面板左侧不出屏(bx=guiLeft-58,极小窗口需留意)。均为纯 fill/Screen API,无新接口风险。

---

## 里程碑 83 — 掠夺者队长 Boss 化加天数门控
应需求(实机:刚开局就遇到「掠夺者 Boss」——掠夺者巡逻队长被 BossHandler 当原版 Boss 强化,而巡逻队自然刷新不受门控)。
- `BossHandler` ENTITY_LOAD:对 `RaiderEntity`(经 isBoss 判定即巡逻队长)加 `ProgressionManager.gameDay(world) < bossRaidCaptainMinDay` 门控,早于设定天数直接 return 不打 IS_BOSS。真·Boss(凋灵/监守者/远古守卫/末影龙)不受影响,仍始终强化。
- 配置 +1:`bossRaidCaptainMinDay = 8`(默认第 8 天起队长才 Boss 化)。
- 注:早于该天数的队长 = 原版(既不 Boss 也不被 MobEnhancement 增强,因 isBoss 仍返回 true 会被其跳过)——符合"开局别遇 Boss 队长"诉求。
- 无新增文件(97)。纯天数判定,无新接口,重建即生效。

---

## 里程碑 84 — 调试菜单加「Boss 门控」组
应需求(把 m83 的队长门控做进调试菜单,免敲命令)。
- `DebugScreen` 新增分组「Boss 门控」3 按钮(走 `config set bossRaidCaptainMinDay`):队长Boss·第8天 / 第15天 / 关闭(9999)。分组计数 8→9(垂直居中估算同步)。
- 无新增文件/无新接口,纯按钮+现有 config set 命令。重建即生效。

---

## 里程碑 85 — 调试菜单重做为「分页全命令」
应需求(把所有可用命令都放进调试菜单)。
- `DebugScreen` 重写为**顶部分类页签 + 分页**:8 页(永夜/道具/神器/职业/刷怪/掉率/配置/天赋),覆盖 ModCommands 全部子命令。
  - 永夜:nightfall 0/4/5/7、status、redeem;quest hunt/survive/flee/core/gather;top。
  - 道具:book、level、enhance、recover;skillbook 全 8 型(attack/armor/regen/evasion/thorns/resistance/satiety/steal)。
  - 神器:artifact 全 10 型(life_idol…world_anchor)L3;wardbook。
  - 职业:classbook×6、classweapon×6、chaosblade、tankshield。
  - 刷怪:elite/mobboss/painboss/core。
  - 掉率:loot show/shard/crystal/core/bloodcore/enable。
  - 配置:config set 常用预设×8 + Boss门控×3 + config list/reset。
  - 天赋:talent / list / reset(learn/info 需 id,按钮无法枚举,留手敲)。
- 数据用 `Page/Section/Btn` record 静态表;`section()` 支持按钮自动换行(超 COLS 换行);页签点击 `rebuildWidgets()` 重建本页;当前页签 `active=false` 高亮。
- 命令串与 config 字段名已逐条核对(8 skillbook 型 / 10 artifact 型 / 6 职业 / 9 config 字段全部对得上)。
- 仅改 DebugScreen 单文件;新增用法仅 `rebuildWidgets()` + `ButtonWidget.active`(均 1.21.1 稳定)。无新增文件(97)。

---

## 里程碑 86 — 爆率编辑器(直接输入)+ 导出配置
应需求(爆率界面可直接输入修改;调完导出配置给作者设默认)。
- **爆率编辑器**(新 `DropRateConfigScreen`):14 个爆率/经验字段做成**文本输入框**(碎片/结晶/核心/血核/技能书精英·普通/职业书/职业武器/精英化/怪物BOSS化 + 4 档额外经验),改完点「✔ 应用并保存」对每个字段 `config set <key> <值>`(即时生效并写盘)。预填当前值:
  - 新 C2S `RequestConfigPayload`(空,`PacketCodec.unit`)+ S2C `ConfigValuesPayload(String data='key=value\n...')`,字段清单 `EDITABLE_KEYS` + 中文标签 `labelOf` 定义在 payload 内(服务端客户端单一来源)。
  - 服务端 `YongyeNet.sendConfigValues` 用 `YongyeConfig.getFieldString`(新公开反射读值)拼回传;打开编辑器即请求,到达后 `onValues` 填入 CACHE 并 `rebuildWidgets` 预填(无请求→无循环)。
  - 入口:调试菜单「掉率」页新增整行按钮「✎ 爆率编辑器」(客户端 setScreen,非命令);另有「↻ 刷新当前值 / ⤓ 导出配置 / 关闭」。
- **导出配置**:新 `/yongye config export` 命令——`save()` 后打印 `config/yongye.json` 绝对路径到聊天 + 日志(`YongyeConfig.configPath()`);调试菜单「配置」页维护组加「导出配置(路径)」按钮。用户据路径找到文件发作者即可设默认。
- 新增 3 文件(RequestConfigPayload / ConfigValuesPayload / DropRateConfigScreen,100);改 YongyeConfig(+configPath/+getFieldString)、YongyeNet、YongyeClient、DebugScreen、ModCommands。
- **[待验证]**:`TextFieldWidget(TextRenderer,x,y,w,h,Text)` 6 参构造 + setText/getText/setMaxLength(vanilla 稳定控件,低风险);其余复用已验证范式(unit 包 / ClientPlayNetworking.send / 反射 config)。

---

## 里程碑 87 — 修编译错误:rebuildWidgets → clearAndInit
m85/m86 用了 `Screen.rebuildWidgets()`,但 1.21.1 无此方法(build 报"找不到符号")。1.21.1 正确的清空重建入口是 `clearAndInit()`(protected,清子控件后重跑 init)。改 DebugScreen(切页)+ DropRateConfigScreen(onValues 刷新)两处。其余 m86 代码(TextFieldWidget 6 参构造、爆率编辑器网络包/反射)本次 build 未报错=已编译通过。

---

## 里程碑 88 — 修精英 tick 重入崩溃(ConcurrentModificationException)
由用户实机崩溃报告定位(双人在线,server crash)。崩溃栈:`ConcurrentModificationException` → `WeakHashMap$KeyIterator.next` → `EliteHandler.lambda$register$5(EliteHandler.java:205)` ← `ServerTickEvents`(每 tick 服务端回调)。
- **根因(单线程重入,非多线程、非 GC)**:`END_SERVER_TICK` 回调直接迭代 `ELITES`(`Collections.newSetFromMap(WeakHashMap)`);循环内对带「召唤」词缀(`AF_SUMMON`)的精英调 `tickElite`→`sw.spawnEntity(僵尸)`,而 `spawnEntity` **同步触发** `ServerEntityEvents.ENTITY_LOAD`→该回调对刚生成的 `Monster` 掷精英概率,命中即 `ELITES.add(...)`——遍历途中结构性改集合,下一次 `it.next()` 抛 CME 打崩 tick。双人在线召唤怪更易命中精英化,故复现稳定。(GC 清理 WeakHashMap 弱键不改 modCount,JDK 专门处理过,与 GC 无关;射箭/扔药生成的是投射物,被 `instanceof Monster` 过滤,只有「召唤」这条路触发。)
- **修法(`EliteHandler` 单文件)**:tick 回调改为**遍历快照** `new ArrayList<>(ELITES)` + 死亡精英**延后统一删**(循环结束后再 `ELITES.remove`/`LAST_TELEPORT_AGE.remove`)。对任何「循环内又生成怪 / 回填同集合」的重入都免疫,不只是堵召唤——召唤出的怪照常进 `ELITES`,只是本 tick 不处理、下一 tick 才纳入。移除原 `Iterator` 用法(连带删 `import java.util.Iterator`),补 `import java.util.ArrayList`。
- **同类排查**:`MobBossHandler`(同设计:追踪集合 + 每 tick 更新血条)tick 循环只刷血条 + `it.remove()` 清死亡,**循环内不生成实体也不回填 `BARS`,安全**。其余 tick 处理器均无「遍历追踪集合 + 循环内生怪 / 回填同集合」组合,不暴露此 bug。
- **静态自检**:`Iterator` 无残留、`ArrayList`/`List` import 到位、花括号 60/60、圆括号 501/501 配平。**仅用 `java.util` 集合 + 现成 `ELITES`/`LAST_TELEPORT_AGE`,未碰任何 Fabric/Mojang 接口或 yarn 映射,无版本敏感点**。无新增文件(100)。
- **[待验证]**:本地 `./gradlew build` 跑通(按守则,结论以实测为准);实机——凑出带【召唤】词缀的精英锁定玩家、撑过多次召唤(原必崩),确认不再抛 CME、tick 正常、召唤的僵尸也正常(部分会自变精英)。

## 里程碑 89 — 守护书可用于护甲/盾牌 + 缴械夺护甲/副手盾守护生效
应用户反馈:守护附魔书只能打武器,护甲和自定义磐盾(`TankShieldItem`)打不上;且即便打上,护甲仍会被精英缴械夺走。
- **根因(两层)**:① `WardBookItem` 用 `EquipmentEnhancer.isWeapon(target)` 当门槛,只放行 WEAPON/HYBRID;磐盾带 `GENERIC_ARMOR` 被 `kindOf` 归为 ARMOR、普通盔甲同理,全被挡。② `EliteHandler` 缴械的「夺护甲」段(头/胸/腿/脚四槽)**从未检查 `DISARM_PROOF`**,故守护过的护甲照夺不误——守护对护甲实际无效。③ 盾在副手槽,而夺取两段(夺主手武器、夺四件护甲)都不遍历 `OFFHAND`,故盾根本不在被夺范围,守护盾无意义。
- **修法**:
  - `EquipmentEnhancer` 新增 `isWardable(stack)`:WEAPON/HYBRID/ARMOR 均可守护,另用 `instanceof ShieldItem` 兜底无属性的原版盾。
  - `WardBookItem`:门槛 `isWeapon`→`isWardable`,变量 `weapon`→`target`,文案/类注释「武器」泛化为「装备」。
  - `EliteHandler` 夺护甲段:① 跳过已被 `DISARM_PROOF` 的部件(守护护甲真正生效);② 候选槽加 `OFFHAND`,副手仅夺「装备类」(`isWardable`,排除火把/食物/方块等杂物),并按主手夺武器同逻辑**允许覆盖精英自带的免费盾**(后期精英从 `eliteEquipStartDay` 起带盾,否则永远夺不到);夺取提示按槽区分「护甲/副手盾」。
  - `zh_cn.json` 守护书说明同步更新。
- **静态自检**:三 Java 文件花括号/圆括号全配平、`isWeapon` 在守护书无残留、`isWardable` 定义+引用到位、`zh_cn.json` 合法。`ShieldItem` 是磐盾已继承的原版类、`EquipmentSlot.OFFHAND` 本文件第 281 行已在用(精英给盾)、`DISARM_PROOF`/`getEquippedStack`/`getOrDefault` 同段已用——**无新接口、无版本敏感点**。无新增文件(100)。
- **[待验证]**:本地 `./gradlew build`(按守则结论以实测为准);实机——守护书对护甲/磐盾右键应可附「⚔ 无法被夺取」;凑精英缴械,守护过的护甲/盾不被夺、未守护的会被夺并穿到精英身上(击杀夺回)。

## 里程碑 90 — 守护书改 GUI(右键开界面点选装备) + 精英必爆套餐
应用户反馈:守护书原本「另一只手拿装备右键」太繁琐;且精英爆率偏低。
- **守护书 GUI(右键开界面,点装备即附)**:`WardBookItem.use()` 改为服务端发 S2C `OpenWardPayload` → 客户端开 `WardScreen`(照 OpenDebug/ExchangeScreen 范式)。`WardScreen` 每帧实时扫本地背包(含护甲/副手),用 `EquipmentEnhancer.isWardable` 过滤出所有可守护装备(原版+永夜,武器/护甲/盾),画图标+名字+分页;点未守护项 → C2S `WardApplyPayload(背包槽位)`。服务端 `WardBookItem.applyWard` 重新校验(槽位合法+可守护+未守护+背包有书)→ 打 `DISARM_PROOF`+扣 1 本书+反馈;界面读实时背包,附完即变「✔已守护」不可再点。新增 `WardScreen`/`OpenWardPayload`/`WardApplyPayload` 三文件,`YongyeNet` 注册收发、`YongyeClient` 注册开屏。**界面 API(drawItem/fill/shouldPause/mouseClicked/renderBackground/ButtonWidget)全部在既有屏幕(WeaponInfoScreen/DebugScreen/ClassSelectScreen/ExchangeScreen)有先例,无新版本敏感点。**
- **精英必爆套餐**:`LootHandler` 精英分支顶部加保底(叠在原概率掉落之上)——必爆 `eliteGuaranteedShards`(10)生命碎片 + `eliteGuaranteedCrystals`(1)生命结晶 + `eliteGuaranteedSkillBooks`(1)本随机技能书(新工具 `randomAnySkillBook`:血量书+6属性书等概率,等级 `eliteGuaranteedSkillBook[Min/Max]Level` 默认1~3)。受 `eliteGuaranteedDrops`(默认 true)总开关控制。新增 6 个配置字段(可 `/yongye config set` 调)。
- **静态自检**:9 文件花括号/圆括号全配平、配置字段定义↔使用一一对齐、调用链闭合(applyWard 被 net 调、WardScreen 被 client 开、两包均注册)、`zh_cn.json` 合法。新增 3 个 Java 文件(100→103)。
- **[待验证]**:本地 `./gradlew build`;实机——debug 神器页拿守护书右键开界面、点护甲/磐盾附守护;debug 刷怪页精英化后击杀看必爆 10 碎片+1 结晶+1 随机书。
- **[待办]** 用户提的「然后 永夜」语义未明,暂挂;是否给必爆配置加 debug 预设按钮未定。

## 里程碑 91 — 噬魂杖 3D 模型替换
应需求(原 handheld 扁平贴图观感差)。
- **模型升级**:`class_weapon_warlock.json` 从 `parent: handheld` 改为完整 `elements` 3D 模型(9 个 cube,全部绕 Z 轴旋转 45° 对角摆放):
  - **杖身**:4 段接续组成斜向长杆(2px 宽,棕色三层深浅:深棕/中棕/浅棕高光)。
  - **握环**:中段比杖身宽 1px 的深紫色装饰环。
  - **水晶主体 + 顶尖**:杖头菱形水晶(青紫渐变,深紫→亮紫→白光)。
  - **托爪左右**:水晶底部夹持爪(深紫)。
  - `display` 块配置了 thirdperson/firstperson/gui/ground/fixed 六档握持姿势。
- **贴图**:`class_weapon_warlock.png` 替换为新手绘 16×16 像素图,配色:水晶区青紫渐变(#E6D2FF→#7B1EFF)、杖身棕色三层(#50371E/##6E502D/##8C643 7)、握环深紫(#230F3C/#3C1964)。
- **纯资源改动,无 Java 代码变动**,无版本敏感点,无新文件(103)。
- **[待验证]**:本地 `./gradlew build` + 实机观察噬魂杖握持/背包图标效果;display 数值为起点,实机截图后可微调 rotation/translation/scale。

## 里程碑 92 — 噬魂杖模型重做(竖直长杖)+术士蓄力施法
**模型重做(纯资源)**:
- `class_weapon_warlock.json` 重写为竖直长杆:杖身 2×2px 截面高 14 格、握环居中、顶部 4×4px 水晶主体+顶尖、四方向托爪(前后左右);取消 m91 的 45° 对角旋转。
- `class_weapon_warlock.png` 重绘:杖身深棕木纹(四列 UV 分区)、握环深紫、水晶青紫渐变、托爪深紫。
**术士蓄力施法(ClassWeaponItem)**:
- `use()`:术士+职业激活时 `setCurrentHand(hand)` 进入蓄力,其他职业武器返回 PASS。
- `getMaxUseTime()`:返回 `warlockBoltChargeTicks×3`(给足余量)。【待编译验证:LivingEntity 参数】
- `getUseAction()`:返回 `UseAction.BOW`(举臂蓄力姿势)。【待编译验证】
- `usageTick()`:每 8tick 旋转灵魂火粒子 + 音调渐高的末影人环境音。【待编译验证】
- `onStoppedUsing()`:松手释放魔法弹——蓄力进度(used/chargeTicks)算倍率 0.4→1.0×;扣血→手动逐点射线(步长 0.5 格)找第一个目标→魔法伤害+命中爆点双粒子;未命中播音效。专属武器持有:伤害×1.2 耗血×0.8。【待编译验证:1.21.2+可能返回 boolean】
- 新增配置:`warlockBoltDamage`(18.0)、`warlockBoltHpCost`(3.0)、`warlockBoltRange`(20.0)、`warlockBoltChargeTicks`(30)。
- tooltip 加「右键蓄力吟唱,松手释放魔法弹」说明。
- 原有潜行近战 AoE 仍保留在 ClassSkillHandler(作为第二技能)。
- 静态自检:花括号 33/33、圆括号 196/196 配平;配置字段定义↔使用对齐;无新增文件(103)。

## 里程碑 93 — RPG 血条 + 六职业 MP 条
应需求:血条重写为横向 RPG 长条,全职业加 MP/资源条。

**血条重写(HudCompactMixin)**:
- 血量超 60 阈值时替换原版心形为横向长条(182px×6px,与经验条等宽)。
- 结构:深红底色→鲜红填充(按当前血/最大血)→顶部 1px 高光;吸收层金色叠在右侧;护甲图标+数值在条右;回血/掉血速率绿/红字在条左;全部数字居中/紧贴。
- 半透明黑底衬,提升亮背景可读性。
- 同时取消原版护甲条上浮(原版血量多时护甲条往上顶的问题)。

**MP 条(血条下方 4px)**:
- 统一样式:深色底→职业色填充→顶部高光线→右侧小字标签。
- 六职业资源含义与颜色:
  - 术士「灵力」紫色:= 当前血/最大血,施法耗血即掉条,实时反映。
  - 刺客「暗能」深蓝:= 脱战时间(最近受伤/命中后清零,10s满格)。
  - 战士「怒气」橙红:= 受伤积累(每次受伤+0.15~0.30,2s无战斗缓慢衰减)。
  - 剑客「剑气」青白:= 命中积累(每次命中+1层,最多10层显示满格)。
  - 肉盾「坚守」暗金:= 静止时间(移动即清零,静止5s满格)。
  - 武僧「拳意」金橙:= 当前连击数/10(复用已有 comboCount)。

**服务端 MP 计算(ClassSkillHandler)**:
- 新增状态变量:warriorRage/swordsmanEdge/tankLastMove+tankLastPos/assassinLastHit。
- 命中钩子:剑客剑气+1、刺客命中清暗能计时。
- 受伤钩子:战士受伤+怒气(0.15~0.30)、刺客受伤清暗能计时。
- tick 循环:战士怒气每 tick 衰减(2s无战斗后开始)、肉盾检测移动重置坚守、每10tick用 getMp() 计算并发 MpSyncPayload。

**新增文件**:MpSyncPayload(104 Java 文件)。
**待编译验证**:renderHealthBar 注入签名(同 m77 已验过的方法,低风险);其余 fill/drawTextWithShadow/drawGuiTexture 均为仓库先例。

## 里程碑 94 — 全武器 3D 模型重做 + HUD 整合饥饿
应需求:法杖难看、锤子要大(雷神锤)、拳套要贴手、盾朝向不对、剑要立体;血条和食物条冲突。

**全武器 3D 模型(elements,沿用/新绘 32×32 贴图)**:
- **法杖(warlock)**:照参考图重画 32×32 贴图(紫色发光宝石+星芒+双爪+螺旋杖身+尖尾);模型竖直长杆+宝石主体+左右张开爪+杖头托+杖尾尖(7 elements)。
- **锤(warrior)**:新画雷神锤贴图(大锤头+蓝色符文+缠绕手柄+金属环);模型大锤头(10×10×6)+颈环+手柄,放大握持(3 elements)。
- **拳套(monk)**:新画贴图(金属指节+皮革+绑带+腕甲);模型护手贴手背(扁平)+指节凸起+腕甲,display 缩小贴手(scale 0.6~0.7,不再像武器平举)(3 elements)。
- **剑(swordsman)**:新画流光剑贴图(青色血槽刃+十字护手+蓝柄+宝石);模型立体剑刃+护手+柄(3 elements)。
- **镇魂(tank)**:立体宽刃重剑(刃+宽护手+柄,沿用旧贴图)(3 elements)。
- **影刺(assassin)**:立体短匕首(短刃+小护手+短柄,沿用旧贴图)(3 elements)。
- **盾(tank_shield)**:从 generated 扁平改为立体盾板(10×16×2 带厚度)+中央盾脐;display 竖直朝外(rotation [0,90,0]),修正原横躺/平板朝向(2 elements)。

**HUD 整合(HudCompactMixin)**:
- 血条+MP条整体上移(top 从 -39 改 -52),腾出底部空间。
- 饥饿整合到血条右侧(图标+数字 food/20),护甲同排;取消原版食物条(renderFood 注入 cancel)+护甲条。
- 速率移到血条左上,避免和护甲/饥饿挤一行。

**待编译验证**:`renderFood` 方法名(1.21.1 InGameHud 食物条方法,可能叫 renderHungerBar/renderFood;若 build 报找不到方法,改成本地 yarn 实际名)。其余 fill/drawGuiTexture/drawTextWithShadow 均有先例。模型纯资源无代码。

## 里程碑 95 — 剑客天空之刃(成品模型接入)+ 血条修复
**剑客武器换成用户提供的成品模型**:
- 用户上传 Skyward_Blade 资源包(作者 Pramanix,252 elements,1024×1024 主贴图+自发光层)。
- 接入:模型 JSON 贴图引用从 `minecraft:item/diamond_sword` 改为 `yongye:item/class_weapon_swordsman`,放入 yongye models;主贴图+`_e`自发光贴图放入 yongye textures。
- display 全套(thirdperson/firstperson/gui/ground/head/fixed)沿用原作者配置,握持朝向已专业调好。
- **自发光说明**:`_e` 贴图是 OptiFine emissive 格式,纯 Fabric 不识别;装 OptiFine 或 Iris+Sodium 才会发光,不装则正常显示不发光(不影响功能)。
- 放弃 m94 手写的剑客立体剑(手写盲调质量差)。

**血条修复(HudCompactMixin)**:
- m94 底衬留了大空框(padX/padY 过大+血量数字挤在 6px 条内被截)。
- 修:底衬收紧到贴合内容(±2px);血量数字从条内移到条上方(top-1)完整显示;速率同步对齐。

**纯资源+单文件改动,无新接口**(剑客模型是纯 JSON,血条是已验证的 fill/drawText)。
**待编译验证**:无新版本敏感点(沿用 m93 已验证的 HUD API)。

## 里程碑 96 — 战士巨阙(成品巨剑接入)
应需求:战士武器换成用户提供的 Scarlet Sands 巨剑(替换 m94 手写锤)。
- 用户上传 Scarlet_Sands 资源包(作者 Pramanix,含三把:金剑/金斧/金锄)。选最长的 golden_sword(324 elements,14.3格长,1024 主贴图+自发光层)作战士「巨阙」。
- 接入:贴图引用从 `minecraft:item/golden_sword` 改为 `yongye:item/class_weapon_warrior`,模型放入 yongye models,主贴图+`_e`贴图放入 textures。
- display 全套沿用作者配置。emissive 同前:OptiFine/Iris 才发光。
- 另两把(金斧/金锄)暂未接,待用户看巨剑效果后定分配。
- 纯资源,无代码,无版本敏感点。

## 里程碑 97 — 战士巨阙改金斧 + 术士法杖改金锄(成品)
应需求:斧子厚重感更配「巨阙」,金锄细长带杆更像法杖。重新分配 Scarlet Sands 三把:
- **战士巨阙** 改用 `golden_axe`(374 elements,8.3格,1088 贴图+自发光);替换 m96 的 golden_sword。修正原模型 textures 引用里多余的 `.png` 后缀。
- **术士噬魂杖** 改用 `golden_hoe`(347 elements,5.4格细长带杆,1024 贴图+自发光);替换 m95/m92 手写法杖。新增术士 `_e` 自发光贴图。
- golden_sword(巨剑)本轮空出未用,备用。
- display 全套沿用作者配置。emissive 同前(OptiFine/Iris 才发光)。
- 纯资源,无代码。

## 里程碑 98 — 刺客影刺(成品剑,改紫色)
应需求:刺客武器换成 Light of Foliar Incision 成品剑,改成紫色。
- 用户上传资源包(作者 pramanix,iron_sword,348 elements,1024 主贴图+2048 自发光层)。原色橙黄+青绿。
- **改色**:用 PIL 色相旋转——所有饱和度>0.08 的像素色相强制设为紫色(275°/HSV 0.764),保留明暗和饱和(略提10%);主图+`_e`自发光图同样处理。
- 接入:贴图引用从 `minecraft:item/iron_sword` 改为 `yongye:item/class_weapon_assassin`,模型放入 yongye models,转紫后贴图放入 textures。
- display 全套沿用作者配置。emissive 同前(OptiFine/Iris 才发光)。
- 替换 m94 手写的影刺匕首。纯资源,无代码。

## 里程碑 99 — 职业武器统一暗黑永夜风(暗紫)
应需求:把所有武器统一改成暗黑永夜风格。
- 四把成品武器(剑客天空之刃白蓝/战士金斧金红/术士金锄金红/刺客叶刃已紫)统一处理:
  - 色相强制转暗紫(275°/HSV 0.76),饱和度×1.15。
  - 主贴图整体压暗×0.82;`_e`自发光层只压暗部(vv<0.7),保留高亮发光处烘托暗黑感。
- 处理后四把主色调全部统一为紫色系,呼应永夜主题。
- 坦克/武僧手写武器+磐盾暂不动(待成品替换)。
- 纯贴图改动(PIL 处理),无模型/代码变动。
- **观感待实机确认**:暗紫色相/压暗程度为起点,过深/过浅可调 hue(0.76)/dark(0.82)/sat(1.15)。

## 里程碑 100 — 永夜之翼(可滑翔背饰,恶意检察官羽翼模型)
应需求:把买的「恶意检察官」武器包里的大羽翼(sword_4)做成可穿戴、有鞘翅滑翔功能的背饰。
- **物品 NightWingItem extends ElytraItem**:继承原版鞘翅,自动获得滑翔功能(1.21.1 滑翔硬编码在 ElytraItem 类;glider 数据组件是 1.21.2+,故继承是正确做法)。穿鞘翅槽即可飞。注册 NIGHT_WING(maxDamage 648,EPIC),进物品组。
- **模型**:sword_4(540 elements,3 张贴图:256/128/128×1280自发光),贴图引用改 yongye 命名空间(night_wing_1/2/3ef),放入 models+textures。手持/物品栏/地上显示为羽翼 voxel 模型。
- **获取**:新命令 `/yongye nightwing` + debug 神器页「其它」区按钮;lang「永夜之翼」。
- **局限说明(诚实)**:穿在背上飞行时,渲染走原版鞘翅实体模型(不是 voxel 羽翼形状)——让背部显示成 voxel 羽翼需自定义 FeatureRenderer(版本敏感大工程),本里程碑未做。当前:手持显示羽翼模型 ✓ + 滑翔功能 ✓ + 背部暂为鞘翅形状。
- **[待编译验证]**:`ElytraItem` 1.21.1 构造函数 + appendTooltip(TooltipContext) 签名(仓库无 ElytraItem 先例,按 TankShieldItem 同款写法);若 build 报错贴出。

## 里程碑 101 — 永夜之翼:加配方 + 可强化
应反馈:m100 永夜之翼没配方、不能强化,补上。
- **合成配方**(`recipe/night_wing.json`,shaped):
  ```
  D E D     E=鞘翅  D=永夜尘
  A E A     A=深渊魂晶
  R B R     R=裂隙碎片  B=灾变血核
  ```
  鞘翅+永夜高级材料合成,产 1 个永夜之翼。
- **可强化**:`EquipmentEnhancer.kindOf` 加 `ElytraItem → Kind.ARMOR` 兜底(鞘翅无攻击/护甲属性、非 ArmorItem,原归 NONE 不可强化)。强化按护甲走:加生命/护甲/韧性 + 耐久上限。
- **属性槽修正**:新增 `slotForItem`——鞘翅类强化属性绑 `AttributeModifierSlot.CHEST`(背饰穿胸甲槽,确保生命/护甲在穿戴时生效;原 armorSlotOf 因鞘翅无 GENERIC_ARMOR 会回退 ARMOR 槽,CHEST 更精确)。
- 现在永夜之翼可用强化窗口(背包「强化」按钮)升级,与其他护甲一致。
- 静态自检:EquipmentEnhancer 花括号 26/26、圆括号 141/141 配平;配方 JSON 合法。
- **[待编译验证]**:`ElytraItem` instanceof 判断(同 m100,仓库无 ElytraItem 先例);`AttributeModifierSlot.CHEST` 取值(应存在,1.21.1 标准枚举)。

## 里程碑 102 — 饰品栏加鞘翅格 + 永夜之翼放饰品栏可滑翔
应需求:永夜之翼放饰品栏生效(滑翔)。
- **饰品栏扩容 10→11**:`AccessoryStorage.SIZE` 11。前 10 槽神器不变,新增第 11 槽(index 10)为「鞘翅/背饰专用格」,位于 GUI 右侧(152,28),只接受永夜之翼或原版鞘翅(canInsert 过滤)。客户端 AccessoryScreen 给该格暗红边框 + 空格显「翼」字标识(按坐标 152,28 识别)。
- **饰品栏滑翔(AccessoryGliderMixin)**:mixin `PlayerEntity#checkGliding` 的 RETURN——若已能滑翔不干预;否则扫描饰品栏,有永夜之翼则 setReturnValue(true) 放行滑翔。即放饰品栏(任意槽,实际进鞘翅格)也能飞,不占胸甲位。
- **旧存档兼容**:SIZE 扩容对 Inventories.readNbt 安全(旧 10 槽照读,第 11 槽空)。
- **[待编译验证·高]**:`PlayerEntity#checkGliding` 方法名(1.21.1 玩家滑翔检查的确切名不确定,可能是 checkGliding/checkFallFlying 等)。mixin 用 require=0 兜底——名字不符则静默跳过不崩游戏;若实测放饰品栏不能飞,把 PlayerEntity/LivingEntity 里滑翔检查的实际方法名告诉我改 method=。

## 里程碑 103 — 武僧单独系统(吃材料强化自身)+ 武僧去武器 + 盾牌改回扁平
应需求:武僧不要武器、空手作战、吃材料直接强化自身(越吃越肥越能打);盾牌改回扁平贴图。

**武僧吞噬系统(新 MonkSystem)**:
- `UseItemCallback`:武僧右键吃永夜材料 → 永久 +拳击伤害 +生命上限(消耗1个),按稀有度给量:
  碎片[1拳/2血]、结晶[2/6]、核心[5/16]、血核[10/30]、永夜尘[6/12]、裂隙[8/20]、深渊魂晶[12/24]、终焉精华[20/50]。
- 反馈:HEART+CRIT 粒子 + 打嗝音效 + "吞噬!拳击+X·生命+Y"。
- 新 `MONK_HP_BONUS` 附件存生命加成(MONK_FIST_BONUS 存拳击);ClassManager 应用:拳击(空手时,MONK_FIST_ID)+生命(不限空手,MONK_HP_ID,越吃越肥一直在)。
- 区别其他职业:别人材料拿去合成/强化装备,武僧直接吃。

**武僧去武器**:删 class_weapon_monk 配方 + debug 武僧武器按钮(物品注册保留避免连锁破坏,但不可合成/不在菜单)。武僧核心=空手拳+吃材料。

**盾牌改回扁平**:tank_shield 从 m94 立体模型改回 `parent:generated` 扁平贴图(沿用 m53 display 块,握持姿势保留),显示现有 tank_shield.png。

- 静态自检全过(MonkSystem 15/15·44/44 等全配平)。
- **[待编译验证]**:`UseItemCallback` 返回类型(1.21.1 Fabric API 应为 TypedActionResult<ItemStack>,仓库无先例)。

## 里程碑 104 — 取消磐盾(并入铁壁核心)+ 配方倒挂修复 + 永夜系兑换链
应需求:盾牌取消、防御并入铁壁核心(所有人可拿);配方有的"残破"比"远古"贵;永夜系材料也支持兑换。

**取消磐盾,防御并入铁壁核心**:
- 铁壁核心(IRON_CORE)除原抗性效果外,新增按等级递增的护甲/韧性/击退抗性/生命(合并原磐盾数值,满级≥原磐盾):护甲2→8、韧性1→5、击退0.1→0.4、生命4→16。所有人放背包即生效(神器机制)。
- 磐盾取消获取:删配方 + debug 按钮 + 移出物品组(物品注册和 ClassSkillHandler 格挡逻辑保留,兼容老存档不报错)。

**配方倒挂修复**:批量核查 10 个神器三档(基础/远古/终焉)配方价值,发现2处倒挂:
- 不灭余烬:基础版中心料用了终焉精华(最贵)→改 life_shard;价值 5008→30,恢复 基础<远古<终焉。
- 巫毒净瓶:基础版用了深渊魂晶→改 life_shard;价值 1526→44,恢复正常梯度。

**永夜系兑换链**:MaterialExchange 加 tier 3/4/5——永夜之尘→裂隙碎片→深渊魂晶→终焉精华,沿用 10:1。ExchangeScreen 兑换界面加 3 行(共 6 行),调整布局。

- 静态自检全过。**[待编译验证]**:无新接口(applyAttribute/兑换均复用既有);属性表/兑换 tier 纯数据。
- 注:物品 tooltip 末尾斜体"永夜"是原版显示来源 mod 名的正常行为,非 bug。

## 里程碑 105 — 坦克镇魂成品大剑接入(暗紫统一)
应需求(自主决定):坦克镇魂换成品,补齐最后一把手写武器。
- 用恶意检察官包 sword_2(大剑,475 elements,256 贴图,作者 Bokprng/Cubik Studio)作坦克「镇魂」——厚重大剑配坦克"立于阵前"设定,且与永夜之翼(同包 sword_4)美术统一。
- 接入:贴图引用改 yongye:item/class_weapon_tank,模型+贴图放入。display 全套沿用作者配置。
- 暗紫统一:同 m99 处理(色相转 0.76 紫,饱和×1.15,压暗×0.82),与其他四把成品一致。

**六职业武器/装备最终状态**:
- 战士巨阙=金斧 / 术士噬魂杖=金锄(红沙包)
- 剑客流光=天空之刃 / 刺客影刺=叶刃光(紫)
- 坦克镇魂=恶意检察官大剑(本里程碑)
- 武僧=无武器(m103 吃材料系统)
- 永夜之翼=恶意检察官大羽翼(背饰)
全部成品 3D 模型 + 暗紫永夜风统一。

**剩余未用备用成品**:红沙 golden_sword(巨剑) + 恶意检察官 sword_1/3/5,留作日后特殊武器/扩展。
- 纯资源,无代码,无版本敏感点。

## 里程碑 106 — 修饰品栏滑翔 mixin 方法名(checkGliding→canGlide)
m102 build 报警告:`AccessoryGliderMixin` 注入 `PlayerEntity#checkGliding`——该方法名 1.21.1 不存在(Unable to determine descriptor),注入未生效(require=0 未崩但失效),导致永夜之翼放饰品栏不能滑翔(穿鞘翅槽仍可,那是继承功能)。
- 修:mixin 目标从 `PlayerEntity#checkGliding` 改为 `LivingEntity#canGlide`(1.21.1 yarn 中实体能否滑翔的判定方法),RETURN 注入,饰品栏有永夜之翼则 setReturnValue(true)。@Mixin 目标类同步 PlayerEntity→LivingEntity。
- 仍 require=0 兜底:若 canGlide 名字仍不符,警告不影响 build,功能失效但不崩;届时换实际方法名(候选 canGlide/wantsToGlide/tickFallFlying)。
- 静态自检:4/4·20/20 配平。
- **build 已 SUCCESSFUL(m105 全部改动编译通过)**,本里程碑仅修此警告对应的功能失效。

## 里程碑 107 — 重写饰品栏滑翔 mixin(依据真实源码 tickFallFlying)
用户提供 1.21.1 LivingEntity 源码,确认 m102/m106 方法名(checkGliding/canGlide)在 1.21.1 根本不存在。
**真实滑翔机制**(源码):滑翔=flag(7)位;每 tick `tickFallFlying()`(private void 无参)检查,维持条件「胸甲槽 isOf(Items.ELYTRA) 且 ElytraItem.isUsable」——只认原版鞘翅,连继承 ElytraItem 的自定义物品都不认。
- 推论:永夜之翼穿胸甲槽也飞不了(非 Items.ELYTRA),放饰品栏更不看。
- **修**:mixin `tickFallFlying` HEAD——若(胸甲槽是永夜之翼)或(饰品栏有永夜之翼)且在空中/无坐骑/无飘浮,强制 setFlag(7,true)+emitGameEvent(ELYTRA_GLIDE)+cancel 原方法(防其因非原版鞘翅关掉滑翔位);落地则关位。private 方法按字节码名匹配可注入,require=0 兜底。
- **[待实测]**:起滑入口(玩家空中按跳跃进入滑翔)在 PlayerEntity/ServerPlayNetworkHandler,可能仍拦非 Items.ELYTRA;本里程碑先接管维持逻辑,若实测起滑都进不去,再补起滑 mixin。

## 里程碑 108 — 修 m107 编译错误(protected 方法用 @Shadow)
m107 build 失败:getFlag/setFlag 是 Entity 的 protected 方法,通过 `(LivingEntity)this` 外部引用调用报"protected 访问控制"。
- 修:mixin 类加 `@Shadow` 声明 getFlag/setFlag/emitGameEvent,以 `this.xxx()` 调用(mixin 标准做法,把父类 protected 方法当目标类自己的)。
- 逻辑不变(tickFallFlying HEAD 接管维持滑翔位)。
- 静态自检 8/8·44/44。
- **[待编译验证]**:@Shadow 签名需与 1.21.1 目标完全一致(getFlag(int):boolean / setFlag(int,boolean):void / emitGameEvent(GameEvent):void,均据源码);emitGameEvent 若有重载冲突或修饰符不符,build 会报,届时调整。

## 里程碑 109 — 修 @Shadow 目标类 + GameEvent 类型
m108 build 失败两点:
1. @Shadow 找不到目标——getFlag/setFlag 定义在 Entity(LivingEntity 父类),@Shadow 默认只在直接目标类找;需 mixin 类 `extends Entity` 才能解析继承来的方法。
2. emitGameEvent(GameEvent.ELYTRA_GLIDE) 报类型错——ELYTRA_GLIDE 是 RegistryEntry<GameEvent> 而非 GameEvent。
- 修:
  - mixin 类 `extends Entity` + 加构造转发 `super(EntityType,World)`(mixin 继承具体父类的标准写法,构造永不被实际调用,仅满足编译);@Shadow getFlag/setFlag 改 public(Entity 中实际可见性,abstract 声明)。
  - 去掉 emitGameEvent 调用(仅触发滑翔音效,非必需),省去 RegistryEntry 类型麻烦。
- 静态自检 8/8·43/43,代码体仅用已 shadow 的 getFlag/setFlag。
- **[待编译验证]**:@Shadow 方法可见性需与 Entity 实际一致(getFlag/setFlag 在 Entity 是 protected,abstract 声明用 public 应兼容或需调;若报错改 protected);Entity 构造签名 (EntityType<?>,World) 据 1.21.1。

## 里程碑 110 — 修 m109 启动崩溃(@Shadow 找不到父类方法 → 改用 @Invoker)
m109 build 成功但**启动崩溃**:
`InvalidMixinException: @Shadow method method_5795(I)Z ... was not located in target class class_1309`
(method_5795=getFlag,class_1309=LivingEntity)。
**根因**:getFlag/setFlag 定义在 Entity(class_1297),不在 LivingEntity。@Shadow 运行时严格在 @Mixin 指定的目标类里查找、**不沿继承链**(即使 mixin 类 extends Entity 也不行)——m109 那两个编译警告"Cannot find target"就是预警,误判为无害。
**修**:
- 新建 `EntityFlagInvoker`(@Mixin(Entity.class) interface + @Invoker)暴露 getFlag/setFlag——accessor 的目标类必须是方法真实所在的 Entity。
- AccessoryGliderMixin 去掉 @Shadow/extends Entity,把 this 转成 EntityFlagInvoker 调 yongye$getFlag/setFlag。
- 注册 EntityFlagInvoker 到 yongye.mixins.json。
- 静态自检配平。
- **教训**:@Shadow 目标方法必须真实存在于 @Mixin 目标类本身,父类方法须用指向父类的独立 accessor。编译期"Cannot find target"警告即运行时崩溃前兆,不可忽略。

## 里程碑 111 — 滑翔 mixin 性能优化 + 坦克镇魂换真武器(查证素材包构成)
用户提供购买页面(builtbybit 51439),**澄清恶意检察官包真实构成**:不是5把武器,而是「1武器+1肩饰+3背饰(1/2/3级,1级可左手持)」。对照尺寸:
- sword_1(X5×Y15细长,刀)=**武器**
- sword_5(X12×Y6扁横)=肩部装饰
- sword_2/3/4(Y21,递增,sword_4最大3贴图)=背饰1/2/3级
- 即:之前 m105 给坦克用的 sword_2 其实是背饰(用错);m100 永夜之翼用 sword_4(背饰3级,用对了)。

**改动1:滑翔 mixin 性能优化(查卡顿)**——tickFallFlying 是所有 LivingEntity 每 tick 热点。原实现对每个实体每 tick 可能读饰品栏 NBT。改为层层廉价早退:非玩家→return、客户端→return、未在滑翔位→return,把昂贵的 AccessoryStorage.stacks(NBT 反序列化)推到"服务端+玩家+滑翔中"才执行。走路/站立/怪物不再碰 NBT。
  注:卡顿更可能来自 boss 战(日志佩恩血量109万/血月天象)+大量重型 mod;本次仅消除永夜侧明确隐患。

**改动2:坦克镇魂换真武器**——从 sword_2(实为背饰)改为 sword_1(真正的刀,286elem),暗紫统一。

- 静态自检配平。无新接口。
- **背饰渲染层(FeatureRenderer)** 待后续大工程立项(让 sword_2/3/4 真正显示在背上分级)。

## 里程碑 112 — HUD 加等级 + 食物横条 + 布局下移
应需求:食物做成横条、血条下移、等级整合进血条区。
- **等级行**:血条正上方显示「Lv.X 职业中文名」(本命职业,金色)。新增 yongye$classCnName + yongye$classLevel(按 levels 数组顺序 tank/warrior/warlock/swordsman/monk/assassin 取本命职业等级)。
- **食物横条**:MP 条下方新增棕黄横条(FOOD_H=3),满格=20,右侧保留图标+数字。取消原"血条右侧食物图标"。
- **布局下移**:锚点 top 从 -50 改 -44(整块下移贴近物品栏);底衬扩展包住 等级行+血条+MP条+食物条。
- 结构(自上而下):等级行 → 血条 → MP条 → 食物条。
- 静态自检 27/27·134/134。沿用已验证的 fill/drawText API,无新接口。

## 里程碑 113 — 修血条不更新 bug(注入点 renderHealthBar→renderStatusBars)
用户报:没食物一直掉血(饥饿伤害),但 HUD 血条不掉。
**根因**:m94 起血条注入 `renderHealthBar` HEAD——该方法被原版 renderStatusBars 调用,但原版有 lastHealth 缓存+心数变化判定,高血量/特定状态下不每帧调 renderHealthBar,导致我的条不重画(画的内容用 player.getHealth() 是对的,但不刷新)。
**修**:注入点改为 `renderStatusBars`(InGameHud 每帧必调) HEAD:高血量(>THRESHOLD)时画自己的 等级+血+MP+食物条并 cancel 整个原版状态栏;低血量 return 交回原版。这样每帧按实时 getHealth() 重画,血量任何变化立即反映。
- 副作用:高血量时原版状态栏整体被接管(氧气泡等也不画,但本 mod 高血量 RPG 模式本就自绘 HUD);renderArmor/renderFood 旧注入在高血量时不再被调(冗余无害),低血量时原版正常。
- require=0 兜底(renderStatusBars 名若不符则不接管,不崩)。
- 静态自检 27/27 配平。
- **[待编译验证]**:renderStatusBars 方法名/签名(DrawContext 单参,1.21.1 InGameHud);若报找不到,贴 InGameHud 实际名。

## 里程碑 114 — 检查肉盾回血(无bug)+ 反苟机制(泡水/虚空/龟缩)
**肉盾回血检查结论**:坦克无"回血"功能,只有"回护盾(吸收)"——每20tick加吸收效果,逻辑正常无bug。发现并清理 m104 取消磐盾后遗留的死代码(副手磐盾+1级判断,永远false)。

**反苟机制(新 AntiCheeseHandler)**——破解三种龟缩流,每20tick检测:
- ① **泡水苟**:玩家泡水累计超 antiCheeseWaterSeconds(默认8s)→ 召2只守护者(Guardian)追杀+持续扣血;每10s补一波。
- ② **虚空/搭方块苟**:站在孤立平台(脚下有支撑但周围8格≥6格下方悬空4格)超 antiCheeseAirborneSeconds(默认10s)→ 召3只幻翼(Phantom)空袭+扣血。
- ③ **龟缩通用扣血**:进入苟态超宽限期(antiCheeseGraceSeconds默认6s)→ 持续扣血=固定点(4/s)+最大生命比例(2%/s,应对高血量苟),setHealth直接削(真伤逼出),下限留1不致死。
- 配置:enableAntiCheese 总开关 + 各阈值/扣血量可调。创造/旁观豁免。
- **[待编译验证]**:GuardianEntity/PhantomEntity 构造(EntityType,World);isSubmergedInWater();refreshPositionAndAngles(x,y,z,yaw,pitch);setTarget(ServerPlayerEntity)。均常见 API 但仓库无精确先例,若 build 报错贴出。

## 里程碑 115 — 热修服务端崩溃(武僧属性修饰符重复应用)
崩溃:`IllegalArgumentException: Modifier is already applied on this attribute!` @ ClassManager.applyClasses:175(武僧生命加成 hpInst.addTemporaryModifier(MONK_HP_ID))。
- 根因:m103 武僧拳击/生命两段直接 addTemporaryModifier,虽函数开头已 removeModifier,但某些时序下(同 tick 重入/实例状态)仍可能撞到已存在的同 ID 修饰符 → addTemporaryModifier 抛异常 → 服务端 tick 循环崩溃。
- 修:武僧两段加前先 `if (getModifier(id) != null) removeModifier(id)` 双保险(对齐 ArtifactManager.applyAttribute 的安全模式)。
- 注:本崩溃与 m114 反苟无关(幻翼/守护者召唤正常,用户反馈"幻翼来了");是 m103 武僧系统的潜伏 bug 被触发。
- 静态自检 33/33·210/210。

## 里程碑 116 — 反苟强化:破顶盖 + 召末影人(应对头顶封方块)
用户反馈:幻翼会来,但玩家头顶放方块就挡住俯冲。需求:① 召会破方块的怪 ② 直接破头顶方块。两个都做。
- **封顶检测 yongye$hasRoof**:玩家头顶(up2 起)向上4格内有固体方块(水不算)=有顶盖。仅在玩家已处于泡水/悬空苟态时才判,避免正常房顶误触发。
- **破顶 yongye$breakRoof**:破玩家头顶 3×3×height(默认4)柱状方块,跳过不可破坏(硬度<0如基岩)/空气,保留掉落物。让幻翼空袭俯冲进来。
- **召末影人 yongye$summonEnderman**:2 只末影人(原版自带搬方块 AI,会拆周围结构),每10s一波。
- 配置:antiCheeseBreakRoof / antiCheeseRoofBreakHeight / antiCheeseSummonEnderman 可调。
- 静态自检 33/33·195/195。
- **[待编译验证]**:EndermanEntity 构造;BlockState.getHardness(world,pos);getFluidState().isEmpty();ServerWorld.breakBlock(pos,boolean) 两参重载(PursuitHandler 用的是三参版)。

## 里程碑 117 — 崩溃复查(全安全)+ 怪多自动削减粒子(减卡顿)
**崩溃复查**:全仓库 9 处 addTemporaryModifier 逐一核查——除 m115 已修的武僧两处,其余(主循环/ArmorHealth/PlayerSkill/HighHpCounter/Talent/SkillEffect)本就先 removeModifier 保护。崩溃隐患已全清,无遗漏同类。待用户 build m115+ 实测确认。

**怪多自动削减粒子(新 ParticleReducerMixin,纯客户端)**:
- 注入 ParticleManager#addParticle(ParticleEffect,6×double) HEAD。
- 每次生成粒子前查客户端世界实体数(每500ms缓存,不每粒子遍历):
  ≤120 不削减;120~400 线性增加丢弃率;≥400 丢弃90%。命中则 setReturnValue(null) 不生成。
- 平滑降压(非全关,保留观感);实体少时零影响。
- require=0 兜底(addParticle 重载签名版本敏感)。
- 静态自检 5/5·27/27;注册 client.ParticleReducerMixin。
- **[待编译验证]**:ParticleManager#addParticle 重载描述符;mc.world.getEntities() 返回可迭代。

## 里程碑 118 — 术士蓄力伤害改为攻击力的倍数(蓄力越久越高)
应需求:术士蓄力魔法弹伤害应是攻击力的几倍,蓄力越久越高。
- 原:伤害 = warlockBoltDamage(固定18) × (0.4→1.0),完全没用攻击力。
- 改:伤害 = max(玩家攻击力, 保底基础值) × 蓄力倍率;倍率 warlockBoltMinMult(0.5)→warlockBoltMaxMult(4.0)随蓄力线性提升。即满蓄力≈攻击力×4倍(持专属武器再×1.2)。
- 新配置 warlockBoltMinMult/MaxMult 可调;warlockBoltDamage 降级为保底(防裸装攻击力过低)。
- 耗血仍按 0.4→1.0(不随倍率暴涨)。命中提示加显示蓄力倍率"×N.N"。
- 静态自检 34/34·206/206。无新接口(复用 getAttributeValue)。

## 里程碑 119 — 定时清理掉落物(带倒计时)+ 职业可任选替换
两个需求一起落地。
- **定时清理掉落物**(新 `ItemCleanupHandler`):服务器启动后第 `itemCleanupFirstMinutes`(默认21)分钟首次清理,之后每 `itemCleanupIntervalMinutes`(默认5)分钟一次;清理前 60/30/10/5/4/3/2/1 秒全服倒计时(60/30 聊天栏、≤10 动作栏);到点遍历所有世界 discard 全部存活 ItemEntity,广播清理数量(0个只记日志)。计时基于 server.getTicks()(重启归零)。配置 enableItemCleanup + 两个分钟字段,可 config set。
- **职业任选替换**:满 2 职业再右键新职业书,不再直接拒绝,而是 S2C `OpenClassReplacePayload` 弹 `ClassReplaceScreen`(照 ClassSelectScreen 卡图范式),展示当前两张职业卡(本命/第二),点哪张丢哪张换上新职业;ESC 取消不扣书。C2S `ClassReplacePayload` → `ClassManager.replaceClass`:校验仍满2职业/达 classLevel2/背包确有该新职业书,新职业占被丢弃者原槽位,扣1本书。`ClassBookItem.use` 加满2职业分支(界面确认才扣)。tooltip 同步。注:被丢弃职业天赋点不退还;替换本命槽不重发开局武器。
- 静态自检全配平;客户端 API + 6 张职业卡资源均 ClassSelectScreen 已验证,无新版本敏感点。
- **[待编译验证]**:ServerWorld.iterateEntities()(全实体遍历)、MinecraftServer.getWorlds()——常见 API 但仓库无先例;iterateEntities 若报错改用 getEntitiesByType(EntityType.ITEM,...)。

## 里程碑 120 — 天赋吸点 + 搜集任务掉目标物 + 后期拿不下三连优化
针对实测三问题。
- **天赋点多到没处用** → 每职业天赋树加第 6 个高上限「精通」节点(maxRank 99,前置=该职业首节点),给小幅 +攻击/生命/护甲等可无限堆,消化溢出点。`TalentScreen.nodeX` 改成按行内节点数自适应居中(原硬编码 5,现容纳 6)。applyTalents 本就通用遍历,新节点自动生效(每效果独立 modId 不冲突)。
- **前期任务物难凑(尤其粘液球)** → QuestManager 死亡事件加 GATHER 分支:持搜集任务时击杀敌对怪,按 `questGatherDropChance`(默认0.4)掉 `questGatherDropAmount`(默认1)个该任务目标物,给粘液球等无稳定来源的物资一条获取路。
- **后期地上东西太多拿不下** → ① 8 种材料 + 技能书 + 血量书堆叠上限 64→99(原版上限,少占格);② 新 `LootMagnetHandler` 战利品磁吸:每4tick把玩家附近(`lootMagnetRadius`默认8格)、命名空间=yongye 的掉落物用 setVelocity 拉向玩家自动拾取;只吸本mod贵重物,原版杂物留给 m119 定时清理(贵的自己飞来、垃圾被扫)。
- 新配置:questGatherDropChance/Amount、enableLootMagnet、lootMagnetRadius,均可 config set。
- 静态自检:改动文件全配平(TalentManager 那处单括号差为原版注释自带,m118 即如此,无影响);新增 22/22 配平。
- **[待编译验证]**:LootMagnet 用的 `Entity.velocityModified` 公有字段、`Registries.ITEM.getId().getNamespace()`——常见但仓库无先例;getEntitiesByClass/setVelocity 有先例。

## 里程碑 121 — 武器破蜘蛛网 + 一键学书 + Ward式一键强化(选武器)
三个需求,先功能后界面美化。
- **武器破不动蜘蛛网** → `ClassWeaponItem`/`ChaosBladeItem`(都 extends Item,无挖掘加成)override `getMiningSpeedMultiplier(ItemStack,BlockState)`:对 COBWEB 返回 15.0F(同原版剑),其余走 super。现在能像剑一样秒破网。
- **一键学书** → 背包加「学书」按钮 → C2S `UseAllBooksPayload` → `SkillEffectManager.useAllBooks`:扫背包,所有属性技能书+血量书按 等级×数量 一次性全学掉并清栈,提示消耗本数。
- **Ward 式一键强化(选武器)** → 「强化」按钮改为开新 `EnhanceSelectScreen`(照 WardScreen:扫背包列出所有可强化装备+当前 Lv+可加级数,分页,点哪件强化哪件)→ C2S `EnhanceSelectPayload(slot)` → `EquipmentEnhancer.enhanceFromInventory`:用背包「全部」强化材料(各 数量×单值 之和)给该件加级并扣光材料,服务端权威。新增 `totalMaterialLevels(inv)` 工具。旧的槽位式 EnhanceScreen/Handler/OpenEnhancePayload 保留但不再由按钮触发。
- 静态自检全配平;调用链闭合;EnhanceSelectScreen 全用 WardScreen 已验证的 API(drawItem/fill/drawTextWithShadow/ButtonWidget/getInventory)。
- **[待编译验证]**:`Item.getMiningSpeedMultiplier(ItemStack,BlockState)` 1.21.1 方法签名(override)。其余为项目内既有写法。

## 里程碑 122 — 开局两本书(剧情《永夜·缘起》+ 玩法《幸存者手册》)
应需求:给新出生的玩家发两本成书 —— 一本讲剧情背景,一本讲怎么玩。
- **新增 `WelcomeBookHandler`**:照 `StartingKitHandler` 范式 —— `ServerPlayConnectionEvents.JOIN` + 持久附件 `GOT_WELCOME_BOOKS`(死亡保留,防重复塞包),每人首次进入发两本 `written_book`。开关配置 `giveWelcomeBooks`(默认 true,可 config set)。
- **书内容**:
  - ①《永夜·缘起》13 页:太阳不再升起 / 脉动黑暗 / 世界换主人 / 永夜加深与赎夜 / 长门·佩恩 / 黑暗恨强者的三条规矩(禁疗·缴械·反苟) / 六大本命 / 两种余烬(生命系·永夜系) / 你的处境。取自仓库内剧情设定,与实装机制一一对应。
  - ②《幸存者手册》13 页:变厚(碎片→结晶/兑换/强化) / 选本命 + 术士蓄力放法术(非近战) / 武僧吃材料 / 第二本命与替换 / 技能书 + 一键学书 / 强化 + 守护书右键开界面 / 神器 + 永夜之翼(鞘翅槽/饰品栏鞘翅格) / 反苟两页(水→守护者·塔→幻翼·封顶→末影人拆墙+持续扣血) / 战利品必爆 + 磁吸 + 定时清理 / 天赋精通 / 撑多久。均按当前实装写,玩家照着就能上手。
- **成书构造(1.21.1 数据组件)**:`new ItemStack(Items.WRITTEN_BOOK)` + `WRITTEN_BOOK_CONTENT` 组件;`WrittenBookContentComponent(RawFilteredPair<String> 书名, String 作者, int generation=0, List<RawFilteredPair<Text>> 页, boolean resolved=true)`;`RawFilteredPair` 用规范构造器 `new RawFilteredPair<>(raw, Optional.empty())`(无 of() 静态方法)。每页 = 暗红粗体标题 + 空行 + 默认黑正文(父空样式、两子各自带样式,互不串色)。
- 静态自检:WelcomeBookHandler 花括号 9/9、圆括号 105/105、26 个 page() = 两本各 13 页;`GOT_WELCOME_BOOKS`/`giveWelcomeBooks` 定义↔引用一一对上;主类已注册。
- **[待编译验证]**(web 查过 yarn 1.21.1 已确认 record 组件名与 RawFilteredPair 规范构造器,但仓库无成书先例):`WrittenBookContentComponent` 五参构造器**参数顺序**(应为 书名/作者/generation/页/resolved);若 build 报参数不符,核对顺序即可。`DataComponentTypes.WRITTEN_BOOK_CONTENT`、`Items.WRITTEN_BOOK` 为原版稳定符号。其余(Text/MutableText/Formatting/giveItemStack/附件 API)均项目内既有写法。

## 里程碑 123 — 主页全景图 + 清掉并入的怪物皮肤/音效(留长门·HIM)
应需求:① 用户做了主页全景图,接进去当标题屏背景;② 去除 m63/m64 并入的怪物皮肤+音效,只留长门(佩恩)和 HIM(他们的资源本就在 yongye 命名空间、独立)。
- **全景图**:gui.zip 内含 6 面立方体全景 panorama_0~5(1024×1025),裁成正方形 1024² 放进 `assets/minecraft/textures/gui/title/background/`(Java 版标题屏全景标准路径,已 web 核实 minecraft.wiki:0-3 横向、4 顶、5 底)。原版标题屏本就渲染这套全景,纯资源覆盖、无 Java 改动即生效。画面是暗蓝末日场景(降临者+暗影军团+闪电),平均亮度约 40/255。
- **去掉全屏压暗**:m80 的 `TitleScreenMixin` 在 render TAIL 叠了 53% 全屏黑(`0x88000000`)——那是没自定义全景时做的;用户全景本身够暗,再叠会压成死黑。删掉那行全屏 fill(连带去掉不再用的 `h` 变量);保留顶部不透明横幅(盖原版 MINECRAFT logo)+「永夜」血红大字+副标题。现在全景完整显示在标题下方。
- **清资源**:删 `assets/minecraft/textures/entity/`(217 个并入的原版怪物替换皮肤)+ `assets/minecraft/sounds/`(784 个并入音效)。长门/HIM 不受影响——皮肤由 `client/EliteSkinFeatureRenderer` 按自定义名叠加 `yongye:textures/entity/pain_boss.png`·`him.png`,音效注册在 `assets/yongye/sounds.json`(pain_bgm/almighty_push/universal_pull/planetary + him_jumpscare),全在 yongye 命名空间,独立于被删的 minecraft 包。精英皮肤(elite_* 同在 yongye)一并保留。
- **保留(非"怪物皮肤/音效")**:`assets/minecraft/textures/environment/`(m78 红月 moon_phases.png + 绿雨 rain.png)、`assets/minecraft/texts/splashes.txt`(m79 splash)。如果这些也想去掉,说一声。
- jar 体积大幅减小(少 ~1001 个资源)。无新增/删除 Java 文件(仅改 TitleScreenMixin);无编译风险(全景为标准路径资源,删的是路径覆盖资源、无代码引用)。

## 里程碑 124 — 热修:破蜘蛛网方法名(m121 build 报错)
m121 给 `ClassWeaponItem`/`ChaosBladeItem` override 的 `getMiningSpeedMultiplier(ItemStack,BlockState)` 在 1.21.1 不存在(build 报"方法不会覆盖超类型的方法"+"找不到符号")——根因:1.21.x 把 1.20 的 `Item.getMiningSpeedMultiplier` **重命名为 `getMiningSpeed(ItemStack,BlockState)`**(挖掘速度默认读 tool 数据组件,该方法仍是 public 可覆盖的扩展点;web 核实 yarn 1.21/1.21.2 Item 均为此名)。
- 修法:两文件的方法名 + super 调用 `getMiningSpeedMultiplier`→`getMiningSpeed`,逻辑不动(对 COBWEB 返 15.0F,其余 super)。最小改动、复用 1.21.x 正确扩展点,不引入 ToolComponent/RegistryEntryList 等新接口。
- 静态自检:ChaosBladeItem 5/5·31/31、ClassWeaponItem 35/35·212/212 配平;全仓库无 getMiningSpeedMultiplier 代码残留(仅注释里提及旧名作说明)。
- 这是 m121 那条"待编译验证 getMiningSpeedMultiplier 签名"的最终落地:已确认正确方法名为 getMiningSpeed。

## 里程碑 125 — 去掉主菜单顶部黑红横幅(透明贴图隐藏原版 logo)
应需求:m123 全景图上线后,顶部那条黑红横幅(m80 加的:不透明黑条 + 血红下边线 + 渐变)挡住了全景顶部,用户要求去掉。
- 横幅原本的作用是**盖住原版 MINECRAFT logo**(直接删条会让原版 logo 冒出来跟「永夜」大字重叠穿帮)。
- 解法(纯资源、零编译风险,不动渲染代码):用**全透明贴图**覆盖原版 logo 与 Java Edition 副标——`assets/minecraft/textures/gui/title/minecraft.png`(512² 透明)+ `edition.png`(256² 透明),原版 logo 直接不可见,横幅随之不再需要。
- `TitleScreenMixin`:删掉第 2 段(bannerH + 5 个 ctx.fill 横幅/渐变/红线),保留「永夜」血红大字 + 英文副标(直接浮在全景图上);更新类 Javadoc 记录 m79/m80→m123→m125 演进。
- 结果:标题屏 = 完整全景图(顶部天空/闪电不再被挡)+「永夜」大字 + 副标 + 按钮,无任何黑红条。
- 静态自检:mixin 花 2/2·圆 38/38 配平,代码体无 bannerH/ctx.fill 残留(仅注释提及)。无新接口、无版本敏感点(logo 贴图路径 textures/gui/title/minecraft.png·edition.png 已 web 核实)。

## 里程碑 126 — 删除 MiningSpeedMixin + 修「被守卫者杀死后无法重生」崩坏
两件事一起处理。
- **① 删除挖矿/砍树减速(应需求)**:`MiningSpeedMixin`(注入 `PlayerEntity#getBlockBreakingSpeed` 对原木/石头/煤铁矿乘 0.3)**整段移除**——删 `mixin/MiningSpeedMixin.java` + 从 `yongye.mixins.json` 的 `mixins` 列表移除条目 + 删掉 `YongyeConfig` 三个只服务于它的死字段(`hcMiningSlowdown`/`hcMiningSpeedMultiplier`/`hcMiningSlowAll`,确认全仓库仅该 mixin 引用)。此后挖掘**恒为原版速度**,不再有任何减速开关。**根因补记**:此前该功能虽代码默认已关(`hcMiningSlowdown=false`),但配置走 GSON 整对象反序列化,旧 `yongye.json`(早期默认 true 时生成)里的 `true` 会盖过代码新默认值,导致玩家挖矿仍慢——彻底删除后免疫该持久化坑。顺手修正 `HardcoreSurvivalHandler`/`TankDefenseMixin` 两处提及已删文件的过时注释。
- **② 修无法重生(关键崩坏)**:`AntiCheeseHandler` 每秒遍历玩家时**只跳过创造/旁观,漏判已死亡的尸体**。玩家被守护者(反苟·泡水召的)打死后,尸体仍在水里、仍在玩家列表,下一秒被判定「泡水超阈值」→ `yongye$drain` 执行 `setHealth(Math.max(1.0f, 0-dmg))` = **对尸体 setHealth(1.0)**,把「死亡↔重生」状态机搅乱(服务端以为活着、客户端卡死亡界面)→ 点重生无反应;且每 10s 在尸体处反复召守护者。**修法**:循环内 `if (!p.isAlive()) { 清空该玩家 waterSec/airSec/lastGuardian/lastPhantom/lastEnderman; continue; }`——尸体期间绝不处理(不再 setHealth/召怪),并清空其反苟状态,使重生后(哪怕落在水里)重新走完整宽限期,杜绝「重生即被旧累计秒数瞬间二次触发」的死循环。
- 静态自检:AntiCheeseHandler 花 34/34 配平;`MiningSpeedMixin` 与三个死字段全仓库无代码残留(仅 YongyeConfig 一条说明性注释);`isAlive()` 为 LivingEntity 既有 API。本轮无新接口、无版本敏感点。

## 里程碑 127 — 动态对位(怪血随玩家攻击拔高)+ 动态爆率 + 1K血条 + 配置陈旧检查 + 灾厄祭坛 + 死亡升永夜
应需求一次性落地六组改动,核心目标:**让后期「打得有来有回」**——玩家变强,怪物同步变强、掉率同步收敛,而不是一刀秒怪/怪物挠痒痒/滚雪球失控。
- **① 属性上限 100 万 → 10 亿**(`Yongye.raiseAttributeCaps`)。用户反馈后期玩家攻击轻松破百万,而怪血卡在百万上限不够肉。先把 max_health/attack_damage/armor/toughness 的硬上限抬到 `1_000_000_000`,给下面的动态怪血留足头部空间。**注**:血量内部是 `float`,精确整数到约 1677 万,再高会按精度步进变粗——但配合动态缩放(怪血≈玩家攻击×期望击杀次数),每击伤害始终是怪血的固定分数,远大于精度步长,实战无感。
- **② 动态对位缩放(新 `DynamicScaling`)**:怪物生成时按「附近最强玩家」的攻击/最大生命等比拔高,**只增不减**。血量目标 = 玩家每击基础攻击 × `dynamicMobTargetHits`(普通 8、BOSS版 45);伤害目标 = 玩家最大生命 ÷ `dynamicMobSurviveHits`(普通 30、BOSS版 12)。用 `ADD_MULTIPLIED_TOTAL` 叠在「基础×精英/永夜倍率」之上,保证在所有既有倍率之后仍能补到对位线。注入 `MobEnhancementHandler`(普通怪,永夜倍率之后)与 `MobBossHandler`(BOSS化之后)。**这直接修了「攻击高、怪血不够」的根因**。
- **③ 普通怪 BOSS 版增强**:根因查明——MobBoss 在 `makeMobBoss` 里打了 `IS_BOSS`,因此**跳过了 MobEnhancementHandler 的全部缩放**,只剩自身 ×12血/×4攻,对高攻玩家就是 240 血的纸老虎。双管齐下:(a) 给 MobBoss 也接上 ② 的动态对位(更高的 45 次击杀目标);(b) 基础倍率上调 血12→25/攻4→6/速1.25→1.3/击退抗0.9→1.0/体型1.6→1.8(早期玩家攻击低、动态缩放还没发力时靠基础倍率撑场)。
- **④ 动态爆率(新 `PlayerPower`,反滚雪球)**:玩家越强、掉率越低,减缓成长速度好让怪物追得上。强度分 = 全部技能书已学等级之和(8 属性书 + 血量书) + 当前装备(主手/副手/四甲)强化等级之和 × `dynamicLootEnhanceWeight`(2)。倍率 `m = max(dynamicLootFloor 0.15, 1/(1+强度/dynamicLootK 150))`——强度=150 时掉率减半,=450 剩 1/4,再强保底 15%。注入 `LootHandler`:乘进全部 9 处概率掉落(技能书/职业书武器/碎片结晶核心/血核裂界精华)+ 普通池掉落按 m 门控 + 精英「必爆」碎片/书数量按 m 缩减(`dynamicLootScaleGuaranteed` 可关)。**坦诚边界**:动态爆率是「减缓变强速度」,不会让已满级的号瞬间变弱;有来有回靠的是玩家成长线 vs 怪物成长线(② 那条)对齐,这条压玩家这边。
- **⑤ 血条 1K/1M 显示**:`HudCompactMixin.yongye$num` 一处改造——<1000 原样、≥1000 显示 K(1234→1.2K、整千不带小数)、≥100万 显示 M。所有血量数字(当前/最大/吸收)自动套用。
- **⑥ 配置陈旧检查**:`YongyeConfig` 加 `configVersion`(当前=2)。`load()` 现在解析 JSON 键、用反射对比当前字段,日志警告①死键(文件有代码删)②缺失键(代码有文件无)③版本不符。新增 `/yongye config check` 命令进游戏直接看诊断报告(版本/字段总数/死键/缺失键)。**根因**:配置走 GSON 整对象反序列化,文件里的旧值会盖过代码新默认值(就是 m126 挖矿减速那个坑)——「旧值盖新默认」无法自动区分「故意调的 vs 过时的」,只能靠 `config check` 看见 + 手动 `config set` 或 `config reset`;这次给的是「看得见 + 一键诊断」。
- **⑦ 灾厄核心祭坛结构**:`spawnCore` 重构,新 `buildAltar` 建 5×5 磨制黑石砖底座 + 四角哭泣黑曜石立柱(顶灵魂灯)+ 中央基座,核心置于基座顶(`base.up(2)`,登记实际核心位置)。`coreAltarStructure=false` 退回旧的光秃秃单方块。旧存档的核心仍按旧位置追踪,向后兼容。
- **⑧ 死亡触发祭坛凝聚 → 永夜+1**:`CatastropheCoreManager` 加玩家死亡监听——世界中已有祭坛时,玩家一死就「激发最近祭坛凝聚完毕」:先摘登记(绕过 `onDestroyed` 的赎夜逻辑,避免与升级冲突)→ 核心块换成哭泣黑曜石残骸 + 粒子 → 全服播报 → `NightfallManager.escalate`(+1层)。与「摧毁核心赎夜」形成张力:抢在有人倒下前砸了它就降一层,有人先死就升一层 + 祭坛被消耗。开关 `coreDeathRaisesNightfall`。
- 新增配置 14 项(动态怪缩放 6 + 动态爆率 5 + configVersion + 祭坛/死亡升永夜 2);MobBoss 基础倍率上调 5 项。新增 2 文件(DynamicScaling、PlayerPower),120 个 Java 文件。
- 静态自检:10 个改动文件花括号/圆括号全配平;全部新 `cfg.*` 引用 ↔ 定义核对一致;关键 API 走仓库既有用法(`getClosestPlayer(Entity,double)`@MobEnhancement:112、`getAttributeValue`/`getMaxHealth`、`getEquippedStack(EquipmentSlot)`、`EntityAttributeModifier` 三参 + `ADD_MULTIPLIED_TOTAL`、`NightfallManager.escalate`、`ServerLivingEntityEvents.AFTER_DEATH`、`setBlockState` 二参)。
- **待编译验证**:(1) 原版方块字段 `Blocks.POLISHED_BLACKSTONE_BRICKS`/`CRYING_OBSIDIAN`/`SOUL_LANTERN`(标准块,但本仓库他处未用过);(2) Gson `JsonParser.parseString` + `JsonObject.keySet()`(标准 API,新引入本仓库)。两者均低风险。

## 里程碑 128 — 无尽永夜 + 久留自动升层
- 需求:永夜深渊要无尽(可一直涨);长时间处于永夜还会自动提升层数。
- **无尽**:`YongyeConfig` 加 `nightfallEndless`(默认 true)。`NightfallManager` 新增 `effectiveCap()`——无尽时返回 `Integer.MAX_VALUE`(等级实质无上限),关闭则取 `nightfallMaxLevel`(默认 99,给想要有限上限的人保留)。`setLevel` / `load` 的钳制全改用 `effectiveCap()`,所以等级可一直往上叠(深渊 N 层无尽)。`getLevelName()` 对 >5 本就输出「永夜 · 深渊 N 层」,无需改。机制层面 `MobEnhancementHandler` 对 >5 的层用 `(nf-5)×nightfallBeyondHpPerLevel` 线性叠血/攻、`progressionMultiplier` 线性加,精英概率/锁定半径数组高层取末位值——升得越高世界越难,无尽有实际意义。
- **久留升层**:加 `nightfallTimeEscalate`(默认 true)+ `nightfallTimeEscalateMinutes`(默认 30)。`NightfallManager` 加运行时计数 `secondsInNightfall`,在已有的每秒 tick(等级≥1 才走)里累计,满 N 分钟 `escalate(+1)` 并清零;离开永夜(level<1)立即归零(避免赎夜后马上再触发)。受 `effectiveCap()` 钳制:无尽时一直升,非无尽到顶即停。计数不持久化(重启重置,最多丢 N 分钟进度,可接受)。
- **避坑**:三个新字段对旧 `yongye.json` 是「缺失键」,GSON 反序列化保留代码初值(true/true/30)——天然绕开「旧值盖新默认」的坑(不像改 `nightfallMaxLevel` 默认值会被旧配置顶掉)。`configVersion` 2→3。
- 静态自检:NightfallManager 30/30 花括号、102/102 圆括号;YongyeConfig 33/33;`effectiveCap`/`secondsInNightfall`/三新字段定义↔引用一致;NightfallManager 内 `nightfallMaxLevel` 仅剩 `effectiveCap` 内部引用(无遗留直接钳)。本轮只用 `Integer.MAX_VALUE` + 现成 `escalate`/`YongyeConfig.get()`,无新接口、无版本敏感点。

## 里程碑 129 — debug 菜单仅限管理员 ID(qiaodaxian)
- 需求:`/yongye debug` 只识别我的 ID `qiaodaxian` 才可以打开。
- `ModCommands` 加常量 `DEBUG_OWNER = "qiaodaxian"`;debug 命令体里取 `p.getGameProfile().getName()`(与本文件 227 行同款用法),`equalsIgnoreCase(DEBUG_OWNER)` 不符则发红字「调试菜单仅限管理员 qiaodaxian 使用」并 `return 0`,不发开屏包。大小写不敏感(MC 用户名全局唯一不区分大小写,既安全又稳),改常量即可换人。
- **范围说明**:门控的是「打开 debug 菜单」这一步。菜单按钮回发的那些 `/yongye xxx` 子命令仍只受 `requires(hasPermissionLevel(2))` 约束——即便不是 qiaodaxian 的 OP,若手敲原始子命令仍可执行(需知道命令名)。若要把全部子命令也锁到该 ID,下一轮可把 ID 校验提到命令树根的 `requires` 上。
- 静态自检:ModCommands 77/77 花括号、659/659 圆括号;`DEBUG_OWNER` 定义↔2 处引用一致;`getGameProfile().getName()`/`sendFeedback`/`Text.literal().formatted` 全是本文件既有写法,无新接口、无版本敏感点。

## 里程碑 130 — 开局难度选择 + 职业选择书(取代强制选职弹窗)+ 武器后期吸血
- 需求:开局不要先弹选职;改为先弹「难度选择」(含游戏介绍),职业改用「职业选择书」之后自选;难度 7 档(游玩/简单/适中/困难/地狱/深渊/永夜);武器强化到 1000+ 出现「0.几」吸血。
- **难度系统**:新 `GameDifficulty` 枚举 7 档(ordinal 即等级),每档带怪物强度倍率(游玩0.5→永夜6.0)+ 简介 + 配色。倍率作用在 `DynamicScaling` 的「对位目标血量/伤害 × diffMult」上;因为对位只增不减,低难度≈接近原版(只是少拔高)、高难度把怪往死里堆。难度按「最近玩家」读取(`GameDifficulty.mobMultOf`,读 `ModAttachments.DIFFICULTY`,未选=-1按适中1.0),与现有「按最近玩家攻击/血量缩放」同一套逻辑,不引入世界级存档。
- **开局流程重做**:`YongyeNet` 的 JOIN 处理器不再自动发 `OpenClassSelectPayload`,改为①未选难度则发 `OpenDifficultyPayload`(客户端 `DifficultyScreen`,强制选择、屏蔽ESC、顶部含剧情/玩法简介);②首次发一本 `ClassSelectBookItem`(职业选择书,复用原版 writable_book 贴图,无新PNG)。新 S2C `OpenDifficultyPayload`+C2S `ChooseDifficultyPayload(idx)`,服务端校验未选过才写 DIFFICULTY 并播报。
- **职业选择书**:右键 → 未选过本命才发 `OpenClassSelectPayload`(复用现有全职业卡图 `ClassSelectScreen`+`ChooseClassPayload`→`chooseStartingClass`);选职成功后 `ChooseClass` 接收器扫背包消耗一本选择书。已选过则书失效提示。客户端 `pendingDifficulty` 标志位延后弹出(同 `pendingClassSelect`,避免被登录过场覆盖,难度先于职业)。
- **武器后期吸血**:`WeaponCombatHandler` 命中结算加吸血——武器强化 ≥`weaponLifestealMinLevel`(默认1000)且攻击蓄满(≥0.9,防连点刷)且未满血时,按攻击力 ×`frac` 回血,`frac=min(max 0.5, base 0.1 + 超阈级数×0.0001)`(即千级 +0.1,封顶50%)。复用 `EntityAttributes.GENERIC_ATTACK_DAMAGE` + `player.heal`,无新接口。
- 新增配置:难度/选择书 2(enableDifficultySelect/giveClassSelectBook)+ 吸血 5(enableWeaponLifesteal/weaponLifestealMinLevel/Base/PerLevel/Max);新增附件 DIFFICULTY/GOT_CLASS_BOOK;configVersion 3→4。
- 静态自检:全部改动文件花括号/圆括号配平;新配置字段定义↔引用一致;`appendTooltip` 签名与 WardBookItem 完全一致;`class_select_book.json` + `zh_cn.json` 合法。无版本敏感点(全走仓库既有 API:Screen/ButtonWidget/ClientPlayNetworking/getClosestPlayer/getAttributeValue/heal/giveItemStack)。

## 里程碑 131 — 武器技能升级(终焉精华升级三大技能)
- 需求:武器技能也可升级,通过难获取的材料升级。
- **每技能独立等级**:新附件 `WEAPON_SKILL_LV`(Map<技能枚举名,等级>,死亡保留)。升级用最稀有材料「终焉精华」(ENDING_ESSENCE),花费 `base 1 + 当前等级×1`(线性递增,越往后越贵),上限 `skillUpgradeMaxLevel`(默认20)。
- **效果**:`WeaponSkillManager.use` 里 `dmgMult = 1 + 技能等级×skillUpgradeDamagePerLevel(0.25)` 乘进三技能最终伤害(改三方法签名 +dmgMult 参数);冷却 `cd = max(skillUpgradeCdFloor 40, 基础CD − 等级×skillUpgradeCdReductionPerLevel 4)`。施放动作栏显示「Lv.N」。
- **升级入口**:`WeaponInfoScreen`(背包→装备)底部加 3 个按钮「升·混沌斩/深渊吞噬/终焉降临」(仅武器+开关开时),点击发 C2S `UpgradeWeaponSkillPayload(idx)` → 服务端 `WeaponSkillManager.upgradeSkill` 校验+扣终焉精华+写回等级+动作栏反馈。面板高 244→270 给按钮腾位、与 init() 共用 PANEL_W/PANEL_H 常量保证对齐。
- **诚实局限**:WeaponInfoScreen 是纯客户端、未同步玩家技能等级,故按钮不显示当前等级/精确花费,结果走服务端动作栏反馈(升至 Lv.N、消耗 N 终焉精华 / 材料不足提示)。要在面板直接看等级需加一条 stats 同步,留待后续。
- 新增配置 7(enableWeaponSkillUpgrade/skillUpgradeMaxLevel/BaseCost/CostPerLevel/DamagePerLevel/CdReductionPerLevel/CdFloor);新增附件 WEAPON_SKILL_LV;新增 2 文件(UpgradeWeaponSkillPayload + 上轮的 m130 新文件)。
- 静态自检:WeaponSkillManager 32/32 花括号、200/200 圆括号;三技能调用均 5 参;`upgradeSkill`/`skillLevel` 定义↔YongyeNet 引用一致;`countItem`/`consumeItem` 用 PlayerInventory 遍历(本仓库既有写法)。无版本敏感点。

## 里程碑 132 — 难度改为世界级(房主/OP 设定,全局锁定,联机不再各选)
- 需求:难度改世界级(整局一个值,存世界存档,像永夜等级);只有房主/OP 首次进入未设定的世界时弹一次,选完全世界锁定;之后任何人联机都不再弹,所有怪按世界难度统一缩放;加 `/yongye difficulty <0-6>`(OP)事后改。
- **从「逐玩家」改「世界级」**:m130 把难度做成了 per-player 附件(每人各选、按最近玩家缩放)——这不符合「房主定一个全局难度」。本轮改:
  - 新 `DifficultyManager`(照 NightfallManager):静态 level(-1未设定/0~6),持久化到存档 `yongye_difficulty.json`,SERVER_STARTED 读 / STOPPING 写;`load()` 开头先 `level=-1` 复位,避免单机切世界时静态字段把上一个世界难度残留到没有难度文件的新世界。`mobMult()` 返回世界难度倍率(未设定=适中1.0)。
  - `DynamicScaling` 的 diffMult 从 `GameDifficulty.mobMultOf(最近玩家)` 改为 `DifficultyManager.mobMult()`(全局统一,不再随谁更近变)。
  - `GameDifficulty` 去掉 per-player 的 `mobMultOf` + ModAttachments/PlayerEntity import,回归纯数据枚举。
  - 删掉 `ModAttachments.DIFFICULTY`(per-player 附件,已无引用;旧存档里的该键变成未注册数据被忽略,无害)。
- **谁能设 + 何时弹**:`YongyeNet` JOIN——`enableDifficultySelect && !DifficultyManager.isSet() && (玩家 hasPermissionLevel(2) || server.isSingleplayer())` 才弹 `OpenDifficultyPayload`。**用 `isSingleplayer()` 兜底单机房主**(单机不开作弊时玩家没有权限2,只靠 OP 判定会导致单机弹不出来)。ChooseDifficulty 接收器同样校验「OP 或单机」+ 未设定,通过则 `DifficultyManager.setLevel(server, idx)` 全服播报+写盘锁定。客户端 `DifficultyScreen`/包不变。
- **命令**:`/yongye difficulty status`(查看)+ `/yongye difficulty <0-6>`(设定),整棵 `yongye` 树本就 requires 权限2。
- **迁移说明**:m130/m131 世界里已选的 per-player 难度数据作废(孤立未注册键),世界难度初始为未设定 → 房主下次进入会被询问一次设定世界难度,符合预期。
- 静态自检:7 改动文件花括号/圆括号全配平;无 `mobMultOf`/`ModAttachments.DIFFICULTY` 残留;`DifficultyManager` 各方法定义↔引用一致(register/isSet/setLevel/getLevel/mobMult);GameDifficulty 已无 ModAttachments/PlayerEntity 依赖。
- **待编译验证**(低风险,仓库无先例):`MinecraftServer.isSingleplayer()`(标准方法,1.21.1 应存在;用于单机房主兜底)。其余全走仓库既有写法(ServerLifecycleEvents/getSavePath/WorldSavePath/broadcast/hasPermissionLevel,与 NightfallManager 同款)。

## 里程碑 133 — 武僧不发武器 + 武器携带即生效 + 重生满血(高血量)
- 需求三连:① 选职业武僧不该发武器(却还在发);② 武器只要在身上就该有加成,现在切走加成就没了;③ 所有职业重生不回满血(比如 60万血只恢复 200 多)。
- **① 武僧不发武器**:`ClassManager.chooseStartingClass` 给武器处加 `&& c != PlayerClass.MONK`。武僧是无武器职业(空手拳 + 吃材料),不再发 class_weapon_monk。`learn()`(第二职业)本就不发武器,无需改。
- **② 武器携带即生效**(根因:类武器属性用 `AttributeModifierSlot.MAINHAND` 配,只有拿主手才生效,强化属性也写在物品 ATTRIBUTE_MODIFIERS 组件同样 MAINHAND/ADD_VALUE,切走自然失效——原版行为):新 `PlayerUpkeepHandler` 每 5 tick 镜像——若玩家主手不是职业武器、但背包里带着职业武器,就读该武器的 ATTRIBUTE_MODIFIERS(含基础+强化),把 MAINHAND/HAND/ANY 槽的修饰用「派生唯一 id(carry_原命名空间_原路径)」镜像到玩家;拿在主手时由原版生效、本镜像撤销,避免双倍。每次刷新前先撤销上次镜像(CARRY_APPLIED 记录),不会叠加;只取背包第一把(玩家单本命,不刷叠加)。开关 enableWeaponCarryBonus(默认开)。
- **③ 重生满血**(根因:重生瞬间 setHealth(maxHealth) 时,职业/武僧/神器/强化/携带武器的「生命上限」加成还没全部重新应用,max 还是很低的临时值,于是只回到 200 多;之后各系统把 max 拉到 60万,但当前血量停在 200):AFTER_RESPAWN 现在先 `ClassManager.applyClasses`(刷职业/武僧生命上限)再 setHealth(max);并 `PlayerUpkeepHandler.scheduleRespawnHeal` 开 40 tick(2 秒)满血窗口,每 tick 把血顶到当前 max——随神器(10tick)/职业(20tick)/强化护甲/携带武器等生命上限陆续到位,血量跟着补满,最终回满。
- 新增配置 1(enableWeaponCarryBonus)、新增 1 文件(PlayerUpkeepHandler)、configVersion 4→5。
- 静态自检:PlayerUpkeepHandler 17/17 花括号、104/104 圆括号(注释列表序号改顿号免误判);ClassManager/Yongye/YongyeConfig 全配平;PlayerUpkeepHandler.register/scheduleRespawnHeal↔Yongye 引用一致;enableWeaponCarryBonus 定义↔引用一致;PlayerClass 已导入。
- **待编译验证**(仓库无先例,低风险):`EntityAttributeModifier` 的记录访问器 `.id()/.value()/.operation()`、`AttributeModifiersComponent.Entry` 的 `.modifier()/.slot()`(`.attribute()` 已有先例)、`AttributeModifierSlot.HAND/ANY` 取值——均为 1.21.1 标准 API,仅本仓库此前未用过。其余全走仓库既有写法(getAttributeInstance/addTemporaryModifier/removeModifier/applyClasses)。
- **未做**:主菜单玻璃蓝按钮美化(图2风格)——属客户端渲染/美术,与本轮玩法修复分开,待与作者确认方案(按钮渲染 mixin 仅标题页 vs 自定义按钮贴图全局)后再做。

## 里程碑 134 — 彻底删除武僧武器(物品+残留资源)+ 重生满血窗口加余量
- 需求:把武僧的武器合成配方和武器都删了;所有职业(不止武僧)重生回满。
- **配方**:武僧武器配方早在 m103 就已删除(recipe 目录只有 tank/warlock/warrior/swordsman/assassin 五个,无 monk),本轮确认无残留。
- **重生满血**:m133 的 AFTER_RESPAWN 本就不分职业(对所有 respawn 玩家:applyHealthModifier + applyClasses + setHealth(max) + scheduleRespawnHeal 满血窗口),已是「所有职业回满」。本轮把窗口 40→60 tick(2→3 秒)加余量,确保神器(10t重应用)/职业(20t)/强化护甲/携带武器等迟到的生命上限都能在窗口内补满——根因仍是重生瞬间生命上限未全部重应用,窗口每 tick 顶满即随 max 增长补满。
- **删武器物品**(m133 只是选职不发,物品仍注册):
  - ModItems 注册循环 `if (c==MONK) continue;` 跳过武僧,不再注册 class_weapon_monk;getClassWeapon(MONK) 返回 null。
  - 新增 `ModItems.WEAPON_CLASSES`(所有职业去掉武僧),供掉落池/创造栏统一使用,避免取到 null。
  - 守住 5 个 getClassWeapon 调用点:ModItemGroups 创造栏武器循环改用 WEAPON_CLASSES;LootHandler 精英掉落 / BossHandler Boss 掉落的随机职业池改用 WEAPON_CLASSES(武僧不再掉武器);ClassManager 选职给武器已在 m133 加 c!=MONK;ModCommands debug 给武器对武僧(getClassWeapon==null)报错而非塞 null。
  - 删资源:class_weapon_monk 的模型 json、贴图 png、zh_cn/en_us 各一条 lang(删后 JSON 仍合法)。
- **迁移说明**:武僧武器物品被注销,旧存档若有该物品会变为无效(空)——武僧武器自 m103 起几乎无法获得,影响极小。
- 静态自检:6 个改动 Java 文件花括号/圆括号全配平;WEAPON_CLASSES 定义↔引用(ModItemGroups/LootHandler/BossHandler)一致;全仓库无 class_weapon_monk 实际引用(仅剩一句说明性注释);ModCommands 守空逻辑读序正确。
- **待编译验证**:本轮无新接口/无版本敏感点,全是普通 Java(枚举过滤、判空、数组)与仓库既有写法。

## 里程碑 135 — 背包侧边按钮美化(自定义玻璃蓝主题按钮)
- 需求:背包旁边那一列按钮(原版灰)不好看,想改好看。
- 根因:这些按钮(成长/装备/饰品/天赋/强化/兑换/学书/本命职业)是在 YongyeClient 的 ScreenEvents.AFTER_INIT(InventoryScreen)里用原版 ButtonWidget 加的,所以是朴素灰按钮。
- 做法(零 mixin、不影响其它界面):新 `YongyeButton extends ButtonWidget`,只重写 renderWidget 自绘——深海军蓝半透明底 + 顶部一道玻璃高光 + 蓝青描边,悬停描边转亮青(发光感)、底色提亮、文字转纯白,禁用态变灰。配色集中为常量,想换血红主题改常量即可。把背包那 8 个按钮从 ButtonWidget.builder(...) 全部换成 new YongyeButton(...)(功能/位置/尺寸不变),并移除 YongyeClient 中已不再使用的 ButtonWidget import。
- 仅替换背包侧边这 8 个按钮;主菜单按钮玻璃化仍待定方案(A 标题页渲染 mixin / B 全局按钮贴图),与本轮无关。
- 新增 1 文件(YongyeButton),无新配置,configVersion 不变。
- 静态自检:YongyeButton 3/3 花括号、35/35 圆括号;YongyeClient 配平、8 处 new YongyeButton、ButtonWidget 已无引用(import 已删)。
- **待编译验证**(标准 1.21.1 API,仓库无先例):`ButtonWidget` 受保护构造器 `super(x,y,w,h,message,onPress,DEFAULT_NARRATION_SUPPLIER)`、重写 `renderWidget(DrawContext,int,int,float)`、继承的 `DEFAULT_NARRATION_SUPPLIER` / 嵌套 `PressAction` / `isHovered()`。绘制用的 fill / drawCenteredTextWithShadow 是仓库既有写法。

## 里程碑 136 — 玻璃蓝背包背景贴图(覆盖原版生存物品栏)
- 需求:作者做了一张玻璃蓝的背包背景图,确认路径并接入。
- 路径正确:生存物品栏(按 E)背景在 1.21.1 仍是 `assets/minecraft/textures/gui/container/inventory.png`,资源覆盖即可(与本模组已覆盖的 title/panorama/rain/moon 同套路,纯资源零代码)。
- 放入作者上传的 1254×1254 RGBA 贴图到该路径(新建 container 目录)。
- 关键校验:Minecraft 把背景图当 256 底、只取左上 176×166 区域绘制(归一化后 = 这张图左上 68.8%×64.8% ≈ 862×813 px)。已验证面板内容包围盒 x[0..860] y[0..812],严丝合缝落在采样区内,右下白边在采样区外不显示——布局正确,直接生效。
- 注意:背包左侧那列功能按钮(m135 YongyeButton)是独立控件、不在这张贴图里;槽位与物品的像素对齐需进游戏实测(若物品与玻璃格子有偏移,说明格子位置要按原版槽位微调)。
- 无 Java 改动,无配置变更。

## 里程碑 137 — 玻璃蓝快捷栏贴图(覆盖 HUD hotbar)
- 需求:作者做了玻璃蓝快捷栏图,确认路径并接入。
- 路径正确:1.21.1 的 HUD 快捷栏在精灵图系统,`assets/minecraft/textures/gui/sprites/hud/hotbar.png`(纯资源覆盖)。放入作者 2164×261 RGBA 图(新建 sprites/hud 目录)。
- 校验:原版 hotbar.png=182×22(比例 8.273:1),本图比例 8.291:1,仅差 0.2%,槽位横向对齐良好;精灵图整张即该控件、会缩放到 182×22,无需留白(与背包背景的「256底取左上」机制不同)。
- 提醒:选中格的白框是另一张精灵 hud/hotbar_selection.png(24×22),本次未替换→仍是原版白框叠在玻璃蓝栏上;要统一可另做该图。物品与玻璃格的精确对齐建议进游戏实测。
- 无 Java 改动,无配置变更。

## 里程碑 138 — 血量 HUD 改蓝 + 食物条加粗上移(配玻璃蓝 UI)
- 需求:血量 HUD 改成蓝色;食物条「没做/没上去」(其实早有,但只有 3px 又压在最底下,作者没认出来)。
- 血条改蓝(HudCompactMixin):深红底 0xFF3B0000→深蓝 0xFF06223F,鲜红填充 0xFFCC1010→亮蓝 0xFF2E86D8,高光 0x40FFFFFF→青色玻璃 0x66BFE6FF;金色吸收条保留(区分)。
- 食物条:FOOD_H 3→6(与血条同高的正经横条),并从「MP 条下方(最底)」上移到「血条正下方」(血条→食物→MP);配色改草绿 0xFF8FBF4A/深绿底,与蓝血条、武僧琥珀色 MP 条都区分得开;右侧保留食物图标+数值。MP 条顺移到食物条下方。
- 布局校验:相对 top —— 血条 0..6、食物 8..14、MP 16..20,底衬覆盖到 22,全包住;totalH 公式(和式)不受顺序影响仍正确。
- 待作者提供并接入的快捷栏配套精灵:hud/hotbar_selection.png(选中白框)、hotbar_offhand_left/right.png(副手)、hotbar_attack_indicator_*(攻击冷却)——收到图即放入 sprites/hud。
- 无新文件、无配置变更。

## 里程碑 139 — HUD 配色修正(纠正 m138 理解错误)+ 底衬对齐快捷栏
- 作者纠正:m138 理解反了。正确需求 = 黑底衬→蓝、血条仍红、食物黄、资源条(拳意等)蓝、所有职业统一这一套配色、半透明背景与底部快捷栏对齐。
- 底衬:黑 0xC0000000 → 半透明蓝 0xCC14406E;宽度从 left-2..left+BAR_W+2 改为 left..left+BAR_W,与原版 182 宽居中快捷栏左右缘精确对齐。
- 血条:撤销 m138 的蓝,改回红(深红底 0xFF3B0000 / 鲜红 0xFFCC1010 / 白高光),金吸收保留。
- 食物条:撤销 m138 的绿,改黄(深黄褐底 0xFF332600 / 黄 0xFFE6C42A),保持 m138 的加粗(6px)+ 上移到血条正下方。
- 资源条:yongye$mpColors 取消按职业分色,六职业统一蓝 {0xFF0A1E38, 0xFF2E7AD0, 0xFF7FCFFF}。
- 最终 HUD 配色:红血 / 黄食 / 蓝资源 / 蓝底衬,全职业一致。
- 无新文件、无配置变更。静态自检:HudCompactMixin 23/23 花括号、151/151 圆括号;四处配色与对齐已核对。

## 里程碑 140 — 删除永夜之翼 + 选中框/副手玻璃框贴图
- 需求:永夜之翼"完全没用",删除;hotbar_selection 与副手用作者新做的玻璃框(中心透明),攻击指示不改。
- **选中框 + 副手**:作者 1254×1204 玻璃蓝框(中心 alpha=0、框充满画布)放入 sprites/hud 的 hotbar_selection.png / hotbar_offhand_left.png / hotbar_offhand_right.png(三处同图,精灵图各自缩放到 24×22 / 29×24)。攻击指示 hotbar_attack_indicator_* 不动(保持原版)。
- **删永夜之翼(NightWingItem,ElytraItem 背饰)**:
  - ModItems 删注册 NIGHT_WING + import;ModItemGroups 删创造栏条目;ModCommands 删 /yongye nightwing;DebugScreen 删按钮;欢迎书页「神器与永夜之翼」→「神器」并去掉飞行那句。
  - AccessoryScreenHandler 第11槽 canInsert 去掉 NIGHT_WING、仅保留原版鞘翅(槽位仍在,存原版鞘翅用)。
  - 删 AccessoryGliderMixin(其唯一用途是让永夜之翼滑翔)+ 从 yongye.mixins.json 移除该 mixin 条目;原版鞘翅穿胸甲槽照常滑翔(走原版,不受影响)。
  - 删文件:NightWingItem.java、AccessoryGliderMixin.java;删资源:模型 night_wing.json、配方 night_wing.json、贴图 night_wing_1/2/3ef.png、lang zh_cn 一条(删后补尾逗号修 JSON)。
  - 迁移:旧存档若有永夜之翼物品变无效;饰品栏鞘翅格的"滑翔"功能随之取消(本就只对永夜之翼生效)。
- 静态自检:6 改动 Java 全配平;src 无 NIGHT_WING/NightWing 代码残留(仅余 AccessoryStorage/EquipmentEnhancer 两处无害注释);mixins.json/zh_cn.json 合法;三张精灵图有效。
- 无新配置;configVersion 不变。

## 里程碑 141 — 副手框贴图换正确比例（修 m140 副手框拉伸变形）
- **问题**：m140 给副手框 `hotbar_offhand_left/right.png` 用的是 1254×1204 中心透明大图（与选中框三处同图、md5 相同）。该图比例 ≈1.04（近正方），而原版 `InGameHud` 把副手框 sprite 固定绘制到约 29×24（比例 ≈1.21），整张被 stretch 拉满 → 副手框相比选中框既整体偏大又被横向拉宽变形，即作者所说“副手框太大、别扭”。`offhand`/`selection` sprite 均无自定义渲染代码，纯走原版 `InGameHud`（grep 确认）。
- **修法（纯资源，只改副手两张，选中框暂不动）**：换成作者新做的 145×120 副手专用图（比例 1.208 = 29:24，拉进副手框不变形）；并照原版偏移做 left/right 镜像——`left` 框靠左、右侧留白；`right` 框靠右、左侧留白。原版那侧留白（约 7px/29）是朝快捷栏一侧的间隔，框本体对准副手物品槽，故 left/right 不可居中（中途曾误改居中，经核对原版后已纠正）。
- 文件：`src/main/resources/assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png`、`hotbar_offhand_right.png` 由 1254×1204 → 145×120。
- 静态自检：两图均 145×120、比例 1.208；`left` 框靠左（右留白 32px）、`right` 框靠右（左留白 35px），偏移方向与原版一致；`git status` 仅这两文件变更；选中框 `hotbar_selection.png` 保持 1254×1204 不动（与副手蓝玻璃风格暂未统一，待后续定）。
- 无 Java/配置改动；configVersion 不变。
- 待作者本地 `./gradlew build` + 进游戏实测槽位/物品对齐（贴图类一律以实机为准）。

## 里程碑 142 — HUD 精致玻璃化（方案A：圆角＋描边＋渐变＋光头）
- **动机**：作者觉得 HUD「太平、底衬笨重」。根因＝所有元素都是 `ctx.fill` 直角纯色矩形（底衬一大块 `0xCC14406E`、各条「底色＋1px高光」），无圆角/渐变/描边/发光。
- **改法（纯渲染，`HudCompactMixin` 内，全部 `ctx.fill`，无贴图、无新接口）**：
  - 底衬：`0xCC14406E` 直角块 → `yongye$panel()` ＝ 2px 切角圆角 ＋ 玻璃描边 `0xFF2E7AD0` ＋ 顶亮底暗渐变 `0xCC1B5288→0xCC0C2C50` ＋ 顶部内高光。
  - 血条：纯红填充 → 渐变 `0xFFE83030→0xFF8B0000` ＋ 顶 1px 高光 ＋ 末端 2px 光头 `0xFFFF7070` ＋ 底槽顶内阴影；金色吸收层保留。
  - 食物条 / 资源条：改用 `yongye$bar()` ＝ 底槽 ＋ 顶内阴影 ＋ 渐变填充 ＋ 顶高光 ＋ 末端光头。
  - 新增辅助：`yongye$lerp`（颜色插值）/`yongye$gradV`（逐行渐变）/`yongye$panel`（玻璃底衬）/`yongye$bar`（单条）。
- 配色基准不变（红血 / 黄食 / 蓝资源），只叠加玻璃质感层次；文字仍 `drawTextWithShadow`。
- **顺带查明**：截图里快捷栏每格上方的紫色↓箭头不是本 mod —— 代码无此渲染（仅有指向灾厄核心的单个中上方箭头）、`hotbar.png` 也无（每格只左上角高光），来源应是作者另装的 mod / 资源包。
- 静态自检：花括号 29/29、圆括号 208/208 配平；`lerp/gradV/panel/bar` 定义各 1、调用齐全；变量 `totalH/hpW/foodW/fillW/totalHp` 均在。
- **待编译验证**：全部 `ctx.fill(int,int,int,int,int)` ＋ 基本算术，标准 API 仓库大量在用，无 yarn 映射敏感点，风险极低；待本地 `./gradlew build` ＋ 进游戏实测观感。
- 预览（mockup，非实机）：`docs/hud/m142_preview.png`。HUD 另有 方案B（青蓝外发光）/ 方案C（去大底衬极简）可选，待作者看 A 实机后再定。
- 无配置变更；configVersion 不变。

## 里程碑 143 — 开局口粮（首次进入发 20 个面包）
- **需求**：玩家出生（首次进入）给 20 个面包。
- **实现（套用 `StartingKitHandler`「首次发一次」范式，无新文件）**：
  - `YongyeConfig`：新增 `giveStartingFood`（默认 true）+ `startingFoodCount`（默认 20，0=不发）；`CURRENT_CONFIG_VERSION` 5→6。
  - `ModAttachments`：新增 `GOT_STARTING_FOOD`（persistent BOOL + initializer false + copyOnDeath），每人只发一次、死亡保留→重生不再发，照 `GOT_STARTING_KIT` 模板。
  - `StartingKitHandler`：`ServerPlayConnectionEvents.JOIN` 里、**背包逻辑之前**加发放段（背包在未装 Sophisticated Backpacks 时会 `return`，放后面会被一起跳过）；`startingFoodCount>0` 且未领取则发，按 64 一组拆叠 `new ItemStack(Items.BREAD, n)`，发完打 `GOT_STARTING_FOOD` 标记。
- 语义：每人**首次进入发一次**，死后重生不再补发（和开局背包/欢迎书一致）。若要改成「每次重生都发」需另走 `AFTER_RESPAWN`、不打一次性标记。
- 迁移：旧 `yongye.json` 缺这两个键 → GSON 保留代码初值（true/20），无「旧值盖新默认」问题；configVersion 自动对齐到 6。
- 静态自检：三文件括号配平；`GOT_STARTING_FOOD`/`giveStartingFood`/`startingFoodCount` 定义↔引用一致；`Items.BREAD`、`ItemStack`、`ModAttachments` 均已 import。
- **待编译验证**：`giveItemStack`、`new ItemStack(Items.BREAD,int)`、附件 `getAttachedOrElse/setAttached` 全是仓库在用的标准 API，无新接口/无 yarn 敏感点，风险极低；待本地 `./gradlew build`。
- 无新文件（改 3 个现有）；配置 +2、configVersion 5→6。

## 里程碑 144 — 关掉磁吸与材料/书堆叠（Sophisticated Backpacks 已自带）
- **需求**：背包 mod（Sophisticated Backpacks）自带磁吸和堆叠升级，本 mod m120 加的那套（`LootMagnetHandler` 磁吸 + 材料/书 64→99 堆叠）重复，关掉。
- **磁吸**：`Yongye` 主类注释掉 `LootMagnetHandler.register()` —— 直接不挂载、彻底失效。**注意**：仅把 `enableLootMagnet` 默认改 false 对老存档无效（`yongye.json` 已存 `true`，GSON 加载保留旧值盖新默认，那个老坑），所以走「停挂载」绕过；`enableLootMagnet` 默认同步改 false 并标注停用、当前无效、保留备查。
- **堆叠**：`ModItems` 去掉 10 处 `.maxCount(99)`（8 种材料 + 血量技能书 + 职业技能书），回归原版默认 64；`.maxCount(16)`（护符/守护书）、`.maxCount(1)`（职业书/选职书）不动。
- 影响：磁吸立即失效（不看配置）；老存档里已堆到 99 的材料不会丢，只是上限回 64、之后不能再堆过 64。
- 静态自检：三文件括号配平；`LootMagnetHandler.register()` 无激活行；`enableLootMagnet` 默认 false；`.maxCount(99)` 残留 0、`.maxCount(16/1)` 各 2 保留。
- **待编译验证**：仅注释一行 + 删 `.maxCount(99)` 调用，无新接口/无 yarn 敏感点，风险极低；待本地 `./gradlew build`。
- 无新文件（改 3 个现有）；configVersion 不变（改默认值/去 maxCount，未加删字段）。

## 里程碑 145 — 玩家皮肤僵尸BOSS（第一步：链路打通 + jiemoLI 打底）
- **需求**：僵尸被 MobBoss BOSS 化时做成「玩家皮肤BOSS」——名牌「<在线玩家名> BOSS」、用该玩家皮肤渲染、每个在线玩家各刷一只自己皮肤的、融入现有 MobBoss 概率自然刷。
- **分步**：动态「按玩家名取其在线官方皮肤」那个 API 仓库从没用过，单独放第二步 build 验证；**本步先把整条链路用 jiemoLI 打底跑通**（全复用现有范式，无新客户端 API，风险低）。
- **复用的现成件**：`EliteSkinFeatureRenderer`（已挂在所有生物渲染器上、按名牌叠自定义皮肤）+ `MobBossHandler`（命名 / 红色 ServerBossBar / IS_MOB_BOSS）。僵尸模型 UV 与玩家皮肤 64×64 兼容，故僵尸直接贴玩家皮肤能显示（手臂为僵尸直臂，贴图对得上）。
- **改动**：
  - 资源：`jiemo_li.png`（作者上传的打底 / fallback 皮肤）→ `assets/yongye/textures/entity/`。
  - `YongyeConfig`：新增 `enablePlayerSkinZombieBoss`（默认 true）；`CURRENT_CONFIG_VERSION` 6→7。
  - `MobBossHandler`：新增 `SKIN_BOSS_OWNER` Map（每个在线玩家同时最多一只皮肤BOSS）+ `pickSkinTarget()`（取一个当前没有活皮肤BOSS的在线玩家，取前先清死项）+ `makeMobBoss()` 僵尸分支（选到玩家 → 名牌「<名> BOSS」+ 记 Map；否则走默认「【BOSS】 怪名」）；import `ZombieEntity`/`MinecraftServer`/`UUID`。
  - `EliteSkinFeatureRenderer.textureFor()`：加分支 —— 僵尸 + 名牌 `endsWith(" BOSS")` 且非「【」开头 → 返回 `jiemo_li.png`。
- **第二步（下一里程碑，本步 build 验证后做）**：把 `textureFor` 里的 `jiemo_li` 换成「按名牌里的玩家名查在线玩家皮肤 `Identifier`、拿不到再 fallback jiemoLI」——客户端取在线皮肤的 API（`ClientPlayNetworkHandler.getPlayerListEntry`→`SkinTextures`）仓库首次用，隔离验证。
- 静态自检：三文件括号配平；`enablePlayerSkinZombieBoss`/`pickSkinTarget`/`SKIN_BOSS_OWNER` 定义↔引用一致；`ZombieEntity`/`MinecraftServer`/`UUID` import 齐；`jiemo_li.png` 就位。
- **待编译验证**：`mob.getServer()`（`Entity.getServer()` @Nullable；若 build 报找不到，改 `mob.getWorld().getServer()`）、`getPlayerManager().getPlayerList()`、`getGameProfile().getName()` —— 多为仓库在用的标准 API，风险中低；待本地 `./gradlew build`。
- 无新文件（改 3 个现有 + 1 张资源）；配置 +1、configVersion 6→7。

## 里程碑 146 — 横扫之刃对自定义武器生效（B方案：手搓横扫 + 补 enchantable/sword 标签）
- **问题**：武器附「横扫之刃」没效果；作者疑「是不是没给标签」。
- **根因（两层，标签只是其一）**：
  - `ChaosBladeItem` / `ClassWeaponItem` **都是 `extends Item`，不是 `SwordItem`**。1.21.1 的横扫在 `PlayerEntity.attack()` 里判 `主手物品 instanceof SwordItem` 才发动 → 普通 `Item` 永不横扫；横扫之刃只是「放大横扫伤害」的附魔,没有横扫这一下就没东西可放大 ＝ 看着没效果。
  - `minecraft:sweeping_edge` 的 `supported_items` ＝ `#minecraft:enchantable/sword`,武器不在该标签里 → 附魔台/铁砧根本附不上(只能 `/enchant` 硬塞,塞上也只挂个名)。
  - 故**补标签解决「附得上」,但解决不了「有效果」**——横扫开关在 SwordItem,标签管不到。
- **修法（B：不重构武器类,避开 SwordItem 构造 + 属性冲突）**：
  - `WeaponCombatHandler` 已有 `AttackEntityCallback`,在**暴击门槛之前**(横扫不依赖暴击)加 `trySweep()`：蓄满 + 在地面 + 非疾跑时,读武器横扫之刃等级,对主目标周围 `LivingEntity` 补一圈 AOE,伤害 ＝ `1 + 攻击力 × level/(level+1)`(贴近原版手感),带击退 + `SWEEP_ATTACK` 粒子 + 横扫音效。
  - 只给本 mod 武器(调用前已 `isWeapon` 过滤,它们非 SwordItem,不与原版双重横扫)。
  - 新建 `data/minecraft/tags/item/enchantable/sword.json`(`replace:false` 追加 6 把武器：`chaos_blade` + 5 职业武器),让横扫之刃附得上。
- 静态自检：`WeaponCombatHandler` 花括号 10/10、圆括号 104/104 配平；`trySweep` 定义/调用各 1；新 import(`Enchantment`/`EnchantmentHelper`/`Enchantments`/`RegistryKeys`/`RegistryEntry`/`MathHelper`/`World`)全被引用；tag JSON 合法。
- **待编译验证**：1.21 数据驱动附魔取等级——`world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.SWEEPING_EDGE).orElse(null)` ＋ `EnchantmentHelper.getLevel(RegistryEntry, ItemStack)`；若 `get(...)` 报错改 `getOrThrow(...)`,`getEntry` 返回类型按 IDEA 提示调。`getNonSpectatingEntities`/`takeKnockback`/`isTeammate`/`squaredDistanceTo` 为标准 API。待本地 `./gradlew build`。
- 新文件 +1(tag JSON,非 Java)；改 1 个 Java；无配置变更、configVersion 不变。
- **镐子不显示**：经查**不是本 mod**——未注册任何镐(仅 loot 表引用原版钻石镐)、未覆盖任何原版物品模型(`assets/minecraft` 下无 `models/`)、无任何碰物品渲染的 mixin(客户端 mixin 仅 HUD/标题/粒子)。疑外部资源包或渲染 mod(Sodium/Iris/光影),待作者确认「不显示」的具体现象(缺失紫块/手中看不见/栏里没了)再定。

## 里程碑 147 — 「按玩家攻击拔怪物血量」只在困难及以上开启(普通打不过修复)
- **需求**:动态对位里「根据玩家攻击拔高怪物血量」这套,现在普通难度也在跑,导致普通也打不过(怪被堆成肉盾)。要求**只在「困难」及以上才开**,普通及以下关掉。
- **根因**:`DynamicScaling.scaleToNearestPlayer` 的血量对位段只受 config `enableDynamicMobScaling` 一个开关管、**不分难度**;普通(适中 NORMAL)同样按 `玩家攻击 × targetHits × diffMult` 把怪血往上堆,玩家越强怪越肉。`diffMult` 只改放大幅度、不会关掉缩放本身,所以低难度仍在拔血。
- **关键澄清(踩坑纠正)**:本 mod 的难度**不是原版 `net.minecraft.world.Difficulty`**,而是自定义枚举 `com.yongye.item.GameDifficulty` 七档:0 游玩 / 1 简单 / 2 适中(NORMAL,默认/未设定按此) / 3 **困难(HARD)** / 4 地狱 / 5 深渊 / 6 永夜。所以「困难以上」＝ `ordinal >= 3`。最初设想的 `Difficulty.HARD` 是错的,查 `DifficultyManager` + `GameDifficulty` 后纠正。
- **修法**:在血量对位段(原 46-56 行)之前加一道硬门:
  ```java
  boolean hpScalingOn = com.yongye.system.DifficultyManager.getLevel()
          >= com.yongye.item.GameDifficulty.HARD.ordinal();
  ...
  if (hpScalingOn && hpInst != null && targetHits > 0 && pAtk > 0) { ... }
  ```
  仅 HARD/HELL/ABYSS/ETERNAL 才按攻击拔血;PLAY/EASY/NORMAL 及**未设定**(`getLevel()` 返回 -1 < 3 → 按「适中」处理)都不拔血。
- **范围**:门加在方法**内部**血量段,一处改动同时覆盖两个调用点 —— `MobEnhancementHandler`(普通怪强化)与 `MobBossHandler`(BOSS 化)。与现有 `enableDynamicMobScaling` 自检同一风格。
- **未动伤害段**:伤害对位段(58-68 行,按玩家最大生命拔高怪物伤害)**未受此门约束** —— 用户只点名「血量」,没提伤害,不擅自扩范围,仍照旧 `diffMult` 缩放。若也要按难度门控,需作者另行确认。
- 静态自检:`DynamicScaling.java` 花括号 7/7、圆括号 42/42 配平;`DifficultyManager.getLevel()` 返回 `int` 真实存在;`GameDifficulty.HARD` 常量存在;两处新引用走全限定名(同既有 `mobMult()` 风格),无需补 import。
- **无「待编译验证」点**:本轮全部使用 repo 既有 / 标准 API —— `getLevel()` 返回 int、`HARD.ordinal()` 标准枚举方法、`int >= int` 比较,**没有引入任何新接口或 yarn 敏感点**,比 m141-m146 干净。仍待本地 `./gradlew build` 走一遍总验(因前几轮 m141-m146 的待验证点尚未在本地编译过)。
- 无新文件(改 1 个现有 `DynamicScaling.java`);无配置变更、configVersion 不变(仍 7)。

## 里程碑 148 — 「按玩家攻击拔怪物血量」门控:世界难度 → 永夜等级 ≥ 5(永夜 V·灭世)
- **需求**:m147 把血量对位挂在世界难度「困难+」上,作者反馈仍太难,改成「永夜5 才开启」。
- **关键澄清(两套系统别混)**:「永夜5」指的是 `NightfallManager` 的**永夜等级 5**,不是 m147 用的世界难度 `GameDifficulty`。
  - **永夜等级**(NightfallManager):0~5 有名 —— 0 昼夜正常 / 1 永夜 I·暗潮 / 2 永夜 II·猎杀 / 3 永夜 III·围城 / 4 永夜 IV·灾变 / **5 永夜 V·灭世**;>5 为「永夜·深渊 N 层」(N=level-5)。**随游戏推进 / 任务失败往上爬**,可赎夜降回。
  - **世界难度**(GameDifficulty,m147 用的那个):游玩~永夜七档,**开局选一次、固定不变**。
  - 用户说「永夜5 **才**开启」的「才」是「升到那一档才触发」——对应的是会爬的「永夜等级」线,不是固定的世界难度。故改挂永夜等级。
- **修法**:把 m147 的门
  ```java
  boolean hpScalingOn = DifficultyManager.getLevel() >= GameDifficulty.HARD.ordinal();
  ```
  换成
  ```java
  boolean hpScalingOn = com.yongye.system.NightfallManager.getLevel() >= 5;  // 5 = 永夜 V·灭世
  ```
  前中期(永夜 < 5)怪不按攻击拔血;世界沉入永夜 V 之后(≥5,含其后「深渊 N 层」)才开始堆血。
- **diffMult 保留不动**:第 44 行 `diffMult = DifficultyManager.mobMult()` 仍在 —— 那是「缩放**幅度**」倍率(开启后世界难度仍调放大多少),与「**是否**开启」的门是两码事,正确保留。
- **未动伤害段**:伤害对位(58-68,按玩家血量拔怪伤)仍未受此门约束 —— 用户只点名「血量」。
- 静态自检:`DynamicScaling.java` 花括号 7/7、圆括号 44/44 配平;`NightfallManager.getLevel()` 返回 `int` 真实存在;无 `GameDifficulty` 残留、无悬空 import(本就用全限定名)。
- **无「待编译验证」点**:全用 repo 既有 / 标准 API(`getLevel()` int、`int >= int`),无新接口。仍待本地 `./gradlew build` 总验(m141-m146 待验证点尚未在本地编译过)。
- 无新文件(改 1 个现有 `DynamicScaling.java`);无配置变更、configVersion 不变(仍 7)。

## 里程碑 149 — 技能书等级上限 65535 → 10亿
- **需求**:技能书 V1~65535 提到 V1~10亿(与 m127 把属性上限抬到 10亿 对齐)。
- **坑(上限不止一处)**:
  - `YongyeConfig.skillBookMaxLevel = 65535` —— 配置默认值。各处 clamp 都读它(`HealthSkillBookItem.create`/`SkillBookItem.create`+上限提示/`PlayerSkillManager`/`SkillEffectManager`/`SkillBookCombineRecipe`/`HealthBookCombineRecipe`),改这一处它们全自动跟。
  - `ModCommands` **两处写死** `IntegerArgumentType.integer(1, 65535)` —— `/yongye book`(健康技能书)与 `/yongye skillbook`(职业技能书)。**只改配置不改命令,命令仍卡在 65535**。
- **改法**:
  - 配置默认 `65535 → 1000000000`(10亿)。
  - 两处命令上限 `integer(1, 65535) → integer(1, 1000000000)`(`/yongye artifact` 的 `integer(1, 6)` 是神器等级,不动)。
  - 两处注释(`Yongye.java` 顶部「V1~V65535」、`HealthSkillBookItem.java`「V1 ~ V65535」)改为「V1~V10亿,取 skillBookMaxLevel」。
- **数据类型**:10亿 = 1,000,000,000 < int 上限 2,147,483,647,装得下;技能书等级喂进属性后,最终属性受 m127 的 10亿 上限钳制,不溢出。
- 静态自检:4 文件(YongyeConfig/ModCommands/Yongye/HealthSkillBookItem)括号全配平;全仓库 `65535` 残留 0。
- **build**:无新接口/无新符号,纯字面量 + 注释替换,风险极低。**注:上一里程碑 m148(689455a)作者本地 `./gradlew build` 已 BUILD SUCCESSFUL ✅** —— 这意味着 m141-m148 那一整串「待编译验证」点(含 m146 的 1.21 数据驱动附魔取等级 API、m145 的 `getServer/getPlayerList/getGameProfile`)**全部已编过、标记可清**;本轮只在已验证的基线上改字面量。
- 无新文件(改 4 个现有);configVersion 不变(改默认值,非加/删字段)。

> **里程碑状态备注(m149 起)**:`build` 基线已对齐到 m148 本地编译通过。此前 DEVLOG 里 m121~m148 散落的「待编译验证」均已随 m148 的 BUILD SUCCESSFUL 落地,后续无需再回头补编;新里程碑若引入新接口仍照常标注。

## 里程碑 150 — 难度高奖励也高(世界难度 → 掉落倍率)
- **需求**:难度越高,奖励也越高(此前世界难度只让怪变强、**不**影响掉落)。
- **现状**:`LootHandler` 只有动态爆率 `lm`(玩家越强掉率越低,反滚雪球)在乘概率掉落;世界难度 `GameDifficulty` 不参与掉落。
- **改法**:引入难度奖励倍率 `dm = enableDifficultyLootBonus ? max(difficultyLootFloor, DifficultyManager.mobMult()) : 1.0`,挂**世界难度**(开局选的游玩~永夜,`mobMult` 0.5~6.0),保底 `difficultyLootFloor`(默认 **1.0** = 低难度不减奖励、只困难以上加成)。
  - 概率掉落:把 `dm` 折进综合倍率 `lm = baseLm * dm` —— 下游 11 处 `*lm` 全自动含难度加成,无需逐处改。
  - 精英必爆数量:`gm = (dynamicLootScaleGuaranteed ? baseLm : 1.0) * dm` —— 防滚雪球仍按 `baseLm` 缩减,难度加成单独乘且**不被 `dynamicLootScaleGuaranteed` 开关吞掉**。
- **倍率幅度**:HARD ×1.6 / HELL ×2.5 / ABYSS ×4.0 / ETERNAL ×6.0(与怪物强度倍率同源)。ETERNAL 下掉落约 6×,部分概率掉落会超 1.0(必掉)——这是有意的「最高难度最丰厚」;嫌多可调低 `difficultyLootFloor` 或后续加帽。
- **为何挂世界难度而非永夜等级**:永夜等级刚在 m148 用于怪血门控;且「难度高奖励高」语义就指开局选的那条世界难度线。
- 静态自检:`LootHandler` 括号 {}34/34·()235/235 配平;`DifficultyManager.mobMult()` 返回 `double`、同包无需 import;新字段 `enableDifficultyLootBonus`/`difficultyLootFloor` 定义↔引用一致。
- **无「待编译验证」点**:全用 repo 既有/标准 API。
- 改 `LootHandler.java` + `YongyeConfig.java`(本轮新增难度奖励 2 字段;并顺带定义 m151 细柱传送的 3 字段);**configVersion 7→8**。

## 里程碑 151 — 细柱兜底传送(修「单格高柱躲正上方」僵尸上不去)
- **现象**:玩家用 1×1 高柱躲在怪正上方,僵尸既搭不上去也传不上去。
- **根因(三条上去的路全卡)**:
  - 搭柱(`mobPillarUp`)+ 挖墙都卡 `ProgressionManager.canMobsDig`(第 5 天才解锁),第 5 天前根本不搭。
  - 现有「卡住传送」即便条件满足,`teleportNear` 是去**玩家四周找有实心地面的落脚点**——玩家站 1×1 细柱顶时,四周同高度全空气、底下无实心块 → 找不到落点 → 传送失败。
  - 爬墙需「精英/BOSS/永夜≥3」,普通怪前中期不触发。
- **改法**:加一条**独立兜底传送** `pursuitTeleportPillarCheese`(默认开),触发 = 玩家水平距离 ≤ `pillarCheeseMaxHorizontal`(2.5,近乎正上方)**且** 高出 ≥ `pillarCheeseMinHeight`(4.0)**且** 持续无进展达 `pursuitStuckTicks`(3s)。满足则 `teleportOntoPlayer` 直接把怪传到**玩家所在格**(站玩家脚下方块=柱顶),**不找地面、不依赖墙/解锁日**。
  - 放在现有传送之后、挖墙之前;先给搭塔/爬墙 3 秒机会(`pillarCheeseMinHeight` 4 略高于搭塔触发的 3 格),仍上不去才兜底。
  - 复用 `STUCK` 卡住跟踪 + `pursuitMaxTeleportsPerTick` 限流;带 `!anchor`(世界之锚仍可免疫,同其它传送)。
- **新辅助 `teleportOntoPlayer`**:与 `teleportNear` 区别 = 不在四周找地面,直接 `refreshPositionAndAngles(player.getX/Y/Z)`,清速度/落距 + 双端末影传送粒子/音效。
- 静态自检:`PursuitHandler` 括号 {}41/41·()271/271 配平;用到的 `hasVehicle/stopRiding/spawnParticles/getNavigation/refreshPositionAndAngles/playSound` 等全在本文件已用过;`dx/dz/wallAhead` 均在前文已算、作用域内;新字段定义↔引用一致。
- **无「待编译验证」点**:全用 repo 既有/标准 API。
- 改 `PursuitHandler.java`(配置 3 字段已在 m150 随 configVersion 8 一并加入)。

## 里程碑 152 — 细柱传送后把玩家撞下柱(m151 续:光传上去人还能继续躲)
- **需求**:单格高柱躲正上方、僵尸上不去时,僵尸 TP 到人旁边后要**把人撞下去**(否则只是传上柱顶,玩家照样站着躲)。
- **改法**:在 m151 的细柱兜底传送成功(`teleportOntoPlayer` 返回 true)之后,给**玩家**一个水平冲量把他从 1×1 柱顶推下去——
  - `player.setVelocity(cos(ang)*kb, 0.2, sin(ang)*kb)`(随机水平方向 + 轻微上抬脱离柱顶),`kb = pillarCheeseKnockback`(默认 0.6,柱仅 1 格宽足以推出边缘坠落;设 0 则只传不撞=回退 m151 行为)。
  - **关键**:玩家移动是客户端权威,服务端改 `setVelocity` 不会自动同步——必须显式 `player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player))` 速度才真正在客户端生效;另置 `velocityModified=true`。
- 静态自检:`PursuitHandler` 括号 {}42/42·()280/280 配平;新字段 `pillarCheeseKnockback` 定义↔引用一致;import 已加。
- **待编译验证(本轮唯一)**:`EntityVelocityUpdateS2CPacket` 是仓库首次使用的 S2C 包——`new EntityVelocityUpdateS2CPacket(player)`(读实体速度)+ `ServerPlayerEntity.networkHandler`(`ServerPlayNetworkHandler`)`.sendPacket(...)` 均为 1.21.1 标准用法(仓库客户端侧已用 `mc.player.networkHandler.sendCommand`,字段同名),风险低但首用故标注;build 报错(如包路径/方法名)贴来即修。
- 改 `PursuitHandler.java`(import +1、击退段)+ `YongyeConfig.java`(新增 `pillarCheeseKnockback`),**configVersion 8→9**。

## 里程碑 153 — 修永夜尸潮实体爆炸(下界多人传送刷到数万只拖崩 TPS)
- **现象**:玩家上传崩溃日志,`/kill` 杀死实体数 7477 → 8899 → 43622 → 49835 持续暴涨,服务端 `Can't keep up! Running 40652ms or 813 ticks behind` 最终三人全部 `lost connection` 掉线。存档「幸运方块单方块生存」,崩溃时在下界 `the_nether_128`,3 名玩家(qiaodaxian/wuyainhe/FK_GK)在高频互相传送。
- **根因**:`NightfallHordeHandler`(永夜尸潮,永夜≥1 时每 40tick 在每个玩家 `nightfallHordeRadius`=24 格内补刷至 `target=min(base100×永夜, max200)`)**只统计那 24 格框内的怪(existing)、没有任何全局实体上限**。尸潮怪出生即 `setTarget(player)` 锁定玩家;玩家一传送,统计框跟着人走,**老的几百只怪被甩在原地、不再计入 existing → 新落点 existing≈0 又补满 target → 反复传送,旧怪在地图上无限累积**。再叠动态对位把怪拔成肉盾(杀不动)+第 10 天起 `mobBossChance` 部分 BOSS 化 + BOSS 召唤小怪,日志满屏「进入狂暴」即这群怪的下游表现。
- 对比:硬核 `HardcoreSurvivalHandler.ambushSpawn` 有 `globalMaxHostilesNearby`(60)/`globalHostileRadius`(28)的全局怪量预算闸;**尸潮完全没有这道闸**。
- **修法**:尸潮 per-player 循环里(锚石检查之后、existing 统计之前)复用硬核同款全局预算——`gbox=expand(globalHostileRadius)` 统计 Monster 数,`globalHostiles >= globalMaxHostilesNearby` 则 `continue`;并把 `want` 再 `min(globalMaxHostilesNearby − globalHostiles)`,防单 tick 把总量顶过上限后下一批继续涌。总量硬闸 ~60/玩家,与单点 `target` 双保险。
- 注意:全局上限(60)比单点 `target`(200)更紧,生效后尸潮实际被钳在 ~60/玩家;要更密的潮请调 `globalMaxHostilesNearby`,而非 `nightfallHordeMax`。
- **诚实交代**:这是 yongye 实打实的 bug 已修;但存档是幸运方块整合包+下界,幸运方块自身也可能刷怪,日志无法 100% 切割两者占比——本次只摁住 yongye 这边的份额。已在老存档地图上累积的怪本修清不掉(玩家 `/kill @e` 即可),修复只防再次爆炸 + 靠原版远距离 despawn 渐渐清理。
- 静态自检:`NightfallHordeHandler` 括号 {}8/8·()89/89 配平;`globalMaxHostilesNearby`/`globalHostileRadius` 配置存在;`Box`/`MobEntity`/`Monster` import 齐全。全用既有 API/配置,无新接口、无「待编译验证」点。
- 改 `NightfallHordeHandler.java`(仅 1 个 Java 文件),无配置变更,configVersion 不变(仍 9)。

## 里程碑 154 — 开局礼包:职业武器带附魔 + 两个背包升级(创造模式那套另谈)
- **需求(本轮只做明确部分)**:开局礼包修改——① 选职发的职业武器自带 抢夺III + 火焰附加II;② 开局发 Sophisticated Backpacks 的「高级磁铁升级」+「高级喂食升级」。(用户同条消息还提了"检测创造模式→拿永夜技能书+强化→开遇强则强×100"和"第二次开创造改生存",那部分触发物品/×100语义/与创造的内在矛盾都没说清,留作下一步确认,未动。)
- **职业武器附魔**:`ClassManager.chooseStartingClass` 发武器处(line 91)改为先建 stack→`enchantStartingWeapon`→再 give。附魔走 m146 同款注册表写法(`p.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.LOOTING/FIRE_ASPECT).orElse(null)`,m146 已 BUILD SUCCESSFUL),`stack.addEnchantment(entry, level)` 写入;等级走配置 `weaponStartingLootingLevel`(3)/`weaponStartingFireAspectLevel`(2),开关 `weaponStartingEnchants`。Looting/FireAspect 由原版按武器附魔组件结算、不依赖 SwordItem(与横扫不同,无 instanceof 门),非剑类职业武器也生效。
- **两个背包升级**:`StartingKitHandler` 加独立块(放在背包 `return` 之前),软依赖按 id 查到才发——`giveById(p, id)` 解析→`Registries.ITEM.get`→查到非空气则发、否则静默跳过不崩。id 走配置 `startingMagnetUpgradeItem`(默 `sophisticatedbackpacks:advanced_magnet_upgrade`)/`startingFeedingUpgradeItem`(默 `sophisticatedbackpacks:advanced_feeding_upgrade`),开关 `giveStartingUpgrades`。用**独立**附件 `GOT_STARTING_UPGRADES`(persistent BOOL+copyOnDeath,照 GOT_STARTING_FOOD 模板),不复用 GOT_STARTING_KIT——这样已进过服的老玩家下次登录也能补发这两个升级。两个都没发成功才不打标记(装上 mod / 改对 id 后下次登录可补发)。
- **作用范围/局限**:武器附魔只作用于「选职时发放的那把」(开局礼包语义);已选过职业、武器早已到手的老玩家**不会**追溯附魔——要覆盖老玩家需另加一次性登录补发(待定);要让所有职业武器实例(合成/掉落)都带附魔也是另一处 hook(待定)。背包升级 id 是按图里合成表名的最可能值,若发现没发到多半是 id 不对,改配置即可(软依赖不崩)。
- 静态自检:ClassManager {}45/45·()296/296、StartingKitHandler {}10/10·()43/43、ModAttachments {}3/3·()232/232、YongyeConfig {}33/33·()326/326 全配平;新字段/方法/import 齐(RegistryEntry 已 import、Item 已 import)。
- **待编译验证(本轮唯一)**:`ItemStack.addEnchantment(RegistryEntry<Enchantment>, int)` 是仓库首次使用(1.21.1 标准方法,风险低);取附魔的 `getRegistryManager().get(...).getEntry(...)` 是 m146 已编译过的写法、`p.getRegistryManager()` 也在 AccessoryStorage 用过。build 报错(如 addEnchantment 签名)贴来即修。
- 改 4 文件:ClassManager.java(武器附魔)、StartingKitHandler.java(两升级+giveById)、ModAttachments.java(新增 GOT_STARTING_UPGRADES)、YongyeConfig.java(开局升级2 id+开关+武器附魔3字段),**configVersion 9→10**。

## 里程碑 155 — 创造模式监听(反作弊)+ 世界崩塌 ×100(确认后实现 m154 的创造那套)
- **需求确认结果**:三问答清——① 第2次开创造强制改生存:**可设 ID 豁免,其余都管**;② 遇强则强×100:**全局永久**(触发后不再关);③ 触发物=**攻击强化技能书 + 稀有材料系列**(生命碎片/结晶/核心/血核/永夜尘/裂界残片/深渊魂晶/终焉精华,任一即可),并要**全服播报谁拿了啥导致世界崩塌、怪物全面×100**。矛盾化解:这其实是一套**反作弊陷阱**——非豁免玩家偷开创造抓强力物品就触发世界崩塌惩罚、第2次开创造踢回生存;管理员(豁免)可自由进创造测试、不触发陷阱。
- **WorldDoomManager(新)**:仿 DifficultyManager 的世界级持久——静态 `doom` 存档 `yongye_doom.json`,SERVER_STARTED 读 / STOPPING 写,load() 先归位防跨世界残留。`trigger(server,玩家名,物品名)` 幂等(已崩塌直接返回):置 doom + 存档 + 全服深红粗体播报「<玩家> 触碰禁忌之物【<物品>】…怪物全面强化×N」+ 遍历 `server.getWorlds()` 的 `world.iterateEntities()` 对所有已加载 Monster 调 `MobEnhancementHandler.applyDoom`;另注册 ENTITY_LOAD:doom 期间新生成/加载的 Monster 也立即 applyDoom(独立于怪物增强开关与早返,保证"全面")。
- **MobEnhancementHandler.applyDoom(public,新)**:加 ID_DOOM_HP/ATK,对单只怪 `addMultiplierTotal`(ADD_MULTIPLIED_TOTAL,叠在所有倍率之上)血量/攻击各 ×`doomMobMultiplier`(默认100)+ setHealth 补满;固定 ID 先 remove 再 add → 幂等,新怪 + 触发时批量调用都安全不叠加。
- **CreativeWatchHandler(新)**:每 10tick 轮询各玩家游戏模式。豁免名单 `creativeExemptIds`(逗号/空格分隔、大小写不敏感,默认 qiaodaxian)内玩家完全跳过(但更新 LAST_MODE 基线)。非豁免:与 transient `LAST_MODE` 比对识别"刚进创造"(首次见到只记基线不计数);在创造中主手持禁忌之物且未崩塌→trigger;"刚进创造"且 `CREATIVE_ENTRIES`(持久 int,死亡保留,跨登录累计)+1 后 ≥2 且开关开→`changeGameMode(SURVIVAL)`+红字提示。
- **触发物判定 isForbidden**:`ModItems.getSkillBook(SkillType.ATTACK)`(攻击强化技能书,SkillType 确有 ATTACK)或 8 种稀有材料之一(注:enhance 系统的 `isMaterial` 只认前4种,这里按用户"裂界残片这些都算"扩到全部8种,显式列举不走 isMaterial)。
- **范围/恢复**:×100 既作用于已加载的怪(触发时遍历)也作用于之后的怪(ENTITY_LOAD);BOSS 是 Monster 同样覆盖,boss 倍率与 doom 倍率独立叠乘。永久=触发后无自动/玩家关闭途径(应需求);**人工恢复**唯一途径=停服删除/改写存档根目录 `yongye_doom.json` 的 doom 字段(没做命令,尊重"不再关";管理员豁免故不会误触发)。
- **设计取舍说明**:把"豁免"同时用于"免强制生存"和"免触发陷阱"——这样管理员在创造里拿材料测试不会误把世界×100(否则全局永久不可逆,风险太大);若想要管理员手动触发崩塌做活动,再加命令。
- 静态自检:6 文件括号全配平(WorldDoomManager {}22·()63、CreativeWatchHandler {}18·()67、MobEnhancementHandler {}15·()113、ModAttachments {}3·()242、YongyeConfig {}33·()332、Yongye {}6·()89);applyDoom/trigger/isDoom/CREATIVE_ENTRIES/4配置/2注册全对齐。
- **无新接口、无「待编译验证」点**:iterateEntities/getWorlds/changeGameMode/interactionManager.getGameMode/getMainHandStack/ServerEntityEvents.ENTITY_LOAD/Codec.INT/addMultiplierTotal/getSkillBook 均为仓库已用且随 m148 编译通过的 API。
- 改/增 6 文件:WorldDoomManager.java(新)、CreativeWatchHandler.java(新)、MobEnhancementHandler.java(+ID+applyDoom)、ModAttachments.java(+CREATIVE_ENTRIES)、YongyeConfig.java(+创造监听4字段)、Yongye.java(注册2系统),**configVersion 10→11**。

## 里程碑 156 — 开局礼包武器再加 横扫之刃(m154 续)
- **需求**:开局发的职业武器在 抢夺III + 火焰附加II 基础上再加横扫之刃。
- **改法**:`ClassManager.enchantStartingWeapon` 加一项——`reg.getEntry(Enchantments.SWEEPING_EDGE)` + `w.addEnchantment(sweeping, weaponStartingSweepingLevel)`;等级配置 `weaponStartingSweepingLevel`(默3=满级,0不附)。
- **效果说明**:职业武器非 SwordItem,原版横扫不触发,但 **m146 的手搓横扫**(WeaponCombatHandler.trySweep)正是读武器上的横扫之刃等级来发 AOE——所以加这个附魔组件后,开局武器挥砍即享 m146 的横扫 AOE(m146 已随 m148 BUILD SUCCESSFUL)。范围同 m154:只作用「选职新发的那把」,已选职老玩家不追溯。
- 静态自检:ClassManager {}45/45·()300/300、YongyeConfig {}33/33·()334/334 配平;SWEEPING_EDGE(m146 已用)+ 新字段引用一致。
- 待编译验证:沿用 m154 的 `ItemStack.addEnchantment`(本轮未引入新接口,SWEEPING_EDGE 取法 m146 已编译过)。
- 改 2 文件:ClassManager.java(+横扫)、YongyeConfig.java(+weaponStartingSweepingLevel),**configVersion 11→12**。

## 里程碑 157 — 热修 m155 build 报错:ServerEntityEvents 错包路径
- **报错**:`./gradlew build` 失败,`WorldDoomManager.java:5 找不到符号 ServerEntityEvents`——我把 import 写成了 `net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents`(不存在该路径下的此类)。
- **修法**:改成仓库其它文件(MobEnhancementHandler/ItemCleanupHandler 等)用的正确路径 `net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents`。
- **同时复核**:把 m155 两个新文件(WorldDoomManager/CreativeWatchHandler)的**全部 import 逐条**与仓库既有用法比对,确认无其它错路径(都有 proven 用法)。教训:新建文件除括号/符号外,还要核对 import 包路径,不能只静态数括号。
- 改 1 文件:WorldDoomManager.java(仅 import 行),无逻辑/配置变更,configVersion 不变(仍12)。

## 里程碑 158 — 强化失败/碎裂系统
- **需求**:强化引入失败概率——≤1000 级必成功;1001 级起到 10000 级成功率线性下降,10000 级时仅 10%;超过 10000 级后,失败有概率令武器「碎裂」(销毁)。
- **成功率曲线**(`EquipmentEnhancer.successRate(level)`):`level < enhanceFailStartLevel(1000)` 返回 1.0;`[start,end]` 区间内 `rate = 1 - t·(1-endRate)`(t=进度,endRate=0.10),≥end 封底 0.10。总开关 `enableEnhanceFailure` 关则恒 1.0。
- **逐级尝试**(`attempt(player, equip, budget)` → `EnhanceResult`):安全段(<start)批量推进不空转 RNG;其后每级 `rng.nextDouble() < successRate(level)` 判定,成功 level+1、失败仅消耗本次预算且 level 不变;失败时若 `level ≥ enhanceBreakLevel(10000)`,先看玩家有无 `ENHANCE_PROTECTED`(有则消耗、挡下碎裂,usedProtect=true),否则按 `enhanceBreakChance(0.25)` 判碎裂(stack=EMPTY、broke=true,停止)。RNG 用 `p.getRandom()`,p 为 null 时 `Random.create()`。
- **接入**:重写 `enhanceFromInventory`(背包强化)和 `EnhanceScreenHandler.applyUpgrade`(强化界面)都改用 `attempt()`;碎裂→清空装备槽 + `ENTITY_ITEM_BREAK` 音效 + 深红提示;否则发成功/失败/保护汇总。**材料无论成败都一次性全消耗**(碎裂时材料也作废)。命令 `withLevel`/合成 `addLevels` 等管理/确定性路径**不**接失败,保持原状。
- **配置**:+`enableEnhanceFailure/enhanceFailStartLevel(1000)/enhanceFailEndLevel(10000)/enhanceFailEndRate(0.10)/enhanceBreakLevel(10000)/enhanceBreakChance(0.25)`;+附件 `ENHANCE_PROTECTED`(Bool,持久,死亡保留)。**configVersion 12→13**。
- 静态自检:全改动文件括号配平;全用 repo 既有/标准 API(getRandom/Random.create/getAttachedOrElse/setAttached/ENTITY_ITEM_BREAK),无新接口、无待编译验证点。

## 里程碑 159 — 强化保护卷(掉落/杀怪兑换 + 贴图)
- **需求**:新增「强化保护卷」,使用后挡下「下一次会令装备碎裂的强化失败」;无法合成,仅怪物低概率掉落 + 杀怪数量自动兑换(每张 2000 击杀,每兑换 1 张后阈值翻倍);职业选择书换用户给的图,保护卷也加贴图。
- **物品** `EnhanceProtectScrollItem`:右键 → 置 `ENHANCE_PROTECTED=true`、decrement(1)、附魔台音效 + 提示;已生效则拒绝重复使用。getName「强化保护卷」(LIGHT_PURPLE)+ tooltip。`ModItems` 注册 `enhance_protect_scroll`,`maxCount(16).rarity(RARE)`,**无合成 JSON = 不可合成**;加入创造栏(WARD_BOOK 之后)。
- **获取** `ProtectScrollHandler`(`ServerLivingEntityEvents.AFTER_DEATH`,敌对怪被玩家击杀):① `protectScrollDropChance(0.002)` 概率直接掉一张;② `SCROLL_KILLS+1`,阈值 `= protectScrollKillBase(2000) << min(SCROLL_EXCHANGES,30)`(翻倍),达标则扣阈值、`offerOrDrop` 一张、`SCROLL_EXCHANGES+1`、升级音效 + 提示。
- **配置**:+`enableProtectScroll/protectScrollDropChance(0.002)/protectScrollKillBase(2000)`;+附件 `SCROLL_KILLS/SCROLL_EXCHANGES`(Int,持久,死亡保留)。
- **贴图**:两张用户图缩放 64×64 LANCZOS → `class_select_book.png`、`enhance_protect_scroll.png`;`class_select_book.json` 的 layer0 由 `minecraft:item/writable_book` 改为 `yongye:item/class_select_book`;新建 `enhance_protect_scroll.json`;双语言加 `enhance_protect_scroll` 条目。
- 静态自检:全改动/新文件括号配平、JSON 校验通过;新文件 import 逐条比对(`ServerLivingEntityEvents` 同 LootHandler 路径、`appendTooltip` 签名同 WardBookItem);全用 proven API(offerOrDrop/setToDefaultPickupDelay/ENTITY_PLAYER_LEVELUP/Rarity.RARE),无待编译验证点。

## 里程碑 160 — 装入 GPT 生成的职业书 / 灾厄核心贴图(填占位)
- **需求**:此前用户喊「贴图占位没做」。用户按之前提供的提示词用 GPT 生成 6 本职业书 + 灾厄核心方块贴图,本轮装入替换占位。
- **来源澄清(重要)**:本轮 uploads 的 `下载__2_.zip` 装的是 **GPT 生图**(7 张 1254×1254:6 张 RGBA 书 + 1 张 RGB 灾厄核心),**不是** DragonCore dagger 模型。上一轮装着「夜绿武器套装」dagger 的沙箱已重置丢失,所以「刺客→dagger 3D 样板」本轮**没做**——要接需用户重新上传 DragonCore 资源包。
- **装法**:6 本书各缩 64×64(LANCZOS,保留 alpha)覆盖 `textures/item/class_book_{assassin,warlock,tank,warrior,swordsman,monk}.png`(原为拿 `skill_book_attack` 凑的占位、6 张 md5 完全相同=六书长一样);灾厄核心缩 64×64(原 16×16 占位,RGB 不带透明,方块填满正方形)覆盖 `textures/block/catastrophe_core.png`(MC 支持 HD 方块贴图)。
- **徽记→职业映射**(按图中徽记识别,无歧义):匕首/暗影→刺客、发光骷髅→术士、骑士盾→坦克、战斧→战士、交叉双剑→剑客、拳套/念珠→武僧。
- **无需改 model/代码**:`models/item/class_book_*.json` + `blockstates`/`models/block`/`models/item` 的 `catastrophe_core.json` 路径都已指 `yongye:...`,替换贴图文件即生效。已核实真在用:`class_book_*` 由 `ModItems.CLASS_BOOKS`(每 PlayerClass 一个 `ClassBookItem`,选职发)、`catastrophe_core` 由 `ModBlocks.CATASTROPHE_CORE` + `CatastropheCoreManager` 放置。
- **纯资源覆盖,无 Java/配置改动,configVersion 不变(仍 13)**。缩到 64×64 后 NEAREST 放大复核,7 张徽记肉眼仍清晰可辨。
- **遗留占位**:精英怪皮肤 `elite_creeper/skeleton/spider/witch` 仍纯色占位(它们是 UV 皮肤图集 64×32/64×64,不适合 GPT 平面图——要么皮肤编辑器手改,要么把原版皮肤发来程序化染色);`accessory_gui` 饰品栏背景待按真实槽位坐标程序化画。

## 里程碑 161 — 新增 SKILL.md(项目踩坑 / 避雷 / API 核查 / 自查手册)
- **需求**:作者要求把这个项目实打实踩过的坑、要避开的写法、要查证的 API、能做不能做的、收尾自查,固化成一个 `SKILL.md`。明确**不是固化开发流程**,目的只是「别让作者再因同样错误难受一遍」。
- **放置**:仓库根目录 `SKILL.md`(与 HANDOVER/DEVLOG 并排)→ 每次重拉仓库它就跟着回来,治「沙箱重置后又踩同一个坑」。带 Claude skill 标准 frontmatter(`name: yongye-mod` + `description`),也可直接丢进 skills 目录用。
- **内容**(9 节):①三条铁律(不装懂不臆想 / 话少 / 沙箱会清先 push 落盘)②环境能做不能做(沙箱编不了 Fabric→新 API 标待编译验证、画不了 UV 皮肤图集、push 靠作者 PAT)③开工前核对(先确认远端 HEAD 别重复造轮子——m158/m159 曾因落旧快照整套重做)④mod 易混点(GameDifficulty 世界难度 7 档自定义枚举 ≠ 原版 Difficulty、世界难度 vs 永夜等级两套系统、怪血 4 层叠加、改机制找全入口、刷怪 handler 需全局上限、GSON 旧值盖新默认 + configVersion)⑤必查 API(新文件 import 路径逐条比对仓库既有=m157 ServerEntityEvents 错包路径教训、1.21.x getMiningSpeedMultiplier→getMiningSpeed、附魔取等级 proven 套路、setVelocity 需 EntityVelocityUpdateS2CPacket 同步)⑥贴图装法(LANCZOS 插画 / NEAREST 像素、64×64、UV 皮肤不能 GPT 平面图)⑦交活前自查清单⑧提交推送规矩(yongye-dev 身份 / PAT 一次性不写 config / 导 patch 兜底)⑨收尾沟通模板。
- 自查:frontmatter YAML 校验通过;HANDOVER 顶部加指针指向 SKILL.md。
- **纯文档,无代码 / 配置改动,configVersion 不变(仍 13)**。改 `SKILL.md`(新)+ `HANDOVER.md`(加指针)+ `DEVLOG.md`。

## 里程碑 162 — 自定义末影龙 BOSS · Stage 1(GeckoLib 前置 + 第一个自定义实体)
- **需求**:用户买的「真正的末影龙」(转龙核 DragonCore + MythicMobs 包)替换末影龙。
- **关键澄清**:该包是 **Bukkit 服务端生态**——DragonCore 客户端渲染 + MythicMobs 服务端怪(`Type: HUSK` 套龙模型的地面近战 BOSS,**不是**原版飞行末影龙)。模型是**基岩 geometry**(`toro_ender_dragon.geo.json` format 1.12.0 / 57 骨骼 / 2048² 贴图)+ 基岩动画。服务端 YAML 在 Fabric 全废;贴图 UV 是给自定义模型的,不能单换原版 `dragon.png`(会糊)。→ **唯一出路:GeckoLib + 自写渲染。**
- **用户决定**:GeckoLib(MIT 开源)用 **JiJ 嵌进 mod**(玩家不单独装);「替换原版 + 新可召唤 BOSS」两个都要,**先做地基(新可召唤实体)build 绿了再加替换原版**。
- **本轮 Stage 1**:
  - 构建:`build.gradle` 加 GeckoLib maven(`dl.cloudsmith.io/public/geckolib3/geckolib/maven`,`includeGroupByRegex software.bernie.*`)+ `modImplementation` + `include`(JiJ);`gradle.properties` +`geckolib_version=4.8.3`(1.21.1 最新);`fabric.mod.json` depends +`geckolib>=4.0.0`。
  - 资源:放 GeckoLib 约定路径 `assets/yongye/geo/`、`animations/`、`textures/entity/`(动画用 rar 里那份全集,含 idle/walk)。
  - 实体:`ToroEnderDragonEntity extends HostileEntity implements GeoEntity`(属性血500/攻20/速0.28/击退抗1/索敌48;基础近战 AI;单 controller `move`:移动→walk 否则 idle;`GeckoLibUtil.createInstanceCache`)。
  - 注册:`ModEntities`(`EntityType.Builder.create(::new, MONSTER).dimensions(6,5).build(KEY)` + `FabricDefaultAttributeRegistry`),主类 `onInitialize` 加 `ModEntities.init()`。
  - 客户端:`ToroEnderDragonModel extends GeoModel`(指三资源)+ `ToroEnderDragonRenderer extends GeoEntityRenderer`;`YongyeClient` 加 `EntityRendererRegistry.register`。
  - 召唤:`/yongye dragon` 在玩家处生成(测试用)。
- **待编译验证(整体)**:全新前置 + 第一个自定义实体,沙箱编不了 Fabric。**最高危 = GeckoLib 内部 import 路径**(4.5 迁过包;写的是 `software.bernie.geckolib.{animatable.GeoEntity, animatable.instance.AnimatableInstanceCache, animation.AnimatableManager/AnimationController/RawAnimation, model.GeoModel, renderer.GeoEntityRenderer, util.GeckoLibUtil}`——build 报「package 不存在 / cannot find symbol」就按 IntelliJ 自动导入修,把报错贴来我改准)。其次:1.21.1 `EntityType.Builder.build(RegistryKey)` 签名、`EntityType.create`/属性名。
- 静态自检:7 个改动/新建 .java 括号全配平;3 个 JSON(fabric.mod.json + geo + animation)合法。
- **configVersion 不变(仍 13)**。
- **遗留**:Stage 2 替换原版末影龙本体(GeoReplacedEntity + 渲染替换);Stage 3 BOSS 技能/血条/平衡。均待 Stage 1 本地 build 绿后再做。

## 里程碑 163 — 热修 m162 build 报错(GeckoLib 接入后 100 错)
- **根因两条**:
  1. **GeckoLib 拽进自带的 fabric-api 传递依赖**,与项目 `fabric_version`(0.105.0)冲突 → 全项目 `getAttachedOrElse`/`setAttached`(fabric-api 数据附件 API)解析失败。那几十个文件(TalentManager/ClassManager/EliteHandler…)的报错**全是这一个根因**,不是文件本身坏。修:`build.gradle` 给 geckolib 的 `modImplementation` 加 `exclude group: "net.fabricmc.fabric-api"`,只用项目自己的 fabric-api。
  2. **`EntityType.Builder.build()` 签名**:本映射版本要 `String` 不是 `RegistryKey`。改 `.build("toro_ender_dragon")`(`TORO_ENDER_DRAGON_KEY` 仍用于 `Registry.register` 的 identifier)。
- **好消息**:GeckoLib 的 import 路径**本身全解析成功**——`ToroEnderDragonModel` 只报「使用/覆盖了已过时的 API」**警告**(非错误),证明 `software.bernie.geckolib.*` 包路径(animatable/animation/model/renderer/util)写对了。`GeoModel.getModelResource(T)` 等是 deprecated 但仍可用(警告不阻断 build;若渲染异常再换非过时签名)。
- 改 `build.gradle`(exclude)+ `ModEntities.java`(build String)。静态括号配平。configVersion 不变(仍 13)。待用户重新 `./gradlew build` 验证。

## 里程碑 164 — GeckoLib 接管原版末影龙渲染(只换皮,飞行/血条/技能全保留)
- **需求**:用户嫌自建龙不会飞、没血条、没技能;要「替换末地原版龙本体」,技能用原版的就行。
- **判断(关键)**:不给自建地面龙补飞行/血条/技能(那等于把原版白送的全部重写一遍),**反过来——留原版末地龙实体一字节不碰,只把它的「渲染器」换成夜绿龙模型**。这样飞行、BOSS 血条、龙息、水晶回血、阶段、死亡演出全部原样保留,一次性解决三个抱怨。
- **版本敏感 API 核查**:GeckoLib 最新 main 分支(v5,配 1.21.5+ 的 render-state 系统)的 `GeoReplacedEntityRenderer` 是**三泛型**(`T,E,R extends EntityRenderState & GeoRenderState`),与本项目 **v4.8.3 / MC 1.21.1** 不符,照 v5 写必挂。改去 `geckolib-examples` 的 **Multiloader-1.21.1 分支**(对症 v4/1.21.1)curl 拉 ReplacedCreeper 三件套真实源码,逐字核对钉死签名。
- **实现(3 新文件)**:
  1. `entity/ToroDragonReplacement` `implements GeoReplacedEntity` —— **独立轻量对象,不是实体,原版龙不碰**。`registerControllers` 单 controller 恒循环 `fly`(动画文件确有 fly;原版龙永远在飞);`getReplacingEntityType()` 返 `EntityType.ENDER_DRAGON`。
  2. `client/render/ToroDragonReplacementModel` extends `GeoModel<ToroDragonReplacement>` —— 复用 m162 已在仓库的同套 `toro_ender_dragon.{geo,png,animation}` 资源,不重复放。
  3. `client/render/ToroDragonReplaceRenderer` extends `GeoReplacedEntityRenderer<EnderDragonEntity, ToroDragonReplacement>`(两泛型:原版实体 + 替身)。构造 `super(ctx, new model, new animatable)` + `shadowRadius 2.5`。yarn 的 `EntityRendererFactory.Context`。
  - `YongyeClient.onInitializeClient` 加 `EntityRendererRegistry.register(EntityType.ENDER_DRAGON, ToroDragonReplaceRenderer::new)` 覆盖原版龙渲染器。
- **静态自检**:三新文件括号全配平;GeckoLib import 逐条比对——`GeoReplacedEntity` 与已编过的 `GeoEntity` 同包(`...animatable`),`AnimatableInstanceCache/Manager/Controller/RawAnimation/GeckoLibUtil` 与 m162 逐字一致,`GeoModel` 同 m162 模型,`GeoReplacedEntityRenderer` 与已编过的 `GeoEntityRenderer` 同包(`...renderer`)。
- **待编译验证(本轮唯一)**:两个真正新类 `GeoReplacedEntity`/`GeoReplacedEntityRenderer` + `getReplacingEntityType()` 签名——已用 v4/1.21.1 官方示例源码逐字核对,但沙箱编不了 Fabric 故标。yarn 名 `EnderDragonEntity`(`net.minecraft.entity.boss.dragon`)/`EntityType.ENDER_DRAGON`/`EntityRendererFactory.Context` 均标准。
- **注意**:替换的是**渲染器(客户端外观)**,原版龙的实体/AI/血条/技能全在服务端,不受影响。龙是多部件实体,GeckoLib 整体渲一个模型、原版部件碰撞箱服务端照常。
- **遗留**:自建可召唤龙(`ToroEnderDragonEntity` / `/yongye dragon`)仍是无血条无技能的地面近战怪——本轮没碰它,只接管了末地原版龙。
- **改 4 文件**(3 新 .java + YongyeClient 注册),**configVersion 不变(仍 13)**。

## 里程碑 165 — 自建末影龙改成会飞 + 第10天起几率刷出(承 m163 遗留 Stage3)
- **需求**:`/yongye dragon` 那条自建龙(`ToroEnderDragonEntity`,m162 起是地面近战 `HostileEntity`)要会飞、有 AI 寻路、不在地上走、跟末地那条差不多;并在第 10 天后有几率自动刷出来。
- **① 飞行化**(改 `ToroEnderDragonEntity`):
  - 不换基类(仍 `HostileEntity`,保留敌对属性/索敌)。构造里 `this.moveControl = new FlightMoveControl(this, 20, true)`(第三参 `true` = 无重力;官方文档明确「用 FlightMoveControl 的实体无重力」)+ `setNoGravity(true)` 双保险。
  - 重写 `createNavigation(World)` 返回 `new BirdNavigation(this, world)`(**裸构造,不调可能随版本改名的 setter**,压低 build 风险)。
  - `initGoals` 去掉 `WanderAroundFarGoal`(地面游荡),只留 `MeleeAttackGoal`(走飞行导航,在 3D 里追玩家、俯冲攻击,不会落地走)+ `LookAtEntityGoal`/`LookAroundGoal` + `RevengeGoal`/`ActiveTargetGoal`。
  - 属性 +`GENERIC_FLYING_SPEED 0.8`(原版蜜蜂同款),`FOLLOW_RANGE` 48→64;重写 `handleFallDamage` 返 false。
  - GeckoLib 动画从 idle/walk 换成 `fly_idle`(悬停)/`fly_walk`(移动)——动画文件确有这两条;渲染器/模型不动。
- **② 野生刷怪**(新 `WildDragonSpawnHandler`,仿 `NightfallHordeHandler`):
  - `ServerTickEvents` 每 `wildDragonCheckIntervalTicks`(默 6000=5min)检定一次。
  - 复用 m155 proven 的 `getWorlds()`/`iterateEntities()` 数全服存活龙,≥`wildDragonMaxAlive`(默 1,稀有 BOSS 事件)就跳过。
  - 收集 生存/冒险 + 在 `ServerWorld` + `ProgressionManager.gameDay(world) >= wildDragonMinDay`(默 10) 的玩家,按 `wildDragonSpawnChance`(默 0.05)全服一次检定;中则在随机合法玩家上方 `wildDragonSpawnHeight`(默 28)格高空(±16 水平偏移、clamp 到 topY-4)`spawnEntity` + `setTarget(player)` 出生即俯冲追杀 + 全服深紫播报。
- **静态自检**:两文件括号配平;飞行 import 包路径标准(`FlightMoveControl`=`net.minecraft.entity.ai.control` 经 yarn 文档确认,`BirdNavigation`/`EntityNavigation`=`net.minecraft.entity.ai.pathing`);handler 全用 proven API(`iterateEntities`/`getWorlds`、`getPlayerManager().broadcast(Text,false)`、`gameDay(World)`、`interactionManager.getGameMode()`),`ProgressionManager` 同包免 import。
- **待编译验证(本轮)**:全是**原版 yarn 飞行 API**(非 GeckoLib、非新库),且多为原版蜜蜂同款——`FlightMoveControl(this,20,true)` 三参构造、`new BirdNavigation(MobEntity,World)`、`createNavigation` 重写、`EntityAttributes.GENERIC_FLYING_SPEED`、`handleFallDamage(float,float,DamageSource)` 返 boolean、`setNoGravity`、`getTopY()`。沙箱编不了 Fabric 故标,把握高。
- **注意**:飞行龙俯冲攻击地面玩家时会降到近地面,但走的是飞行移动不是走路(符合「不在地上走」)。
- **新增 1 文件**(`WildDragonSpawnHandler`)+ 改 `ToroEnderDragonEntity`/`YongyeConfig`(+6 字段)/`Yongye`(注册),**configVersion 13→14**。

## 里程碑 166 — 末影龙攻击距离拉远(可配)
- **需求**:自建龙(`/yongye dragon`)现在要贴到身上才打,太近;要攻击距离远一些。
- **原因**:m165 用的 `MeleeAttackGoal`,触发攻击的判定距离 `getSquaredMaxAttackDistance` 按体型算、不可直接配。
- **修法**:新建 `DragonAttackGoal extends MeleeAttackGoal`,覆盖 `protected double getSquaredMaxAttackDistance(LivingEntity entity)` 返回 `reach² + 目标宽度`。原版 Vindicator/守卫者就是靠子类覆盖这个方法改攻击距离的;`tryAttack` 直接结算伤害、不做距离二次判定,所以放大判定距离 = 攻击距离变远。
- `ToroEnderDragonEntity.initGoals` 把 `new MeleeAttackGoal(this, 1.0, true)` 换成 `new DragonAttackGoal(this, 1.0, true, YongyeConfig.get().dragonAttackReach)`(撤掉 MeleeAttackGoal import,DragonAttackGoal 同包免 import)。
- 配置 +`dragonAttackReach`(默 16 格,可调,越大越远处就能打到)。
- **静态自检**:`DragonAttackGoal{}3·()4` + `ToroEnderDragonEntity{}10·()40` 配平;无残留 MeleeAttackGoal 代码引用;`getSquaredMaxAttackDistance(LivingEntity)` / `MeleeAttackGoal(PathAwareEntity,double,boolean)` 签名经 yarn 文档确认,`this`(HostileEntity→PathAwareEntity)合法;`getWidth()` 标准。
- **待编译验证**:`getSquaredMaxAttackDistance` 覆盖(yarn 文档确认、把握高,沙箱编不了故标)。
- **新增 1 文件**(`DragonAttackGoal`)+ 改 `ToroEnderDragonEntity`/`YongyeConfig`(+1 字段),**configVersion 14→15**。

## 里程碑 167 — 新增两只动画蜘蛛(精英·毒液蜘蛛 + BOSS·红蜘蛛,GeckoLib,Stage1)
- **需求**:用户传 `毒液蜘蛛.zip`/`红蜘蛛.zip`(各 = 基岩 geo 1.12.0 模型 + 基岩 1.8.0 动画 + 512² 贴图),挑一个当精英、一个当 BOSS,都带动作。
- **分配**:红蜘蛛 22 骨骼/29 动画(climbup/climbdown/roarrally/smashbelow/stab/swipe/egglay/cocoon/jumpfly… 明显 BOSS)→ **BOSS**;毒液蜘蛛 13 骨骼/6 动画(waiting/walking/meleehit/die/body/venomspit 精简)→ **精英**。
- **坑**:两模型内部 identifier 都叫 `geometry.spiderboss`(撞名)→ 装入时各改成 `geometry.venom_spider`/`geometry.red_spider`(只改几何名、不动骨骼名,动画照常匹配),避免 GeckoLib 几何缓存冲突。
- **路子**:与龙(m162)同款已验证的 GeckoLib,且**扩展原版 `SpiderEntity`**(白嫖爬墙 + 蜘蛛 AI;yarn 文档确认 `SpiderEntity(EntityType<? extends SpiderEntity>,World)`/`static createSpiderAttributes()`/`initGoals()`/`createNavigation()`)。按龙的分阶段法,**本轮 Stage1 = 能召唤 + 渲染 + 基础动画**;刷怪(精英/BOSS 怎么出)下一轮。
- **实现**:
  - 资源装 `assets/yongye/{geo,animations,textures/entity}/{venom_spider,red_spider}.*`。
  - `VenomSpiderEntity`(extends SpiderEntity implements GeoEntity,血80/攻10/速0.32/索敌32,控制器 move:isMoving→walking 否则 waiting)+ `RedSpiderEntity`(同结构,血400/攻18/速0.34/击退抗0.8/索敌48)。
  - 各 `GeoModel`(显式路径)+ `GeoEntityRenderer`(shadowRadius 毒 1.0/红 1.6)。
  - `ModEntities` 注册 `VENOM_SPIDER`(dim 1.6×1.0)/`RED_SPIDER`(dim 3.0×1.8)+ 各 `FabricDefaultAttributeRegistry`。
  - `YongyeClient` 注册两渲染器;`ModCommands` 加 `/yongye venomspider`、`/yongye redspider` 召唤(仿 `/yongye dragon`)。
- **静态自检**:9 文件括号全配平(含 ModCommands 727/727)+ 4 新 JSON 合法 + 蜘蛛 GeckoLib import 与已编过的龙逐字一致(`animatable.GeoEntity`/`model.GeoModel`/`renderer.GeoEntityRenderer`)。
- **待编译验证(本轮)**:`SpiderEntity` 扩展(`createSpiderAttributes`/构造,yarn 文档确认、把握高)+ 两套基岩 geo 能否被 GeckoLib 渲(同龙格式)。沙箱编不了 Fabric 故标。
- **遗留**:Stage2 = 精英/BOSS 怎么刷出(接 `EliteHandler`/`MobBossHandler` 或新刷怪 handler)、BOSS 血条、把模型自带的 stab/roar/smash 等攻击动画按 AI 触发——均待 Stage1 build 绿 + 实机确认模型动画对了再做。
- **新增 8 文件**(2 实体 + 2 模型 + 2 渲染器 + 资源 6 个)+ 改 `ModEntities`/`YongyeClient`/`ModCommands`,**configVersion 不变(仍 15)**。

## 里程碑 168 — 热修 DragonAttackGoal 适配 1.21 近战 AI + debug 菜单新怪召唤分组(补记)
- MC 1.21 移除了 `MeleeAttackGoal.getSquaredMaxAttackDistance(LivingEntity)`,m166 的覆盖点编译报错;改为覆盖 `canAttack(LivingEntity)` 返 `squaredDistanceTo <= reach² + target.getWidth()`,逻辑等价。
- debug 菜单「刷怪」页新增「新怪召唤」分组:末影龙 BOSS / 精英·毒液蜘蛛 / BOSS·红蜘蛛 三按钮(等同手敲 `/yongye dragon|venomspider|redspider`)。
- 两笔已随 `cc58899` / `b0b7ef7` 推送,本条为补记(当轮沙箱在写 DEVLOG 前重置)。**作者 build 报错点只剩该覆盖方法 = m167 全量其余代码已过编译**,m162-167 各「待编译验证」点基本可视为清零。
- 改 2 文件(DragonAttackGoal / DebugScreen),无配置变更,**configVersion 不变(仍 15)**。

## 里程碑 169 — 新增 BOSS·浴火凤凰(GeckoLib,Stage1:召唤 + 渲染 + 血条 + 基础动画)
- **需求**:用户传 `二.zip`(基岩 geo 1.12.0 + 基岩 1.8.0 动画 ×10 + 1024×512 贴图),新增 BOSS。
- **素材**:identifier `geometry.phoenixec` → **`geometry.fire_phoenix`**(照 m167 只改几何名、不动骨骼名);动画 10 条 = flapping / beam / divestart / dive / divestop / eggfold / eggunfold / flappingup / spiralend / firetornado;模型 21 骨,`hitbox` 骨 51×53×51 单位(`"uv": {}` 不渲染,照红蜘蛛保留)→ `dimensions(3.2f, 3.3f)`。
- **实体 `FirePhoenixEntity`**(HostileEntity + GeoEntity):**飞行**照 m165 龙——`FlightMoveControl(this,20,true)` + `setNoGravity(true)` + `BirdNavigation` 裸构造 + `handleFallDamage` 返 false;AI = `DragonAttackGoal`(reach 常量 8,m168 已修 canAttack)+ LookAt/LookAround + Revenge/ActiveTarget;血 650 / 攻 24 / 速 0.3 / 飞速 0.9 / 击退抗 1.0 / 索敌 64。
- **自带金色 BOSS 血条**:原版凋灵同款——`onStartedTrackingBy`/`onStoppedTrackingBy(ServerPlayerEntity)` 增删观众(despawn/换维度/卸载都走停止追踪,不留残条),`tick()` 里自计数器每 10t `setPercent`(钳制写法照 MobBossHandler);血条名走 `getType().getName()`(lang 已补)。
- 注册 `.makeFireImmune()`(浴火:免疫火/岩浆);`/yongye phoenix` 召唤(出生头顶 +6 格,飞行 BOSS 不卡地);debug 刷怪页按钮;渲染器 shadowRadius 2.2;动画控制器恒循环 flapping(飞行生物无站桩 idle)。
- **lang**:补 `entity.yongye.fire_phoenix`,并顺带补齐龙 / 双蜘蛛缺失的实体名条目(此前血条 / 死亡讯息会显示裸翻译键)。
- **待编译验证(本轮)**:`onStartedTrackingBy` / `onStoppedTrackingBy` / `tick()` 覆盖、`EntityType.Builder.makeFireImmune()`——均已在 FabricMC/yarn 1.21.1 官方 mapping 逐条查到(method_5837 / method_5742 / method_19947),沙箱编不了 Fabric 故标,把握高。
- **遗留(Stage2)**:beam / dive 三段 / firetornado / eggfold(浴火重生:濒死收蛋→无敌→重生半血一次)按 AI 触发;自然刷怪;与 MobBossHandler 0.8% 二次 BOSS 化的豁免。
- 新增 3 Java(实体/模型/渲染器)+ 3 资源 + 改 ModEntities/YongyeClient/ModCommands/DebugScreen/双语 lang,**configVersion 不变(仍 15)**。

## 里程碑 170 — 新增 BOSS·死亡法师 + 精英·巨型螃蟹(GeckoLib,Stage1)
- **需求**:用户传 `死亡法师.zip` / `巨型螃蟹.zip` 继续加怪;分配照 m167 数据法——死亡法师 15 动画(idle/walk/fall/hurt×2/cast×3/attack×3/attackmelee×2/shockwave1/death)→ **BOSS**,巨型螃蟹 9 动画(idle/walk/snip/threeslam/toss/death/crabrave 等)→ **精英**。
- **坑**:两模型 identifier 分别是 **`geometry.steve` / `geometry.unknown`**(比 m167 撞名更危险的通用默认名)→ 装入改 `geometry.death_mage` / `geometry.giant_crab`。
- 死亡法师贴图 512² 但 geo 声明 128²(4× 同布局导出);GeckoLib 按声明尺寸归一化 UV,无需改——实机若 UV 错位再处理。
- **`DeathMageEntity`**:HostileEntity + GeoEntity,Stage1 近战(MeleeAttackGoal + WanderAroundFarGoal + LookAt/LookAround + Revenge/ActiveTarget,照 m162 龙 Stage1 AI 组);血 500 / 攻 20 / 速 0.3 / 击退抗 0.6 / 索敌 48;**紫色 ServerBossBar**(与凤凰同款凋灵挂法);dims 1.0×2.2(人形,整体包围盒 2.3 含法袍/手臂)。**`GiantCrabEntity`**:同结构、无血条(精英定位同毒液蜘蛛);血 120 / 攻 12 / 速 0.28 / 击退抗 0.5 / 索敌 32;dims 3.0×1.5(`hitbox` 骨 44×24×48)。
- `/yongye deathmage`、`/yongye giantcrab` + debug 按钮;渲染器 shadowRadius 0.8 / 1.4;lang 双语补两实体名;动画控制器 = isMoving→walk 否则 idle(两模型的 idle/walk 文件里本就 loop:true)。
- **待编译验证(本轮)**:`WanderAroundFarGoal`(FabricMC/yarn 1.21.1 官方 mapping 已核 class_1394;原版通用游荡 goal,m162 曾用、m165 撤,当前树首用故标)+ 血条覆盖点同 m169。
- **遗留(Stage2)**:法师 cast/shockwave、螃蟹 snip/threeslam/toss 按 AI 触发;death 动画播完再移除;刷怪接入(精英接 EliteHandler?BOSS 接哪条线待定)。
- 新增 6 Java + 6 资源 + 改同 m169 四文件与双语 lang,**configVersion 不变(仍 15)**。

## 里程碑 171 — 暂存 DragonCore·阿努比斯 BOSS 资源包进仓库(防沙箱丢失)
- 用户传 `DragonCore.zip`(1.2MB / 129 文件)= **完整阿努比斯多形态 BOSS 资源包**:主体 33 骨 14 动画(idle/sitting/get_up/walk/run/death/melee1-3/spell1-2/stun…)+ 二形态(512²)+ 恶灵小怪(4 动画)+ 水晶塔(+出生/射线)+ 恢复血量/环绕鬼火/踩踏特效/二形态斩等特效模型 + 49 段 ogg 音效 + `controller.yml`(DragonCore 触发配置,Fabric 不能用但可当动画/音效触发说明书)。
- 沙箱每轮清空 → 本轮先**原样存进 `docs/staging/dragoncore_anubis/`**(含 `说明.md` 盘点),未实装、不进 mod 资源路径、不影响 build。
- 注意:所有 geo identifier 均为 `geometry.unknown` / `geometry.lr_* - Converted`,装入时必须逐个改名(steve/unknown 撞名教训 ×3)。
- 待与作者对齐玩法(多阶段 BOSS?恶灵当召唤物?水晶塔当场景机制?)后,照凤凰/死亡法师的 Stage 分法实装。
- 纯资源暂存,无代码/配置改动,**configVersion 不变(仍 15)**。

## 里程碑 172 — 实装 BOSS·阿努比斯(GeckoLib,Stage1:召唤 + 渲染 + 血条 + 基础动画)
- 用户重传 `DragonCore.zip` 并说「继续」→ 把 m171 暂存的阿努比斯包开工实装;玩法尚未与作者对齐,先按既定套路做 Stage1(主体形态当地面 BOSS),多阶段设计留 Stage2。
- **素材**(取自仓库 `docs/staging/dragoncore_anubis/`,原件不动):identifier `geometry.unknown` → **`geometry.anubis`**;主体 33 骨 / 256²(声明=实际)/ 14 动画(idle, sitting, get_up, walk, run, death, melee1-3, spell1-2, stun, stun_hit, rage);`hitbox` 骨是**纯 pivot 无 cubes**(不能当尺寸用)→ dims 取整体包围盒 宽 2.44 / 高 6.43 → `dimensions(2.5f, 6.4f)`。
- **`AnubisEntity`**:与 m170 死亡法师逐字同模板(近战 Stage1 AI 组 + 凋灵同款血条挂法 + isMoving 切 idle/walk);血 800 / 攻 28 / 速 0.3 / 击退抗 1.0 / 索敌 64;血条 = **蓝条金字**(埃及蓝金,`BossBar.Color.BLUE` + `Formatting.GOLD`)。
- `/yongye anubis` + debug 刷怪页按钮 + 渲染器 shadowRadius 1.6 + lang 双语。
- 本轮**零新 API 面**:新文件 import 与覆盖点全部与 m169/m170 已在树的代码逐字一致(静态自检脚本核过),编译风险仅剩 m169/m170 那批共同的「待编译验证」点。
- **遗留(Stage2)**:开场 sitting(坐姿雕像)→ get_up 起身、追击切 run、melee1-3 / spell1-2 / stun+stun_hit / rage 按 AI 触发、death 播完再移除;**二形态**(32 骨与主体 32/32 同名 → 可直接共用主体动画文件,换 geo+贴图即可)与阶段切换;召唤恶灵;水晶塔场景机制;49 段 ogg 接 sounds.json(需首次注册 SoundEvent,新 API 面,单独立项)。
- 新增 3 Java + 3 资源 + 改 ModEntities/YongyeClient/ModCommands/DebugScreen/双语 lang,**configVersion 不变(仍 15)**。

## 里程碑 173 — 实装小怪·阿努比斯恶灵(未来当阿努比斯召唤物)
- 同包木乃伊小怪:19 骨 / 64²(声明=实际)/ 4 动画(idle, walk, attack1, attack2)+ 单独的「恶灵出生」动画文件(留作召唤登场演出);identifier → `geometry.anubis_wraith`。
- **大坑(已写进实体类注释)**:模型几何建在原点**下方约 2 格**(y ∈ [-2.04, -0.06]),靠每条动画里 torso 根骨 position +29~31 单位抬回地面(walk 的 29↔31 起伏 = 悬浮感)。所以 ① 动画控制器必须**常驻**播 idle/walk,没动画在播就整只沉进地里;② 千万别去「修正」geo 的 Y 坐标,会和动画位移叠加飞到天上。静止时脚底比地面低约 0.1 格,当幽浮效果,违和再调。
- **`AnubisWraithEntity`**:召唤物级(血 40 / 攻 8 / 速 0.35 / 索敌 24,无血条);dims 1.0×1.9(视觉高度);`/yongye wraith` + debug 按钮 + shadowRadius 0.6 + lang 双语。
- **遗留**:attack1/2 按 AI 触发、出生动画当召唤演出、由阿努比斯 Stage2 召唤接线、要不要改成会飘(现走地面)。
- 新增 3 Java + 3 资源 + 改同 m172 四文件与双语 lang,**configVersion 不变(仍 15)**。

## 里程碑 174 — 阿努比斯 Stage2:狂怒系统 + 法术 AoE + 恶灵召唤 + 更丰富动画
- **需求**:用户说「继续」,依 m173 遗留说明推进阿努比斯 Stage2(玩法阶段化 + 召唤机制)。
- **动画状态机(纯同步数据推断,无 DataTracker)**:
  满血且静止 → `sitting`(刚生成雕像感);
  死亡 → `death`(thenPlayAndHold 定格);
  移动时恒为 `run`(大型 BOSS 奔跑压迫感);
  狂怒(<50% HP)且静止 → `rage` 循环;
  其余 → `idle`。
  Stage3 可用 DataTracker byte 加入 melee1-3/spell1-2 精确动画同步。
- **狂怒触发(`triggerRage`,一次性)**:
  HP 首降到 `anubisRageHealthThreshold`(默 0.5)阈值以下触发:
  ① 属性提升:速度 0.3→0.45、攻击 28→40;
  ② 血条改红(`bossBar.setColor(BossBar.Color.RED)`,**待编译验证**:仓库首次 setColor 调用,标准 API);
  ③ 14 格内玩家 AoE 击退+失明(60t)+EntityVelocityUpdateS2CPacket 同步(照 m152);
  ④ 大烟雾+火焰粒子效果;
  ⑤ 全服红字粗体播报「阿努比斯陷入狂怒」。
- **法术 AoE(`castSpell`)**:每 `anubisSpellCooldownTicks`(默 300t=15s)在有目标时施放;
  魔法 AoE(半径 `anubisSpellRadius`=6、伤害 `anubisSpellDamage`=30)+ 爆炸/暴击粒子;
  狂怒后冷却减半(最短 60t);spell1/spell2 按 `spellIndex` 轮换(动画 Stage3 再接)。
- **恶灵召唤(`summonWraiths`)**:每 `anubisSummonCooldownTicks`(默 600t=30s)在 HP<`anubisSummonHealthThreshold`(0.75)时召唤;
  先统计 32 格内已有恶灵,补足至 `anubisMaxWraiths`(4)上限;
  环绕 3.5 格半径等间隔出生 + 继承攻击目标 + PORTAL 粒子。
- **静态自检**:AnubisEntity {}30/30·()212/212 配平;YongyeConfig {}33/33·()配平;
  AnubisEntity 新引用 import 全部与仓库已用代码逐条核对(`StatusEffectInstance`/`StatusEffects`←ClassSkillHandler、`EntityVelocityUpdateS2CPacket`←PursuitHandler、`ParticleTypes`←NightfallWeatherHandler、`ServerWorld`←NightfallHordeHandler、`Text`←WorldDoomManager、`Box`←NightfallHordeHandler,全 ✓);7 个新配置字段定义↔引用一致。
- **待编译验证**:`bossBar.setColor(BossBar.Color.RED)`(仓库首次 setColor 调用;ServerBossBar 标准方法,把握高;其余全仓库 proven API)。
- 改 2 文件:`AnubisEntity.java`(Stage2 全量重写)、`YongyeConfig.java`(+7 字段)**configVersion 15→16**。

## 里程碑 175 — 阿努比斯改第 10 天起自然降临 + 属性大幅拔高 + 自定义 BOSS 豁免二次 BOSS 化/精英化(检查产出)
- **需求**:用户「这个 BOSS 应该是十天后才会出现的。属性应该很高。你做检查吧」。
- **① 自然降临(新 `AnubisSpawnHandler`)**:逐字照 m165 `WildDragonSpawnHandler` 模板——
  `ServerTickEvents` 每 `anubisCheckIntervalTicks`(默 6000=5min)检定;
  全服存活数 ≥`anubisMaxAlive`(默 1)跳过(复用 m155 proven 的 getWorlds()/iterateEntities());
  收集 生存/冒险 + `ProgressionManager.gameDay ≥ anubisMinDay`(默 **10**)的玩家,
  按 `anubisSpawnChance`(默 0.03)全服一次检定;中则在随机合法玩家附近 12~24 格
  **地表**(`getTopY(Heightmap.Type.WORLD_SURFACE)`,PainBossHandler/尸潮同款 proven 写法,
  与龙的高空生成不同——阿努比斯是地面 BOSS)刷出 + `setTarget(player)` 出生即锁定 +
  全服金字粗体播报「黄沙翻涌——永恒的裁判者·阿努比斯降临人间」。`Yongye.java` 挂载注册。
- **② 属性大幅拔高**(`createAnubisAttributes`):血 800→**8000**、攻 28→**80**(提为常量 `BASE_ATTACK`)、
  新增 **护甲 20 + 韧性 10**(`GENERIC_ARMOR`/`GENERIC_ARMOR_TOUGHNESS`,TankShieldItem 等 proven),
  击退抗 1.0/索敌 64 不变;狂怒后攻击同步改 `BASE_ATTACK×1.5`=120(原硬编码 40 作废)、速度 0.3→0.45 不变;
  法术伤害默认 30→45。定位=明显高于凤凰(650/24)/死亡法师(500/20)一个档位的顶级世界 BOSS;
  出生还会再吃 MobEnhancementHandler 进度缩放 + DynamicScaling 玩家攻击对位(只增不减),
  后期不会被玩家百万攻秒杀——这两层缩放**有意保留**不豁免。
- **③ 检查产出:自定义 BOSS 豁免**(m169 遗留豁免本轮落地):
  检查发现 `MobBossHandler`(0.8% BOSS 化)与 `EliteHandler`(精英化)的 ENTITY_LOAD 门
  只判 `instanceof Monster`——yongye 全部自定义实体(阿努比斯/凤凰/法师/龙/双蜘蛛/螃蟹/恶灵)
  都会中招:BOSS 化=叠第二根红血条+改名【BOSS】与自带蓝金血条打架,精英化=光环/皮肤/词缀全不兼容。
  修法=两处门各加一行 `Yongye.MOD_ID.equals(Registries.ENTITY_TYPE.getId(mob.getType()).getNamespace())` 则 return
  (`Registries.ENTITY_TYPE` ModEntities proven、`getId().getNamespace()` LootMagnetHandler proven,全限定名免 import 变更)。
  连带效果:阿努比斯召的恶灵也不会被精英化。
- **静态自检**:6 文件括号全配平(AnubisSpawnHandler {}8·()77 / AnubisEntity {}30·()216 /
  YongyeConfig {}33·()361 / Yongye {}6·()94 / MobBossHandler {}25·()181 / EliteHandler {}62·()518);
  AnubisSpawnHandler 全部 import 逐条与 WildDragonSpawnHandler/NightfallHordeHandler 比对 ✓;
  5 新配置字段定义↔引用一致;BASE_ATTACK 定义↔引用一致,旧硬编码值残留 0;
  EliteHandler 豁免确认落在 ENTITY_LOAD 概率门(非 makeNearbyElite 测试命令处)。
- **无新「待编译验证」点**:本轮全部 API(getTopY/Heightmap/iterateEntities/getWorlds/broadcast/
  Registries.ENTITY_TYPE.getId/getNamespace/GENERIC_ARMOR/GENERIC_ARMOR_TOUGHNESS/setBaseValue)均仓库已编过用法。
  (m174 的 `bossBar.setColor` 待验证项仍在,待作者本轮一起 build。)
- **注意**:`/yongye anubis` 命令召唤不受天数门槛限制(测试用途保留);天数门槛只管自然降临。
- 新增 1 文件(AnubisSpawnHandler)+ 改 5 文件(AnubisEntity/YongyeConfig(+5 字段)/Yongye/MobBossHandler/EliteHandler),
  **configVersion 16→17**。

## 里程碑 176 — 五只新怪自然刷怪接入(收 m167/m169/m170 的「刷怪接入」遗留)
- **需求**:用户「继续做别的」→ 按守则挑收益最大能闭环的方向:毒液蜘蛛/红蜘蛛/浴火凤凰/死亡法师/巨型螃蟹
  五只至今只能命令召唤,游戏里根本不会出现——本轮统一接入自然刷怪,买的模型真正进玩法。
- **新 `CustomMobSpawnHandler`**,单 ServerTick 监听、每 `customMobCheckIntervalTicks`(默 1200=1min)一个节拍,两条线:
  - **BOSS 线**(`rollBoss`,照 m175 阿努比斯模板逐字):红蜘蛛(第12天/0.012/地表)、
    死亡法师(第14天/0.010/地表)、浴火凤凰(第16天/0.008/**玩家上方24格高空**,飞行 BOSS 照 m165 龙的 clamp topY-4 写法);
    各自全服存活上限(默 1)+ 天数门槛 + 全服一次概率检定 + 出生锁定随机合法玩家 + 各自配色的全服播报。
  - **精英线**(`rollElite`):毒液蜘蛛(第4天/0.06/附近上限2)、巨型螃蟹(第6天/0.05/附近上限1);
    逐玩家独立检定,落点=玩家附近 14~28 格地表;附近 48 格同类上限防扎堆;
    **过 m153 全局敌对预算闸**(globalMaxHostilesNearby/globalHostileRadius,尸潮实体爆炸教训,表达式逐字复用)。
- **精英白嫖掉落/经验**:精英线出生打 `IS_ELITE` 附件——检查确认 LootHandler 精英掉落档(line 112)与
  BonusXpHandler 精英经验档(line 33)都只读该附件;而 EliteHandler 的 ELITES 追踪(光环/技能/词缀)
  因 m175 命名空间豁免排在 IS_ELITE 恢复分支**之前**,不会挂上不兼容的原版精英包装。
  两只精英从此有真·精英掉落,不再白打。BOSS 三只暂无专属掉落表(见遗留)。
- **实体工厂**:私有 `Factory` 接口直接走各实体 proven 构造器
  (与 ModCommands 召唤处同签名,ModCommands 用全限定名写法、m167/m169/m170 已随作者 build 验证),
  刻意规避 `EntityType.create` 的跨版本签名差异。
- **静态自检**:3 文件括号全配平(CustomMobSpawnHandler {}11·()145 / YongyeConfig {}33·()365 / Yongye {}6·()96);
  17 个新配置字段 + 2 个复用字段定义↔引用全一致;
  23 条 import 逐条与 AnubisSpawnHandler/NightfallHordeHandler/MobBossHandler/EliteHandler 比对全 ✓;
  5 个实体构造器签名与实体类声明逐字核对(SpiderEntity 泛型界满足)。
- **无新「待编译验证」点**:全部 API(iterateEntities/getWorlds/getTopY/Heightmap/getEntitiesByClass/
  setAttached/setTarget/broadcast/getBoundingBox().expand)均仓库已编过用法;
  `Class.isInstance` 为标准 Java。
- **遗留**:三只 BOSS 的专属掉落表(接 BossHandler 档或单独表,涉及 IS_BOSS 会连带 BossAbilityHandler
  能力叠加,需与作者对齐再做);凤凰浴火重生/法师施法/螃蟹钳击等技能动画按 AI 触发(各 Stage2);
  蜘蛛系是否也进夜晚尸潮池。
- 新增 1 文件(CustomMobSpawnHandler)+ 改 YongyeConfig(+17 字段)/Yongye(注册),**configVersion 17→18**。

### 编译验证记录(m174~m176 全量,含 m169/m170 残留项)
- 作者本地 `./gradlew build` **BUILD SUCCESSFUL** ✅(Fabric Loom 1.7.4,1m37s),仅剩「使用或覆盖了已过时的 API」警告(= m163 已知的 GeoModel deprecated 警告,不阻断、无需处理)。
- 至此以下「待编译验证」点**全部清零**:
  m169(onStartedTrackingBy / onStoppedTrackingBy / tick() 覆盖 / makeFireImmune)、
  m170(WanderAroundFarGoal)、m172/m173(与 m169/m170 共享点)、
  m174(bossBar.setColor);m175/m176 本就零新 API 面。
- **余下均为实机验证项**:阿努比斯狂怒(血条变红/提速/AoE 击退)/法术粒子/恶灵召唤/坐姿动画、
  阿努比斯与五只新怪的自然刷怪(天数门槛/播报/凤凰高空出生)、毒蛛/螃蟹的精英级掉落与经验、
  自定义怪不再被二次 BOSS 化/精英化。

## 里程碑 177 — BOSS 血条 UI 玻璃化(纯资源覆盖,零代码)
- **需求**:用户嫌原版 BOSS 血条(阿努比斯蓝条/凤凰黄条截图)难看,问「怎么写、可以用图片吗、可以用 GPT 生图吗」。
- **原理**:1.21.1 的 BOSS 血条是纯 GUI 精灵贴图——
  `assets/minecraft/textures/gui/sprites/boss_bar/{颜色}_background.png`(底槽)+ `{颜色}_progress.png`(填充),
  基准 182×5、支持高清(渲染按名义尺寸比例取 UV,进度按比例横向裁切,资源包 HD 血条即此机制);
  mod jar 资源盖过原版(m136/m137 背包/快捷栏同款套路),**零 Java、零 mixin、零 build 风险**。
- **本轮**:程序化(PIL)画一套**玻璃质感高清血条**(8×=1456×40),对齐 m142 玻璃 HUD 风格——
  填充=顶部白高光渐隐 + 上亮下暗三段渐变 + 光核细线 + 底暗缘 + 黑描边圆角;
  底槽=暗色玻璃凹槽(顶部内阴影渐变)+ 下缘微反光 + 半透明(alpha 210)。
  **7 色全套 14 张**:蓝(阿努比斯)/黄(凤凰)/红(怪物BOSS版+阿努比斯狂怒)/紫(死亡法师)/
  绿/粉/白(原版凋灵=紫、末影龙=粉、袭击=红等也一并统一风格)。
- **坑(本轮踩过)**:PIL 的 `ImageDraw` 画半透明色是**替换像素不是 alpha 叠加**——第一版把高光/斜纹直接画在条上,
  低透明白线把底色替换成近透明 → 显示成深色条纹一团糟;修法=装饰层画在独立透明图层再 `Image.alpha_composite` 合成。
  已写进本条防重犯(m160 贴图经验同源:程序生成后必须肉眼复核预览)。
- **预览**:`docs/hud/m177_bossbar_preview.png`(7 色 70% 血量 + 1 根 25% 残血,模拟实机裁切;已肉眼复核)。
- **圆角说明**:进度条中途裁切时右端为直角(原版同此),满血/空血两端圆角完整,可接受。
- **GPT 生图路线(备选)**:血条是 36:1 极端长条 + 右端任意裁切,GPT 直出效果差;
  若要美术版,按规格出图覆盖同名文件即可——每色两张、1456×40(或任意 182:5 比例)、
  横向纹理均匀(勿在条身放会被裁切破坏的图案/文字)、background 暗 progress 亮。
- **待实机验证**:HD 精灵在 BossBarHud 的比例映射观感(机制为资源包通行做法,把握高;若显示异常把截图发来调)。
- 纯资源:新增 14 张 PNG + 1 张预览,无 Java/配置改动,**configVersion 不变(仍 18)**。

## 里程碑 178 — 阿努比斯/凤凰专属华丽血条画框(用户 GPT 图实装,客户端 mixin)
- **需求**:用户嫌血条难看,GPT 生成两条像素风画框(胡狼首紫焰石框 + 凤首金翼火焰框,已抠透明)并上传,要求用上。
- **判断**:这种带装饰头/BOSS 牌匾的画框**不能走 m177 的精灵覆盖**——精灵是 182×5 纯条、按颜色全局生效、
  装饰会被压扁;必须自定义渲染 = 拦 `BossBarHud.renderBossBar`(单条血条的底槽+填充绘制)按条识别绘制。
- **素材加工(PIL,量取靠网格图人工读坐标)**:每条拆两张——
  ① `*_frame.png` = 整框,槽内替换成 亮度×0.25 的熄灭暗色(天然空血底槽),**牌匾上烘焙死的「BOSS」金字抹掉**
  (逐行取字左侧 8px 牌匾像素横向延展,保留竖向明暗不出色块;第一版平涂色块+bbox 量窄露「SS」已修);
  ② `*_fill.png` = 槽内岩浆/火焰截条。**全部预缩放到目标 GUI 像素**(槽宽统一 182=原版等长):
  anubis_frame 262×57(槽 40,30 高12 / 牌匾cy22)+ fill 182×12;phoenix_frame 286×62(槽 52,31 高15 / 牌匾cy25)+ fill 182×15。
  预缩放的意义 = 渲染全程只用仓库 proven 的 **9 参 drawTexture** 1:1 画,绕开 11 参缩放签名风险。
- **新 mixin `client/BossBarStyleMixin`**:@Inject HEAD cancellable 拦 renderBossBar(DrawContext,int,int,BossBar);
  按血条名**翻译键**识别(服务端名=getType().getName() 序列化后仍是 translatable,键 entity.yongye.anubis /
  fire_phoenix,与客户端语言无关);命中→画框(中心=x+91 对齐)→按 getPercent() 裁填充→cancel。
  **原版名字文本不拦**:画框垂直定位取 fy0=y-4-牌匾cy,让空牌匾中心正对名字行(y-9 起高 9)——
  金色实体名正好落牌匾上当名字牌(预览已模拟复核)。`require=0`:目标方法若名字/签名不符则静默不挂,
  血条回退 m177 玻璃条不崩游戏。法师/BOSS版/原版血条不受影响(键不匹配直接放行)。
- **待编译/启动验证**:`BossBarHud`(client.gui.hud 包,同 InGameHud proven 路径,类名首用)、
  `TranslatableTextContent.getKey()`(标准 yarn 首用)、@Inject 目标 renderBossBar 描述符(启动日志看是否挂上;
  没挂=回退玻璃条,把日志贴来改描述符)。BossBar/DrawContext/Identifier/drawTexture 9 参全 proven。
- **已知取舍**:进度中途裁切右端直角(原版同);两 BOSS 同屏时原版堆叠步进 19px 小于框高,框体轻微交叠(稀有,违和再议)。
- **预览**:docs/hud/m178_custom_bossbar_preview.png(70%/25% 血量 + 名字落位模拟,已肉眼复核)。
- 新增 1 mixin + 4 贴图 + 改 yongye.mixins.json,无配置变更 **configVersion 不变(仍 18)**。

## 里程碑 179 — BOSS 血条布局接管:修重叠 + 自动降档缩放 + 新增末影龙/苦力怕画框(重写 m178)
- **需求**:实机截图两只 BOSS 同屏画框严重交叠、首条框顶被屏幕裁切;新给两张框图——图2 龙首魔能框=末影龙、
  图3 苦力怕酸浊框;要求「自动设计高度、怪太多自动调整大小」。
- **根因**:m178 只拦单条绘制,堆叠游标仍走原版 `+19px/条`,而画框高 57~62px → 必叠;
  且首条框顶 `y-4-牌匾cy` 为负 → 出屏。
- **修法 = 接管整个 render**:
  - 新 `client/BossBarHudAccess`(@Accessor bossBars + @Invoker renderBossBar,照仓库既有
    ClampedEntityAttributeAccessor / EntityFlagInvoker 范式);成员名已用 FabricMC 官方 yarn 1.21 javadoc
    逐字核对(`private void renderBossBar(DrawContext,int,int,BossBar)` 两重载之一 + `bossBars` 字段);
    **名字若不符会在本地 build 期被 mixin AP 直接报错,不会带病进游戏**。
  - `BossBarStyleMixin` 重写:@Inject render HEAD cancellable(require=0,不挂整套回退原版不崩);
    无画框条→提前 return 走原版零开销;有→cancel 自排:画框条按**真实框高+11px** 推进游标(修重叠),
    首条 `j=max(j, 牌匾cy+6)` 防顶裁,原版条(法师紫条/任务/凋灵…)调 @Invoker 照原版画+原版 19px 步进,
    名字文本全部由本 mixin 按原版位置画(正落空牌匾);上限从 1/3 屏放宽到 1/2 屏。
  - **自动降档**:按当前画框条数选档——≤2 大(槽宽182=原版等长)/ 3~4 中(136)/ ≥5 小(100);
    **三档贴图全部预缩放到 GUI 像素**(4 BOSS×3 档×2 张=24 PNG,替换 m178 旧 4 张),
    渲染全程仍只用 proven 9 参 drawTexture 1:1。
- **新画框**:龙框(art 2111×598,槽 x[470,1645] y[306,412],空牌匾 cy190)→ 匹配
  `entity.yongye.toro_ender_dragon` **和** `entity.minecraft.ender_dragon`(原版末地龙战血条名=龙实体名);
  苦力怕框(art 1720×390,槽 x[275,1405] y[150,240],cy140)→ 匹配**字面量**名
  「【BOSS】」前缀 / 「 BOSS」后缀 = MobBossHandler 的怪物BOSS版血条(含玩家皮肤 BOSS);
  识别顺序 = 翻译键优先(「末影龙 BOSS」的 lang 值以 " BOSS" 结尾但其键先命中龙框,不串)。
  两新图牌匾本就是空的无需抹字;四图统一做 alpha<45 清边(抠图毛边噪点)。
- **顺手补齐**:`ToroEnderDragonEntity` 一直没血条(m164 只接管了原版龙渲染)——
  本轮加凋灵同款血条块(与凤凰/阿努比斯逐字一致,已编译通过),自建龙从此有龙框血条。
- **测量法**:行像素总量定槽带 + 带内列密度最长连续段定槽宽(避开龙首红眼干扰),
  叠加校验图肉眼复核后人工微调;坐标网格图归档 /home/claude(沙箱会清,量取结果已固化进常量)。
- **待编译/启动验证**:BossBarHud/ClientBossBar/TranslatableTextContent 三个标准 yarn 类首用;
  @Accessor("bossBars")/@Invoker("renderBossBar") 名字(build 期 AP 校验);render 注入点(require=0 兜底)。
  drawTexture 9参/drawTextWithShadow/getScaledWindowWidth 全 proven/标准。
- **预览**:docs/hud/m179_bossbar_preview.png(左=龙/苦力怕大档,右=三 BOSS 同屏中档堆叠模拟,已复核不重叠)。
- 新增 1 accessor + 重写 1 mixin + 24 贴图(删旧4)+ 改 ToroEnderDragonEntity/mixins.json,
  无配置变更 **configVersion 不变(仍 18)**。

## 里程碑 180 — 再接三张专属血条画框:红蜘蛛 / 死亡法师 / 长门·佩恩(顺手补两条缺失血条)
- **需求**:用户再传三张 GPT 画框——蜘蛛首魔能框(→BOSS·红蜘蛛)、紫焰死灵框(→BOSS·死亡法师)、
  佩恩·天道框(→长门 BOSS,Rinnegan+晓袍元素);归属按画面意象判定并在回复中言明,不符可换绑。
- **顺手补两条缺失血条(检查产出)**:
  - **红蜘蛛**:m167 起就是 BOSS 却一直没血条(当时遗留)——补凋灵同款血条块
    (与凤凰/阿努比斯/龙逐字一致,已编译通过),红条,名字键 entity.yongye.red_spider。
  - **长门·佩恩**:同样没血条——PainBossHandler 加 `PAIN_BARS`(HashMap)+ 10 tick 节流内
    `computeIfAbsent` 附挂(名=`Text.literal("佩恩·天道")`)+ `entrySet().removeIf` 一遍完成
    刷新/64 格观众同步/死亡·卸载清理(照 MobBossHandler 同款,clearPlayers/setPercent/
    addPlayer/removePlayer 全 proven);重启后认领已加载长门时血条随 tick 自动重挂。
- **画框接入**(沿用 m179 全套管线):三图各拆 frame(槽内=亮度×0.25 熄灭底槽)+ fill,
  alpha<45 清抠图毛边,三档预缩放(槽宽 182/136/100)= 18 张新 PNG;
  测量=蜘蛛/法师牌匾本就是空的无需抹字,槽位网格图人工量取 + 左右对称校验
  (蜘蛛 art1672×763 槽 x[240,1432] y[257,334] 牌匾cy120;法师 art1729×353 槽 x[300,1430] y[178,268] cy128;
  佩恩 art1706×520 槽 x[395,1390] y[235,358])。
- **佩恩框特殊处理**:图上没有名字牌匾(顶部中央是轮回眼徽记)→ 几何表 pcy=-4,
  名字**悬浮在框顶上方**(天空底+阴影,可读;预览已复核)。
- **匹配规则新增**:翻译键 red_spider / death_mage(排在键判定区);
  字面量 `contains("佩恩")` → 佩恩框,**排在苦力怕的【BOSS】/『 BOSS』规则之前**(佩恩名不含二者本无冲突,防御性排序)。
- **静态自检**:3 改动 Java 括号全配平(mixin {}42·()98 / RedSpider {}11·()47 / PainBossHandler {}54·()451);
  18 张贴图尺寸与几何常量逐一比对一致;血条块与凤凰逐字 proven;佩恩补丁 4 个 bar API 全在
  MobBossHandler proven;lang 两键在。贴图文件名 deathmage2(法师实体资源已占用 death_mage 前缀,避免混淆)。
- **无新 API 面**:本轮零首用类(m179 那批 BossBarHud/TranslatableTextContent/accessor 名待作者 build 一并验)。
- **预览**:docs/hud/m180_bossbar_preview.png(三新框大档 + 名字落位,已复核)。
- 新增 18 贴图 + 改 BossBarStyleMixin/RedSpiderEntity/PainBossHandler,无配置变更 **configVersion 不变(仍 18)**。

## 里程碑 181 — 血条画框全套换用户高清素材:HD 贴图 + 缩放绘制(修「压缩太狠看不清」)+ 新增僵尸框
- **需求**:用户重做并上传全部 8 对画框素材(狗头.zip,每对=框 + 血条同画布对齐分层,已抠图),
  点名「素材别压缩太狠,压缩完都看不清了」。
- **根因**:m179 管线把贴图**预缩放到 GUI 逻辑像素**(大档框宽仅 ~262px)再 1:1 绘制,
  实机 GUI scale 2~4 时被引擎放大 2~4 倍(且 MC 默认最近邻采样)必然糊——压缩发生在管线里,
  素材本身 1700~2200px 宽的细节全被丢掉了。
- **新管线(贴图三件套 × 8)**:
  - 每 boss 三张:`{key}_frame.png`(框,压顶)/ `{key}_fill.png`(血条)/ `{key}_back.png`
    (空槽底 = 血条压暗 ×0.22,掉血露出熄灭槽);槽宽统一钉死 **728px = 大档 182 的 4 倍**
    (GUI scale 4 下 1:1 原生分辨率,scale 2 仅 2× 缩小),LANCZOS 缩放 + **alpha 颜色扩散**
    (不透明像素颜色向透明区扩 8 轮,防双线性采样晕边)。
  - 每张 PNG 配 `.png.mcmeta` `{"texture":{"blur":true,"clamp":true}}` 开双线性过滤
    (原版 mojangstudios.png.mcmeta 同款资源元数据机制),缩小平滑不锯齿。
  - 42 张旧预压贴图删除,换 24 PNG + 24 mcmeta,共 4.5MB。
- **BossBarStyleMixin 重写**:
  - 绘制换 **11 参缩放版 drawTexture**(id,x,y,w,h,u,v,regionW,regionH,texW,texH)——
    已从 FabricMC/yarn 1.21.1 官方 mapping 逐字核对(method_25293,ARG 顺序一致);
    血条按百分比裁 = 贴图区域宽与屏幕宽同比例缩,不变形。
  - 几何改成「贴图像素一份定义 + 档位缩放因子」:屏幕尺寸 = 贴图像素 × (SLOT_W[档]/728),
    三档共用同一套贴图,Geo[3] 数组 ×7 换 Style ×8,常量少一半。
  - **绘制顺序改为 底→血条→框压顶**(顺应用户素材分层:蛛网/中央宝石/佩恩零印等装饰
    本就该盖在血条上;m178~m180 框在底、装饰会被血条盖住)。
  - 牌匾中心逐张放大目检定位(狗头/凤凰自动检测偏低,人工微调到牌匾正中);
    佩恩框顶是轮回眼徽记非牌匾 → 名字仍悬浮框顶(pcy=-1)。
  - **新增僵尸框**:字面量名「xx BOSS」后缀(m145 玩家皮肤僵尸BOSS,本体是僵尸)→ 僵尸框;
    「【BOSS】」前缀(其余怪物BOSS版)→ 苦力怕框——原来两类同用苦力怕框,现在分开。
  - 堆叠/自动降档/require=0 兜底/翻译键识别全承 m179/m180 不变。
- **待编译验证**:11 参 drawTexture 仓库首用(官方 mapping 逐字核对,把握高);
  mcmeta blur 是纯资源机制零编译面。
- **预览**:docs/hud/m181_bossbar_preview.png(GUI scale 3 模拟:新管线三条堆叠 vs
  m180 旧管线同倍率对比,清晰度差距一目了然;名字落位/佩恩悬浮已复核)。
- 重写 1 mixin + 换 48 资源文件(删 42 旧),无 Java 文件增减、无配置变更 **configVersion 不变(仍 18)**。

## 里程碑 182 — 长门/红蜘蛛画框换用户重制素材
- **需求**:用户重画两套(图1/2=长门,图3/4=蜘蛛,同画布对齐分层),m181 管线原样重跑。
- 新蜘蛛框:紧凑横幅(去掉旧版垂坠链饰,框高 568→239 贴图px,省一半屏占),顶部空牌匾
  自动检测+目检 pcy=52(缩放前77);血条变细(sh 54→29)红紫双色电浆。
- 新长门框:血条撑满整个中央槽(高 67)、轮回眼徽记居中框顶、底部零印;仍无名字牌匾
  → 名字继续悬浮框顶(pcy=-1)。
- 几何常量随管线输出更新(SPIDER/PAIN 两行),6 张贴图覆盖;三档/blur/绘制顺序全承 m181 不动。
- **预览**:docs/hud/m182_bossbar_preview.png(scale3 模拟,名字落位/掉血槽底已复核)。
- 改 1 mixin 常量 + 换 6 资源,无配置变更 configVersion 不变(仍 18,与 m183 合并升 19)。

## 里程碑 183 — 末地末影龙终局化(10亿血/三命/脱战回血)+ 末地尸潮增强 + 尸潮排查答疑
- **尸潮排查(用户问「一键永夜2尸潮没来,跟创造有关系吗」)**:有——NightfallHordeHandler
  逐玩家刷怪循环开头就跳过 CREATIVE/SPECTATOR(设计如此:尸潮只围生存/冒险玩家蜂拥,
  管理员开创造调试不会被怪海淹)。切生存/冒险即验,代码无 bug 本轮不改该逻辑。
- **末地末影龙强化(新 EndDragonHandler,应需求)**:
  - **10 亿血+高防**:ENTITY_LOAD 对末地维度 EnderDragonEntity 挂持久属性修饰(照
    MobEnhancementHandler removeModifier+addPersistentModifier 幂等套路):生命补到
    endDragonHealth(默 1.0E9 = m127 属性上限装满)、护甲 +40 / 韧性 +20(近原版减伤 80% 上限,
    「防御你看着办」的落点);首次强化才回满(END_DRAGON_BUFFED 附件门,重载不重复回血);
    原版龙战(水晶回血/龙息/传送门)全保留,m164 换的只是渲染器不冲突。
  - **三条命**:ServerLivingEntityEvents.ALLOW_DEATH 拦死亡(生命归零、死亡处理前回调,
    返 false 取消,回调内满血复活)——已用命数存龙实体持久附件 DRAGON_LIVES_USED,
    < endDragonLives-1 时消耗一命+全服深紫播报剩余命数,最后一命走原版死亡演出;
    水晶重召新龙命数从 0 起。
  - **脱战回血**:每 20t 遍历末地 iterateEntities 找龙,血量对比检测掉血(阈值 0.5;
    10 亿量级 float 步进 ~64 任何有效伤害必触发,m127 已论证),连续 endDragonRegenDelaySeconds
    (默 30)秒没掉血且未满 → 每秒回 endDragonRegenPercent(默 1)% 最大生命(=每秒 1000 万,
    DPS 低于此磨不死,逼持续输出);复活重置计时,map 按存活龙 retainAll 清残留。
  - 只作用末地原版龙;自建 ToroEnderDragonEntity 不受影响。
- **末地尸潮增强(应需求「尸潮末地会增强」)**:NightfallHordeHandler 两处——
  末地目标怪量 ×endHordeTargetMultiplier(默 1.5,仍受 m153 全局预算硬闸);
  末地刷出的怪经 MobEnhancementHandler 新公共方法 applyEndHordeBuff 额外血/攻
  ×endHordeStatMultiplier(默 2.0,固定 ID 幂等叠在常规增强上,照 applyDoom 模板)。
  尸潮本就无维度门(末地也刷,落点走 WORLD_SURFACE 主岛可用),本轮只加增强。
- **待编译验证**:ALLOW_DEATH 仓库首用(同类 ALLOW_DAMAGE 已在 ClassSkillHandler 编过,
  同一事件类的兄弟字段,把握高)+ server.getWorld(World.END)(标准方法首用);
  **待实机验证**:龙死亡拦截语义(龙的死亡演出由自身 DYING 阶段接管,取消死亡+满血后
  isDead=false 应跳过该分支,需实测复活是否流畅)。
- 新增 EndDragonHandler + 改 NightfallHordeHandler/MobEnhancementHandler(+applyEndHordeBuff)/
  ModAttachments(+2)/YongyeConfig(+9 字段)/Yongye(注册)**configVersion 18→19**。


## m184(2026-07-09)BOSS 血条同类合并 ×N + 名字落位全量校准(重写 BossBarStyleMixin 布局层)

**背景**:实机截图两问题——① 5 只 BOSS 同屏 5 根框铺满上半屏;② 名字没落在牌匾上
(小档下尤其明显,字浮在框顶外)。用户点名「BOSS 太多就用一个血条,旁边显示有多少个
什么 BOSS 怪」,并要求所有 BOSS 检查一遍。

**同类合并**(核心新特性):
- 画框条按组键合并:翻译键各自成组(阿努比斯/凤凰/龙/蜘蛛/法师,自建龙与原版龙战两个
  键=两组不跨并),佩恩一组,「【BOSS】」前缀怪物BOSS版一组,「xx BOSS」玩家皮肤BOSS一组。
- 组内 ≥2 只 → 一根条:血量取组内**平均**,牌匾名带「×N」;同名条本无法区分,合并零信息损失。
- 混名组(怪物BOSS版/玩家BOSS)加**成分标注**:框右侧小字(0.8×档位字号,琥珀色
  0xFFCC66)如「僵尸×3 骷髅×2」,>3 种缩略「等N种」;右侧超屏自动换左侧。
- 尺寸档改按**合并后行数**定(≤2大/3~4中/≥5小)——合并让行数=类型数,实战基本停在大/中档。

**名字落位修复**(根因两条):
- ① 文字恒 9px 不随档缩:小档框仅 ~35px 高,9px 字盖到框顶装饰,视觉=浮在框外。
  → 文字按档缩放 1.0/0.85/0.7(MatrixStack push/translate/scale,TitleScreenMixin 同款 proven)。
- ② int 取整逐级累积 + 游标语义借用原版「名字行在条上方」:→ 布局改「框顶=游标」,
  名字以浮点精确对齐牌匾中心 nameCy = fy0 + pcy*s,文字顶 = nameCy - 4.5*ts。
- **8 框牌匾全量刻度尺校准**(逐张贴图加像素尺人工目检):凤凰 89→86、蜘蛛 52→58、
  苦力怕 61→57、僵尸 112→97;阿努比斯 85/龙 105/法师 77 核对无误;佩恩仍无牌匾(-1),
  名字悬浮框顶上方且行内预留名字高度(首条不再顶裁)。

**布局语义简化**:原版条(任务计时/原版凋灵)排在全部画框组之后照原版画法(名字行+
@Invoker 原样条),不再与画框穿插;半屏上限保留。record 私有字段同外部类直取合法
(m181 同款已 build proven);嵌套 static Group 类只是数据壳(Style record 先例)。

**静态自检**:括号三类全配平;yongye$* 符号定义↔引用一致;accessor 签名与调用逐字核对。
**预览**:docs/hud/m184_bossbar_preview.png(左=投诉场景合并后 3 行中档,右=8 类同屏
小档压力测试)已人工复核——零重叠、名字全落牌匾、佩恩悬浮、成分标注在框右。
**待编译验证**:无新 API 面(MatrixStack 缩放/drawTextWithShadow/11参drawTexture 全 proven)。
**待实机验证**:合并条的平均血量观感;成分标注在 GUI scale 4 窄屏下的左右换位。
**改动**:BossBarStyleMixin 重写(156→261 行);无配置变更,configVersion 不变(仍 19)。


## m185(2026-07-09)修「末地末影龙倒着飞」:替换渲染器 yaw 补 180°

**病根**(一处一行,但因果链值得记):原版末影龙是全 MC 唯一一只「朝向反着存」的实体——
它的 bodyYaw 与飞行方向恒差 180°(Notch 的原始龙模型就是反着建的,原版
EnderDragonEntityRenderer 用 rotate(-yaw) 补偿,普通生物是 rotate(180-yaw),二者恰差 180°)。
m164 把原版龙渲染器换成 GeckoLib GeoReplacedEntityRenderer 后,GeckoLib 按普通生物
mulPose(YP, 180f - rotationYaw) 转(已拉 GeckoLib 4.8 branch-1.21.1 官方源码逐字核对
GeoReplacedEntityRenderer#applyRotations),原版龙于是尾巴朝前倒飞。自建龙
ToroEnderDragonEntity 是正常 yaw 语义,走 ToroEnderDragonRenderer,一直没事——所以
只有末地这只原版龙反着。

**修法**:ToroDragonReplaceRenderer 覆写 applyRotations,yaw + 180f 再交父类,恰好抵消。

**待编译验证**:覆写签名 6 参版(animatable=替身对象/MatrixStack/ageInTicks/rotationYaw/
partialTick/nativeScale)——依据 GeckoLib 4.8 源码(5 参版已 @Deprecated),PoseStack 在
yarn 开发环境重映射为 MatrixStack 是 loom 标准行为但本仓库无先例;若报
「method does not override」贴报错即改。
**待实机验证**:①转向/俯冲时机身朝向是否全程正确;②最终死亡演出(第三条命)期间
GeckoLib 会按 deathTime 施加 90° 侧翻(普通生物死亡倒地),与原版龙升天演出叠加的观感
——若违和下轮覆写 getDeathMaxRotation 归零。
**改动**:仅 ToroDragonReplaceRenderer(+1 import +1 覆写);无配置变更,configVersion 仍 19。


## m186(2026-07-09)末地原版末影龙渲染还原默认(替身渲染器退场),属性加强保留

**背景**:m185 修完倒飞后暴露新问题——替身模型翅膀只扇一下(一上一下)就不对了。
GeoReplacedEntityRenderer 全场共用一个替身 GeoAnimatable 实例承载动画状态,替身自身
不 tick、动画进度与原版龙的实体状态对不上,扇翅循环表现异常;继续在替身架构上修
性价比低。作者拍板:**末地原版龙恢复原版模型/动画,自建 BOSS 龙保留 GeckoLib 外观,
末地龙只保留属性加强**。

**改动**(全是减法):
- YongyeClient 删原版 ENDER_DRAGON 的渲染器覆盖注册(m164 引入)——Fabric 不注册即回
  原版 EnderDragonEntityRenderer,模型/扇翅/龙息/死亡升天演出全回原版。
- 删 3 个 Java:ToroDragonReplaceRenderer(含 m185 的 yaw+180 补丁,随架构一起退场)、
  ToroDragonReplacementModel、ToroDragonReplacement(替身 GeoAnimatable)。
- **不动**:EndDragonHandler(m183 的 10亿血/护甲/三命/脱战回血,纯服务端属性挂载,
  与渲染无关照常生效);自建龙 ToroEnderDragonEntity 三件套;血条画框 mixin 里
  entity.minecraft.ender_dragon → 龙框的映射(HUD 样式,与实体模型无关,原版龙战
  照旧套华丽龙框)。toro_ender_dragon 的 geo/贴图/动画资源仍被自建龙使用,保留。

**静态自检**:YongyeClient 括号配平;全仓库 ToroDragonReplace 零残留引用。
**待编译验证**:无(纯删除+注释,零新 API)。
**待实机验证**:末地龙扇翅回原版即为修复;m185 的倒飞问题随原版渲染器一并消失
(原版渲染器自带 -yaw 补偿)。
**改动统计**:Java 166→163;无配置变更,configVersion 仍 19。

## m187(2026-07-09)所有 BOSS 血条加血量数字显示(服务端嵌入 + 客户端解析)

**问题**:血条只显示进度条,看不到实际数字;10亿血的末地龙进度条跌一点点看不出来伤害。

**实现**:协议层「服务端嵌入 + 客户端解析」:
- 服务端(7处):AnubisEntity/FirePhoenixEntity/DeathMageEntity/RedSpiderEntity/
  ToroEnderDragonEntity/MobBossHandler/PainBossHandler 的每 tick 血条刷新处,
  在 setPercent 前调用 setName 把 `‖当前/最大`(Unicode U+2016 分隔,整数)拼进血条名末尾。
- 末地原版龙(EndDragonHandler):新增 `EnderDragonFightAccessor` mixin 暴露
  EnderDragonFight.bossBar 字段(待编译验证);在每秒龙循环里更新血条名。
- 客户端(BossBarStyleMixin):新增 `yongye$parseHp` 解析 ‖ 后缀 → `long[]{cur,max}`;
  `yongye$fmtHp` 格式化为万/亿紧凑单位;`yongye$rawName` 剥离后缀供名字匹配/显示用;
  合并组使用 `yongye$parseGroupHp` 求和后显示「X.X亿 / 10.0亿」金字;
  无 HP 数据的条(原版凋灵等)兜底显示百分比。
- `yongye$styleOf`/`yongye$groupKey`/`yongye$label`/`yongye$annotation` 全部改用
  rawName,避免 ‖ 干扰匹配。

**待编译验证**:`EnderDragonFightAccessor @Accessor("bossBar")` 字段名
(yarn 1.21.1 mapping 表中 EnderDragonFight.bossBar;若报错改查 MCP/官方名)。

**Java 数**:163 → 164 (+1 accessor)。configVersion 不变(无配置字段增删)。

## m188(2026-07-09)m187 全面检查修正(编译阻断 ×1 + 逻辑/观感 ×6 + 文档 ×2)

作者要求「检查一遍」,对 m187 全量复查,查出并修复 9 处:

1. **编译阻断**:FirePhoenixEntity/DeathMageEntity/RedSpiderEntity 的 m187 补丁用了
   `Text.literal` 但三文件没有 Text import(构造器原本不需要)→ build 必报
   cannot find symbol。补 `import net.minecraft.text.Text;`。
2. **名字颜色错**:m187 给 5 实体统一硬编码 Formatting.GOLD,但构造器原色是
   法师 DARK_PURPLE/红蛛 RED/自建龙 LIGHT_PURPLE——每 tick setName 会把原色刷掉。
   改回各自构造器原色(阿努比斯/凤凰本来就是 GOLD 不动)。
3. **HP 数字与牌匾名重叠**:m187 把数字画在 nameCy+4ts,而名字占 nameCy±4.5ts,
   两行文字叠约 4ts 高度。改成画在**血条槽正中**(slotCx/slotCy,MMO 惯例),
   与牌匾名字彻底分离;颜色金→白(金字在凤凰金色填充上对比度不足)。
4. **原版条数字半悬**:画在 j+11 悬在条下沿。改覆画条正中(j+7 上下对称跨条),
   行距还原 26。
5. **玩家BOSS 匹配还原精确语义**:m187 把 endsWith(" BOSS") 改成了 contains
   (为兼容 ‖ 后缀),但匹配输入本就是剥过后缀的 rawName,contains 反而会误伤
   「xx BOSS yy」类名字。还原 endsWith(styleOf + groupKey 两处)。
6. **Locale**:fmtHp 的 String.format 补 Locale.ROOT,防区域设置把小数点渲成逗号。
7. **末地龙数字与回血配置耦合**:名字更新写在了 `regenPercent <= 0 早退`之后,
   作者关回血则血量数字也消失。早退条件收窄到只看 enableEndDragonBuff,
   regenPercent 判断下沉进回血块。
8. **DEVLOG 条目错位**:m187 条目插在了 m186 前面(本文件时间正序应在末尾)且
   日期写成 2025。挪正 + 修头。
9. **HANDOVER 没更新成**:m187 收尾用的正则没匹配到实际行文(静默失败),
   0.5 指针停在 m186、代码量行停在 163。本轮按实际行文更新。

无新 API 面;m187 的待编译验证点不变(EnderDragonFightAccessor 字段名 +
ServerBossBar#setName)。Java 数 164 不变。configVersion 不变(仍 19)。

## 里程碑 189 — 怪物伤害来源检测:外模组伤害不作数,只认原版 + 永夜武器
- **需求**(作者原话):「新增一个怪物伤害检测。如果是别的MOD造成的伤害不作数。只认原版和永夜MOD的武器造成的伤害」。
- 新 `ForeignDamageFilterHandler`,挂 `ServerLivingEntityEvents.ALLOW_DAMAGE`(挂载写法照 ClassSkillHandler 既有用法):
  - 只对**怪物**(`instanceof Monster`,判法同 LootHandler/MobEnhancementHandler)生效;玩家/动物/村民不受影响。
  - **无攻击者**的环境伤害(摔落/岩浆/药水残留/`/kill`)一律放行——原版机制不动。
  - **玩家出手**:看造成伤害的武器命名空间——1.21 `source.getWeaponStack()` 伤害源自带武器栈(近战/弹射物都填),拿不到就兜底主手;空手 = `minecraft:air` 照常有效。非 `minecraft`/`yongye`/白名单 → **伤害整个取消** + action bar 提示「外来模组武器对怪物无效」(可关)。
  - **非玩家攻击者**(外模组召唤物/宠物/炮塔):看攻击者实体类型命名空间(写法逐字同 EliteHandler/MobBossHandler 的自家怪判定)。原版狼/铁傀儡/怪物内斗不受影响。
- 配置 +3,**configVersion 19→20**:`enableForeignDamageFilter`(默认开)/ `foreignDamageFilterHint`(默认开)/ `foreignDamageFilterExtraNamespaces`(逗号分隔额外放行命名空间,默认空)。
- **有意取舍(已写进类注释)**:手持外模组武器期间,职业技能反伤这类「借玩家名义」的伤害同样判无效(伤害源武器=主手,与「拿外模组武器这刀不算」一致);借玩家名义、空手也能打伤害的外模组法术会被当空手放行——要堵死得再查伤害类型命名空间,等实测确有需要再加。
- **待编译验证**:仅 `DamageSource.getWeaponStack()`(FabricMC/yarn 1.21.1 官方 mapping 已核 = method_60948,仓库首用);其余 import 与调用全部有在树先例(静态自检脚本逐条核过)。
- 改 3 文件:新 handler + YongyeConfig(字段+版本)+ Yongye 挂载。

## 里程碑 190 — 外模组武器打怪:怪物开口嘲讽(m189 续,风味文案)
- **需求**(作者原话):「如果发现用别的MOD的武器打怪,怪物就会说『哎呦喂,您拿前朝的剑,斩本朝的官?』可以多写一些文案」。
- 挂在 m189 `ForeignDamageFilterHandler` 玩家分支上:这一刀因外模组武器被判无效时,**怪物在聊天栏对攻击者说话**——
  格式 `「怪物名」 台词`(名字红字走 `mob.getName()`,与 BOSS 化改名兼容:BOSS 版会带着【BOSS】前缀开口;台词黄字)。
- **内置台词池 20 条**,全按作者示例的损嘴风格:前朝的剑斩本朝的官 / 兵器没上永夜户口 / 海关都没过就想通关我 /
  外来的和尚好念经 / 水土不服 / 烧火棍 / 拿错剧本 / 三无兵器 / 签证过期 / 兵器不认伤害不算 / 跨服砍人 /
  给我扇风 / 落户再来 / 羽毛挠痒 / 异界神兵=牙签 / 蚊子叮 / 问问你那武器这是谁的地盘……随机抽一句。
- **可配置(遵守「新机制必须后台可调」守则)**,配置 +3,**configVersion 20→21**:
  - `foreignDamageTaunt`(默认开)嘲讽总开关;
  - `foreignDamageTauntCooldownTicks`(默认 60t = 3 秒)**每玩家冷却**——连点攻击不刷屏,冷却内只出 action bar 灰字;
    transient `HashMap<UUID,Long>` 记上次时间,套路照 ClassSkillHandler.lastCombat,计时用 `world.getTime()`(在树 proven);
  - `foreignDamageTauntExtraLines`(默认空)竖线 `|` 分隔的自定义台词,追加进内置池,作者想加梗不用改代码。
- action bar 那条「外来模组武器对怪物无效」灰字提示**保留双轨**:嘲讽=风味、提示=讲机制,各自开关互不影响。
- **零新 API 面**:`sendMessage(Text,boolean)` / `getName().copy()` / `getRandom()` / `world.getTime()` 全部有在树先例;
  静态自检两文件括号配平、3 新字段定义↔引用一致、taunt 定义/调用各 1。
- 改 2 文件:ForeignDamageFilterHandler(台词池+冷却+发话)+ YongyeConfig(3 字段+版本 21)。

## 里程碑 191 — 外模组伤害过滤:修「秒杀类武器仍能杀怪」(AvaritiaNeo 无限剑绕过 damage())
- **现象**(作者反馈):装 AvaritiaNeo-Fabric,用「无限剑」照样能把怪物打死,m189 的过滤器没拦住。
- **根因**(核实 AvaritiaNeo 源码 `ItemInfinitySword.hurtEnemy`/`onLeftClickEntity`):它的击杀链是
  `entity.hurt(源, Float.MAX); entity.setHealth(0); entity.die(源);`——**后两句直接改血 / 直接触发死亡,压根不走 `damage()`**。
  m189 只挂了 `ServerLivingEntityEvents.ALLOW_DAMAGE`,只能拦第一句 `hurt()`(伤害数字/击退没了),
  可 `setHealth(0)+die()` 照样把怪弄死——事件够不着。弓箭(`EntityInfinityArrow`)则走 `hurt()`+自定义伤害类型 `avaritia:infinity`,原本靠武器命名空间也能拦,但攻击者判不出时会漏。
- **修复(双拦 + 伤害类型信号)**:
  1. **新增 `ALLOW_DEATH` 拦截**:怪物因外模组来源死亡时,返回 false 取消死亡并把血抬回(回调内必须让血 >0,套路照 `EndDragonHandler` 三命复活)。这是堵 `setHealth(0)+die()` 秒杀的关键。
  2. **判定统一进 `isForeignToMonster(cfg, source)`**,`ALLOW_DAMAGE`/`ALLOW_DEATH` 共用;三路信号任一为外来即拦:①**伤害类型命名空间**(新增,如 `avaritia:infinity`)②玩家武器命名空间(伤害源武器栈,兜底主手)③非玩家攻击者实体类型命名空间。无攻击者+原版伤害类型=环境伤害照放(不让怪对摔落/岩浆免疫)。
  3. **血量复原用快照**:秒杀链里 `hurt()`(→①记 `HpSnapshot{tick,血}`)紧接着 `setHealth(0)+die()`(→②读快照),同 tick 完成;复原优先用快照值(保留此前的合法伤害),取不到兜底满血。record `HpSnapshot` + `Map<UUID,HpSnapshot> PRE_KILL_HP`。
- **行为**:外模组武器/秒杀彻底打不死永夜的怪(被强杀会回血复活,刻意为之);嘲讽冷却天然去重两路重复触发,action bar 提示只在伤害路径发一次免同 tick 闪烁。
- **待编译验证**:仅一处新 API——`DamageSource.getTypeRegistryEntry().getKey().map(k->k.getValue().getNamespace())`(yarn 1.21.1 官方 mapping,仓库首用)。`getWeaponStack()` 为 m189 已在用。若这行报错,删 `damageTypeNamespace()` helper 及其调用即可,核心双拦对 AvaritiaNeo 仍生效(靠武器主手命名空间)。
- **零配置变更**:复用 m189 的 `enableForeignDamageFilter` 总开关,**configVersion 仍 21**,不触发老存档版本不一致提示。
- 静态自检:花括号 19/19、圆括号 123/123、方括号 1/1 全配平;无未使用 import;私有方法定义 {isForeignToMonster, damageTypeNamespace, isAllowedNamespace, taunt} 与调用一致。
- 改 1 文件:`ForeignDamageFilterHandler`(重写:双事件 + 统一判定 + 血量快照)。

## 里程碑 192 — 外模组伤害过滤加固:纳入「直接来源实体」判定(枪械子弹场景)
- **触发**:作者问「如果是别的模组呢,枪械?」——核实主流枪械模组 TaCZ(Timeless and Classics Zero)源码。
- **核实结论**(`EntityKineticBullet` / `ModDamageTypes`):
  - TaCZ 用**自定义伤害类型** `tacz:bullet` / `tacz:bullet_ignore_armor` / `tacz:bullet_void`(命名空间 `tacz`);
  - 伤害经 `parts.hitPart().hurt(source, dmg)` 走 `damage()`——**不像 AvaritiaNeo 无限剑那样 setHealth(0)+die() 绕过**(它只把 `invulnerableTime=0` 以允许爆头连击);
  - 伤害源由 `ModDamageTypes.Sources.bullet(registryAccess, directCause, getOwner(), ...)` 构造,directCause 可能是**子弹实体本体**,attacker(getAttacker)则是射手。
  - ⇒ **m191 已能拦 TaCZ**:伤害类型命名空间 `tacz` 直接判外来;退一步射手主手也是 `tacz:` 的枪。
- **加固(m192)**:为覆盖「某些枪械可能用**原版伤害类型** + 只挂子弹实体、不挂射手」的漏网场景,`isForeignToMonster` 新增**第 (2) 路信号——直接来源实体** `source.getSource()`:非玩家的直接来源实体(子弹/投射物)命名空间不在白名单即判外来。玩家本体不在此判(走武器分支)。现四路信号:①伤害类型命名空间 ②直接来源实体 ③玩家武器 ④非玩家攻击者实体类型,任一外来即拦。
- **不误伤**:原版箭=`minecraft:arrow`(放行)、玩家近战 getSource=玩家本体(跳过)、外模组子弹实体=外来(拦);vanilla 环境伤害无直接实体(getSource=null,跳过)。
- **待编译验证**:新增 `DamageSource.getSource()`(yarn 官方 mapping,直接实体;长期稳定 API,风险低)。连同 m191 的 getTypeRegistryEntry 一并本地验。
- **零配置变更**,configVersion 仍 21。静态自检:花括号 20/20、圆括号 130/130、方括号 1/1;无未用 import;私有方法四个定义↔调用一致。
- 改 1 文件:`ForeignDamageFilterHandler`(isForeignToMonster 增第 2 路信号 + javadoc 同步)。
- **残留(记录在案,均非 TaCZ 问题,待实测按需补)**:①爆炸类弹药(RPG/榴弹)若走 `minecraft:explosion` 且未把射手记为爆炸源实体→与原版 TNT 无法区分会漏(TaCZ 有传 getOwner,能拦);②燃烧弹点燃后的**持续燃烧**按 `minecraft:on_fire` 无攻击者→漏(直击已拦);③特殊弹附加的中毒/凋灵等 DoT 按原版伤害类型 tick→漏;④极个别直接 `entity.discard()/remove()` 强删的武器→连 ALLOW_DEATH 都绕过。这些要堵需按具体模组再定,不宜一刀切(否则误伤原版 TNT/火/毒)。

## 记录 — 1.21.1 Fabric 高伤害/秒杀武器面排查(m192 续,调研,无代码改动)
- **背景**:作者问「1.21.1 Fabric 还有没有别的高伤害武器(会绕过过滤器)」。核实思路:拦不拦的关键**不是伤害数值,而是这一下怎么投送出来的**——Minecraft 造成伤害/击杀的方式有限,按机制归类即可覆盖没见过的模组。
- **已核实源码**:AvaritiaNeo(无限剑 setHealth(0)+die() 绕过 damage())、TaCZ(子弹走 hurt()+自定义伤害类型 tacz:bullet)。
- **机制归类 + 覆盖情况**:
  - **① 走 `hurt()`/`damage()` 的伤害(占绝大多数)**:数值再大也会被 ALLOW_DAMAGE 取消。主流高伤模组——TaCZ(已确认)、L_Ender Cataclysm(boss武器/AOE,极大概率,未逐一翻源码)、Simply Swords(特殊攻击)、AE2 物质加农炮、Create 土豆加农炮、Iron's Spells/Ars Nouveau(法术,自定义伤害类型 → 命名空间直接判)——都属此类,**已覆盖**。
  - **② `setHealth(0)+die()` 秒杀(绕过 damage())**:m191 的 ALLOW_DEATH 拦(AvaritiaNeo 已确认)。**已覆盖**。
- **仍会漏(需按具体模组定制,不能一刀切,否则误伤原版)**:
  1. **`entity.kill()` 类**:1.21 里 `kill()` 内部 = `damage(genericKill(), MAX)`,伤害类型 `minecraft:generic_kill`、**无攻击者**,与玩家 `/kill` 完全一致 → 放行。堵它会连 `/kill`/虚空死亡一起废,故不堵。个别 novelty「秒杀棒」可能走这条。
  2. **直接 `discard()`/`remove(RemovalReason.KILLED)`**:连 `die()` 都不走 → ALLOW_DEATH 够不着。极骚写法,罕见。
  3. **爆炸 `minecraft:explosion` 未挂射手 / 燃烧·中毒·凋灵等原版 DoT**:与原版 TNT/火/毒无法区分(m192 已记)。
- **结论**:只要武器是"正经打伤害"(哪怕数值爆表)都拦得住;能绕的只有极少数"跳过伤害系统直接删血/删实体/借 generic_kill"的写法。遇到具体可疑模组,照 TaCZ/Avaritia 翻源码再精准补。**本条纯调研,无代码改动。**

## 里程碑 193 — 外模组伤害过滤:近战「手持前置检查」(照作者思路,最干净的一层)
- **作者思路**:「看武器标签是不是这个 MOD 的也可以啊,检查手持是不是原版和用的就行,不是就不能造成伤害。」
- **回应**:这正是过滤器对玩家出手的主判据(m189 起就是「伤害源武器栈,拿不到兜底主手 → 看命名空间」)。但「看手持」只在**伤害源里有玩家握着东西**时才有手可看;m192 记录的漏网(entity.kill()/discard()/爆炸/DoT)恰恰是**伤害源里没有玩家**,没手可查,故仍需 ①伤害 ②死亡 兜底。
- **本轮加固(把「看手持」提前到攻击瞬间)**:新增 `AttackEntityCallback`(⓪)——玩家**左键攻击怪物**的一刻就看主手命名空间,不是原版/永夜/白名单直接 `ActionResult.FAIL` 取消整次攻击。好处:连无限剑那种自定义击杀逻辑(setHealth(0)+die())**都还没来得及跑**,比 ①②「先秒杀再复活」更干净利索;服务端补嘲讽+提示,客户端只取消预测(`!world.isClient` 分流)。
- **边界**:ⓠ只挡**近战左键**;弓/枪/法术**发射的投射物**不走 AttackEntityCallback,仍由 ①(命中时 damage 拦)②(死亡兜底)负责——三层各管一段,不重叠冲突。手持外模组武器右键(UseEntity)不受影响(只挡攻击不挡交互)。
- **零新 API**:`AttackEntityCallback.EVENT.register((player,world,hand,entity,hitResult)->)`、`ActionResult.PASS/FAIL`、`world.isClient` 全部在树先例(ClassSkillHandler/WeaponCombatHandler/SkillEffectManager 已用已编)。守卫写法 `entity instanceof LivingEntity living && entity instanceof Monster` 照 ClassSkillHandler 同款。
- 与现有 AttackEntityCallback(职业技能/武器连击,仅对**永夜武器**生效)不冲突:本层对永夜/原版手持一律 PASS,只 FAIL 外模组手持;后者本就被那些回调忽略。
- **零配置变更**,configVersion 仍 21。静态自检:花括号 23/23、圆括号 150/150、方括号 1/1;无未用 import;三事件注册点(AttackEntity+ALLOW_DAMAGE+ALLOW_DEATH)各一;私有方法四个定义↔调用一致。
- 改 1 文件:`ForeignDamageFilterHandler`(+AttackEntityCallback 前置层 + 2 import)。
- **要实机验**:手持外模组近战武器左键怪→打不动+嘲讽(且应比之前更"干净",怪不会闪一下 0 血);手持原版/永夜武器→正常;空手→正常(minecraft:air 放行);外模组枪械→仍由 ①② 拦(本层管不着投射物)。

## 里程碑 194 — 更换 mod 图标(资源替换)
- **需求**:作者上传新的《永夜》圆形徽标(暗夜日食 + 永夜二字 + 紫色十字剑纹 + 废墟城堡),要求更换图标。
- **处理**:源图 1254×1254 RGB 正方形 → LANCZOS 降采样到 **512×512 RGBA**(比原 128×128 清晰得多,2 的幂通用规格),PIL optimize 存 PNG(376KB)。覆盖 `src/main/resources/assets/yongye/icon.png`。
- **无需改 json**:`fabric.mod.json` 的 `"icon": "assets/yongye/icon.png"` 路径不变,仅替换文件本身。
- **备注**:源图四角为纯黑(RGB 无透明),Mod Menu 里会显示成黑底方形中的圆形徽标;若要四角透明呈纯圆形,可另做一版 alpha 抠圆(待作者确认再动,不擅自改设计)。
- 零代码 / 零配置变更,configVersion 仍 21。改 1 资源文件:`assets/yongye/icon.png`。

## 里程碑 195 — 真正修好无限剑秒杀:自写 onDeath mixin(m191 的 ALLOW_DEATH 设计错误)
- **现象**:作者反馈 m191~194 全上了,AvaritiaNeo「寰宇支配之剑」(无限剑)**仍直接秒杀怪物**。
- **根因(查 Fabric 官方文档确认)**:`ServerLivingEntityEvents.ALLOW_DEATH` 的触发点是 **`LivingEntity.damage()` 里的致死判定**,并<b>不是</b>挂在 `onDeath()` 上。无限剑击杀链 = `hurt(MAX); setHealth(0); die();`:
  1. `hurt(MAX)` 被 ① ALLOW_DAMAGE 取消 → 根本走不到 damage() 的致死判定 → **② ALLOW_DEATH 从未触发**;
  2. `setHealth(0)+die()` 直接调 `onDeath()`,完全绕过 `damage()`。
  ⇒ 我 m191 的 ② 对这种秒杀是**空的**(设计基于"ALLOW_DEATH 挂 onDeath"的错误假设),这才是一直"直接秒杀"的真因。API 都能编(getTypeRegistryEntry/getSource/onDeath 均已核官方 yarn 文档存在),不是没编译进去。
- **修复**:自写 **`MonsterDeathGuardMixin` 直接 `@Inject` 到 `LivingEntity.onDeath(DamageSource)` HEAD(cancellable, require=0)**。所有死亡(含直接 die())都汇流到 onDeath,故在此拦:外来致死 → 回血 + `ci.cancel()`。判定/回血集中在新公开方法 `ForeignDamageFilterHandler.tryBlockForeignDeath(mob, source)`(复用四路 isForeignToMonster + 血量快照)。② ALLOW_DEATH 重构为委托同一方法,降级为"damage 致死路径"的次要网(对外来其实走不到,留着无害)。
- **签名核实**:`LivingEntity.onDeath(DamageSource)` 在 1.21.x 全程为单参(无 ServerWorld),mixin 注入对得上;`require=0` 兜底(万一映射不符则静默跳过不崩)。
- **现四层防线**:⓪ AttackEntityCallback(近战手持前置)/ ① ALLOW_DAMAGE(伤害)/ ② ALLOW_DEATH(damage 致死路径,次要)/ **③ onDeath mixin(直接击杀兜底,本轮关键)**。
- 静态自检:handler 花括号 23/23 圆括号 152/152、mixin 3/3·9/9、mixins.json 合法且已登记;无未用 import。零配置变更 configVersion 仍 21。
- 改 3 文件:`ForeignDamageFilterHandler`(+tryBlockForeignDeath,② 重构)、新增 `mixin/MonsterDeathGuardMixin`、`yongye.mixins.json`(登记)。
- **要实机验(关键)**:①务必 `./gradlew build` 重新构建、用新包(排除跑旧包);②启动日志应有 `[永夜] 怪物伤害来源检测已挂载(伤害+死亡双拦...)`,且无 MonsterDeathGuardMixin 注入失败告警;③拿无限剑左键/右键砍怪→应打不死(回血+嘲讽)。若仍被秒,基本只剩"Fabric 版用 remove()/discard() 而非 die()"这一可能,届时再加 remove 守卫。

## 里程碑 196 — 修「新存档直接是永夜 I」+ 排查不追人/血条对不上(部分)
- **新存档=永夜1(真 bug,已修)**:`NightfallManager.level` 是 **static** 字段;`load()`(SERVER_STARTED)只在状态文件存在时覆盖它,新存档无文件时**什么都不做**→ 残留上一个世界在内存里的等级。表现:先在世界A升到永夜1、退主菜单再建世界B,B 直接显示「永夜 I·暗潮」。修:`load()` 的 else 分支把 `level=0; secondsInNightfall=0` 归零。
- **不追人(排查结论:AI 是写了的,非"没写")**:FirePhoenix/Anubis/DeathMage/GiantCrab/Wraith/ToroDragon 的 initGoals 都有 `ActiveTargetGoal(PlayerEntity)`+攻击 goal;RedSpider/VenomSpider extends SpiderEntity 未重写 initGoals=**继承原版蜘蛛完整 AI**;另有 PursuitHandler 在永夜时让怪主动锁定玩家+挖墙/爬墙/传送。故"不追人"更可能是情境问题:①**创造/旁观模式**下 ActiveTargetGoal 本就不锁玩家(原版行为);②蜘蛛类**白天不主动**(原版行为,永夜锁夜后才凶);③PursuitHandler 锁定**仅永夜≥1** 才生效。待作者给复现(生存/白天黑夜/哪只/是否 debug 刷出)再定。若要 BOSS 无视昼夜恒追,可给 RedSpider 显式重写 initGoals 加不受光照约束的 ActiveTargetGoal。
- **血条「BOSS怪 ×12」名字/框对不上(诊断:是 m184 合并设计,分组有瑕疵)**:BossBarStyleMixin 的 groupKey 把"非佩恩、名字不以\" BOSS\"结尾"的一律归入 `creeper_group`,label 多只时显示「BOSS怪 ×N」+成分标注「僵尸×10 尸壳×2」——这是 m184「同类合并+成分标注」的**预期行为**,但 boss 化的原版僵尸/尸壳因命名没落进 zombie_group、被塞进 creeper 框,观感"对不上"。属分组规则瑕疵,修需动 m184~188 血条系统,待作者确认要不要按真实怪型分组/换框再改。
- 改 1 文件:`NightfallManager`(load 归零)。零配置变更 configVersion 仍 21。

## 里程碑 197 — jiemoli 加入创造白名单 + 调试菜单权限 + 游玩攻略入库
- **需求**:作者「jiemoli 加入创造白名单;游玩攻略和 DBUG 也加进去;游玩攻略也上传」。
- **① 创造白名单**:`YongyeConfig.creativeExemptIds` 默认 `"qiaodaxian"` → `"qiaodaxian, jiemoli"`(逗号分隔,大小写不敏感)。**注意 GSON 坑**:老存档 yongye.json 若已存旧值会盖新默认,作者现有世界需手动加——编辑 yongye.json 把 `creativeExemptIds` 改成 `qiaodaxian, jiemoli`,或游戏内 `/yongye config set creativeExemptIds "qiaodaxian, jiemoli"`;新世界/新配置自动带上。改默认值非加删字段,**configVersion 仍 21**。
- **② 调试菜单权限**:`ModCommands.DEBUG_OWNER`(单 String)→ `DEBUG_OWNERS`(`java.util.List.of("qiaodaxian","jiemoli")`);判定改 `DEBUG_OWNERS.stream().noneMatch(name::equalsIgnoreCase)`,拒绝提示列出全部管理员(`String.join("、", DEBUG_OWNERS)`)。**硬编码,重新 build 即生效**(不依赖配置),jiemoli 可开 `/yongye debug`。要再加人改这一行即可。
- **③ 游玩攻略入库**:`docs/游玩攻略.md`(玩家向,覆盖开局/永夜六级/六职业/成长线/怪物与BOSS默认数值/灾厄核心/外模组武器规则/世界难度/反作弊/指令速查/FAQ),随仓库分发。
- 静态自检:ModCommands 花括号 94/94 圆括号 816/816 配平,无残留旧 `DEBUG_OWNER` 单数引用,`DEBUG_OWNERS` 定义↔引用一致。零新 API(`List.of`/`stream().noneMatch`/`String.join` 标准)。
- 改 2 Java(YongyeConfig 默认值 / ModCommands 权限)+ 新增 1 文档(docs/游玩攻略.md)。

## 里程碑 198 — 强化保护卷改「整次强化消耗一张」+ 调试可关
- **需求**:作者「每次强化不管多少次都消耗一个(保护卷);这个在调试里可以关闭」。即把 m159 那套「手动右键激活、只挡一次碎裂、批量强化里第二次碎裂就没得挡」的模型,改成**一次强化操作消耗一张卷、整次不碎**,且可开关。
- **实现(EquipmentEnhancer.attempt)**:开头算 `opProtected`——开关 `enhanceProtectPerOperation` 开 且 本次会摸到碎裂等级(`startLevel+budget >= enhanceBreakLevel`)时,**优先消耗已激活的手动护盾 ENHANCE_PROTECTED,否则从背包扣一张保护卷**(新辅助 `consumeOneProtectScroll` 逐栈找 ModItems.ENHANCE_PROTECT_SCROLL 扣 1),置 opProtected;碎裂判定块改为:opProtected 则**整次任何高级失败都不碎**,否则回落到老的「手动护盾挡一次 / 否则按 enhanceBreakChance 判碎」。
  - **只在会碎的强化上扣卷**(op 触及 ≥enhanceBreakLevel 才扣),低级安全强化不浪费保护卷——这是对「每次强化都消耗一个」的合理取舍(每次**会碎的**强化消耗一张);要「字面每次都扣」可再说。
  - 关闭开关 = 回到 m159 老行为(右键激活手动护盾、挡一次碎裂)。
- **开关**:配置新增 `enhanceProtectPerOperation`(默认 true)。命令 `/yongye protectperop`(无参=切换,或跟 true/false,存盘+反馈,照 enable 模板)。调试菜单「道具/成长」页加按钮「每次强化护盾:切换」→ 发 `yongye protectperop`。
- **configVersion 21→22**(新增字段;GSON 缺失键取代码默认 true,老存档自动带上、不触发盖默认)。
- 静态自检:EquipmentEnhancer 53/53·246/246、ModCommands 96/96·839/839、DebugScreen 44/44·202/202、YongyeConfig 27/27·133/133 全配平;opProtected/consumeOneProtectScroll/protectperop 定义↔引用一致。**零新 API**(getInventory/getStack/decrement/BoolArgumentType/config.save 全在树 proven)。
- 改 4 文件:EquipmentEnhancer(逻辑+辅助)/YongyeConfig(+字段+版本)/ModCommands(+命令)/DebugScreen(+按钮)。
- **要实机验**:开关默认开——冲 1 万级以上强化时,背包有保护卷则每次强化自动扣一张、整次不碎;背包没卷则照常可能碎;`/yongye protectperop` 或调试按钮切到关,回到右键手动护盾模式。

## 里程碑 199 — 碎裂加难度门:困难档以上才会碎武器
- **需求**:作者「碎武器在困难难度以上才会触发」。
- **实现**:`EquipmentEnhancer.attempt` 顶部算 `canBreak = DifficultyManager.getLevel() >= c.enhanceBreakMinDifficulty`(新配置,默 3=困难;档位=GameDifficulty 序号 0游玩~6永夜)。`canBreak` 同时管住**两处**:①m198 整次强化保护的**预扣**(加 `canBreak &&`,难度不够不预扣保护卷)②循环内碎裂块 `if (canBreak && level >= enhanceBreakLevel)`(难度不够高级失败只是普通失败:白费本次预算、等级不变、**不碎、不消耗保护卷**)。
- **语义**:只门控「碎裂」,不动失败/成功率系统——低于困难仍会强化失败,只是永不碎、永不掉保护卷。难度未设定(getLevel()=-1)按不可碎处理(安全)。
- **可调**:新增 `enhanceBreakMinDifficulty`(默 3),想改成地狱起(4)/深渊起(5)/永夜起(6)改它即可;想任何难度都可碎设 0。**configVersion 22→23**(加字段;老 json 缺该键 GSON 保留默认 3 自动生效,仅弹一次版本不一致提示属正常)。
- 静态自检:EquipmentEnhancer 花括号 53/53 圆括号 247/247 配平;`canBreak` 定义 1 + 引用 2;DifficultyManager 同包(com.yongye.system)免 import。零新 API。
- 改 2 文件:`YongyeConfig`(+enhanceBreakMinDifficulty、CURRENT_CONFIG_VERSION 23)、`EquipmentEnhancer`(canBreak 门)。

## 里程碑 200 — 补强化保护卷的获取入口(命令 + debug 按钮)
- **背景**:作者问「保护卷在哪兑换?debug 里为什么没开关?还是杀够怪自动给?」。核实 `ProtectScrollHandler`:保护卷**没有兑换界面**,只有两条**自动**途径——①敌对怪被玩家击杀时按 `protectScrollDropChance`(默 0.002)低概率直接掉;②击杀累计到阈值自动兑换 1 张,首张 `protectScrollKillBase`(默 2000)击杀、每兑换 1 张阈值翻倍(2000→4000→8000…)。debug 里原有的「每次强化护盾:切换」是 m198 的 `enhanceProtectPerOperation` 消耗开关,**不是发卷**。确实缺一个直接拿卷的入口。
- **本轮补**:①命令 `/yongye protectscroll` —— 直接发 16 张(=一整叠,maxCount 16),照 wardbook 内联写法;②DebugScreen「成长」页加按钮「给强化保护卷×16」→ 该命令。便于测试/管理,正常玩法仍走掉落+杀怪兑换。
- 零新 API(giveItemStack/ItemStack(item,count)/sendFeedback 全同 wardbook proven),零配置变更 **configVersion 仍 23**。
- 静态自检:ModCommands 花 97/97 圆 851/851、DebugScreen 花 44/44 圆 203/203 配平;`literal("protectscroll")` 唯一、按钮唯一。
- 改 2 文件:`ModCommands`(+protectscroll 命令)、`client/DebugScreen`(+发卷按钮)。

## 里程碑 201 — 碎武器(碎裂)总开关 + 成长面板加「当前属性」显示(含武僧)
- **需求**:作者「(debug 里)碎武器这个开关」;「武僧属性在哪显示,看不见」;「所有人加一个当前属性显示」。
- **① 碎武器总开关**:新配置 `enableEnhanceBreak`(默 true)。`EquipmentEnhancer` 的 m199 `canBreak` 前置改为 `c.enableEnhanceBreak && DifficultyManager.getLevel() >= c.enhanceBreakMinDifficulty`——关掉后强化仍可能失败(白费材料/等级不涨),但装备**永不碎裂**,且不消耗保护卷。命令 `/yongye enhancebreak`(切换,照 protectperop 模板)+ DebugScreen 成长页按钮「碎武器:切换」。**configVersion 23→24**。
- **② 当前属性显示(所有职业通用,解武僧看不见)**:根因=成长面板只显示技能书等级/加成,不显示玩家**最终属性**;武僧又无武器(武器面板空)故看不到。方案=**纯客户端**在 StatsScreen 顶部加「◆ 当前属性 ◆」块,直接读本地玩家 `MinecraftClient.getInstance().player` 已被原版同步的最终属性值(getAttributeValue),含职业/携带(m133)/强化加成——武僧照样显示。展示:生命 当前/上限、攻击伤害、攻击速度、护甲、韧性、移动速度、击退抗性、幸运;大数用 big() 紧凑(≥1亿→X.X亿/≥1万→X.X万,后期十亿级不爆行)。不改服务端/网络包(本地玩家属性原版就同步到客户端)。
- 静态自检:5 文件括号全配平(YongyeConfig 27/27·133/133、EquipmentEnhancer 53/53·247/247、ModCommands 98/98·862/862、DebugScreen 44/44·204/204、StatsScreen 15/15·96/96);新 import(ClientPlayerEntity/EntityAttributes)均被引用。
- **待编译验证**:仅 StatsScreen 首用 `player.getAttributeValue(EntityAttributes.GENERIC_*)`(1.21.1 标准 API,常量名与仓库既有 createAttributes 一致,风险低)。其余(bool 开关/toggle 命令/Btn/canBreak &&)全 proven。
- 改 5 文件:`YongyeConfig`(+enableEnhanceBreak、ver24)、`EquipmentEnhancer`(canBreak 接开关)、`ModCommands`(+enhancebreak 命令)、`client/DebugScreen`(+按钮)、`client/StatsScreen`(+当前属性块+big)。

## 里程碑 202 — 修饰品栏「翼」槽放不进原版鞘翅
- **现象**:作者「饰品栏翼槽放原版鞘翅不识别」。核实 AccessoryScreenHandler 第11槽(index 10):
  1. `canInsert` 用 `instanceof ElytraItem`——1.21.1 里原版鞘翅仍是 ElytraItem(鞘翅改 glider 组件、ElytraItem 移除是 **1.21.2**,已查 Fabric 官方迁移文档),手动拖拽理论可行;
  2. **真凶=`quickMove`(shift 点击)**:「背包→饰品区」分支只处理 `instanceof ArtifactItem`,鞘翅不是神器→shift 点击直接返回 EMPTY 什么都不发生=「放不进」。
- **修**:①canInsert 加 `stack.isOf(Items.ELYTRA)` 兜底(确保原版鞘翅一定认得)保留 instanceof 兼容模组鞘翅;②quickMove 背包→饰品区分支加鞘翅路由:神器进神器槽 `insertItem(0, SIZE-1)`,鞘翅进翼槽 `insertItem(SIZE-1, SIZE)`;③新增 `isWing()` 辅助(与 canInsert 同判定)。
- **⚠ 遗留(已如实告知作者,待定)**:m140 删了 AccessoryGliderMixin 后,**没有任何代码读这个翼槽来提供滑翔**(全局 grep 确认:EntityFlagInvoker 的使用者 AccessoryGliderMixin 已删、不在 mixins.json)。故鞘翅**放进翼槽只是存放,不会飞**;飞行现在靠把鞘翅穿在**正常胸甲槽**(m140 的设计)。若作者想要「翼槽本身赋予飞行(可同时穿胸甲)」,需重新加滑翔逻辑(m140 因其 finicky 移除,是较大改动)——待作者确认再做,本轮不擅自加不可实测的滑翔 mixin。
- 零新 API(isOf/insertItem/instanceof 全 proven),零配置变更 configVersion 仍 24。静态自检 AccessoryScreenHandler 花 24/24 圆 58/58 配平。
- 改 1 文件:`screen/AccessoryScreenHandler`。

## 里程碑 203 — 模组改名《夜蚀 / NightBlight》(原《永夜》;内部 id 不动,存档兼容)
- **需求**(作者原话):「给模组改一个名字 你给起一个名字 不叫永夜了」→ 定名 **《夜蚀》**(英文 NightBlight):夜色如蚀、一层层侵吞世界,贴合「永夜等级不断下沉」的核心玩法;作者不满意随时换,改的全是字符串。
- **改的是「显示名」这一层**(66 文件):fabric.mod.json(name=「夜蚀 NightBlight」+ description 冠《夜蚀》)、主菜单大字「永夜」→「夜蚀」+ 英文副标 ETERNAL NIGHT → NIGHTBLIGHT(TitleScreenMixin,血红辉光风格保留)、Logger 名与全部 `[永夜]` 日志前缀 ×76 → `[夜蚀]`、调试菜单标题、开局难度界面标题「◆ 夜蚀 · 选择难度 ◆」、物品组/按键分类双语 lang(zh「夜蚀」/ en「NightBlight」)、欢迎书作者署名、m189 伤害检测的模组指称与吐槽文案(「没上夜蚀的户口」等)、README 与 游玩介绍.md 的标题及《》书名号、YongyeButton 注释。
- **刻意不动(重要)**:内部 mod id / 包名 / `/yongye` 命令 / 资源路径 / 存档文件名(yongye.json、yongye_doom.json 等)——动了老存档的物品、实体、附着数据全部丢失;游戏机制与剧情用语「永夜(等级/降临/尸潮/天象/剥视)」「永夜之尘/之眼/之翼」与欢迎书剧情——那是世界观本体,不是模组名;开发文档历史不回溯改写,SKILL.md 顶部加了改名说明行。
- 校验:三份 JSON 合法;改动均为字符串字面量,无代码结构变化;**无「待编译验证」**;configVersion 不变(仍 22)。

## 里程碑 204 — 选职界面重做:程序化职业卡(替代旧 AI 卡图)
- **需求**(作者):「职业选择界面…现在有点不好看我想重新生成一下」。沙箱画不了像样的卡面插画(SKILL §5),改走**程序化绘制**:观感统一、可随时改配色、不再依赖 AI 生图。
- 新 **`ClassCardRenderer`**(共享卡片渲染器,106×132 与旧卡图同尺寸,两个界面网格零改动):职业色描边(悬停双圈发光)→ 夜蚀深蓝纵向渐变底(2px 色带循环,零新 API)→ 1.25× 职业名 + 分隔线 → 2× 职业武器图标(matrices push/translate/scale + drawItem,WeaponInfoScreen 同款;**武僧空手画大字「拳」**)→ 定位语 → 三行特长 → 悬停「▶ 点击选择 ◀」。特长/介绍文案与 ClassManager.mods() 与职业技能实际数值对齐(肉盾+20血+8甲盾反、战士怒气、术士燃血施法、剑客+4攻格反、武僧吞噬、刺客+20%速夜视)。
- **`ClassSelectScreen`** 重写:金色 1.4× 标题 + 夜色压暗层 + 悬停时底部显示该职业一句话介绍(职业色);网格、点击判定、ChooseClassPayload 提交、屏蔽 ESC 全部沿用旧版。**`ClassReplaceScreen`** 接入同一渲染器(红框「将丢弃」语义保留,卡面不另发光),删 cardTex/Identifier 依赖。
- 删除 6 张旧 AI 卡图 PNG(git 历史可找回);`class_card` 引用全仓清零。
- **无「待编译验证」**:全部绘制调用(fill / drawCenteredTextWithShadow / matrices 缩放 / drawItem)与 import 均有在树先例(ChooseClassPayload 为本仓库类)。要作者实机看:六卡观感、悬停发光与底部介绍、替换界面卡面、武僧「拳」字卡;配色不满意改 ClassCardRenderer.THEMES 即可。
- configVersion 不变(仍 22)。

## 里程碑 205 — 选职界面换用户 6 张「职业介绍」海报(整图翻页版)
- **需求**(作者):「职业图换成这个」+ 传来 武僧/术士/刺客/坦克/战士/剑客 六张 AI 海报(1086×1448,自带职业名、定位语、技能列表、立绘)→ m204 的程序化卡片让位,删 `ClassCardRenderer`(git 历史可找回)。
- **装图**:LANCZOS 降采样至 768×1024(插画按 SKILL §5 用 LANCZOS,3:4 比例不变)→ `textures/gui/class_poster_<id>.png` ×6(约 8MB)。坦克海报对应 TANK(枚举 cn=肉盾:页签显示「肉盾」、海报标题写「坦克」,同一职业)。
- **`ClassSelectScreen` 重写 = 海报翻页版**:左侧 6 个夜蚀主题页签(YongyeButton)切换 + 金色 ▶ 指示当前;海报在右侧区域按 3:4 等比最大化居中(matrices 缩放 + drawTexture 9 参,签名照 AccessoryScreen 在树先例);底部「✔ 选定当前职业(不可更改)」确认后才提交 ChooseClassPayload——选职不可逆,由「点卡即选」改为显式确认,防误触;仍屏蔽 ESC。海报自带全部文案,界面不再叠字。
- **`ClassReplaceScreen`**:两张卡面改为海报 99×132 等比缩略(同一套贴图),红框「将丢弃」语义与标签保留。
- **无「待编译验证」**:drawTexture 9 参 / matrices 缩放 / YongyeButton+addDrawableChild 组合均有在树先例;ChooseClassPayload 为本仓库类(import 比对脚本的"无先例"是重写后的已知误报)。
- 实机看:海报清晰度(现 768 宽,嫌糊可提 1024 宽)、页签切换、确认按钮、替换界面缩略。configVersion 不变。

## 里程碑 206 — 全物品标识「抖音:乔大仙」(tooltip 水印,可配)
- **需求**(作者):「所有物品添加 抖音:乔大仙」→ 悬停**任意物品**(原版 + 模组)的提示栏末尾追加一行金色「抖音:乔大仙」。
- 实现:客户端 `ItemTooltipCallback` 一个钩子覆盖全部物品,零逐物品改动(YongyeClient 注册;包路径与签名照 FabricMC/fabric **1.21.1 分支源码**逐字核对:`net.fabricmc.fabric.api.client.item.v1`,`getTooltip(stack, Item.TooltipContext, TooltipType, List<Text>)`)。
- 配置 +2,**configVersion 24→25**:`enableItemWatermark`(默认开)/ `itemWatermarkText`(默认「抖音:乔大仙」,换字/关闭改配置即可)。
- **待编译验证**:仅 `ItemTooltipCallback`(仓库首用,已按官方源码核对);TooltipType / Text / Formatting 均在树。

## 里程碑 207 — 选职界面居中重排(实机反馈)+ 聊天前缀改名补漏
- 作者实机截图:m205 版海报偏右、页签孤在屏幕最左、确认钮压到底部快捷栏,「看着难受,能不能放中间」。
- 重排为一个居中整体:**海报屏幕正中**等比铺满高度(上下各留 10);**6 个页签紧贴海报左侧**(金 ▶ 指示当前);**确认钮挂在页签列正下方**(下带灰字「(不可更改)」),彻底离开底部 HUD 区。逻辑零改动,纯布局。
- 顺手补 m203 改名漏网:玩家可见聊天前缀 **【永夜】→【夜蚀】** ×3(DifficultyManager 难度公告、NightfallManager 永夜等级公告/赎夜提示;「【永夜天象】」是机制名保留)。
- 无「待编译验证」。

## 里程碑 208 — 海报技能对账 + 补齐三个缺口(坦克真减伤 / 剑客身法如风 / 剑气凌空)
- **对账结论**(六张海报逐条 vs 代码):大招层全在(旋风斩/不动如山/影遁/灭世/百裂拳/万剑归一 = ClassUltimateManager);战士吸血15%/斩杀、刺客背刺/闪避20%/暴击20%/脱战加速/夜视、武僧连击叠伤/缴械15%/拳意成长、术士潜行耗血AOE/法杖蓄力远程弹(以命为薪=法系炮台+生命献祭)、坦克嘲讽/护盾/盾反、剑客剑气波/招架反弹 —— 全部已实现且配置默认值与海报数字一致。缺口只有三个,本轮补齐:
- **坦克·15% 真减伤**(`tankFlatReductionFraction`,默认 0.15):ALLOW_DAMAGE 里取消原伤害、按减免后数值重放一次;重放由 `TANK_REAPPLY` 守卫直接放行走原版结算 → **无视护甲的真实伤害同样被减免**(HighHpCounter 那类真伤也吃减免)。放在刺客闪避判定之后,双职业不吞闪避。
- **剑客·身法如风**:ClassManager.mods() 给 SWORDSMAN 加移速 +12%(ADD_MULTIPLIED_BASE,与刺客 +20% 同写法)。
- **剑客·剑气凌空**(`swordsmanPierceRange`=12 / `swordsmanPierceDamage`=10,持流光×1.5):剑气层此前只喂 MP 条无消耗,现在攒满 10 层后的下一次近战命中,沿视线逐格细判定放出**穿透直线剑气**(命中去重),打完清零重攒——资源闭环。
- 配置 +3,**configVersion 25→26**;`new Box(6 double)` / getEntitiesByClass / takeKnockback 等全部在树先例,**无「待编译验证」**。
- 实机盯:坦克挨真伤(高血量反制)是否按 85% 结算、真减伤重放有没有异常(击退/无敌帧观感)、剑客攒满 10 层剑气后的穿透与 MP 条清零、剑客移速手感。

## 里程碑 209 — 成长面板/装备介绍界面排版修复 + 攻击伤害真值同步(实机截图三连修)
- **需求**(作者实机截图):「有重叠;拿着武器但是攻击没变(面板攻击伤害显示 1,攻速 2.1 却在变);武器模型放在哪里」。
- **① 攻击伤害显示 1 的根因(不是武器坏了)**:成长面板(StatsScreen,m201)在客户端本地读 `getAttributeValue(GENERIC_ATTACK_DAMAGE)`,但原版这个属性**不是 tracked 属性、永远不下发客户端**——客户端读到的一直是玩家基础值 1.0;攻击速度是 tracked 的所以正常同步(2.1 会变)。**服务端的实际伤害一直是对的**,纯显示问题。修法:新增 `AttackSyncPayload`(S2C,double),ClassSkillHandler 的每玩家 10-tick 循环里在服务端读终值(含手持武器 + 强化组件 + 职业修饰符),**数值变化才发包**(lastAtkSync 缓存,不分职业所有玩家都同步);客户端存 `ClientStats.attackDamage`(<0=未收到,收到前回落本地值),面板改读它。
- **② 成长面板重叠**:旧版单栏 17 行(当前属性 5 行 + 成长 12 行)在 GUI 缩放 4 / 小窗口(高度≈270)下直接压住「返回」按钮并顶出屏幕底。重排为**双栏**:左栏「当前属性」8 行(原本一行两项拆成一行一项,栏更窄),右栏「成长(技能书)」10 行,行距 15,栏心偏移 `max(96, min(120, width/4))` 随窗口收缩、最小 96 保证不互压;高度 240 的窗口也能完整放下且不碰按钮。
- **③ 装备介绍面板(WeaponInfoScreen)重叠**:属性区行距 14 时,第 4 行「耐久度」画在 y0+112~121,而品质框底色从 y0+116 起、「品质」行在 y0+120——**耐久度正好压进品质框**;另外「强化 +X」(y0+162~171)与「✦ 神器技能」行(y0+168)在灰字提示横向延伸段也有 3px 互压。修法:属性区与品质框行距 14→12,品质框上移到 y0+112(框体 y0+108~164),各区间留净空:属性区最深 y0+101 < 框顶 108,框底 164 < 神器技能 168,技能 3 行到 y0+215 < 按钮 224。
- **武器模型位置(作者问,记录备查)**:职业武器 ×5(武僧无武器,m134 起不注册)= `src/main/resources/assets/yongye/models/item/class_weapon_<职业id>.json`(**Blockbench 3D 模型**,elements+UV,credit "Done by Pramanix")+ 贴图 `textures/item/class_weapon_<职业id>.png`(1024²/128² 不等);同目录 `*_e.png` 是 OptiFine 发光贴图约定,**未被任何模型引用**,原版不加载,删改均不影响。混沌之刃 = `models/item/chaos_blade.json`(**平面 handheld 贴图模型**)+ `textures/item/chaos_blade.png`(64×64)。改外观:改形状用 Blockbench 开 json,只换皮直接重画对应 png(尺寸随意,保持正方形)。
- 校验:7 个改动文件括号全配平;AttackSyncPayload 注册/发送/接收三点齐全;ClientStats.attackDamage 定义↔引用一致。**无「待编译验证」**:PacketCodec.of + writeDouble/readDouble、ServerPlayNetworking.send、registerGlobalReceiver、drawCenteredTextWithShadow 均为在树先例写法。configVersion 不变(仍 26)。
- 实机验:①拿任意武器开成长面板,攻击伤害应在半秒内变成真值(换手持物跟着变);②GUI 缩放调到 4 看成长面板双栏是否完整、不压返回钮;③开装备介绍看耐久度/品质框/神器技能三段是否清爽分离。
- **m210** **武器贴图黑白化(作者:「武器改成黑白的 我改的不好看」)**:仓库原始彩色贴图(作者本地改动未推送,不受影响)→ 明度灰度(Rec.709 权重)+ 按不透明像素 1%~99% 百分位对比度拉伸(防纯去色的灰蒙)+ 40% 轻 S 曲线(压深阴影提亮高光),出「墨黑→钢白」质感;**alpha 通道逐像素校验不变**(轮廓/形状零改动),尺寸/模式全保持。共 10 张:5 把职业武器(warrior/assassin/swordsman/warlock/tank,武僧无武器)+ 4 张 `_e` 发光图(虽未被模型引用,一并转保一致)+ 混沌之刃 `chaos_blade.png`(64² 平面,预览即成品)。零 Java 改动、零配置变更(configVersion 仍 26);彩色原版随时 `git checkout 5bbc7bd -- <路径>` 找回。转换脚本参数(想调风格改这三处):S 曲线混合 0.4、百分位 1/99、Rec.709 权重。**tank_shield.png(坦克盾牌)未动**——本轮范围=上轮点名的 6 件武器,盾要不要跟着黑白化等作者定。
- **m211** **武器随强化等级动态染色(作者:「0到100级黑白,越高越鲜艳,最后红色,不要绿/黄」)**:吃 m210 黑白贴图的红利——原版乘法染色下「白→染成该色、黑保持黑」,天然有色金属质感。**① 模型打 tintindex**:5 把职业武器 Blockbench 模型全部 face(warrior 2244/assassin 2088/swordsman 1504/warlock 2082/tank 1716)批量加 `"tintindex": 0`(不打染色不生效);混沌之刃是 `item/handheld` 平面模型,原版 ItemModelGenerator 自动给 layer0 分 tintindex,零改动。**② 客户端注册**(YongyeClient):`ColorProviderRegistry.ITEM.register` 挂 5 武器+混沌之刃;`weaponTintColor(stack)` 读 `ENHANCE_LEVEL`(数据组件带 packetCodec,自动同步客户端)→ ≤起始级返白;超过后**对数插值**(等级阶梯 100/250/500/1000/2500 是指数型,线性插值会前 90% 没色变)算 t,色相 200°→360°(冰蓝→蓝紫→紫→品红→正红,**刻意绕开 60°~150° 的绿/黄**),饱和度 `min(1, t×1.25)` 略快拉满=「越来越鲜艳」;`hsvToRgb` 自实现 12 行(不赌 MathHelper 映射名,零待编译验证项)。实测曲线:+150=#D6E4FF 淡冰蓝 / +250 史诗=#ACA4FF / +500 传说=#C95FFF / +1000 神器=#FF1AC8 / +2500 至尊=**#FF0000 纯红**封顶。**③ 配置+3**(m211 块):`weaponTintEnabled`(关=永远黑白)/ `weaponTintStartLevel=100` / `weaponTintEndLevel=2500`,**configVersion 26→27**。性能:provider 每 quad 每帧调用,单次=一个组件读+两个 log,量级微秒下毫无压力。**待编译验证:仅 1 项**——`ColorProviderRegistry`(fabric-api client rendering v1,仓库首用;同文件 EntityRendererRegistry 同包已在用,风险极低)及其 lambda 对应的 `ItemColorProvider#getColor(ItemStack,int)` 签名。附:tank_shield 与所有盔甲未接染色(贴图还是彩色的,染上去会浑浊),要接得先黑白化,等作者点名。
- **m212** **夜蚀群系(作者:「新建一个夜蚀群系,所有生物都会攻击玩家,也会掉落相应道具」)**:被永夜吞噬的土地,四件套。**① 群系本体=纯数据驱动**:`data/yongye/worldgen/biome/nightblight.json`(模组 data 目录即数据包,动态注册表自动加载,**零 Java 注册**)——暗紫天空/浓紫雾/黑紫水色/灰紫草叶、无降水、洞穴环境音;自带刷怪表(monster:僵尸/骷髅/蜘蛛/苦力怕/末影人/女巫,creature:牛羊鸡猪狼——被动生物照刷,刷出来就是「敌人」);lang 补 `biome.yongye.nightblight`=夜蚀之地(中英)。**② 侵蚀转化 `blightArea`**(NightBlightHandler):把已加载区块整柱转为夜蚀群系,**不自己碰 chunk 内部**而是逐区块、分 4 个 Y 段执行原版 `/fillbiome`(每段 16×16×96=24576 < commandModificationBlockLimit 默认 32768,**不动 gamerule**);原版命令自带写入+标脏存盘+ChunkBiomeData 包同步客户端,**对已生成地形立即生效**(作者老档直接能测,不用新档跑图);未加载区块跳过防报错。**③ 全生物敌化**:每 20 tick 扫描,玩家(生存/冒险)身处夜蚀群系→半径 blightAggroRange(24)内:敌对/中立怪 `setTarget`(自带攻击 AI 接管);**被动生物(牛羊鸡猪村民)没有攻击 AI 也没有 GENERIC_ATTACK_DAMAGE 属性(挂 MeleeAttackGoal 会崩)**,自实现「导航追击(1.25 速)+ lookAt + 贴身 2 格啃咬」,啃咬伤害 blightPassiveDamage(3.0),攻击节奏=扫描间隔天然 1 次/秒;已驯服宠物豁免(TameableEntity.isTamed)不背叛主人。**④ 侵蚀掉落**(AFTER_DEATH,照 LootHandler 模板):在群系内死亡且玩家击杀(可关)→全员 50% 掉 1~2 永夜之尘;被动生物额外 20% 生命碎片;怪物额外 12% 裂隙碎片;全员 1.5% 深渊魂晶。**⑤ 自然侵蚀**(照 AnubisSpawnHandler 模板):第 blightStartDay(12)天起每 1 分钟检定 3% 概率,在随机玩家 48~96 格外出现半径 40 的侵蚀区并全服播报。**命令** `/yongye blight [半径 8~128,默认 40]`(OP,不受天数限制)。**配置+14,configVersion 27→28**。**待编译验证 4 项**:`CommandManager#executeWithPrefix`+`server.getCommandSource().withWorld/withSilent`(命令串路线,仓库首用)、`ServerChunkManager#isChunkLoaded(int,int)`、`DamageSources#mobAttack`(getDamageSources 在树,方法名首用)、`PassiveEntity`/`TameableEntity` 类路径(net.minecraft.entity.passive,极低险);biome JSON 格式按 1.21.1(carvers 为 map、features 为空表)写,若加载报错会在日志 registry loading 阶段点名该文件。遗留 Stage2:侵蚀区随时间蔓延(需持久化圆心)、群系内专属事件/BOSS、debug 菜单按钮。
- **m213** **修 m211 武器隐形(作者截图:武器格子变空白,tooltip 正常)**:根因=**1.21 起物品染色 int 按 ARGB 解释**(1.20.5→1.21 迁移说明:Model#renderToBuffer 等渲染入口全面改为单个 ARGB 整数 tint;物品用 alpha 通道、方块不用),m211 的 weaponTintColor 返回纯 RGB(`0xFFFFFF`/`0xRRGGBB`)高 8 位全 0=**alpha 0=整件物品全透明**,所以不管等级多少、开关开没开(白色早退分支同样没 alpha)武器一律隐形,tooltip/数据不受影响。修=三个返回点全部补满 alpha:两处早退 `0xFFFFFF`→`0xFFFFFFFF`,hsv 结果 `0xFF000000 | rgb`;此写法**双保险**——若某版渲染忽略 alpha,高位被掩掉无副作用。顺带排查:结构自查发现 swordsman 模型与 m210 版「不等价」,查实是**原始 Blockbench 导出就自带一个 tintindex 面**(元素 242 down 面),剥离对比时误伤,五个模型结构全部完好,m211 的 json 重写零损坏。零配置变更(configVersion 仍 28)。教训已记:**凡向 1.21 渲染层返回颜色 int,一律带满 alpha(0xFF000000 起手)**。
- **m214** **物品标识文案改版(作者:「抖音:乔大仙 改成 DY:乔大仙」)**:两手改保证老配置也生效——① 配置默认值 `itemWatermarkText` = "DY:乔大仙"(只对新生成的配置文件起效);② **load() 里加一次性迁移**:老配置文件里的值会被 Gson 原样读回、盖过默认值,所以解析后判「仍是旧默认值 "抖音:乔大仙" 才替换」,作者若自定义过文案则一个字不动,下次 save 自动落盘新文案。**configVersion 28→29**(默认值改版惯例 +1)。显示端零改动(tooltip 一直读的是配置字段)。
- **m215** **选职界面横版全屏(作者换 6 张 16:9 新海报,「上次的太传奇了」)**:新图(~1670×941)LANCZOS 统一 1280×720 覆盖原 `class_poster_<id>.png`(路径不变);ClassSelectScreen 重写为**全屏 cover**——海报等比放大盖满整屏、超出居中裁掉(不留黑边不拉伸;海报信息栏都在左侧,底部裁一点无伤大雅),底部暗色横带压两行:6 页签(当前页签下金色底条指示;窄窗口页签宽 64→自适应最小 40)+ 确认钮「✔ 选定职业(不可更改)」;ClassReplaceScreen 缩略卡 99×132(3:4)→176×99(16:9),s=CH/720,红框「将丢弃」逻辑不变(两卡+间距 402px,GUI 最小宽 427 放得下)。
- **m216** **反滚雪球 Debug 开关(作者点名)**:DebugScreen「掉率」页新增分区 4 钮——反滚雪球·开/关(`config set enableDynamicLoot`)、必爆缩减·开/关(`dynamicLootScaleGuaranteed`);走既有 config set 命令通道,零服务端改动。
- **m217** **新增「战斗爽」难度(作者:「地狱以上,怪物血量高、爆率也高,反滚雪球适量减弱」)**:GameDifficulty **末尾追加** BATTLE("战斗爽", ×3.2, LIGHT_PURPLE)——追加而非插入是为了**老存档难度序号不漂移**,代价是 ordinal 顺序≠强度顺序(3.2 介于地狱 2.5 与深渊 4.0 之间),已在枚举头注明;做「难度≥档位」比较的地方(EquipmentEnhancer 碎装 enhanceBreakMinDifficulty 默认 3)会把战斗爽当最高档对待,行为=地狱以上,符合定位。怪血/怪攻走 mobMult×3.2(DynamicScaling 自动);**爆率**走 m150 难度奖励自动 ×3.2(概率掉落与必爆数量并乘);**反滚雪球减弱**挂在 PlayerPower.lootMultiplier:难度=战斗爽时倍率向 1 回拉 `battleFunSnowballRelief`(默认 0.5=衰减减半;0=不减弱,1=等于关)——实测曲线:强度1500 普通只剩 ×0.15,战斗爽 ×0.57,再乘难度奖励净掉落 ≈×1.8。DifficultyScreen 用 values() 迭代,第 8 行自动出现无需改布局。**配置+1,configVersion 29→30**。全程零新 API(待编译验证:无)。
- **m218** **战斗爽下永夜 V5+ 倍增减弱 + Debug 可调倍数(作者:「永夜5级的那个机制在战斗爽里也要适当修改,也在DEBUG里可以修改倍数」)**:永夜 5 级机制=MobEnhancementHandler 里「V5(灭世)之后每多一级,血攻乘 `nightfallBeyondHpPerLevel×(等级-5)`(默认 2 → V6×2/V7×4/V8×6…,ADD_MULTIPLIED_TOTAL 叠加)」。改法:算出 abyssMult 后,**世界难度=战斗爽则再乘 `battleFunBeyondScale`(新配置,默认 0.5)**——V6 缩到 ×1 走既有 >1 守卫自然不生效、V7×2、V10×5,狂欢模式怪已 ×3.2 不再指数上天;设 1=不减弱、0=该机制在战斗爽中关闭。**Debug「永夜」页新增一区 6 钮**:倍数/级 1 / 2·默认 / 4(`nightfallBeyondHpPerLevel`,全难度通用),战斗爽×0.25 / ×0.5·默认 / ×1不减(`battleFunBeyondScale`),走既有 config set 通道。注意:倍增在**怪物生成/加载时**写入属性,改配置后已在场的怪不回溯,新刷的生效。**配置+1,configVersion 30→31**;待编译验证:无(同包引用+在树写法)。
- **m219** **血量突破 int 上限 + 全模组 K/M/B/T 紧凑数字(作者:「血量要可以突破 2147483647,数字太大用 1K 1B」)**:三层修。**① 上限**:Yongye.raiseAttributeCaps 从 1e9(十亿)抬到 **1e15(千万亿)**——属性是 double(整数精确到 9e15),血量是 float(超 ~1677 万有精度粒度但功能正常),彻底与 int 无关。**② int 强转真凶**:8 处 BOSS 血条标题服务端拼 `‖(int)血量/(int)上限`——double 超 21.47 亿被强转**卡死在 2147483647**(作者看到的就是它);全部 `(int)`→`(long)`(客户端 BossBarStyleMixin 解析本就是 Long.parseLong,通道原生 long 安全):MobBossHandler/PainBossHandler/EndDragonHandler + DeathMage/RedSpider/Anubis/ToroEnderDragon/FirePhoenix 五实体。**③ 统一格式**:新建 `client/NumFmt.compact(double)`——<1万原样、≥1万 K、≥100万 M、≥10亿 B、≥1万亿 T,商<100 保一位小数(2147483647→2.1B / 3.5e9→3.5B / 1.2e13→12T);四处显示全部收编:BossBarStyleMixin.yongye$fmtHp(原万/亿)、StatsScreen.big(原万/亿)、HudCompactMixin.yongye$num(原只有 K/M,十亿会堆成一长串 M)、WeaponInfoScreen 四条随强化等级放大的行(攻速等小数行保留原 fmt)。零配置变更(configVersion 仍 31);待编译验证:无(全在树写法,NumFmt 同包裸引用)。注:HUD/成长面板原 <1000 起 K 改为 <1 万原样,口径统一。
- **m220** **上限改无符号 64 位最大值 + 血量/攻击等通道全 double 化 + Qa/Qi 单位(作者:「改成无符号64位整数最大值,攻击伤害这些也要修改」)**:① 属性上限 1e15 → **1.8446744073709552E19(=2^64,u64 最大值 18446744073709551615 的 double 表示)**,血/攻/甲/韧四属性同吃(m219 就是四个一起抬的,攻击天然同待遇);② m219 的 `(long)` 通道在 9.22e18(long 上限)会再卡一次,这轮**端到端 double 化**:8 处服务端 ‖ 拼接改 `String.format("%.0f")`(u64 级整数串无损),客户端 BossBarStyleMixin 的 parseHp/parseGroupHp/fmtHp 全改 double + `Double.parseDouble`(**兼容旧格式**:旧 long 串 parseDouble 照读);实测 u64max→"18446744073709551616"→parseDouble→18.4Qi 全链路通;③ NumFmt 补两档:**Qa=1e15(千万亿)、Qi=1e18**,u64 上限显示 18.4Qi,四处显示(血条/HUD/成长面板/装备介绍)自动继承。零配置(仍 31);待编译验证:无。
- **m221** **成就系统(作者:「设计成就,从简单到困难到极难,最终是击败末影龙」)**:纯数据驱动,`data/yongye/advancement/`(1.21 起目录为单数)11 个 JSON,单线成长链:**root 初入夜蚀**(tick 触发,end.png 底图)→ 简单三连(获得永夜之尘「夜之结晶」/ 生命碎片「生命的碎片」/ 裂隙碎片「裂隙在低语」,inventory_changed)→ 困难(击杀精英毒液蜘蛛「毒牙断折」/ 巨型螃蟹「横行到此为止」,player_killed_entity)→ 极难 goal 档(浴火凤凰「焚翼折羽」/ 阿努比斯「亡者的审判」/ 红蜘蛛「血色蛛网」/ 死亡法师「死灵归寂」,全部全服播报)→ **终焉 challenge:「终焉:黎明将至」击败 minecraft:ender_dragon**(即 m188 终局化的 10 亿血三命末地龙)。criteria 全按 1.21.1 原版写法(entity=loot condition 列表 / items=id 列表 / icon={"id"});parent 链闭合、图标物品 id 与注册表逐一核对通过。若加载报错会在日志 advancement loading 阶段点名文件。遗留:强化等级/职业/夜蚀群系类成就需自定义 criterion(要写代码),作者点单再上。
- **m222** **修 m220 编译错 + 终局龙血=u64 门面(作者 build 报错回传+点名)**:①编译错=BossBarStyleMixin:133 `(float) groupHp[0] / groupHp[1]`——m220 double 化后成了 float/double 混算再塞回 float;修=全程 double 计算后整体 `(float)` 收窄(唯一算术点,157/189/190 是字符串拼接无碍)。②`endDragonHealth` 默认 1e9→**1e19(10000000000000000000)**,load() 加一次性迁移(仍是旧默认值 1e9 才替换,自定义不动);注意 float 血量在 1e19 量级粒度约 1.1e12,低于万亿的单刀在血条上看不出变化——终局龙本来就是三命神像,符合定位。configVersion 31→32。
- **m223** **新职业·召唤师(作者:「召唤流:召唤5铁傀儡/强化翻倍血攻/癫狂耗血加攻速并召唤肝帝玩家,皮肤待定」)**:第七职业 SUMMONER("summoner","召唤师"),无专属武器(同武僧)。**三技能全挂在大招键**:按键=「**召唤**」——身边环形召出 5 座铁傀儡(原版 IronGolem+setPlayerCreated=白嫖友军 AI,只打怪不打玩家),自带「**强化**」=血/攻各挂 ADD_MULTIPLIED_TOTAL ×(1+summonerGolemBoostMult,默认 1=翻倍);再次召唤先散上一批,寿命 60s 自散(POOF);潜行+按键=「**癫狂**」——献祭 20 血 → 力量II+速度II 20s + 召唤「**肝帝玩家**」。肝帝=新实体 GanDiEntity(PathAwareEntity,血300/攻40/速0.35,近战只锁 HostileEntity,离主人>12格跑回,60s 灵魂粒子自散)+ GanDiRenderer(**原版玩家模型宽臂 + 模组皮肤** textures/entity/gandi.png——现为程序化占位小人,**作者发正式皮肤直接覆盖该文件零代码**)。傀儡追踪=内存表+命令 tag 双保险,重启遗留傀儡 ENTITY_LOAD 清理;HUD 资源条=存活傀儡比(标签「傀儡」)。**编译保障**:PlayerClass 新增枚举会引爆的 3 个 switch 表达式全部补臂(ClassWeaponItem flavor/synergy ×2、ClassSkillHandler getMp),HudCompactMixin 四个 String switch 补 "summoner"。选职界面自动出第 7 页签,占位海报 1280×720 已配(注明等正式图)。配置+9(configVersion 32→33),Debug 职业页+召唤师书按钮,lang+entity.yongye.gandi。**待编译验证 3 项(全在 GanDiRenderer)**:EntityModelLayers.PLAYER / PlayerEntityModel(ModelPart,boolean) / BipedEntityRenderer 三参构造——报错只会在这一个文件,贴来即修。遗留:肝帝正式皮肤与召唤师正式海报(等作者)、傀儡数值实测再调。
- **m224** **肝帝天团四人化 + 正式召唤流海报(作者发来四张皮肤+海报+四人技能设定:岛风/晚安/不爱肝/迷人)**:①「癫狂」召唤物从 1 个占位肝帝升级为**四人齐上**——GanDiEntity 加 `VARIANT` DataTracker(0岛风/1晚安/2不爱肝/3迷人,同步客户端选皮肤),环形落位、彩色名牌常显(青/黄/绿/紫);②**分工照作者设定**(每 3 秒一轮光环,作用于主人全部铁傀儡):**岛风·圆梦筑城**=恢复I+抗性I(控场奶)|**晚安·极限生电**=每轮直接修复 4 血+给主人缩 2 秒大招 CD(ClassUltimateManager 新增 public reduceCooldown)|**不爱肝·百万方工程**=生命上限II+抗性II,自身 +100% 血(主坦)|**迷人·蒸汽武装**=力量II+速度I,自身 +50% 攻 +20% 速(输出);SummonerHandler 新增 golemsOf(owner) 访问器。③**四张真皮肤**装入 textures/entity/gandi_{daofeng,wanan,bugan,miren}.png(占位小人退役);皮肤混两种臂型(岛风/不爱肝=细臂 Alex、晚安/迷人=宽臂,按 (55,20) alpha 检测),GanDiRenderer 持**宽/细双 PlayerEntityModel**,render() 覆写里按变体切模型再走 super(细臂皮肤上宽模型会花)。④正式**召唤流海报** 1280×720 覆盖占位图。零配置变更(仍 33)。**待编译验证 5 项**:GanDiEntity 的 initDataTracker(DataTracker.Builder)+TrackedDataHandlerRegistry.INTEGER(1.20.5+ 标准);GanDiRenderer 的 EntityModelLayers.PLAYER_SLIM、render(...) 覆写签名、this.model 可写(LivingEntityRenderer.model 是 protected 非 final)——报错集中在这两个文件。遗留:四肝帝专属大技能(岛风筑墙/晚安放机器/不爱肝巨树击飞/迷人飞艇轰炸)按作者原案属 Stage2,点单再上。
- **m225** **修 m223 编译错:ClassManager.mods 穷举缺口(作者 build 报错回传)**:`return switch (c)` 这个形态被 m223 的 switch 排查 grep 漏掉(当时只搜了 `= switch`/`-> switch`,没搜 `return switch`)——ClassManager:41 职业基础属性包表达式缺 SUMMONER 臂。补:**召唤师=后排指挥位**,+10 血(0/ADD_VALUE)、-20% 攻(2/ADD_MULTIPLIED_TOTAL,输出全靠傀儡)、+1 实体交互距离(5/ADD_VALUE)。顺带把全仓库 16 处 switch 逐一验明受体:其余全是 String/档位/天气/任务/技能枚举或语句式,PlayerClass 表达式仅此一处漏网,现已 7/7 齐。教训入册:**排查 switch 穷举必须同时搜 `return switch` / `= switch` / `-> switch` 三种形态**。零配置(仍 33)。
- **m226** **肝帝优化:台词系统 + 持续时间理顺 + 修连按叠队(作者:「大招是有持续时间的吧,还要有对话什么的」)**:①**台词系统**——GanDiEntity 内置 4 人 ×5 类台词池(登场/战斗/闲聊/告别/阵亡,按抖音人设写:岛风建筑梗、晚安生电梗、不爱肝百万方块梗、迷人蒸汽机梗),只发给主人聊天栏(【彩色名字】+白字),登场白按变体 5/25/45/65 tick 错峰开口不刷屏;战斗白(有目标 35%)与闲聊(无目标每 15 秒 25%)共用 12 秒节流;寿终=告别白+灵魂粒子,阵亡=阵亡白(onDeath 覆写,区别于告别);新配置 `gandiChatEnabled`(关=全员沉默)。②**持续时间**——大招消息带时长「驻场 60 秒」(读 gandiLifeSec);剩 10 秒时岛风代表全队预警一句(只报一次)。③**修 bug:连按癫狂会叠好几队肝帝**——SummonerHandler 加 gandiByOwner 跟踪,重复施放先 POOF 散上一批(与傀儡同策略)。癫狂自身 buff(力量/速度 20 秒)与天团驻场(60 秒)是两个时长,均已可配。configVersion 33→34;待编译验证:仅 onDeath(DamageSource) 覆写签名(原版常规,极低险)。
- **m227** **肝帝台词进配置(作者:「台词系统可以在DEBUG里修改,用|分隔符区分句子」)**:20 个 String 字段 `gandiTalk{Daofeng/Wanan/Bugan/Miren}{Spawn/Combat/Idle/Bye/Death}`,默认=m226 内置句,**竖线 | 分句随机抽**,清空字段=该类沉默;GanDiEntity 删静态池改 `pool(cat)` 实时读配置(split("\\|") 转义已核)。**补 `config get <key>` 命令**(此前只有 set/list,查单字段要翻文件);Debug「配置」页新增「肝帝台词」区 6 钮:查·岛风登场/晚安战斗/不爱肝闲聊/迷人告别(config get 示例)+ 台词开/关;改句走 `config set 字段 句1|句2`(set 是 greedyString,中文与 | 都吃)。configVersion 34→35。
- **m228** **选职界面技能按键介绍(作者:「所有技能按键在选择职业的时候要给介绍」)**:ClassSelectScreen 底部按钮带上方压三行暗底介绍,随页签切换——金字=大招「【X】名称:效果」,蓝字=职业机制(术士潜行蓄力/剑客十刀剑气/武僧空手连击/刺客背刺/坦克格挡/战士吸血斩杀/召唤师潜行+X癫狂),灰字=通用「R/G/V=混沌之刃武器技能 · 按键可在设置-按键改」;SKILL_INTRO 顺序与 PlayerClass.values() 一一对应(肉盾/战士/术士/剑客/武僧/刺客/召唤师)。零配置变更;两轮待编译验证:无(全在树写法)。
- **m229** **鹰扬法杖 + 召唤物随主人成长(作者截图:class_weapon_summoner 紫黑块无名字;问召唤物是否吃主人属性、傀儡是什么键;附 Blockbench 鹰杖模型)**:①**真相**:m223 加职业时 ModItems 的职业武器注册循环(除武僧外全注册)自动生出了 `class_weapon_summoner`,无模型无贴图无 lang → 紫黑块+原始键名。②**鹰杖装入**:作者的 vs_staff_eagle 模型(26 元素/156 面)改贴图引用为 `yongye:item/class_weapon_summoner`、全 face 打 tintindex 0(**自动进 m211 等级染色管线**——武器在 CLASS_WEAPONS 表里,染色注册遍历该表);贴图 64×64 走 m210 同款黑白管线;lang=「职业武器·鹰扬」(中英);词条改真实文案(鹰扬——鹰目所及,傀儡所至/持杖召唤:傀儡强化额外+50%);baseAttributes 补 SUMMONER=攻6/速-2.6 法杖手感。③**召唤物随主人属性成长**(此前不吃,固定值):傀儡+肝帝统一附加「主人最大生命×summonerOwnerHpRatio(0.5)」「主人攻击×summonerOwnerAtkRatio(0.5)」ADD_VALUE 平加成(addFlat 助手,与 ×2 强化乘区分离);**持杖加成**:主手鹰扬且本职业生效 → 傀儡强化倍率 +summonerStaffExtraBoost(0.5,即 ×2→×2.5)。④癫狂消息补键位提示「直接按键=召唤傀儡」(作者被键位绕晕:X=召傀儡,潜行+X=癫狂)。**技能审计**:海报三技能全实装——召唤(X)/强化(倍率内置)/癫狂(潜行+X,耗血+力量速度+肝帝天团),四肝帝光环分工 m224 亦全在。配置+3,configVersion 35→36;待编译验证:无(全在树写法)。
- **m230** **肝帝第五人·芥末(作者发皮肤+出场词「肝痒痒了,该活动一下了」,嘱查抖音定人设)**:**抖音方向两轮检索未能锁定**(搜到的是《烦人的村民》动画角色与同名民谣歌手,已如实告知作者)——人设先按出场词立「纯肝帝劳模」,**分工=爆肝节奏光环:给主人挂急迫II+恢复I**(前四位都是傀儡向,芥末补主人向效率位,不重叠),自身 +30% 移速(劳模腿快);作者补充真实方向后台词(配置)与光环(一个 case)分钟级可调。接入五处:GanDiEntity(名字表/台词池第5行/夹取0..4/光环 case 4/speak 颜色表+深绿)、GanDiRenderer(第5贴图 gandi_jiemo.png 宽臂/SLIM 表/夹取)、SummonerHandler(召 5 人/环形 2π/5/颜色表/芥末提速)、癫狂消息加名、配置 5 个台词字段(gandiTalkJiemo*,出场词=作者原话)。皮肤 64×64 宽臂已验。configVersion 36→37;待编译验证:无。
- **m231** **肝帝是朋友不是主人 + 召唤流海报换新(作者:「不是主人 是朋友」「主图换一个」)**:①措辞全面改版——肝帝相关的「主人」一词从代码注释/配置说明/玩家可见文案中全部退场:GanDiEntity(类 javadoc 明写「是并肩作战的朋友,不是仆从」/跟随·光环·台词注释)、SummonerHandler(召唤者/朋友)、YongyeConfig(台词与成长比例说明);癫狂消息改「朋友们来助阵了……并肩作战 N 秒」(原「肝帝天团降临……驻场」)。**不动任何标识符/字段名/修饰符 ID**(owner、summon_owner_hp 等纯内部,改了空引风险),纯措辞层。②召唤流海报换作者新图(1672×941→LANCZOS 1280×720,与 m215 六张同规格,直接覆盖 class_poster_summoner.png,零代码)。
- **m232** **职业小技能系统(作者:「召唤铁傀儡不应该占用大招的CD吧 小技能 这个是不是每个角色都应该设计一个」)**:新按键 `key.yongye.minorskill`(默认 **C**,双语 lang 已补)→ 新 C2S `ClassMinorSkillPayload`(unit codec 照 ClassUltimatePayload)→ 新 `ClassMinorSkillManager`(结构照大招管理器:本命职业校验+独立冷却表 `minorSkillCooldownTicks` 默认 300t=15 秒,**与大招 CD 互不占用**)。七职业各一小技能:肉盾·盾击(小范围重击+击退+缓慢II)/战士·战吼(周围怪虚弱+缓慢,自身力量I)/术士·生命虹吸(小范围魔伤,按命中回血)/剑客·剑气斩(前方短距剑气,万剑归一迷你版)/武僧·金钟罩(抗性II+回复I)/刺客·疾影步(向前猛冲+速度II,速度同步走 m152 proven 的 EntityVelocityUpdateS2CPacket+velocityModified)/**召唤师·召唤=5 座强化铁傀儡从大招挪到这里**;大招 X 专职癫狂(不再需要潜行,潜行与否都放癫狂),两条施放消息互相提示键位。选职界面 SKILL_INTRO 每职业 2→3 条(金=大招/亮青=【C】小技能/蓝=机制),介绍区 3 行→4 行(introY 与 fill 同步扩);召唤师大招行同步朋友口吻。配置 +14(总开关/冷却/12 项技能数值),configVersion 37→38。**待编译验证 3 项(均新文件 ClassMinorSkillManager 内,同族常量在树低险)**:SoundEvents.ENTITY_RAVAGER_ROAR / BLOCK_BELL_RESONATE / ENTITY_EVOKER_CAST_SPELL 首用(ENTITY_EVOKER_PREPARE_SUMMON/ITEM_SHIELD_BLOCK 等同类已编过);报错只会在音效那三行,换成任意在树常量即可。
- **m233** **召唤师强化包(作者:「召唤师只有三个技能是不是应该强一些」)**:三技能职业深度不足,按「件件够硬+补一条被动」加强,全走 proven API:①**傀儡持续回血**=寿命扫描(每20t)里存活傀儡 heal(summonerGolemRegenPerSec 默认2/秒)——「强化」的血量翻倍从一次性变成可持续,傀儡经打;②**统御被动(新)**=场上有自己的召唤物(傀儡/朋友,两表 isAlive 计数)时召唤者获抗性I,存活数≥summonerGuardAuraBigCount(默认5)升抗性II,45t 时长每20t刷新,取玩家走 getPlayerList()(在树先例)不引新API;扫描早退条件同步改双表判空(否则只有朋友在场时统御不生效);③**癫狂增益升级并可配**=自身力量 amp 硬编码1→cfg.ultSummonerFrenzyPowerAmp 默认2(力量III)、速度 amp→ultSummonerFrenzySpeedAmp 默认1(速度II);④选职界面召唤师机制行同步(回血/统御)。配置+5,configVersion 38→39;待编译验证:无(StatusEffectInstance/StatusEffects/heal/getPlayerList 全在树)。**海报对账(答作者问)**:七职业大招与被动=海报文案(m208 全量对过);m232 六职业的【C】小技能是海报之外的新增设计(应作者「每个角色都设计一个」);召唤师三技能全部来自其海报,但**新海报「召唤」一栏写的是"召唤五个肝帝"疑为"铁傀儡"笔误**(强化栏指铁傀儡、画面全是傀儡、癫狂栏已含五肝帝)——按作者最初口述实现为召傀儡,若海报为准需改随时说。
- **m234** **技能全面吃攻击力(作者:「技能是不是按照攻击来提升的?」——查证结论=两套标准并存,统一之)**:**现状对账**=已吃攻击的:武器技能R/G/V(m72,基础+等级+攻击×倍率)、战士吸血、刺客暴击追伤、术士法杖蓄力弹(攻击×0.5~4.0);**固定值不吃攻击的(后期攻击百万级时形同挠痒,与 DynamicScaling「怪血=玩家攻击×次数」直接脱节)**:四个伤害型大招(旋风斩/灭世/百裂拳/万剑归一)+三个伤害型小技能(盾击/生命虹吸/剑气斩)+两条被动(术士潜行AOE/剑客剑气凌空)。**修法**=九处全部统一为 m72 同款公式「基础值 + 攻击×倍率」(旧固定值降级为基础/保底,倍率设0=回老行为):大招倍率 战士2.0/术士3.0(耗血理应最高)/武僧1.5/剑客2.5,小技能 盾击0.5/虹吸0.8/剑气斩1.0,被动 潜行AOE 0.8/剑气凌空1.0(两条被动算完再乘持职业武器的×1.5,乘区不变);大招/小技能管理器各加 atk(p) 助手(GENERIC_ATTACK_DAMAGE=ClassSkillHandler 在树先例),万剑归一/剑气斩伤害提到循环外算一次。非伤害型技能(坦克不动如山/刺客影遁/武僧金钟罩/战吼/疾影步/召唤系)无伤害数值不涉及。配置+9,configVersion 39→40;待编译验证:无(全在树写法)。
- **m235** **修「按C召傀儡消息报5座却一只不出」(作者实机截图,傀儡出生即死)**:**根因(m223 起就潜伏)**=Fabric 的 `ServerEntityEvents.ENTITY_LOAD` **对新生成实体也会同步触发**(官方语义:新 spawn 与 chunk 读盘都算 load),而召唤流程是 打tag → spawnEntity → **返回后**才 list.add/byOwner.put——于是 spawnEntity 内部触发的「清残留」钩子看到:带 `yongye_summon` tag + `isTracked()==false`(登记还没发生)→ 当场 `discard()`,傀儡出生即被自己人静默秒杀;spawnEntity 照样返回 true,消息照报「召唤!5 座」,`discard()` 无粒子无声音,与截图症状(有消息/无实体/无散场特效)完全吻合。此前一直没炸是因为作者从未实测过纯召傀儡(m229 截图按的是潜行+X 癫狂),m232 挪到 C 键后首测即暴露。**修法**=登记提前:`byOwner.put` 提到循环前、`new Tracked` + `list.add` 提到 `spawnEntity` 之前(ENTITY_LOAD 回调里 isTracked 引用相等即命中),生成失败才 `list.remove`;收尾从「非空才 put」改「空了才 remove」。**副作用核验**=重启清残留语义不变(重启后 byOwner 天然为空,读盘残留傀儡照删);顺手给清残留 discard 加了一条日志(getBlockX/Y/Z 三文件在树先例),以后同类症状看日志一眼定位。肝帝无此钩子(owner 在 spawn 前已 set,残留靠自身逻辑清)故一直正常。零配置(仍40);待编译验证:无。
- **m236** **强化继承(作者:「新增一个强化继承」)**:入口=现有强化界面,**材料槽放一件「已强化装备」**(槽过滤放行:isMaterial 之外,继承开启+isEnhanceable+等级>0)→ 点「升级」走继承分支:来源等级 × enhanceInheritKeepFraction(默认 0.8,设 1.0=无损)向下取整并入左边装备,**来源装备销毁**,铁砧音效+金字回执「强化继承!来源 Lv.X × 80% → +Y 级,当前 Lv.Z」;转移不到 1 级红字拒绝不吞装备。**设计取舍**=继承是确定性转移、不走失败/碎裂系统(等级本就是材料+概率挣来的,20% 税即成本,不再赌一次);跨类型允许(武器↔盔甲,属性按目标类型的每级数值重算,天然正确);shift 点击仍按老路由(装备进装备槽),继承需手动拖入材料槽,防误吞。配置+2(enableEnhanceInherit/enhanceInheritKeepFraction)。
- **m237** **武器强化数值平衡(作者:「所有武器强化加血但+0.1/级、不能跟肉盾比、肉盾强化攻击要降」)**:①**普通武器(WEAPON)强化加血**=applyStats WEAPON 分支补 GENERIC_MAX_HEALTH 修饰(level × enhanceWeaponHealthPerLevel 默认 0.1,主手槽持握生效),与肉盾系(HYBRID 走 enhanceHealthPerLevel=1.0)保持十倍差距;②**肉盾攻击折减降档**=enhanceHybridDamageFraction 默认 0.5→0.3(镇魂实得 0.25→0.15 攻/级),load() 加一次性迁移(仍为旧默认 0.5 才改,自定义不动,照 m214/m222 模板);③**介绍面板三连**=修「肉盾武器攻击加成显示没乘折减」的旧显示 bug(一直虚高一倍)+ 攻速/暴击合并一行腾位 + 新增「最大生命」行(按 WEAPON/HYBRID 取对应每级值)——武器分支仍恰 3 行,m209 版面坐标(属性区底 104 < 品质框 108)分毫未动。配置+1,合计+3,configVersion 40→41;待编译验证:无(BLOCK_ANVIL_USE/kindOf/HP_ID 主手槽全在树)。**强化数值总表(每级,全可配)**:普通武器=攻+0.5/血+0.1/耐久+8+品质攻速暴击;镇魂(肉盾)=攻+0.15/护甲+0.3/韧性+0.1/血+1.0/耐久+8;盔甲=护甲+0.3/韧性+0.1/血+1.0/耐久+8。
- **m238** **召唤师职业书补齐(作者发像素风书图)**:与 m229 武器同病——m223 加职业时 ModItems 的职业书注册循环(每 PlayerClass 一本)自动生出了 `class_book_summoner`,但模型/贴图/lang 三缺 → 紫黑块+裸键名。补齐=作者 1254² RGBA 像素书图 LANCZOS 降 64×64(与其余六本同规格,保留 alpha)装 textures/item/class_book_summoner.png;模型 json 照 monk 同款 minecraft:item/generated + layer0;双语 lang 补名字「职业书·召唤师 / Class Book: Summoner」+ 悬停描述键 class_book.summoner.desc(ClassBookItem tooltip 走 translatable,描述按 m225 属性包写:召唤傀儡与朋友并肩作战、开局30血、自身攻击低、交互距离远)。七职业「模型+贴图+名字+描述」4×7 齐全性终检通过。纯资源+lang,零 Java 零配置(configVersion 仍 41)。
- **m239** **沉浸式战斗手感(作者:「加一个那种沉浸式战斗的战斗动画效果」)**:整套 Epic Fight 式玩家动作动画(第三人称挥砍动作库)工程量=独立大项目且沙箱无法验骨骼,本轮先落地「打击感包」——命中反馈的视听层,零伤害改动:①**镜头微震**=新 CameraShakeMixin 在 Camera.update 末尾 setRotation(当前角+随机小偏移),强度存客户端 CombatFxManager 每 tick 指数衰减(×0.70),取 max 不叠加防连击震到失控;②**FOV 顿挫**=新 FovKickMixin 挂 GameRenderer.getFov 返回值负偏移(命中瞬间视野轻微拉近再回弹);③**命中粒子**=怪身上补 CRIT 火花(数量随「单刀÷怪最大生命」占比),击杀加 CLOUD 消散;④**击杀反馈**=更重震动+整屏淡金闪光(HudRenderCallback ctx.fill,7t 淡出)+经典"叮"确认音(客户端 mc.player.playSound 只自己听见)。**链路**=服务端 CombatFxHandler 挂 ALLOW_DAMAGE(观察者永远放行,注册刻意排在 ForeignDamageFilterHandler 之后——外来伤害被取消时事件链短路,本监听不跑,无效伤害天然不出打击感)+AFTER_DEATH,算好强度(乘 combatFxShakeScale/combatFxFovKick 服务端折算,客户端不读配置)发新 CombatFxPayload(kind/shake/fov/flash/sound)给攻击者本人;只对「玩家→非玩家」生效(PVP 不掺和),每玩家 3t 节流(ALLOW_DAMAGE 在无敌帧判定前触发,连点会高频进,击杀不受节流);后期怪血上天时单刀占比小→反馈自动收敛成轻微震感不会全程狂震,一刀≥25% 判重击加重。**两个 mixin 都 require=0**:方法名/签名与运行时映射不符则静默不挂只丢效果不崩游戏(mixins.json defaultRequire=1 故显式标)。配置+6(enableCombatFx/combatFxShakeScale/combatFxFovKick/combatFxParticles/combatFxKillFlash/combatFxKillSound),Debug 配置页新增「战斗手感」区 7 钮(开关/震动三档/闪光关/确认音关),configVersion 41→42。**待编译验证 3 项集中两个 mixin+一处音效**:Camera.update 注入点签名(BlockView,Entity,boolean,boolean,float)与 setRotation/getYaw/getPitch shadow、GameRenderer.getFov(Camera,float,boolean) 返回 Double、SoundEvents.ENTITY_ARROW_HIT_PLAYER(标准原版常量仓库首用)——前两项即便不符也只是静默不挂(require=0),报错只可能在 shadow 解析,贴来即修。遗留=真·玩家攻击动作动画库(第三人称挥砍/连段骨骼动画,需 PlayerEntityModel 动画注入或 GeckoLib 玩家替身,大工程点单再上)、受击顿帧(hit-stop 需冻结渲染 tick,风险高暂缓)。
- **m240** **拔刀剑式攻击动画(作者:「能不能学习一下拔刀剑,攻击动画学习 SlashBlade-Refabricated」;上轮沙箱重置作者令「重新做吧」)**:已 clone Sh1roCu/SlashBlade-Refabricated 源码研读——他们的**斩击特效**=带 yaw/pitch/roll 的弧面实体、随进度旋转扫出(SlashEffectRenderer 里 `-135°×progress`),**玩家动作**=硬依赖外部 player-animator 库(dev.kosmx.playerAnim)+ VMD 动作文件,**连段**=ComboState 注册表状态机(motion 帧区间+超时+next 链)。本实现**取其神不引其依赖**(player-animator 是整个外部 mod,JiJ 引入+VMD 资产沙箱无法验证):**① 斩击轨迹弧光**=新 `client/SlashFxManager`——纯客户端弧面网格(零实体/零网络包/零伤害改动),三层色带:内圈全透明→0.82R 白色刀芯→外圈武器色渐隐,`RenderLayer.getLightning()`(位置+颜色附加混合=发光,原版闪电/末影龙死亡射线同款);按「揭示进度」95ms 从一侧扫到另一侧(复刻拔刀扫动)再 320ms 幂次淡出;计时 System.nanoTime 帧率无关不依赖 tickDelta;**顶点全部手工基向量旋转**(F=视线/R=F×上/绕 F 转 roll 得斩面横轴,P=O+F·r·cosA+R'·r·sinA),只用 `vertex(float,float,float)+color(int×4)`,不赌 Matrix4f/MatrixStack 重载签名;双面绕序免疫背面剔除;同屏上限 10 道。**② 三式连击**=斜劈(roll-52°)→反手(roll+52° 反向扫)→横扫(roll6° 更大 152°/1.88R),1.2 秒不出刀回第一式(致敬 ComboState 但轻量)。**③ 颜色接 m211 染色管线**=`YongyeClient.weaponTintColor` 从 private 放宽包内可见,+100 前银白刀光、往上冰蓝→紫→红与武器同步。**④ 触发双保险**=新 `mixin/client/PlayerSlashSwingMixin` 钩 `MinecraftClient.doAttack` RETURN(**含挥空**=拔刀精髓;对方块=挖掘不出;**不用 @Shadow**——`(MinecraftClient)(Object)this` 走 public 字段 player/crosshairTarget,javac 编译期即校验少一个运行时解析点;「真挥了手」用 `handSwinging && handSwingTicks<0` 判,攻击冷却早退分支天然不触发)+ YongyeClient 注册 `AttackEntityCallback`(world.isClient 分支)兜底——mixin 若映射不符没挂上(require=0)命中实体仍出轨迹,两路在 trySpawn 里 50ms 去重。**⑤ 第三人称拔刀姿态**=新 `mixin/client/SlashPoseMixin` 在 `PlayerEntityModel.setAngles` TAIL **叠加**三式姿态(身体拧转+持械臂大弧摆+副手反向平衡+头部随动,包络 sin(p·π) 起收归零,左撇子镜像 getMainArm);安全性:只在 handSwingProgress∈(0,1) 生效而原版 animateArms 攻击期间每帧重新赋值 body.yaw/双臂/head.yaw=叠加量不跨帧累积,盔甲层 copyBipedStateTo 照抄部件角度跟着摆;require=0 不符静默不挂。**武器判定**=本模组 ClassWeaponItem/ChaosBladeItem 恒生效;原版剑/斧/三叉戟按 `slashFxVanillaWeapons`(默认开)且命名空间必须 minecraft/yongye——**外模组武器即便 extends SwordItem 也不出**(伤害本就被 m189 过滤,假刀不配发光,与打击感 m239 口径一致)。**配置+5**(enableSlashFx/slashFxPose/slashFxVanillaWeapons/slashFxSize/slashFxAlpha),**configVersion 42→43**;Debug 战斗手感区扩 7 钮(轨迹开关/姿态开关/大小/仅本模组武器);注:这些是客户端渲染读服务端配置,单机/局域网同 JVM 直接生效(作者场景),专用服上客户端按默认值走。**待编译验证 4 项**:`WorldRenderEvents.AFTER_TRANSLUCENT`+`WorldRenderContext.consumers()/camera()`(fabric-rendering-v1 仓库首用,同包 HudRenderCallback 在用)、`RenderLayer.getLightning()`+`VertexConsumer.vertex(float×3)/color(int×4)`(在树类新方法,1.21 已无 .next() 无需调)、`MinecraftClient.doAttack` 注入点与 `handSwinging/handSwingTicks/crosshairTarget` 公共字段名、`PlayerEntityModel.setAngles` 注入点与 BipedEntityModel 公共字段(handSwingProgress/body/head/rightArm/leftArm)——两 mixin 均 require=0 不符只丢效果,报错只可能在字段名那几行,贴来即修。**遗留**=真·骨骼级动作库(引 player-animator+VMD/GeckoLib 玩家替身,大工程点单再上)、轨迹拖尾采样版(跟手挥动路径而非固定弧面,需逐帧记录手部骨骼位置,依赖姿态先实机验收)。
- **m241** **修 m240 编译错:跨包可见性(作者 build 报错回传)**:`poseVariant`/`poseEligible` 写成了包内可见,但调用方 SlashPoseMixin 在 `com.yongye.mixin.client` 包(mixin 全在 mixin 包不与 client 同包)——两方法改 `public` 即修。`weaponTintColor` 保持包内可见不动(唯一调用方 SlashFxManager 与它同在 `com.yongye.client`,本次编译已证明可过)。教训入册:**给 mixin 用的辅助方法一律 public——mixin 固定住在 `com.yongye.mixin(.client)`,与业务包永远跨包**。零配置(仍 43)。
- **m242** **拔刀动作库扩充 3→7 式(作者:「拔刀剑里是不是有更多动作」;已扒 SlashBlade-Refabricated 的 ComboStateRegistry 全表:upperslash/aerial_cleave/rising_star/piercing/rapid_slash/circle_slash/judgement_cut/sakura_end/drive 等,核心思路=按玩家状态触发不同动作)**:照它的状态触发式扩充,零新 API 面(isOnGround/isSprinting/isSneaking 均在树已编)。**① 地面连击 3→4 式**:斜劈→反手回斩→**上撩斩(新,近垂直斩面 roll 96° 向上挑,学 upperslash)**→横扫收式(第四击加大 sweep 168°/r 1.95);combo %3→%4,断连 1.2s 回第一式不变。**② 状态动作三件(新,优先于连击链,不推进 combo)**:空中(!isOnGround)=**空中回旋斩**(sweep 300° 近整圈,学 aerial_cleave/circle_slash,与原版跳劈暴击天然联动);疾跑=**突进突刺**(sweep 26°/r 2.6 窄长向前光刺,学 piercing);潜行=**居合横斩**(sweep 205° 低平大横抽,学居合)。**③ 姿态同步扩 7 式**(SlashPoseMixin switch 补 case 2/4/5/6):上撩=臂大弧挑上过头、回旋=躯干大拧+双臂横甩、突刺=持械臂直挺肩部前送、居合=低姿平甩;状态旗标(onGround/sprinting/sneaking)是同步字段,**远端玩家姿态也按真实状态匹配**(地面链远端仍 age 伪随机,%3→%4)。配置+1 `slashFxContextMoves`(默认开,关=只留地面连击)**configVersion 43→44**+Debug 战斗手感区+2 钮(状态动作·开/关)。待编译验证:无(全在树写法;m240 标的 4 项照旧待作者 build)。实机盯:平地连砍 4 刀看上撩+加大的收式、跳劈看回旋整圈、疾跑砍看前刺、潜行砍看居合、开关关掉回三连击老观感(注:combo %4 后关掉开关=纯四连击,与 m240 三连击略有别,属预期)。
- **m243** **MoBends 式全身发力姿态(作者:「学 MoBends,不是一个 API 但要帅」;已扒 ThatSoulyGuy/MoBends 源码 AttackSlashDown/WhirlSlash/AttackStance/Sprint 各 Bit 的逐帧关键值)**:MoBends 是 Forge 1.7~1.12 老库(自建 IModelPart 骨架+ForeArm/ForeLeg 膝肘关节+SmoothOrientation 插值,整套不可移植),但它「帅」的三板斧全是**纯旋转手法**,搬进我们的 setAngles TAIL 叠加通道:**① 躯干大幅参与**——body 拧身量级 ×1.6 + 新增 body.pitch 前倾 0.12~0.30 rad(他们 bodyRotationX 20°/Y 30~40°,发力感来自躯干不是手臂);**② 头部反向补偿(灵魂手法)**——head.yaw/pitch 减去躯干增量的 85%/80%(他们 head.orient(headYaw−bodyRotation)),身体甩出去**视线锁定目标不动**;**③ 攻击弓步**——副手侧腿前弓 −0.45/持械侧后蹬 +0.32 + 分腿 yaw ±0.12(他们腿 orientX −30°+rotateY ±25°),突刺弓步加深、空中回旋改收腿剪;**④ 不对称三段包络** `yongye$strike(p)`:蓄力反向(0~0.22 峰 −0.40,出手前臂反向预摆=拉弓感)→ smoothstep 爆发过冲到 1(~0.52)→ 二次缓落归零(他们 armSwing=clamp(t×3) 快打慢收);数学自检=两接缝连续(−0.400/−0.400、1.0000/0.9996)、两端归零(additive 安全前提)。**安全边界(逐项核过)**:只碰旋转不碰 pivot(pivot 重置路径不保证,碰=跨帧漂移);body.pitch 原版有 else 归零分支、双腿 pitch/yaw 由 limbSwing 公式无条件每帧赋值→TAIL 叠加不累积;七式(m242)全部接入新包络,bYaw/bPitch 七分支全赋值(definite assignment)。**配置+1 `slashFxBends`(默认开;关=回 m242 简版:sin 包络+仅拧身臂部+头微随动)configVersion 44→45**+Debug 战斗手感区+2 钮(全身发力·开/关)。**待编译验证仅 1 项(极低险)**:BipedEntityModel.rightLeg/leftLeg 字段首用(与已在用的 head/body/rightArm/leftArm 同类公有字段,yarn 标准名)。实机盯:第三人称连砍看蓄力反向预摆→爆发→缓落的节奏、拧身时头是否稳定锁定目标(反补效果)、弓步分腿观感与盔甲跟随、突刺深前倾、空中回旋收腿、关 slashFxBends 对比回退;若前倾角度与坐骑/潜行姿态叠加违和贴截图调系数。

- **m245 战利品宝箱**:BOSS级怪被玩家方(含傀儡/肝帝)击杀掉宝箱物品,右键开箱按品质加权摇奖散落(普通3/稀有5/史诗7/传说9次,lootCrateRollScale缩放);品质映射=末地龙·佩恩→传说(附赠史诗箱)/四极难BOSS→史诗/巨蟹毒蛛→稀有/二次BOSS化(红名「【BOSS】」识别,无附件标记)→普通;传说箱lootCrateWeaponChance(默认0.20)开随机职业武器;模型贴图取自暂存包(4档箱体17→107元素,vanilla格式,贴图引用改写yongye命名空间,ItemsAdder冗余键剔除);新增LootCrateItem/LootCrateHandler,配置+3 v46;全绿零待验(setPickupDelay零先例已规避);实机观察项=箱体模型在手持/GUI下的display比例。

- **m246 魔法阵技能特效**:法师技能包实装——5色×18帧地面魔法阵(128×128,textures/vfx,_e发光图与本体同字节只装本体)+10个技能音效(sounds/skill_*,全注册先接3个);大招成功施放点(ClassUltimateManager唯一冷却写入处)脚下展开职业色阵:战士红/坦克绿/刺客召唤师粉/术士蓝/武僧剑客黄绿,音效战士=火焰甲/坦克=魔法防御/其余=白星;新增MagicFxPayload(S2C,64格就近广播照佩恩口径)+MagicCircleFxManager(AFTER_TRANSLUCENT,650ms逐帧生长→1400ms定帧缓旋→500ms淡出,30°/s自转,全亮度双面quad,相机相对坐标照SlashFx);配置+2 v47;**待编译验证3项**=getEntityTranslucentEmissive(Identifier)/VertexConsumer.overlay(int)/.normal(fff)(均标准yarn名,集中在MagicCircleFxManager一个文件);遗留=小技能/武器技能R G V挂阵点、治疗类接tree_heal。

- **m247 疾跑收刀**:疾跑且不在挥击时主手武器收到背后斜挎(第三人称;第一人称不动保操作感)——WeaponBackFeatureRenderer挂玩家渲染器(LivingEntityFeatureRendererRegistrationCallback,仓库已有先例;body.rotate跟随躯干,FIXED模式0.85缩放,-125°斜挎)+WeaponSheathMixin藏第三人称手持份(HeldItemFeatureRenderer#renderItem HEAD cancellable require=0,只藏主手臂副手照常),两侧共用shouldSheath永不手背两把;武器判定同拔刀口径(原版剑斧戟+本模组);配置+1 v48;**待编译验证3项**=ItemRenderer.renderItem十参重载/ModelTransformationMode.FIXED/HeldItemFeatureRenderer注入点签名(全在两个新文件,最坏纯观感失效不崩);实机调优项=各武器背挂比例与贴背深度(SCALE/BACK_OFF/DOWN_OFF三常量);**docs/staging已删**(素材全实装,m244提交可找回)。

- **m248 姿态注入点加固+打击感全面上调(作者实机:「三式姿态没生效,打击感还是不强」)**:**① 姿态没生效的最大嫌疑与修法**——SlashPoseMixin 原挂 `PlayerEntityModel.setAngles` 且 require=0:该注入点在运行时对不上会**静默失效、build 照样绿**,与「刀光有(挂 MinecraftClient.doAttack,另一条注入线)、姿态没有」的症状完全吻合;修=注入点改挂 **`BipedEntityModel.setAngles` TAIL**(姿态计算的本体实现、必然存在,注入面最稳),处理器开头加 `instanceof PlayerEntity` 门保持「只玩家摆姿态」语义不变;**顺带修一个旧瑕疵**:PlayerEntityModel.setAngles 是 super 之后才把袖子/裤腿/外套 copyTransform 过去,旧挂法在拷贝之后改角度=皮肤外层不跟手,新挂法姿态在拷贝之前就位、外层自然跟随。**② 注入点存活探针(以后不再瞎猜)**——CombatFxManager 新增 `markInjected(name)`(Set 去重,首次触发打一行日志),五个手感 mixin(SlashPose/SlashSwing/CameraShake/FovKick/WeaponSheath)处理器第一行各调一次;进游戏后 `latest.log` 搜「客户端注入已生效」,**少哪行=哪个注入点没挂上**,require=0 的静默失效从此可见。**③ 姿态幅度可调并默认加大**——新配置 `slashFxPoseScale`(默认 1.35,生效钳制 0.3~2.5)乘进拧身/挥臂/弓步/头补全套增量,1=旧幅度;嫌浮夸 `config set slashFxPoseScale 1` 一条即回。**④ 打击感基准强度整体上调**(服务端 CombatFxHandler 硬编码基准,老配置也吃到):普通命中震动 0.30→0.55 起步、封顶 1.0→1.5;FOV 顿挫 轻击 0.6→1.1/重击 1.4→2.4;击杀 震动 1.1→1.7/顿挫 1.8→2.8;两倍率配置(combatFxShakeScale/FovKick)照乘,想回旧手感设 0.6 即可。配置+1,**configVersion 48→49**;待编译验证:无新 API 面(BipedEntityModel.setAngles 注入点=m240 已核的同名方法本体、PlayerEntity/markInjected 全在树);实机盯=①latest.log 五行「注入已生效」齐不齐(缺哪行贴来)②F5 连砍看拧身弓步是否明显③打怪震动/顿挫对比。

- **m249 难度门控重构(作者:「反滚雪球、血量加倍默认关闭,地狱以上才开;但按天数成长不能关」)**:**① 统一难度门**——DifficultyManager 新增 `atLeast(GameDifficulty)`(战斗爽 ordinal=7 追加在枚举末尾数值最大,天然通过任何「≥某档」的门=按最高档对待;未设定 -1 一律不达标)。**② 反滚雪球(动态爆率)**=PlayerPower.lootMultiplier 开头加门:世界难度<地狱恒返 1.0 不衰减(=游玩/简单/适中/困难 默认关闭),地狱/深渊/永夜/战斗爽 才启用;战斗爽仍吃 m217 的 battleFunSnowballRelief 减半回拉;必爆数量缩减(dynamicLootScaleGuaranteed 分支)读同一 lootMultiplier 自动跟随。**③ 血量对位(「血量加倍」=DynamicScaling 按玩家攻击拔怪血)**=门的演进 m147 困难+ → m148 永夜V → **m249 定版:世界难度≥地狱才开,不再看永夜等级**;伤害对位仍不受门约束(作者只点名血量)。**④ 按天数成长恒开**=MobEnhancementHandler 的进度成长块(永夜等级+游戏天数+附近玩家强度)去掉 enableMobScaling 门改为无条件执行;PainBossHandler 佩恩的同款 progressionMultiplier 引用同步改恒开;enableMobScaling 配置弃用保留占位(注释注明,防旧 json 报死键),enableMobEnhancement 总开关仍是全局最后保险。零新配置字段(仅注释),configVersion 不变(仍 49);待编译验证:无(atLeast=纯 int 比较,其余全在树)。实机盯=适中/困难档打高强度号看掉率不再衰减、怪血不再按攻击暴涨;切地狱+看两者恢复;任意档位过天数看怪仍随天数变强。

- **m250 战斗爽任务强化:全物品随机+抽奖揭晓(作者:「战斗爽要提高任务难度,任务物品随机可能是任何物品,以抽奖的方式显示最后要的是什么」)**:仅世界难度=战斗爽时生效,其余档任务原样。**① 任务全面加难**=assign() 引入 bScale(questBattleScale 默认 1.5,≥1 钳制):猎杀精英/屠戮击杀数、搜集数量、逃离距离全部照乘。**② 搜集任务全物品随机**(questBattleAnyItem 默认开)=目标物不再走 13 项固定物资池,改从**整个物品注册表**随机抽(Registries.ITEM.get(rnd.nextInt(size)),64 次尝试);内置黑名单挡「生存拿不到=任务必败」的技术物(命令方块×3/矿车命令方块/结构方块/结构空位/拼图/屏障/光源方块/调试棒/知识之书/基岩/刷怪笼/试炼刷怪笼/宝库/强化深板岩/末地传送门框架/萌芽紫水晶/石化橡木台阶/耕地/土径)+刷怪蛋(instanceof SpawnEggItem)+配置追加黑名单 questBattleAnyItemExtraBans(物品 id 逗号分隔,想排龙蛋/鞘翅填这里);数量按可堆叠性:不可堆叠 1~2 件封顶 3、可堆叠 3~14 基数,再乘永夜/人数/战斗爽倍率封顶 128;**anyItem 任务不吃「杀怪掉目标物」辅助**(目标可能是钻石块级,白送就没难度)。**③ 抽奖揭晓演出**=真目标派发时已抽好,滚动纯演出:血条先显「命运转盘转动中……」,questBattleRollTicks(默认 60t=3 秒)内每 4t 在 action bar 闪现一个随机假物品名(经验球音效声调 0.8→1.8 渐升=转盘减速感),滚完定格:血条换真标题+聊天栏金字「【命运揭晓】本次搜集目标:N× 物品名」+升级音;**滚动期间 tickQuest 早退不判成功/失败**(防包里恰有目标物提前完成剧透),限时照走。配置+4,**configVersion 49→50**。**待编译验证 4 项(全低险,集中 QuestManager)**:Registries.ITEM.get(int rawId)+.size()(IndexedIterable 标准方法仓库首用)、SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP(标准常量首用)、Item.getMaxCount()(1.21.1 标准)、Items.TRIAL_SPAWNER/VAULT/DIRT_PATH 等黑名单常量首用(名字若有出入=报错行一眼见)。实机盯=战斗爽档等任务派发:搜集任务看 3 秒滚动+揭晓、目标物五花八门、聊天栏与血条标题一致、集齐后正常完成;非战斗爽档任务观感不变。

- **m251 战斗爽排位入正+门控定版(作者:「战斗爽这个难度选择放在该放的地方不要放在最后」「不是说了关闭反滚雪球和血量对位吗」;latest.log 证实作者玩的正是战斗爽档,m249 把它当最高档过了地狱门=两系统仍开,即抱怨来源)**:**① 选难度界面按强度排序**=DifficultyScreen 的 diffs 从 values() 改为显式强度序(游玩→简单→适中→困难→地狱→**战斗爽(×3.2)**→深渊(×4.0)→永夜),点选改发 `diffs[i].ordinal()` 而非行号——枚举 ordinal/存档序号分毫不动(m217 保老存档的前提不破)。**② difficulty 命令修 bug**=上限原硬编码 `integer(0,6)`,战斗爽(7)从来用命令设不进去(m217 就漏了),改按枚举长度。**③ 反滚雪球+血量对位门定版**=m249 的 atLeast(HELL) 换成 `DifficultyManager.growthSuppressionOn()`:**仅 地狱/深渊/永夜 三档启用;战斗爽明确关闭**(强度虽介于地狱深渊之间,但定位是「爽」——不搞掉率衰减、不按玩家攻击拔怪血),游玩~困难与未设定同样关闭;PlayerPower 里 m217 的战斗爽减半回拉块随之不可达已删,battleFunSnowballRelief 弃用占位;BATTLE 枚举描述「反滚雪球减半」改「无掉率衰减」。零新字段(仍 v50);待编译验证:无。实机盯=选难度界面顺序、`/yongye difficulty 7` 能设、战斗爽档掉率满额且怪血不按攻击暴涨。
- **m252 天数口径收口(作者:「睡觉跳过黑天也算天数,这个一定要写对了」)**:全库审计=所有按天逻辑(ProgressionManager.gameDay/怪物按天成长/技能书前期保护)本就走 `getTimeOfDay()`(昼夜时钟)——**玩家睡觉跳夜时原版把它快进到次日清晨,天数照涨**,睡觉本来就算天,口径没错;本轮把它收口防回退:MobEnhancementHandler 与 LootHandler 两处手写的 `getTimeOfDay()/24000` 统一改走 `ProgressionManager.gameDay()`(唯一口径),gameDay 加权威 javadoc 写死「睡过去的夜也算一天(作者点名);⚠ 严禁改成 getTime() 世界年龄——它睡觉不跳会漏算」。零配置零行为变化,纯收口;待编译验证:无。
- **m253 全物品抽取黑名单扩充(作者:「排龙蛋鞘翅之类,填前期不好拿的东西」)**:**① 追加黑名单默认值从空串改内置清单**(QUEST_BANS_DEFAULT,22 项,支持通配:`xxx*`=前缀/`*xxx`=后缀/`*xxx*`=包含):龙蛋/鞘翅/下界之星/信标/龙息/末影水晶/不死图腾/沉重核心/三叉戟/远古残骸/**minecraft:netherite_***(合金锭·块·碎片·全套装备·升级模板一网打尽)/附魔金苹果/潜影壳/***shulker_box**(含 16 色)/***smithing_template**/**music_disc_***/唱片残片/回响碎片/追溯指针/嗅探兽蛋/***_head**/***_skull**(玩家头·僵尸头·龙首·凋灵骷髅头颅等);想解禁删对应项即可,清空会被迁移回默认(留一个占位项可等效清空)。**② 抽取池限定原版命名空间**=pickAnyItem 只抽 `minecraft:`(本模组物品多为任务奖励/Boss 掉落,抽到=变相必败;顺带隔离他模组)。**③ load() 一次性迁移**(照 m237 模板):仅当字段仍为空(m250 旧默认)时填入新清单,自定义不动。**④ 顺手修 m250 笔误**=分隔正则落盘成了单反斜杠 `"[,\s]+"` 形态(Java 21 里 `\s` 转义=空格仍能编译,但语义偷偷变成只认逗号/空格),改回规范双反斜杠。configVersion **50→51**;通配匹配规则已用 21 组用例离线单测全过;待编译验证:无新 API。实机盯=战斗爽搜集任务连抽几轮,确认不再出龙蛋/鞘翅/合金件/潜影盒/唱片/头颅这类目标。

- **m254 真·骨骼级拔刀七式(作者:「上」——批准引入 player-animator)**:m240 拔刀剑调研的结论落地——SlashBlade-Refabricated 的动作=外部库 player-animator 驱动,「直接抄」的正确姿势是接同一个库;动作文件不抄它的 VMD(MMD 社区动作,版权来源存疑),改用库原生 emotecraft 关键帧 JSON **自制七式**。**① 接库(JiJ)**=build.gradle 加 KosmX maven(includeGroupByRegex 限定)+ modImplementation+include(照 GeckoLib 在树口径;该库不带自有 fabric-api 无需 exclude),版本锁 **2.0.1+1.21.1**(SlashBlade 1.21.1 分支同款=实证存在于其 maven;gradle.properties 注明备选 2.0.4+1.21.1);fabric.mod.json depends +playeranimator>=2.0.1。**API 全部照真源码核对**(clone 库 1.21 分支逐类读过):PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(玩家构造时挂层并自动进关联数据)/PlayerAnimationAccess.getPlayerAssociatedData().get(取回层)/PlayerAnimationRegistry.getAnimation→IPlayable.playAnimation()(2.0.0 起返 IPlayable)/ModifierLayer.replaceAnimationWithFade(fade,anim,fadeFromNothing) 三参/AbstractFadeModifier.standardFadeIn+Ease.INOUTSINE;资源目录**双查证**=加载器认 player_animations(复数,单数会警告)、注册键 path=JSON 的 name 字段非文件名、emote 头仅 endTick 必填、version 3 起 torso=胸 body=整体骨、degrees 默认角度制。**② 桥接类 client/SlashAnimManager(新)**=所有库引用只准写在本类:register() 挂空 ModifierLayer(优先级 1000);playFor(player,variant) 按式号取动画 2t 快融入播放(七式 JSON 末帧全零,结束无跳变),动画缺失返 false 退回程序化姿态。**③ 双保险接线**=YongyeClient 用 try/catch(Throwable) 调 register()(库缺失时本类加载即 NoClassDefFoundError,兜住=整体退回程序化姿态不崩游戏)成功才置 SlashFxManager.animLibOk;trySpawn 里本地玩家播放成功→0.8 秒姿态让位窗(poseSuppressUntil),poseEligible 对本地玩家窗内早退——**远端玩家仍走 m243 程序化姿态**(真动作纯本地不同步,单机/局域网本人视角为主)。运行期任何异常一次性降级并打日志。**④ 七式动作 JSON(生成器产出,结构一致已回读校验)**=assets/yongye/player_animations/ 七文件,全身编排(躯干拧转+头部反向锁定+持械臂大弧+副手平衡+弓步分腿,MoBends 三板斧关键帧化):斜劈(举肩斜挥 12t)/反手回斩(横抱反抽 12t)/上撩(低伏挑天 13t)/横扫收式(最大拧身平抡 14t)/空中回旋(body 整体骨 -360° 自旋+展臂收腿 10t)/疾跑突刺(深前倾直刺大弓步 10t)/潜行居合(低伏抱刀平抽 11t);节奏统一「蓄力(EASEOUTQUAD)→爆发(EASEINQUAD)→收势→归零」。**调参提示:动作 JSON 支持 F3+T 资源重载热更新,改角度不用重启。**配置+1(slashFxAnimLib 默认开,关=回程序化姿态),**configVersion 51→52**。**待编译验证(m254,风险集中两点)**:①gradle 依赖解析——maven.kosmx.dev 国内可达性是最大变量,卡住=挂代理/换 2.0.4+1.21.1/手动下 jar,报什么贴什么;②SlashAnimManager 全类=player-animator API 首用(已照库 1.21 分支真源码+官方 testmod 逐字核对,锁的 2.0.1 与源码 2.0.4 存在极小版本差可能;Yarn 环境下库类名经 loom 自动重映射,AbstractClientPlayerEntity/Identifier 写法=官方 README 指定路径)。实机验证=本人 F5 连砍看七式真动作(蓄力预摆→爆发→收势,头部锁定目标)、连击换式融合是否顺滑、空中/疾跑/潜行三态各验、关 slashFxAnimLib 对比回退、latest.log 看「真·拔刀动作库已桥接」一行。

- **m255 武器技能特效夸张化(作者:「混沌斩、吞噬和终焉这些特效要夸张一些,现在看着没效果」;另答疑=「沉浸战斗」整合包动作核心是史诗战斗 Epic Fight,Forge 1.20.1 系,Fabric 接不了,咱们走 player-animator 同类线)**:现状=三招基本裸奔(一条线火花/几撮门粒子/一朵爆炸云)。**核心思路=分帧演出**:单帧粒子堆再多也只是「一坨」,新建 system/WeaponSkillFx 极简多帧任务队列(END_SERVER_TICK 驱动,任务≤20t,逐条 try/catch 单任务炸不拖服务端),让特效「演出来」。**三招编排(粒子/音效全部在树,零冒险)**:①**混沌斩**=出手足下附魔环爆+凋灵射击音+自己震屏(CombatFxPayload 复用 m239)→ 剑气月牙(SWEEP_ATTACK+END_ROD 排成两端后掠的弯月阵型)沿视线 6t 逐帧推进、随距离展开变大,末端 EXPLOSION 炸开收尾;②**深渊吞噬**=唤魔者吟唱音 → 12t 内每个受害者位置持续放出灵魂(SOUL **定向粒子**,count=0 语义 delta 当方向)**逐帧追玩家当前位置飞**(人在移动灵魂会拐弯跟着追,吸魂感的灵魂手法),玩家周身 REVERSE_PORTAL 漩涡环逐帧收缩+魂火双螺旋盘升,收尾**真回到血才**放图腾金光爆发+心形+低音升级音(禁疗时吸不到=没金光,视觉与机制一致);③**终焉降临**=脚下**血红巨型魔法阵**(复用 m246 管线索引色 4,半径=技能半径封顶 8,64 格广播照 SkillFxHelper 口径)+凋灵诞生音+**24 格内玩家集体重震+整屏闪光**(末日感)→ 16t 内天光柱(END_ROD)从头顶节节升起、地面冲击波环(EXPLOSION 隔帧+FLAME 补密度+LAVA 火星)逐帧扩散到技能半径、每个受害者脚下魂火柱冲天(封顶 8 根),终帧四点 EXPLOSION_EMITTER 大爆+爆炸音。三招原有简版粒子保留打底(大演出可关)。灵魂源封顶 12/魂火柱封顶 8/冲击波环粒子数封顶,防大怪群刷屏卡顿。配置+2(weaponSkillFancyFx 默认开 / weaponSkillFxScale 密度倍率默认 1.5 钳制 0.2~3),**configVersion 52→53**;待编译验证:无新 API 面(粒子/音效/spawnParticles/CombatFxPayload/MagicFxPayload/world.random 全在树;ParticleEffect 接口作参数类型首次显式 import,标准接口极低险);实机观察项=count=0 定向粒子的吸魂方向感、终焉 16t 满编时的帧数(卡就 weaponSkillFxScale 调低)。实机盯=三招各放一次对比旧观感,吞噬在怪群里放看灵魂汇流,终焉看红阵+光柱+冲击波三件套,多人 24 格内看集体震屏。

- **m256 刀光贴图化+辉光(学 EpicACG;作者点名「2 学习」)**:三仓库调研结论已同步作者——Epic Fight 资产 All Rights Reserved 一字不能搬(代码 GPLv3 会传染许可证),EpicACG 的华丽=贴图化拖尾+辉光后处理管线,技术思路可搬代码不可移植。本轮把咱们的纯色弧面刀光升级成**贴图刀身+辉光双层**:①**程序化拉丝贴图**=PIL 生成 256×64 白色亮度图(assets/yongye/textures/vfx/slash_trail.png):径向剖面内缘透明→刀刃亮带(v≈0.58)→外缘羽化,横向逐行相关噪声+分段拉丝(挥砍的「刷」感),两端 5% 收口;白图+顶点色染色=颜色仍走 m211 武器染色管线(+100 前银白→蓝→紫→红)。②**渲染双通道**=旧的纯色三带(lightning 附加混合)降为 45% 透明度当**辉光底层**,上面压**贴图刀身**(RenderLayer.getEntityTranslucentEmissive=**m246 已实机编译验证的同一条顶点链**:vertex→color→texture→overlay(DEFAULT_UV)→light(0xF000F0)→normal,全亮度自发光双面);刀身顶点色内缘白热→外缘武器色渐变,U=沿扫掠(拉丝方向)V=径向,揭示进度天然让贴图逐段扫出。配置+1(slashFxTextured 默认开,关=回旧纯色观感),v53→54;待编译验证:无新 API 面(顶点链/渲染层全为 m246 已编);实机盯=七式刀光的拉丝质感与辉光层次、强化染色武器看刀身颜色跟随、嫌花关 slashFxTextured 对比。
- **m257 蓄力重斩(学 Epic Fight 的按住派生;作者点名「3 学习」)**:Epic Fight 招式感的核心手法之一=「按住派生」,原生自研落地:**手持可出刀光的武器、准星不指方块时,按住攻击键蓄力**——每 4t 咔哒声调渐升+action bar 十格蓄力条(▮▮▯…),满蓄(默认 1.5s)「叮」提示;**松开=放出前方锥形重斩**:伤害=攻击力×[1.6, 3.2] 按蓄力时长线性插值(刚过 0.6s 门槛就松也能放,鼓励节奏取舍),锥形判定与混沌斩同款(dot≥0.35,范围 5 格),击退随蓄力加重,冷却 5s。**链路**=新 client/ChargeSlashManager(END_CLIENT_TICK 计时;准星压到方块/换不合格武器/开界面=静默归零不误触挖掘;松开即本地先放一道 **spawnHeavy 加大刀光**(sweep200°/半径2.3×,SlashFxManager 新公开方法)不等回包)→ 新 C2S ChargeSlashPayload(int tick,照 UpgradeWeaponSkillPayload 模板)→ 新 system/ChargeSlashHandler 服务端结算(武器口径与刀光一致的服务端版校验+冷却表+**tick 服务端钳制防包造假**),演出复用 m255 剑气月牙推进+重震+暴击重音(满蓄音调更低更狠)+action bar 结算回执(×倍率/命中数,满蓄金字)。SlashFxManager 另公开 weaponEligible 给客户端管理器共用口径。配置+7(enableChargeSlash/min12/max30/multMin1.6/multMax3.2/range5/cd100),**configVersion 54→55**;**待编译验证 2(低险)**=GameOptions.attackKey 字段首用(mc.options 在树、attackKey 为 yarn 标准名)、ClientPlayerEntity 本地 sendMessage/isUsingItem(PlayerEntity 标准方法必编过,运行期本地显示已知语义)。实机盯=按住攻击键 0.6s 起条→1.5s 叮→松开看加大刀光+剑气推进+伤害回执;对着方块按住确认不触发(正常挖掘);半蓄 vs 满蓄伤害差;冷却回执。

- **m258 空中回旋斩范围伤害(作者:「旋转的时候为什么不能给一圈造成伤害」)**:七式之四(空中回旋)此前纯视觉,原因=整套刀光/动作是零伤害改动的观赏层;本轮补上「转一圈扫一圈」:客户端触发回旋斩的那一刻(SlashFxManager.trySpawn 的 V_AERIAL 分支)发新 C2S **SpinSlashPayload**(unit codec 照 ClassMinorSkillPayload)→ 服务端新 **SpinSlashHandler** 校验后结算:①离地判定宽容化(isOnGround=false 或 fallDistance>0,防同步瞬差)②武器口径共用蓄力重斩的服务端版(ChargeSlashHandler.weaponOk 放宽 public)③短冷却 12t 防狂点叠圈(冷却内静默不刷提示);伤害=攻击力×spinSlashDamageRatio(默认 0.8,略低于正刀)对半径 3.5 格内全部敌对(Monster+精英)结算+**向外击退**(转圈把怪甩开);正常砍中的那只=原版一刀+本圈双份,回旋斩应得的爽点;演出=身周 12 点横扫粒子环+转圈重音(有命中才低音,挥空轻风声)。配置+4,**v55→56**;待编译验证:无(全在树)。实机盯=跳劈砍怪群看一圈全掉血+外甩、狂点空中连挥确认 12t 内不叠圈。
- **m259 武器右键格挡(作者:「所有武器除了法杖,右键可以格挡,有格挡值,格挡值被打掉后就无法格挡了」)**:学 Epic Fight 护盾槽设计原生落地,新 **WeaponGuardHandler** 三事件:①**举盾**=UseItemCallback(照 MonkSystem 在树先例):主手武器(刀光同口径:本模组武器+原版剑斧戟;**唯独排除法杖**=ClassWeaponItem.playerClass==WARLOCK,它右键是蓄力弹)按住右键——原版按住右键每 4t 重发交互,借它当「持续举着」的心跳(guardHoldTicks=8,松开约 0.4s 自然放下),举着缓慢 I 负重+action bar 十格格挡条;②**挡伤害**=ALLOW_DAMAGE:只挡「正面(点积≥0.15≈前方 160°)、有攻击者」的伤害——背刺/摔落/中毒挡不了;挡下=**伤害全免**、格挡值扣除、盾声+面前火花+轻震+更新条;**被击穿(该击伤害≥剩余格挡值)=破防**:这一下**全额命中**、格挡值清零、5 秒硬直无法格挡(红字「破防!」+碎裂声+缓慢 II),期满格挡值直接回满;③**回复**=每秒结算:未破防且距上次挡下超 2s,每秒回 上限×8%(12.5s 回满)。**格挡值上限=最大生命×60%(保底 20)**——跟随本模组的属性成长曲线,后期血几十万格挡值同步几十万,照样挡得动(定平衡数值全 8 项可配)。**事件顺序讲究**(注册插在 CombatFxHandler 之前):外来伤害过滤/职业受击(坦克真减伤)先行→本格挡后审(坦克重放的**折减后**伤害被格挡接住,不双扣)→挡下的伤害不触发攻击者打击感(m239 观察者在链后)。配置+8,**configVersion 56→57**;待编译验证:无新 API 面(UseItemCallback/StatusEffectInstance/ITEM_SHIELD_BLOCK/sendMessage(Text,true) 全在树)。实机盯=持剑按住右键看格挡条+缓慢负重、正面挨打看全免+扣条、绕背挨打确认挡不住、小血量号硬吃 BOSS 一记看破防 5s+期满回满、法杖右键确认仍是蓄力弹不举盾。

- **m260 战斗站姿+格挡姿态(作者:「和 Epic Fight 做的像,现在不像」)**:「像不像」的大头=Epic Fight 拿武器有**架势**(待机/格挡都是姿态,不是原版垂手站),攻击动画只是瞬间。补上循环姿态两条:①**战斗站姿** yongye_battle_idle(48t 循环微呼吸:持械臂半举备战+躯干侧身+头部回正,**只动上身——腿不碰,行走跑步照常**);②**格挡姿态** yongye_guard_pose(40t 循环:武器横举胸前双臂交叉护体+缩身,与 m259 格挡联动)。**架构**=SlashAnimManager 加第二动作层(STANCE_ID,优先级 900<挥砍层 1000=攻击瞬间自动盖过站姿)+**站姿状态机 tickStance**(每客户端 tick,只管本地玩家):按住右键+可格挡武器(法杖除外)→格挡姿态 > 手持可出刀光武器→战斗站姿 > 无;切换 4t 快融;YongyeClient 挂 tick 走 m254 同一套 Throwable 降级口径(库炸=站姿静默消失不崩)。循环 emote 格式=isLoop:true+returnTick:0(照库源码字段)。配置+2(slashFxBattleStance/slashFxGuardPose 均默认开),**v57→58**;待编译验证:无新 API(全走 m254 已核面+useKey 与 attackKey 同族字段);实机盯=拿剑站定看备战架势与呼吸感、走跑腿部正常、按住右键看格挡横举、收武器回原版站姿、F3+T 可热调两条 JSON。
- **m261 法杖无限蓄力·按秒倍增(作者:「法杖右键可以一直右键,按秒数倍数增长」)**:原版蓄力=1.5s 封顶 ×4;改为**想按多久按多久**(getMaxUseTime→72000 弓同款),倍率=**秒数×warlockBoltMultPerSecond(默认 1.0/秒:1s=×1、5s=×5)**,保底 warlockBoltMinMult(手快松也有下限),封顶 warlockBoltMultCap(默认 ×10=按满 10 秒,到顶金字「已满」再按不涨);**每整秒 action bar 播报当前倍率**,吟唱粒子密度随秒数加码、音调按封顶秒数归一渐升;**耗血同步加码**(×(0.4+0.2×秒),封顶 ×3——蓄越久献祭越狠,术士人设一致)。旧 warlockBoltMaxMult/warlockBoltChargeTicks 弃用占位(代码零残引已核)。配置+2,**v58→59**;待编译验证:无新 API。实机盯=按住右键 10 秒看播报 ×1→×10(已满)、松手一炮对比 1 秒松手、观察扣血随秒数变狠。
- **m262 术士小技能改·暗影分身(作者:「法师小技能改成召唤两个分身,50% 血量、100% 攻击」)**:生命虹吸退场(配置三字段+攻击倍率弃用占位,零残引已核),换**暗影分身**:①新实体 WarlockCloneEntity(照 GanDiEntity 极简化:PathAwareEntity 友军近战锁 HostileEntity,无台词无变体),**属性=召唤瞬间按主人快照 setBaseValue**(血量=主人最大生命×minorWarlockCloneHpRatio 默认 0.5、攻击=主人攻击×AtkRatio 默认 1.0——与肝帝的 addFlat 成长线互不相干,分身是一次性快照),寿命 600t=30 秒到点魂火散场,出生**继承主人当前仇恨目标**(getAttacking),无目标时贴身跟随主人(>12 格追赶);②渲染=玩家模型+**程序化暗紫剪影皮肤**(warlock_clone.png 64×64 宽臂:暗紫剪影+亮紫双眼+胸口符纹,一眼「影分身」,不碰在线皮肤 API 零风险);③注册三件套照 GANDI 模板(ModEntities+FabricDefaultAttributeRegistry+EntityRendererRegistry)+双语 lang;④ClassMinorSkillManager WARLOCK case 重写=环形落位召 2 个+唤魔者召唤音+回执「暗影分身!×2(50%血/100%攻,30秒)」;⑤选职界面术士介绍行同步(【C】暗影分身+法杖按秒倍增)。配置+4(count2/hp0.5/atk1.0/life600),**configVersion 59→60**;**待编译验证 1(低险)**=LivingEntity.getAttacking() 首用(yarn 标准方法);实机盯=术士按 C 看两个暗紫分身落位、F3 悬停核血量=自己一半、分身打怪伤害与自己一刀持平、30 秒魂火散场、无目标时跟人。

## m263 BOSS 出场演出 + 皮肤 BOSS 出场血量拔高(2026-07-23)

作者:「BOSS 出场血量不能太低要高,就是我给皮肤的那几个」+「战斗帅怎么帅怎么来」。

- **出场血量可配并大幅拔高**:五只皮肤 BOSS 的基础血量从硬编码改为配置(属性在实体注册时烘焙,改配置需重启)——阿努比斯 8000→**100万**(anubisBaseHealth)、自建末影龙 500→**60万**、浴火凤凰 650→**40万**、死亡法师 500→**30万**、红蜘蛛 400→**25万**。生成后照旧再吃 MobEnhancement 天数成长 + DynamicScaling 玩家攻击对位(只增不减),后期实际血量远高于此。写法照 GanDi 先例(cfg 在 ModEntities.init 前已 load)。
- **出场演出**:新 `system/BossEntranceFx`——登场瞬间给 `bossEntranceRange`(48 格)内玩家整屏标题(BOSS 名+「巨物苏醒」副标,颜色随其血条色系)+ 镜头重震/闪光(CombatFxPayload,m239 管线)+ 凋灵吼 + 魂火双螺旋自地面盘升顶端炸开。全在树 API(Title 三件套=CatastropheCoreManager 已编、spawnParticles/playSoundToPlayer 多处已编),**零新 API**。
- 接入点:五实体 tick 首帧(`entrancePlayed` 旗标;实体 age 不持久化,**区块重载后 BOSS 再次进视野会重演一次**,压迫感有意保留)+ 佩恩 spawnEntity 后。命令召唤同样触发。
- 配置 +8(enableBossEntrance/bossEntranceRange/bossEntranceShake + 五血量),configVersion 60→**63**(与 m264/m265 三笔合并一次跳版)。
- 实机盯:`/yongye anubis` 看标题+震屏+魂火螺旋;F3 核血量 100 万;嫌演出频繁关 enableBossEntrance。

## m264 蚀矿·蚀锭(只在被侵蚀的土地上出现)(2026-07-23)

作者:「想做一套夜蚀装备,要在被污染的区域生成蚀矿」。素材=作者 GPT 生成的蚀矿/蚀锭图(1254²),走 m159/m160 proven 管线 LANCZOS 64×64(锭图自带 alpha,按 m179 口径 alpha<45 清毛边)。

- **蚀矿方块** `blight_ore`:strength 5/6 + requiresTool + 微光 5(紫纹发光);**钻石镐起挖**(tags: mineable/pickaxe + needs_diamond_tool,1.21 单数 block 目录);掉自身(loot_table/blocks,survives_explosion);熔炼/高炉 → **蚀锭**(经验 2.0,照原版远古残骸口径「挖块→烧锭」)。
- **只在夜蚀群系出现,两条路**:①**播种**——NightBlightHandler.blightArea 每转化一个区块播 `blightOreVeinsPerChunk`(2)条矿脉:地下(bottom+8 ~ 地表-6)随机取点,把石头族(石/深板岩/花岗闪长安山/凝灰岩)原地转化成蚀矿,单脉 `blightOreVeinSize`(5)块,落到空气/矿洞就换点重试;②**生长**——每 `blightOreGrowIntervalTicks`(1 分钟)检定,身处侵蚀区的每名玩家按 `blightOreGrowChance`(0.35)在周围 12 格内长 1 块(老侵蚀区也能长新矿,资源可再生)。
- 命令 `/yongye blight` 造的侵蚀区同样播种(共用 blightArea)。
- 配置 +4;**待编译验证 1(低险)**=AbstractBlock.Settings.requiresTool() 仓库首用(标准方法)。
- 实机盯:`/yongye blight 40` 后往地下挖看紫矿;铁镐挖不掉、钻石镐掉;熔炉烧出蚀锭;站侵蚀区里挂 1 分钟看脚边长矿。

## m265 夜蚀套装(灵魂绑定:别人抢不走)(2026-07-23)

作者:「可以做盔甲,别人抢不走」。ArmorMaterial 七参构造 + Registry.registerReference 写法与真实 1.21.1 编译过的模组源码(Kaupenjoe Fabric-Tutorial 17-armor 分支,yarn 1.21.1+build.3)逐字核对;mixin 目标方法全经 yarn 1.21.1 官方 mapping 核实(dropInventory=method_16078 / onPlayerCollision=method_5694 / ItemStack.OPTIONAL_CODEC=field_49266);Fabric 附件持久化经官方源码核实走 RegistryOps,ItemStack 列表可安全存档。

- **材质 BLIGHT**:防 6/12/9/6=33(下界合金 20)、韧性 6、每件击退抗 0.1、耐久系数 45(合金 37)、修复材料蚀锭;穿戴层 `textures/models/armor/blight_layer_{1,2}.png`(程序化:暗钢底噪+紫纹裂隙,原版 UV 布局,靴只画腿区下 5 行、护腿=腰带+腿上 8 行,透明像素不渲染)。
- **合成**:4 件经典盔甲配方 ×蚀锭;强化系统自动识别(EquipmentEnhancer instanceof ArmorItem 兜底)可无限强化,吃 m237 盔甲强化表(甲 0.3/韧 0.1/血 1.0/耐久 8 每级)。
- **灵魂绑定三件套**(总开关 blightArmorSoulbound):
  - **认主**:BlightArmorItem.inventoryTick——未绑定 + 进了玩家背包 → 写 BLIGHT_OWNER 组件("uuid|名字")+ action bar「与你的灵魂缔结了契约」;tooltip 显示主人。
  - **他人捡不起**:SoulboundPickupMixin 挂 ItemEntity.onPlayerCollision HEAD,带主物品被非主人碰撞直接 cancel(原地留给主人;丢给队友是丢不出去的——这是特性)。
  - **死亡不掉落**:SoulboundDropMixin 挂 LivingEntity.dropInventory HEAD(instanceof ServerPlayerEntity 门),把带主物品/夜蚀盔甲从背包截走存 SOULBOUND_STASH 附件(persistent+copyOnDeath),Yongye 的 AFTER_RESPAWN 里 offerOrDrop 原样归还+提示;keepInventory 开着时 dropInventory 不会被调,天然兼容。
- 物品图标 4 张程序化打底(调色板取自蚀锭),作者随时可用 GPT 生图替换 `textures/item/blight_*.png`(64×64,透明底)——**穿戴层那两张勿用 AI 生成**(UV 布局 AI 画不对)。
- 配置 +1;**待编译验证(集中在盔甲注册,均照真实 1.21.1 源码,低险)**=Registries.ARMOR_MATERIAL / ArmorMaterial 七参构造+Layer / Registry.registerReference / ArmorItem.Type.getMaxDamage / SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE / ItemStack.OPTIONAL_CODEC.listOf() 附件 / 两个 mixin 目标(yarn 已核) / Inventory.size()/setStack(标准)。
- 实机盯:烧锭合套穿上看穿戴层观感;A 捡起→B 去捡确认捡不动;A 死亡确认装备不掉、重生回背包+紫字提示;强化界面塞头盔确认能强化;tooltip 看「已认主」。

## m266 永夜阶段/核心箭头 HUD 移到血条上方(2026-07-23)

作者:「永夜阶段会被挡住,改到血条上面」。根因:阶段名画在屏幕中上 y=4,原版 BOSS 血条恰好也从顶部中央往下叠(名字 y≈3、条 y=12,每条 +19),m263 之后 BOSS 一多阶段名整个被压死。

- 阶段名移到**玩家血条面板正上方**(HudCompactMixin 面板顶=h-55,阶段名放 h-66 居中),BOSS 血条再多也挡不到;
- 顺手把**灾厄核心方向箭头**(原 y=30,两条 BOSS 血条就压住)也移到 h-82(阶段名上方),距离文字 h-74,和面板等级行(h-54)互不打架;
- 低血量走原版心形时(≤60 HP 不接管),h-66/h-82 也在心形+护甲行(h-49)之上,无重叠。纯客户端,零配置。

## m267 大体型 BOSS 原地转圈修复(2026-07-23)

作者:「BOSS 走路总是原地转圈」。根因:原版寻路按实体宽度取整算通道——阿努比斯宽 2.5、红蜘蛛/巨蟹宽 3.0,需要 3 格宽无障碍走廊才算"有路",野外几乎处处寻路失败;MeleeAttackGoal 每秒重找路 → MoveControl 不停朝失败节点拧身子 = 原地转圈。贴脸时还有第二来源:目标点就在自己巨型 hitbox 边上,路径瞬间完成又瞬间重找。

- 新 `entity/BossNavAssist`(每 tick,零 mixin):①近身(≤体宽×2+1)→ 停寻路+锁头锁身面向目标(出手判定走 Goal.canAttack 与寻路无关,照打);②寻路失败(navigation.isIdle 且有目标)→ 绕过路径图,MoveControl 直线压上;
- 六只接入:阿努比斯/红蜘蛛/死亡法师/巨蟹(地面)+ 凤凰/自建龙(飞行,BirdNavigation 偶发失败同样兜底);
- **跨步高度属性**(GENERIC_STEP_HEIGHT,1.20.5+ 原版属性):阿努比斯/红蜘蛛/巨蟹 1.6、法师 1.1——巨体直接踏上 1 格高低差,不跳不绕,穷追不舍(战斗帅方针);
- **待编译验证(均低险)**:GENERIC_STEP_HEIGHT 字段(yarn 对注册表常量自动命名,注册 id generic.step_height)、MoveControl.moveTo/EntityNavigation.isIdle/stop/LookControl.lookAt(Entity,f,f)(全部 yarn 1.21.1 官方 mapping 已核:method_6239/6357/6340/6226)。

## m268 四只皮肤 BOSS 技能包(2026-07-23)

作者:「这些 BOSS 设计技能了吗,不能一个技能都没设计吧」。盘点:阿努比斯有(m174 狂怒/法术AoE/召唤恶灵)、佩恩有整套,**凤凰/死亡法师/红蜘蛛/自建龙 Stage1 光杆近战**。本轮全部补齐,全服务端(粒子+音效+伤害+位移),零新实体零 mixin;伤害/冷却/阈值全配置(+21,configVersion 63→**64**)。

- **浴火凤凰**:①烈焰吐息(直线火舌 24 格,命中点燃,炎柱粒子+烈焰人音);②火焰龙卷(目标脚下 8 层螺旋火柱,命中击飞+点燃);③**浴火重生**(一次性招牌):血量跌破 30% 蜷入烈焰之卵——5 秒无敌、火焰螺旋收拢,破壳回复最大血量 40% + 爆炎 AoE 点燃击退 + 全服播报。
- **死亡法师**:①魂火锁定(目标脚下魂火四点标记 1.25 秒后爆燃+凋零 II——给走位窗口的延迟爆点);②亡者音爆(近身 7 格触发:监守者音爆粒子+范围伤害+1.8 倍击退+缓速 II);③虚影闪现(被贴脸挨打 → 传送到目标侧后方 7 格,双端紫门粒子+末影人音——法师不陪你贴身互殴)。
- **红蜘蛛**:①猛扑(4~14 格锁定飞扑,落地范围伤害+击退+尘土);②蛛网陷阱(目标脚下十字铺蛛网+中毒 I);③**蛛群咆哮**(一次性):血量跌破 50% 怒吼(掠夺兽吼)+ 环形召唤 4 只毒液蜘蛛(在树实体复用,继承目标)+ 范围缓速+黑暗;④毒刺(近战命中附中毒 II,覆写 tryAttack——yarn 核实在 LivingEntity,method_6121)。
- **自建末影龙**:①龙息射线(28 格直线龙息,命中+缓速 II);②俯冲冲撞(6~30 格锁定,FlightMoveControl 3.0 速档压上 2 秒,贴近 5 格撞击:45 伤+2.0 击退+龙火爆音);③**重力撕裂**(一次性):血量跌破 40%,24 格内玩家漂浮 III 3 秒被掀上天+20 伤+龙息粒子+全服播报(摔落交给重力)。
- 数值口径:技能伤害是固定基础值(不吃天数成长),BOSS 压迫感主要来自 m263 的百万级血量+属性成长,技能负责"帅"和逼走位;嫌疼/嫌轻改对应 cfg。
- **待编译验证(低险)**:粒子 SONIC_BOOM/DRAGON_BREATH/ITEM_SLIME/EXPLOSION_EMITTER/CLOUD、音效 ENTITY_WARDEN_SONIC_BOOM/ENTITY_BLAZE_SHOOT/ENTITY_ENDERMAN_TELEPORT/ENTITY_RAVAGER_ROAR·STEP/ENTITY_ENDER_DRAGON_SHOOT·GROWL/ENTITY_DRAGON_FIREBALL_EXPLODE/ENTITY_SPIDER_AMBIENT·STEP/ITEM_FIRECHARGE_USE(注册表常量自动命名,全是普通 SoundEvent 档,刻意避开 RegistryEntry 型的 GENERIC_EXPLODE);setFireTicks/setInvulnerable/addVelocity/heal/tryAttack/velocityModified/hurtTime 全 yarn 已核。

## m269 完美格挡·弹反(2026-07-23)

作者:「继续优化战斗系统」+ 常设方针「怎么帅怎么来」。在 m259 格挡地基上加 Sekiro 口径的精准弹反。

- **判定**:右键**起手举盾的头 6 tick(0.3 秒)** 内接住任意可挡攻击 = 完美格挡。关键防蹭设计:右键心跳续期**不刷新**起手时刻——按住不放蹭不出弹反,必须掐着对方出手节奏放下重举(raiseTick 只在 `now >= guardUntil` 的全新起手时记录)。
- **奖励**:①伤害全免且**不耗格挡值**(连本该破防的重击也能弹开——弹反判定排在破防判定之前);②6 格内的近身攻击者吃**反噬**(被挡伤害×parryReflectFraction,保底自己一刀攻击力,先清无敌帧)+ 被弹开(takeKnockback 1.6,传向语义同 ChargeSlashHandler 先例)+ 缓速III/虚弱II 2 秒硬直;③自身获**力量II+速度I** 3 秒反击窗口(纯原版效果零新管线);④演出=烟花火花+末地烛白光爆点、盾击高音+铁砧金铁交鸣、客户端重震+闪光+确认音(CombatFxPayload HEAVY)、金色粗体「完美格挡!弹反!」。
- 远程箭矢同样可弹(全免不耗值),只是射手在 6 格外不吃反噬。配置 +4(enableParry/parryWindowTicks/parryReflectFraction/parryBuffTicks)。
- 全在树 API 零新符号(STRENGTH/SPEED/WEAKNESS 与已编译的 SLOWNESS 同族常量)。

## m270 处决斩杀(2026-07-23)

- **规则**:玩家**直接近战**(source.getSource()==getAttacker(),弓/魔法/召唤物不触发)把敌对怪打到「落刀后仍活但剩血 ≤12%」→ 这一刀升格处决:魂柱冲天+暴击粒子雨+横扫月牙+利刃终结音,当场毙命,深红粗体「处决!」。
- **豁免**:最大血量 ≥5 万(m263 后 BOSS 全在 25 万+)或凋灵——BOSS 该一刀一刀磨,处决只负责清小怪爽;本来这刀就致死的不抢戏,交还原版。
- **实现**:ALLOW_DAMAGE 预判 → 取消原伤害 → 清无敌帧 → REENTRY 旗标下补一记玩家名义 1e7 致死刀(坦克折减重放同款嵌套模式)——走真实伤害管线,掉落/经验/击杀归属/m239 击杀闪光确认音全部自然触发,零重复演出。注册位=格挡后、打击感前。
- 配置 +3(enableExecute/executeThresholdFraction/executeBossHpExempt),两笔合并 **configVersion 64→65**。
- **待编译验证 0**:全部符号在树已编(HostileEntity/WitherEntity(entity.boss 包,BossHandler 先例)/takeKnockback/timeUntilRegen/playerAttack/SWEEP_ATTACK 粒子=原版横扫月牙)。
- 实机盯:①掐点右键格挡骷髅箭/僵尸拳看金字弹反、怪被弹飞、自己 3 秒力量;②按住右键不放确认蹭不出弹反只出普通格挡;③把僵尸打到残血看「处决!」魂柱一刀收;④打 BOSS 确认不触发处决。


## m271 强化保护卷全被动化(2026-07-24)
- 实机反馈「为什么不是消耗还需要去用,进永夜没时间用」——根因=保护卷主路径是右键激活护盾,m198 的整次预扣只在会摸到碎裂等级时兜底,玩家心智负担仍在。
- 修=碎裂瞬间直接从背包**自动扣一张**抵挡(attempt 碎裂分支新增 consumeOneProtectScroll 兜底),右键改纯说明不消耗;旧存档已激活护盾仍被优先认,零迁移成本。
- 新增 system/InventoryDeepScan 深扫助手:背包全槽位 + 深入一层「原版容器组件」物品(潜影盒等),保护卷/自动强化材料/自动吃书共用。
- **精妙背包(SophisticatedBackpacks)查证结论**(clone 其 1.21.x 分支核实):NeoForge 端 + 内容存世界数据(contentsUuid→BackpackStorage)不在物品本体——与本 Fabric 模组既装不到一起也读不到,**无法支持**,结论写入助手类注释防止以后再查一遍。
- 待编译验证:ContainerComponent 三符号(DataComponentTypes.CONTAINER / iterateNonEmpty / fromStacks)。写回经组件重建,空位可能压缩(不丢物品只影响摆放)。

## m272 夜蚀装备属性提升(2026-07-24)
- 实机反馈「夜蚀装备属性太低」。材质=总防 33→48(9/13/17/9)、韧性 6→12、每件击退抗 0.1→0.2、附魔亲和 20→25。
- 新增「夜蚀共鸣」BlightSetHandler:每穿 1 件 +10% 生命 +6% 攻击(ADD_MULTIPLIED_BASE 乘基础值,随成长曲线走后期不缩水),集齐 4 件额外 +10% 移速;每秒重算、值不变不重挂(防生命上限重挂闪血条);盔甲 tooltip 同步显示。
- 配置 +3(blightSetHpPct/AtkPct/SpeedPct)。**本笔一并预写 m272~m276 全部配置字段,configVersion 65→70 一次到位**。
- 待编译验证 0(applyAttribute 照 ArtifactManager、getModifier 照 PlayerUpkeepHandler)。

## m273 连击计数器(2026-07-24)
- 命中累计连击(同 tick 横扫/回旋多目标只算 1 连),comboTimeoutTicks(默 5 秒)没打中清零;每 5 连一档给 攻击+4%/攻速+3%(ADD_MULTIPLIED_TOTAL,封顶 40%/30%,五项全可配)。
- ComboPayload 实时同步;HUD 在热栏右上画「N 连击」,档位越高颜色越燥(白→黄→金→橙红→亮紫),计数跳动瞬间放大回落(getMatrices push/scale 照 TitleScreenMixin),tier≥1 附小字加成速览(展示按默认档速算,真实值在服务端)。
- 观察者挂 ALLOW_DAMAGE 永远放行;注册排在过滤/格挡之后=被取消的伤害不涨连击;下线即清。待编译验证 0。

## m274 BOSS 阶段转换·半血狂暴(2026-07-24)
- 五只皮肤 BOSS(阿努比斯/龙/凤凰/法师/红蛛)+ 佩恩(按名「佩恩·天道」识别,PainBossHandler 同口径)血量跌破 bossRageThreshold(默 50%)一次性狂暴:攻击 +35%、移速 +25%(可配)。
- 演出照 BossEntranceFx 口径:48 格内血红大字「狂 暴」+ 副标「XX 被激怒了 · 它不再留手」+ 重震闪光 + 龙吼 + 怒焰双螺旋(FLAME+ANGRY_VILLAGER)+ 熔岩爆点。
- 一次性=打怪物命令标签 yongye_raged(存档持久,重载/凤凰重生回血都不二次触发);用「扣完这刀后的血量」判越线,直接打死不演。
- 配置 +4。待编译验证:Entity#addCommandTag/getCommandTags(yarn 命令标签标准名,原版 /tag 同源,低险)。

## m275 击杀顿帧(2026-07-24)
- CombatFxPayload 加 hitstop 字段(全部构造点九处同步补参);服务端重击发 2t / 击杀发 4t(×combatFxHitstopScale,enableCombatFxHitstop 总开关;轻击不停避免连打发黏);m269 完美格挡弹反也给 3t 金铁交鸣感。
- 客户端收到后把第一人称挥臂计时按住 N tick 到点自然续上——只回卷挥臂观感,不碰攻击冷却/判定;连杀取最重封顶 6t;没在挥手立即放行。
- 待编译验证:lastHandSwingProgress 公有字段(另两个挥臂字段在树已用;报错删那一行只损失一帧插值平滑)。

## m276 自动强化卷 + 自动吃书卷(2026-07-24)
- 作者两张卷轴图走固定管线(洪泛清白底→裁剪→64×64)入库;TimedAutoScrollItem 一类两 Kind,模型/双语 lang 齐。
- 右键激活 autoScrollDurationTicks(默 60 秒),重复使用叠时长封顶 5 分钟;截止时间存持久附件(下线重连不清零),到期边沿播报。
- 自动强化:每 3 秒深扫背包+潜影盒吞全部强化材料,强化主手(不可强化则等级最低的一件身上盔甲);走 EquipmentEnhancer.attempt 正规管线——碎裂/被动保护卷(m271)/成功率与手动完全一致,自动化不解锁任何超模路径。
- 自动吃书:每 1.5 秒研读一本技能书/血量书(SkillEffectManager.learn / PlayerSkillManager.learnHealth,满级跳过找下一本)。
- 获取:杀怪掉落(0.3%×2,独立于保护卷开关)+ 任务奖励概率进池(10% 起随永夜每级 +2%)。
- 实机盯:①保护卷放背包直接强化到碎裂线看自动抵挡;②穿夜蚀 4 件套看共鸣 tooltip 与移速;③连砍看 HUD 连击与断连清零;④把佩恩/凤凰打到半血看「狂 暴」演出与攻速变化(凤凰 30% 浴火重生回血后不二次狂暴);⑤重击/击杀感受挥臂定帧;⑥两张卷轴各激活一轮:塞一叠材料+几本书进潜影盒验证也能被吃到。


## m277 夜蚀套装换作者GPT美术(2026-07-24)
- 四张图标(头盔/胸甲/护腿/靴,暗紫水晶+青蓝符纹)走固定管线(洪泛清底→裁剪→64×64)覆盖 m265 程序化打底图。
- 两张穿戴层 crystal_void_armor_layer_{1,2} 尺寸正好 64×32 标准 UV;分部位核验=四部位都有内容但每面覆盖约为在树版一半(AI 只画主要面,头盔顶/背面有镂空风险)→ **与在树程序化底层合成**:底层压暗 20% 打底保所有面,上传层压顶出风格,色系一致(暗紫+青蓝)。
- 纯资源零 Java 零配置(configVersion 仍 70)。实机盯:穿全套 F5 转一圈看各角度无镂空、图标观感;嫌底层痕迹重可再调压暗系数或改纯上传版。

## m278 格挡值进血条面板(2026-07-24)
- 沙箱教训:上一轮 m278~m280 本地做完没 PAT 未推,沙箱清空全丢,本轮从 m277 重做——再次印证守则"一笔一推别攒着"。
- 新 GuardSyncPayload(cur/max/破防剩余tick/是否持械)每 5t 下发;持械即建档,条一出现就是满值,不再依赖有职业。
- HudCompactMixin:MP 条下方加青蓝格挡条(m142 同质感渐变+高光+末端光头),余量<30% 转橙预警;破防=整条红色呼吸闪烁+右侧倒计时秒数(客户端每 tick 递减保平滑)。
- 有格挡条时面板整块上移 6px 不压物品栏;永夜阶段名(h-66)与灾厄核心箭头(h-82)读 ClientStats.guardBarShown 连锁上移。
- 原每 4t 刷屏的 action bar 十格条降级为面板未接管期(总血≤60)兜底;「格挡已恢复」同口径。零待编译验证。

## m279 连击特效(2026-07-24)
- 升档瞬间:档位色双方环(直角+45°,fill 画 1px 方框)从数字中心外扩淡出 + 称号弹字上浮(凌厉→狂怒→无双→灭世)+ 经验球叮声随档位升调(0.8+0.2×档,封顶2.0)。
- 常驻:2 档起数字伪辉光(0x38 透明同色八向 drawText 描边),3 档起 1px 高频抖动(currentTimeMillis/50 伪随机),跳动放大回落照旧。
- 断连:10 连以上被断(服务端本就发 count=0),灰字「连击中断 ×N」下沉淡出 30t。
- 总开关 enableComboFancyFx(关=回 m273 素版);连同 m281 字段一并写入,CURRENT_CONFIG_VERSION 70→71。零待编译验证。

## m280 穿戴层重制+头盔隐形(2026-07-24)
- 作者反馈:头不好看不要显示 + 参考宇宙水晶皮肤改。上一轮成品丢失,本轮基于 m277 在库贴图重做(参考图已随沙箱清空,程序化复刻该质感)。
- layer_1 头盔 UV 区(y<16,含帽层)整像素清空——盔甲 cutout 渲染丢弃全透明像素=头盔戴上不渲染、属性/共鸣照常;恢复=换回贴图,零代码。
- 身/臂/靴(layer_1)与腿(layer_2)逐像素重上色:深空底+46 簇紫青水晶辉光+零星星点,以原图明暗 lum 调制(65% 新质感+35% 原图)保留 m277 作者线稿结构。核验:头区最大 alpha=0,身区 756 像素完好。

## m281 装备不可摧毁+抢不走(2026-07-24)
- 统一口径 BlightArmorItem.isSoulbound(夜蚀盔甲本体或任何带 BLIGHT_OWNER 组件的物品),全部保护走同一判定。
- 掉落物:①ItemEntity.damage HEAD 取消(火/岩浆/爆炸/仙人掌全免)+ 四件 Settings.fireproof(岩浆漂浮,下界合金同款);②setNeverDespawn 永不消失(yarn method_35190 已核);③虚空营救(世界底-32 抢在原版销毁线前):主人在线=塞回背包(满则脚下掉出),离线/未认主=setNoGravity+清速度钉在虚空边缘悬浮等主人。
- 抢不走:④MobEntity.loot HEAD 取消(僵尸系/精英怪捡地上的装备全拦);⑤精英缴械(EliteHandler)夺武器/扒护甲两分支豁免;⑥ItemCleanupHandler 定时清理豁免。
- 耐久面:inventoryTick 自动补 UNBREAKABLE 组件(耐久永不下降,tooltip 自带"无法破坏"行,老装备自动补齐);tooltip 加"不可摧毁·火/爆炸/虚空不灭·怪物与精英抢不走"。
- 开关 blightArmorIndestructible(默认开)。待编译验证三处、互不依赖各有退路:loot 注入点(报错删 mixins.json 该行)、DataComponentTypes.UNBREAKABLE/UnbreakableComponent(报错注释 inventoryTick 两行)、fireproof(报错删 .fireproof())。

## m282 穿戴层换 AvaritiaNeo 底·头盔恢复显示(2026-07-24)
- 作者:「如果是用 AvaritiaNeo 里的皮肤改的话就不用隐藏了」。clone 核验:MIT(Copyright 2025 Aqua3,全仓库单一许可无资产限制)→ 可改作,署名入 THIRD_PARTY_NOTICES.md(本仓库首次实际引入第三方资产)。
- 以其 infinity_layer_{1,2}.png(64×32)为底:专业画师层图头盔顶/背全覆盖(头区 268px),根治 m277"AI 只画主要面"的镂空病根;m280 的头区清空随整图替换自动撤销=头盔恢复显示。
- 它的宇宙感来自着色器蒙版(mask 那几张),不接;改静态烤入:保留原甲片线稿明暗(lum),整体重映射为夜蚀紫色阶(暗10,6,26→亮214,178,255)+40 簇紫青水晶辉光(轻,线稿担主角)+零星星点。
- alpha 归一为 0/255 两档,cutout 渲染安全。纯资源零 Java 零配置(v 仍 71)。实机盯:穿全套 F5 转一圈看头盔在、各面无镂空、紫青质感;嫌头盔造型不好看再说,那就回"隐藏头盔"一行方案。

## m283 HUD 防遮挡(2026-07-24)
- 实机截图证实三处重叠,逐个治:①连击块(y=h-62)压面板右侧标签(护甲数/20/20/坚守)→整块上移 h-112,加成行 h-100;②「坚守」「格挡」上下只隔 6px、CJK 字高 9px 必叠→格挡标签/破防倒计时改画条左侧(面板外,与 HP 速率同侧不同排);③处决!/完美格挡!/蓄力条走的原版 action bar 画在 h-68,正压永夜阶段名 h-66 →面板接管期 renderOverlayMessage 矩阵 push/translate(0,-18)/pop 抬到 h-86,核心箭头再上移 h-108(不再随格挡条位移)。
- 底部中列自下而上:面板(顶 h-61/-55)→ 阶段名 h-66/-72 → action bar h-86 → 箭头块 h-100~113;右列:面板标签 → 连击块 h-112。互不压。
- 待编译验证一处:renderOverlayMessage+RenderTickCounter(yarn method_55800 / class_9779=net.minecraft.client.render.RenderTickCounter 已核,仓库首用);require=0,失败只回旧重叠不崩,报错删那两个注入+字段即可。

## m284 连击颜色/称号扩容(2026-07-24)
- 作者:「连击要多做一些颜色,名称也要多写一些」。档位色 5→10 档:白→黄→金→橙红→亮紫→青→天蓝→品红→血红→白金;50 连(10 档)以上彩虹流转(色相 1.8s 循环,复用在树 hsvToRgb)。
- 称号 4→10 阶:凌厉/迅猛/狂怒/无双/修罗/鬼神/灭世/弑神/超凡入圣/万象俱灭。
- 冲击环/称号/辉光全部同走 comboColor(tier) 统一取色,顶档环也是彩虹。零新 API 零待编译验证。

## m285 灵魂绑定兼容 keepInventory(2026-07-24)
- 实机反馈:开死亡不掉落时仍触发「灵魂契约生效——装备回到了身边」——m265 注释里"keepInventory 时 dropInventory 不会被调"在 1.21.1 不成立。
- SoulboundDropMixin 显式判 GameRules.KEEP_INVENTORY:开着=完全不介入不截留(原版本来就全保留),播报自然消失;关着=截留/归还照旧。
- 待编译验证:KEEP_INVENTORY/getBoolean(标准常量+取法首用,报错删那一行回旧行为)。

## m286 状态泄漏清理(2026-07-24)
- WeaponGuardHandler.STATES 与 ClassSkillHandler 的 lastCombat/lastAtkSync/tankLastMove/tankLastPos 五个按 UUID 的 Map 此前只增不减,长开服务器缓慢累积——统一挂 ServerPlayConnectionEvents.DISCONNECT(JOIN 在树同类)下线即清。纯服务端,零行为变化。

## m287 濒死危机演出(2026-07-24)
- 战斗帅方针:血量≤lowHpFxThreshold(默 20%)→ 屏幕边缘血红渐晕(视野压缩同款分级 fill 画法)随心跳呼吸,越残越浓越大越急;配监守者心跳音(间隔 26t→12t、音量音调随残血抬升)。
- 纯客户端观感,开关 enableLowHpFx;配置 +2,CURRENT_CONFIG_VERSION 71→72。待编译验证:ENTITY_WARDEN_HEARTBEAT(家族在树,常量首用,报错换 ENTITY_WARDEN_AMBIENT)。

## m288 战况看板:杀怪统计+天数+阶段预告(2026-07-24)
- 位置选定**左上角**(3,3):底部中列/右列 m283 刚排满,顶部中央是 BOSS 血条区,左上是整屏唯一长期空闲区;半透明黑底+蓝顶描边保证任何背景可读。
- 行1「第 N 天 · 击杀 X」:天数不走包——昼夜时钟原版就同步,客户端直接 ProgressionManager.gameDay(m252 收口,睡觉跳夜也算天)+1 起算;击杀走 TOTAL_KILLS 附件(persistent+copyOnDeath,口径=Monster+玩家击杀,与保护卷完全一致),NumFmt 紧凑显示。
- 行2「下一阶段:XXX mm:ss」:NightfallManager 新增 getNextLevelName(含深渊 N+1 层)/getEscalateRemainingSeconds(久留升层倒计时;未入永夜/久留关/已至上限=-1 只显示名);最后 60 秒倒计时转红;已至上限整行省略。任务失败等事件升层不预告(那是突发,预告的是"什么都不做也会到"的时间线)。
- HudInfoPayload 每 20t 下发;开关 enableHudInfoPanel,配置 +1,CURRENT_CONFIG_VERSION 72→73。
- 待编译验证(均极低险):writeVarLong/readVarLong(PacketByteBuf 标准方法首用)、Codec.LONG(Codec.INT 在树同类)。

## m289 按天事件预告+看板挪位(2026-07-24)
- 实机截图:左上被第三方小地图(Xaero类)整个压住——m288 选位没算第三方 HUD。挪到**左边缘垂直居中略上**(中线-10):左上/右上=小地图、左下=聊天、右中=计分板、顶中=BOSS血条、底中=面板,左中是整屏唯一没人抢的常空区。
- 作者真正想要的是「第几天会出现什么」:全库 gameDay 门槛收口成一张预告表,按**实时配置值**取(改配置预告自动变)——佩恩(5)/怪物挖掘(5)/精英着装(5)/袭击队长(8)/怪物BOSS(10)/野生黑龙(10)/阿努比斯(10)/大地侵蚀(12)/红蜘蛛(12)/死亡法师(14)/浴火凤凰(16),外加周期事件怪物进化(每 evolutionEveryDays 天取下一整倍数日)。
- 取最近一个未到的事件日,同日多件并列(最多 3 件+「等」),尾注(还有 N 天),只差 1 天转「(明天!)」整行橙红。口径:门槛判 gameDay>=minDay(0 起算),展示=minDay+1(第 1 天起算)。全部已过=只剩周期进化;都没有=整行省略。
- HudInfoPayload 加 dayForecast 字段(构造点已全同步);看板第三行暖橙。零新 API(VarLong/String 上一笔已用)。

## m290 吸血强化技能书(2026-07-24)
- 作者供图走固定管线(裁透明边→64×64 LANCZOS)入 skill_book_lifesteal;SkillType 尾部加 LIFESTEAL——注册/创造栏/两条掉落池/佩恩与核心奖励/命令全部 values() 循环自动覆盖,零逐处接线;成长面板固定 6 项顺序在前不受尾部追加影响。
- 效果:ALLOW_DAMAGE 观察者永远放行只旁听,口径同处决=只认「玩家直接近战」(source.getSource()==攻击者;弓/魔法/召唤物不吸),不吸玩家;回血走 heal(),禁疗系统照常拦。
- 数值(作者点名「不能太高」):每级 +0.4%(skillLifestealPerLevel),**封顶 8%**(skillLifestealMax)——技能书等级上限 10 亿,必须靠封顶封死不能按级裸乘;与武器强化吸血(+1000 级起 10%~50%)是两条线,叠加后仍远低于伤害本身。
- 配置 +2,CURRENT_CONFIG_VERSION 73→74;双语 lang 两条;零新 API 零待编译验证。

## m291 六新强化技能书(2026-07-24)
- 作者供图六张(暴击/迅捷/破甲/屹立/贪婪/回春)全部实装。图标管线本轮升级:上传图是**白底 RGB**(非透明 alpha),固定管线前加「边缘泛洪抠图」——只有与图像边界连通的近白区判背景(保住书面内部白色星点),再做 24 轮透明区颜色外扩(防 LANCZOS 缩图白晕),64×64 + alpha<25 清尘。
- SkillType 尾部追加 CRIT/SWIFT/PIERCE/STEADFAST/GREED/REJUVENATE(尾部追加保证既有序号不漂移);注册/创造栏/掉落池/佩恩与核心奖励/命令全 values() 循环自动覆盖,零逐处接线。
- 效果与数值(百分比一律封顶,口径同 m290 吸血):
  - **暴击**:亲手近战概率追加 (倍率1.5-1)×原伤,走 playerAttack(吃护甲、能触发击杀归属),CRIT 粒子+暴击音;每级 +0.2% 概率封顶 25%。与 m290 吸血监听合流为「近战触发合流」一次判定,追加伤害置 procApplying 防重入(套路同 HighHpCounter 的 applying,proven)。
  - **破甲**:亲手近战追加 amount×比例 的 magic 伤害(无视护甲,口径同高血量反制穿甲刀);每级 +0.3% 封顶 30%。
  - **迅捷**:移速/攻速 ADD_MULTIPLIED_TOTAL 百分比,每级各 +0.2%,封顶 +30%/+25%;走 applyAttributes,setModifier 加 Operation 重载(原三参转调,零行为变化)。
  - **屹立**:击退抗 ADD_VALUE 每级 +0.5% 封顶 60%(属性本身 0~1)。
  - **贪婪**:玩家亲手击杀 → 额外经验 = 基准5 × min(100%, 级×1%),挂 BonusXpHandler AFTER_DEATH,与精英+分档独立叠加。
  - **回春**:脱战 160t(8s)后每秒回 最大生命×min(3%, 级×0.1%);REJUV_LAST_COMBAT 出手/挨打双向记时,DISCONNECT 下线即清(m286 口径),与持续恢复(平铺常时)错位互补。
- 配置 +17,CURRENT_CONFIG_VERSION 74→75;模型 json×6 + 双语 lang×12;零新 API 零待编译验证。

## m292 禁疗改版「重创减疗」+ 饕餮心脏免疫(2026-07-24)
- 作者四点名逐条落地:①**减疗而非全禁**——期间治疗 ×(1-healBlockHealReduction=0.7),新 ArtifactManager.healFactor 统一系数,六个回血入口全接(技能恢复/饱食回血/回春/吸血/吞噬技能/饕餮击杀回血),全仓 NO_HEAL_UNTIL 裸判清零只剩三个合法住址;②**只 BOSS 触发**——healBlockBossOnly 默认 true,精英与永夜普通怪退出施加名单;③**免疫 CD**——结束后 400t(20s)内不可再施加,判 `now >= prevUntil + CD` 一条式同时防「生效中叠加」与「无缝续」;④**神器免疫**——饕餮心脏 ≥4 级(神话)完全免疫(免疫时写零时长记录吃 CD、顺带节流金字提示),未达级每级缩短时长 15% 保底 1s。
- **纠错入册**:m290 注释声称「回血走 heal(),禁疗照常拦得住」——不实。禁疗从来是**逐入口手动判**,heal() 本身无拦截,m290 吸血当时并没有接检查,禁疗期间照吸。本轮借 healFactor 统一真接上。教训:声称「X 系统会管住」前先确认 X 的作用机制是拦截式还是自觉式。
- 施加/免疫各配 action bar 播报(深红「伤口被缝住了!治疗效果 -70%」/金字「饕餮心脏吞噬了重创诅咒」);DifficultyScreen 简介与 regen/lifesteal 双语 desc 同步改词「重创减疗」。
- 配置 +5,CURRENT_CONFIG_VERSION 75→76;零新 API 零待编译验证。

## m293 等级加法整型溢出加固(2026-07-24)
- 起因=作者问「18,446,744,073,709,551,615 是游戏里最大的数值,应该不止 10 亿吧」。**答疑对齐**:2^64=18.4Qi 是 m220 抬的**属性上限**(血攻甲韧,存 double);技能书**等级**是另一个数,上限=10 亿(m149 定的,存 int,int 上限 21.4 亿);百分比类效果全靠封顶封死,等级再高也只到封顶,平铺类(攻击书 0.5/级)10 亿级=+5 亿攻已够摸属性上限,故等级存储不升 long(大手术零收益)。
- 但审出**真隐患**:等级加法多处裸 int 运算,堆近 21.4 亿再加会回卷负数——最狠的是 EquipmentEnhancer.addLevels 回卷后被 withLevel 钳 0 = **装备等级一夜清零**。七处全部改 long 运算后钳 [0, Integer.MAX_VALUE]:addLevels/attempt 免失败直通端/保护卷预判比较/安全段后到顶停推/循环内 level++ 防回卷、SkillEffectManager.learn 与 useAllBooks 两处「等级×数量」(1e9×64=6.4e10 乘法先溢出)、PlayerSkillManager.learnHealth。
- 纯防御性修复,常规数值零行为变化;零配置(仍 76)零新 API 零待编译验证。

## m294 强化石十档:物品+四入口接线+大额溢出加固(2026-07-24)
- 起因=作者供十眼渐变图一张 + 定稿的掉落机制设计(十档数值+合成+书分档+滑动窗),本笔先落"物品与接入"。
- 面值 = 10^(档-1):1 / 10 / 100 / 1000 / 1万 / 10万 / 100万 / 1000万 / 1亿 / 10亿。**封顶 10 亿不做 100 亿**:强化等级存 int(顶 21.4 亿,m293 已加固),100 亿一颗只能吃进 21.4 亿、七成八直接蒸发;10 亿档两颗多一点正好摸顶,是"整颗都有效"的最大档。
- **强化石直加等级、必得不碎**:口径同工作台 EquipmentEnhanceRecipe.addLevels 与强化继承 m236("等级本就是材料+概率挣来的,不再赌一次");传统材料照旧走 attempt() 逐级 RNG(失败/碎裂/保护卷全套不变)。新 EquipmentEnhancer.MaterialSum(direct/budget 两账,全 long)+ enhanceWith(先直加后 RNG,合并 EnhanceResult)。
- 四条强化入口全接线:enhanceFromInventory(背包一键)/EnhanceScreenHandler.applyUpgrade(界面)/EquipmentEnhanceRecipe(工作台,本就直加,仅 long 化)/AutoScrollHandler(自动卷轴深扫)。previewLevels 与 totalMaterialLevels 同步 long 化后钳 int——**10 亿面值 × 一叠 64 = 6.4e10,原裸 int 乘法/求和会先溢出**(m293 同款隐患,本笔审出四处全修)。
- 贴图管线:作者原图自带 alpha,按 alpha>25 连通域切出十档包围盒 → 方形留 4% 边 → LANCZOS 缩 64×64 → alpha<25 清尘;肉眼核过渐变顺序与细节。模型 json×10(item/generated),lang 双语十条(强化石·平静之瞳 → 爆裂魔瞳),稀有度 1-3 普通/4-5 罕见/6-7 稀有/8-10 史诗,创造栏排在血核之后。
- 零配置(仍 76)零新 API 零待编译验证(Rarity/appendTooltip/TooltipType 全在树先例)。

## m295 强化石十合一向上合成(2026-07-24)
- 定稿设计里的「10合1 向上合成」。3×3 工作台每格只扣 1 个、放不下 10 颗,故不走配方——改**右键整叠一次并完**:手持 N 颗第 t 档(t<10)右键,得 N/10 颗第 t+1 档,余数 N%10 留在手里;面值严格等值(10 颗 10^(t-1) = 1 颗 10^t),攒的小石头永远不废,顺手解决爆背包。
- 10 档(爆裂魔瞳)到顶,右键提示不可再合成;不足 10 颗提示所需数量。合成产物 insertStack 进背包,满了掉脚下(SoulboundItemGuard 同款兜底)。ANVIL_USE 升调音效 + 金字 action bar 播报「X 颗 → Y 颗 上档名」。
- tooltip 增加用法行(右键:整叠向上并);零配置(仍 76);零新 API 零待编译验证(use/TypedActionResult/insertStack/dropItem 全在树先例,照 SkillBookItem.use 口径)。

## m296 强化石滑动窗掉落(2026-07-24)
- 定稿设计的「按阶段掉落·滑动窗」。新 `EnhanceStoneDrops`(system 包):
  - **基准档 t**:第 1~5 天=1;佩恩降临后(gameDay ≥ painSpawnMinDay,默 5)=2;进入永夜 I=4、每升一层 +1(即 3+NightfallManager.getLevel(),深渊层继续爬);每次怪物进化(每 evolutionEveryDays 天,默 10)再 +1;封顶 stoneTierCap(默 10)。每升层/每进化,地上的眼球肉眼可见换一个颜色——十档渐变按序见证。
  - **普通怪** stoneDropChanceNormal(默 5%)掉一颗:70% t / 25% t+1 / 5% t+2。**不乘动态爆率 lm**——档位窗本身就是防滚雪球的进度闸,再叠一层强度衰减会互相打架(有意为之,权重可配)。
  - **精英**必掉一颗:50% t+1 / 40% t+2 / 10% t+3(挂在 LootHandler 精英分支)。
  - **BOSS**必掉 stoneBossMinCount~Max(默 3~5)颗、档位 t+2~t+4 均匀,颗数随 bossDropMultiplier 放大(挂在 BossHandler.dropBossRewards)。
- **封顶期收口**:t 到顶后若普通怪也满地掉最高档,一晚全毕业——stoneTopTierEliteOnly(默开)把普通怪的最高档降为次档,10 亿石只从精英/BOSS 出(毕业节奏=几十只精英或几个 BOSS,可关)。
- 配置 +15(开关/概率/三组窗权重/BOSS 颗数与偏移/封顶/收口),CURRENT_CONFIG_VERSION 76→77;零新 API 零待编译验证(全在树先例)。

## m297 技能书×100分五档+按阶段掉落(2026-07-24)
- 定稿设计的书分档:**×100 一档、共 5 档**(1 / 100 / 1万 / 100万 / 1亿)。不做 10 亿单本——一本直接满级等于把成长线一刀砍死,留「十本 1 亿大书」的收集过程。书档 b = (石档 t + 1) / 2(石 ×10 一档、书 ×100 一档,量级对齐),与强化石共用同一进度基准(EnhanceStoneDrops 新增 bookTierLevel / stageBookTier / bookLevelFor)。
- **攻击书独占高档**:十几种书里只有它是平铺无封顶(0.5/级)、吃得下大数;百分比类(吸血 8%/暴击 25%/破甲 30%/闪避 50%…)封顶都极低,掉百万级纯浪费掉落位——钳 skillBookPercentTierCap(默 2 档=100 级,两本内全能摸各自封顶)。
- 接线三处 LootHandler(普通怪概率书/精英概率书/精英必爆套餐——套餐里血量书照旧走配置区间)+ BossHandler(改必掉 bossBookMinCount~Max 默 1~3 本、skillBookBossAttackBias 默 0.5 概率强制攻击书=**攻击书高档的主要出处**,随 bossDropMultiplier 放大);enableStagedSkillBooks 关掉全部回旧固定小等级。血量书体系(COMMON~GODLY 池 / BOSS V10~V20)不动。
- 书名大数紧凑显示:SkillBookItem.getName 用 EnhanceStoneItem.cn(整万→V1万、整亿→V1亿);创造栏补攻击书 1万/100万/1亿 三档样本。
- 配置 +5,CURRENT_CONFIG_VERSION 77→78;零新 API 零待编译验证。

## m298 方案D:超上限成长曲线(强化到后面能打动末影龙)(2026-07-24)
- 作者拍板方案 D:「强化到后面就能打动末影龙」。核心认知(m293/龙账那轮):打龙缺的不是等级上限,是**每一级值多少攻击**——等级存储一字节不动(零迁移风险),改折算曲线。
- **曲线**:level ≤ 拐点 K(enhanceCurveKneeLevel,默 1万,与强化失败曲线降底同点)每级 perLevel 原样(前中期手感零变化);level > K 每级增值 ×(level/K)^p(enhanceCurveExponent,默 1.2),总加成用闭式积分 perLevel×[K + K/(p+1)×((L/K)^(p+1)−1)],O(1) 无循环、拐点处连续。
- **龙账(默认参数)**:int 顶 21.4 亿级 → 攻击 ≈ 1.2e15;+10 亿级攻击书(同曲线)≈ 2.3e14;蓄力重斩 3.2 × 暴击 1.5 × 过龙甲(-32%)→ 单刀 ≈ 4~5e15,**跨过龙血 1e19 的 float 粒度墙**(单刀门槛 ≈ 8e11);一条龙命 ≈ 2000+ 刀、三条命一场史诗战(脱战回血 1%/s 仍在,别挂机)。m221「终焉挑战·击败末影龙」成就从永不可达变可达。世界怪物 DynamicScaling 按玩家攻击对位会跟着涨、唯独龙是定值——世界照常难、龙从死墙变能打。
- **落点四处 + 攻击书**:withLevel 武器/HYBRID(hybrid 折减照乘在曲线总量上)、critBonusDamage、WeaponInfoScreen 显示(与实际属性同式,不再各算各的);攻击书原写死的 0.5/级开成 skillAttackPerLevel 并入同一条曲线,zh/en 两条 desc 同步提示拐点。
- enableEnhanceCurve 关 = 全部回纯线性;配置 +4,CURRENT_CONFIG_VERSION 78→79;零新 API 零待编译验证(Math.pow/既有属性修饰全在树)。

## m299 召唤物免友伤:傀儡/肝帝/暗影分身(2026-07-24)
- 作者两点名:①召唤师的攻击会伤到自己的召唤物;②查一下分身术会不会同样。**核验结论:会**——WarlockCloneEntity(术士暗影分身 m262)是普通 PathAwareEntity,和铁傀儡一样吃主人横扫,一并修。
- **病根**:自家范围技(回旋斩/蓄力重斩)的目标过滤本就只认 Monster/IS_ELITE,不会误伤;漏的是**原版路径**——横扫之刃对弧内一切 LivingEntity 结算、近战误点、弹射物,全走 damage()。
- **修法**:新 SummonFriendlyFireHandler 挂 ALLOW_DAMAGE(取消式,口径同 ForeignDamageFilterHandler 先例):受击者是三类己方召唤物(召唤师铁傀儡 tag yongye_summon / 肝帝天团 GanDiEntity / 暗影分身 WarlockCloneEntity)且 source.getAttacker() 是玩家(直接近战、弹射物=射手)→ 取消。**不做「只豁免主人」**:召唤物只打怪从不打玩家,任何玩家对它们的伤害都只可能是误伤(联机队友横扫同理);GanDi/分身也没有公开 owner 取值口,按全体玩家免最稳。
- 环境伤害(岩浆/摔落/无主爆炸)与怪物攻击照常生效,寿命到点自散/再召唤散场不受影响(不走玩家伤害)。开关 summonFriendlyFireImmune(默开),配置 +1,CURRENT_CONFIG_VERSION 79→80。
- 顺带:横扫命中被取消后,连击/暴击等 ALLOW_DAMAGE 观察者对该目标不再误触发(打自己傀儡不该涨连击)。零新 API 零待编译验证。

## m300 击杀归属统一:召唤物击杀记主人(2026-07-24)
- 作者实机反馈(选召唤师):「杀了一堆怪物,击杀只显示 2」「爆率不对吧」「召唤物击杀也算这个人击杀,要么就没意思了」。
- **病根**:爆率没坏,是**归属坏了**——全库「玩家击杀」口径只认 source.getAttacker() instanceof Player。召唤师绝大多数怪是傀儡杀的:看板 TOTAL_KILLS 不涨;LootHandler 的 lootRequirePlayerKill(默开)直接 return = 随机掉落/强化石/技能书全部跳过;保护卷计数、贪婪经验、击杀任务、蚀域掉落同理。
- **修法**:SummonerHandler 新增 **creditedKiller(DamageSource)** 统一口径——攻击者是玩家 → 本人;是己方召唤物 → 折算到主人(在线才算):肝帝/暗影分身各补 public getOwner(),傀儡用内存表 byOwner 反查(量小直扫)。六处全改走它:
  ① KillStatsHandler 看板计数(作者截图那行);② LootHandler 掉落门 + baseLm 动态爆率按主人强度算(防召唤师白嫖满爆率);③ ProtectScrollHandler(保护卷计数+自动卷轴掉落);④ BonusXpHandler 贪婪额外经验(傀儡杀怪原版本就不掉经验球,这份保底经验归主人,召唤师不至于零经验);⑤ QuestManager 击杀/猎精英任务;⑥ NightBlightHandler 蚀域掉落门。
- **不动**:武僧「空手击杀拳意+1」仍只认亲手空手(机制本义);连击/暴击/吸血/处决是命中系个人技,不涉击杀归属。
- 开关 summonKillsCreditOwner(默开,关=回只认亲手);配置 +1,CURRENT_CONFIG_VERSION 80→81;零新 API 零待编译验证(getPlayerByUuid 在树先例=GanDi 台词)。

## m300a 编译修复:GanDiEntity.getOwner 补丁未命中(2026-07-24)
- 作者 build 报错:SummonerHandler 引用 GanDiEntity.getOwner() 找不到符号。
- 根因:m300 用脚本批量打补丁,GanDi 的 setOwner 实际比我拿来匹配的旧文本多一行 this.setPersistent(),str.replace 未命中= **静默跳过**,而脚本无条件打印"成功"——假阳性。WarlockCloneEntity 的 getter 是真落上了。
- 修复:补上 getOwner 纯一行 getter;其余 m300 六处接线不受影响。
- **教训(入册)**:脚本化替换后必须逐符号 grep 回验(getOwner 两个文件都该各有一处),不能信脚本自己的打印。

## m301 强化石档位天数墙+爆率编辑器接入(2026-07-24)
- 作者实机(第 2 天,战斗爽/召唤师):「敲死一个怪爆了一个一万级的强化石」「怎么也得十天以后才会爆 1000 级以上」「这个爆率没写进 DEBUG 吗」。
- **病根**:该档第 2 天已因任务失败进永夜 I(截图 HUD「永夜 I·暗潮」),m296 基准档公式「进永夜 I = 4 档」被提前触发 → 普通怪 25% 掉 t+1 = 5 档 = 1万级,与实测严丝合缝。设计表原假设永夜 I 是正常推进七八天后的事,没防「早期任务失败推层」这条路。
- **修**:新 stoneDaysPerTier(默 3,0=关)**天数硬顶**——无论永夜/进化把档位信号推多高,最终掉落档 ≤ 1 + 游戏天数/3:第 0~2 天只出 1 档,第 3 天起 2 档,第 9 天起才可能 4 档(=1000 级,正对作者点名的"十天以后"),第 12 天起 1万,第 27 天起才摸 10 档。普通/精英/BOSS 的窗口偏移(+1~+4)同样被顶住;技能书档随石档折半自动被顶。永夜推得快只是提前"够到"墙,不再越墙。
- **爆率编辑器补三旋钮**(作者问得对,m296 确实漏了):EDITABLE_KEYS/labelOf 加 stoneDropChanceNormal / stoneDaysPerTier / stoneTierCap,取值/写盘走 getFieldString+config set 反射零接线;其余窗权重仍走 /yongye config set。
- 配置 +1,CURRENT_CONFIG_VERSION 81→82;零新 API 零待编译验证。
- 附:本条入册晚了一笔——上轮文档脚本的正则只认 m+数字,被 m300a 的字母后缀卡住(代码已随 990708a 推送),正则已改 m\w+。

## m302 全量审计收口:混料碎裂吞石/自动卷轴吞高档石/难度序陷阱(2026-07-24)
- 作者点名全量检查。系统性扫了 m294~m301 全部改面:溢出钳位/四入口一致性/曲线连锁(DynamicScaling·属性上限·暴击·吸血·显示)/掉落经济/击杀归属副作用/难度序比较,查实三处不合理并修,其余结论入册。
- **修①(最重)混料碎裂吞强化石**:enhanceWith 原顺序「先加石、后跑传统材料 RNG」——背包一键/自动卷轴把 10 亿石和碎片混在一起时,RNG 段碎裂会把装备连同刚加进去的石头等级一起蒸发,且石头已被扣,违背"必得不碎"。改为 **RNG 先跑**(在较低等级段跑,成功率只高不低,对玩家有利),碎了直接返回、石段不执行;两个混料入口的强化石改「**成功后才扣**」,碎裂时石头原封不动并播报 [强化石未消耗]。强化界面材料槽是单栈、混不了料,天然无此问题;工作台无碎裂。
- **修② 自动卷轴吞亿级石**:AutoScrollHandler 深扫见材料就吞,会把 1亿/10亿 石自动砸进主手或最低甲——新 autoScrollMaxStoneTier(默 5)只自动吞 ≤1万 档,高档石留给玩家亲手决定砸哪件(0=石头全不吞,10=全吞)。
- **修③ 难度序号陷阱收口**:战斗爽 ordinal=7(m217 存档兼容)但强度 3.2 介于地狱与深渊之间;全库唯一一处「难度 ≥ N」比较(碎裂门槛)按 ordinal 判,默认门槛 3(困难)时行为碰巧正确,但门槛一旦调到 5(地狱)战斗爽会被错误地包含。新 DifficultyManager.strengthRank()(ordinal→强度序 {0,1,2,3,4,6,7,5},未设定按适中)替换之,默认行为不变、未来免疫。
- **审计结论(查过,无需改)**:石掉率不乘动态爆率 lm=m296 有意(档位窗即节奏闸);精英必掉石不受 eliteGuaranteedDrops 约束=有自家总开关 enableEnhanceStoneDrops;m298 曲线只动攻击不动护甲=安全(怪物攻击按天数/进化缩放,不按玩家攻击对位,生存端不被曲线拉爆);吸血 8%×e15 巨额回血被最大生命天然封顶;m300 六处归属无重复变量声明;书名/预览大数显示均走钳后值。
- **遗留(待作者拍板)**:血量书未入 ×100 分档体系(后期血量成长明显慢于攻击,要不要同套路分档说一声);战斗爽下碎裂仍开(强度序上它高于门槛档"困难",合理,保护卷可挡——想让爽档不碎需单独开关,一句话的事)。
- 配置 +1(autoScrollMaxStoneTier),CURRENT_CONFIG_VERSION 82→83;零新 API 零待编译验证;全部替换 assert 命中+grep 回验。

## m303 爆率复检收口:佩恩/灾厄核心书源入分档(2026-07-24)
- 作者点名再查爆率。全链复走:强化石三源(普通 5%/精英必掉/BOSS 3~5 颗)经 m301 天数墙后无越档路径;技能书主链(普通/精英/必爆套餐/BOSS)m297 已分档。
- 查出**最后两处写死等级的书源**:①佩恩死亡奖励 3 本属性书 V5~V15;②灾厄核心被摧毁的奖励书 V1~V3——都在分档体系外,佩恩第 6 天就来,掉 V15 攻击书与"第 9 天才见 1000 级石"的节奏错拍。两处均接入:开 enableStagedSkillBooks 走 EnhanceStoneDrops.bookLevelFor(攻击书吃满当前档、百分比钳前两档、随天数墙),关=回旧区间。血量书两处照旧(未入分档,遗留同 m302)。
- 复检其余结论:LootCrate 查证不掉书;战斗爽下 lm 含难度奖励 ×3.2(m150「难度越高掉落越丰厚」+爽档简介「掉落更多」)是设计口径非 bug;石掉率有意不乘 lm(m296);精英必掉石走自家总开关(m296)。
- 零配置(仍 83)零新 API 零待编译验证。

## m304 皮肤BOSS格挡条+攻击平衡(2026-07-24)
- 作者点名:「boss 也要有格挡条,就是有皮肤的那些;攻击也要平衡」。
- **格挡机制**(新 BossGuardHandler):六只皮肤 BOSS(阿努比斯/浴火凤凰/死亡法师/红蜘蛛/自建龙 + 佩恩——佩恩是带皮肤的 Husk,经 PainBossHandler 新增 isPain(PAIN_BARS 记账)识别)各带格挡值 = 最大生命 × bossGuardFraction(默 20%)。格挡在:实体伤害打 bossGuardDamageCut 折(默五折),格挡值按**原始伤害**消耗;打空 → **破防** bossGuardBreakTicks(默 10 秒):伤害全额 ×bossGuardBreakDamageMult(默 1.25),盾裂音效+给破防者金字播报「破防!」;窗口结束格挡回满,循环。环境伤害不吃格挡。减伤走坦克真减伤 m208 同款「取消+守卫重放」(REAPPLY set),真实伤害一并被格。
- **攻击平衡**:皮肤 BOSS 对玩家的单击伤害钳到 玩家最大生命 × bossHitCapFraction(默 35%)——m298 曲线后世界数值会跑很大,这道钳保证任何阶段至少三刀才可能带走玩家;同款取消+重放(CAP_REAPPLY),0=关。
- **同步零新网络包**:走血条名 ‖ 通道(m187 先例)追加「‖G当前/上限/破防剩余tick」段——五只实体的 10t 名字刷新语句 + 佩恩 bar 循环统一接 barSuffix(破防到点的懒恢复也在这);客户端 BossBarStyleMixin:parseHp 先截断格挡段(否则 "max‖G..." 会把血量解析炸回百分比兜底)、新 parseGuard、血条槽正下方画 3px 格挡条(青蓝余量/破防红色呼吸闪,和玩家格挡条同一套视觉语言);合并组 ×N 不画(成员格挡各自独立,合着画会撒谎)。
- **已知取舍(m208 同款既有行为)**:重放的伤害会再次经过其它 ALLOW_DAMAGE 观察者——连击等命中系对 BOSS 计两次;保留原伤害源不破坏击杀归属/处决口径,故不改。
- 静态自查逮住一个错:五处实体嵌名替换少一个右括号(setName 层未闭),已修并逐文件配平复核。
- 配置 +6,CURRENT_CONFIG_VERSION 83→84;零新 API 零待编译验证(ctx.fill=HudCompactMixin 在树/ITEM_SHIELD_BLOCK=ClassSkillHandler 在树/取消重放=m208 在树)。

## m305 烛之维度:烛块门/紫天/淡紫滤镜/百倍刷怪+实体闸(2026-07-24)
- 作者供「烛块」贴图并点名五件事:烛块搭地狱门形状打火石点燃进入 / 维度内淡紫滤镜 / 紫色天空 / 刷怪一百倍 / 但要实体优化。
- **维度**:数据包三件套——dimension_type(固定正午 6000 亮堂展示紫天、床不炸、非 natural)、dimension(noise 生成用 minecraft:overworld 设定 + 固定群系)、biome candle_wastes(紫天 0x9C6CF0/紫雾/紫水,无降水无特征,原生 spawners 也配了高权重怪表)。
- **烛块/门**:candle_block(作者图 LANCZOS→64,自发光,创造栏);candle_portal 门芯(无物品、无碰撞、AXIS 薄板照下界门口径,视觉模型直接 parent 原版 nether_portal_ns/ew 换贴图,程序化紫焰噪点带透明孔走 cutout);框失支撑连锁塌门。
- **点燃**:UseBlockCallback——打火石右键烛块,点击面外一格空气起扫:沉底找内空矩形(宽 2~21 高 3~21),四边全烛块即整片填门 + 燧石音 + 紫字播报。
- **传送**:门内碰撞、80t 冷却、双向 1:1 坐标;到达取地表,±24 格找现成门,没有就地搭一扇 4×5 烛块门(点好)+ 落脚台,保证能回。**非玩家实体不传送**——猎场怪涌回主世界会炸档,有意为之。
- **百倍刷怪 + 实体优化**:原版地表刷怪≈每玩家 400t 一波;这里每玩家 candleDimSpawnIntervalTicks(默 4t)一波 1~3 只,恰约百倍;落点地表环带 12~40、无视亮度;亡灵出生戴皮革帽掉率 0(固定正午会点燃亡灵,帽子替它烧,husk 口径)。**三重实体闸**(NightfallHordeHandler 无全局闸拖崩 TPS 的教训,照 HardcoreSurvival 口径):①每玩家 48 格内 ≥120 停刷;②全维度 ≥400 硬预算;③每 100t discard 离所有玩家 >96 格的敌对。全部配置。
- **滤镜**:HudRenderCallback 整屏淡紫 fill(candleDimFilterAlpha 默 40,0=关),与 m287 濒死渐晕同挂点同画法。
- 配置 +7,CURRENT_CONFIG_VERSION 84→85。**待编译验证五处,各有独立退路**:①UseBlockCallback(player 事件模块在树=AttackEntityCallback,本类首用);②ServerPlayerEntity.teleport(ServerWorld,…) 跨维传送(原版标准方法,首用);③BlockRenderLayerMap cutout(报错删 YongyeClient 那一行,门退实心渲染);④CandlePortalBlock 一组 Block API 首用(HORIZONTAL_AXIS/noCollision/nonOpaque/getOutlineShape 等,全挂 @Override 编译期即验);⑤getStateForNeighborUpdate 签名(报错删该方法=门不自动塌,功能不受影响)。数据包 JSON 结构(1.21.1 biome/dimension 必填字段)也请 build 后进游戏首验。

## m306 烛之维度落点修复+紫色草地(2026-07-24)
- 作者实机:传送落在基岩层而不是地表;草地颜色也该跟着紫。
- **基岩层病根查实**:`World.getTopY` 对未加载区块有 `isChunkLoaded` 早退——不生成区块、直接返回世界底(-64);新维度首次进入时目标区块必然未加载,落点被 `Math.max(bottomY+2, y)` 钳到 **-62 正好是基岩层**,自动回程门也跟着建进石头里。刷怪侧(CandleSpawnHandler)有 `sy <= bottomY` 守卫且刷在玩家已加载区块内,无此病。
- **修法**:`findOrBuildArrival` 先 `dest.getChunk(x>>4, z>>4)` 强制把目标区块同步生成到 FULL,再从区块自身高度图 `chunk.sampleHeightmap(MOTION_BLOCKING)+1` 采样(绕开 getTopY 的早退分支,这是 getTopY 已加载路径的同款内部调用);`y <= bottomY+2` 兜底落海平面。双向都走此函数,回主世界同修。
- **顺手加固**:自动搭门的落脚平台上方清三格净空——斜坡/山体处到达不再有把玩家埋进土里的窗口。
- **紫色草地**:biome effects 补 `grass_color` 0xA36BE0(薰衣草紫)+ `foliage_color` 0x8F5BD4(深紫,叶/藤),与紫天 0x9C6CF0/紫雾同色系;水色 m305 已紫。石头/泥土不吃群系染色,整体紫罩由淡紫滤镜负责(设计如此)。
- **旧档遗留**:之前落基岩层时自动建的那扇门还埋在 -62 附近的石头里,是孤儿门,不影响新落点(新门建在地表),介意就挖掉。
- 零配置(仍 85)。**待编译验证两处同一行族**:`World.getChunk(int,int)` 与 `Chunk.sampleHeightmap`(均标准 API 首用,报错贴回即修,退路=保留 getTopY 但先 getChunk 强制加载)。

## m307 烛之维度地形重铸:人神大战后的废土(2026-07-24)
- 作者点名:不要泥土,整片土地要「异常破乱不堪——一场人神大战后留下的土地」。
- **病根**:m305 的 dimension 直接引用 `minecraft:overworld` 生成设定,泥土/草地是它的原版表层规则带出来的;表层规则(surface_rule)与地体方块(default_block)都锁在 noise_settings 里,不能单改——必须整套自定义。
- **修法**:新数据包 `worldgen/noise_settings/candle.json`,底子=**从 misode/mcmeta 拉取的原版 1.21.1 amplified.json 逐字副本**(不凭记忆手写巨型 worldgen JSON;amplified=撕裂式险峻地形——断崖/悬空碎块/深壑,天然一副被巨力轰碎的样子,零自造密度数学),只动三处:
  - `default_block` stone→**deepslate**(地下整体=石化焦土,不再有泥土层);
  - `surface_rule` 整套换自写废土版:基岩地板(照原版,防漏虚空)+ above_preliminary_surface 带内地表补丁——**哭泣黑曜石**(gravel 噪声>1.2,神血凝晶紫光疤)/**黑曜石**(0.8~1.2)/**岩浆块**(surface 噪声>1.1,灼痕)/**黑石**(0.35~1.1,焦土)/**圆石深板岩**(±0.35,瓦砾)/**裂纹深板岩砖**(-1.0~-0.35,被灭文明的断壁地基)/**凝灰岩**兜底(灰烬);表层下垫圆石深板岩;深层洞穴地板保持裸深板岩(原版 above_preliminary_surface 口径);
  - dimension 的 `generator.settings` → `yongye:candle`。
- 全文件无 dirt/grass 任何引用(grep 验过);水仍在(海平面 63,m305 已调紫);m306 的紫草配色留着无害(玩家自带树叶会显紫)。噪声 id `minecraft:surface`/`minecraft:gravel` 已对 mcmeta 1.21.1 注册表验证存在。
- **旧档注意**:已生成过的旧区块(草地那批)不会重铸,新旧交界有断层——删存档里 `dimensions/yongye/candle/` 整个文件夹即可全维度重开(主世界/背包不受影响,门重点一次就行)。
- 零 Java 零配置(仍 85)。**待验证=纯数据包**:worldgen JSON 错误会在启动/进维度时日志报 `Failed to parse yongye:candle`,报什么贴回来即修;退路=dimension 的 settings 改回 `minecraft:overworld` 一行即回 m306 状态。

## m308 战况看板可挪+紧凑短文案(2026-07-28)
- 作者实机:天数显示(战况看板)有点挡住,要能挪动,而且太长要短一些。
- **可挪**:新 `hudInfoAnchor` 六档停靠位 0=左中(默认,m289 原位)/1=左上/2=左下/3=右上/4=右中/5=右下,再叠 `hudInfoOffsetX/Y` 像素微调;最终坐标钳回屏内,乱填偏移也不会飞出屏幕。`/yongye config set hudInfoAnchor 3` 这类命令即改即生效(HUD 每帧读配置)。上/下缘各留了净空(上=4px,下=48px 避热栏与聊天输入行)。
- **紧凑**:新 `hudInfoCompact`(默认开)三行全换短文案——行1「第N天·击杀X」去空格;行2「下阶段:永夜I·暗潮 mm:ss」(去空格+短前缀);行3 预告换服务端短版「N天后:首事件+M」(M=同日余件数,明天则「明天:xxx」照旧转橙红)。关掉=逐字回 m289 长文案。
- **实现口径**:预告长短两版都在服务端 `buildDayForecast` 一次拼好(改返回 `String[]{长,短}`),`HudInfoPayload` 加 `dayForecastShort` 字段同包下发——客户端按**自己的**配置挑,专用服上各客户端可各选各的;顺手让「同日 3 件封顶+等」对周期进化(怪物进化)也生效(原版逻辑 3 件已满时进化被静默吞掉)。
- 配置 +4,configVersion **85→86**。零新 API 零待编译验证(switch 表达式 m225 先例,其余全在树)。

## m309 精英战斗AI(2026-07-28)
- 作者点名五件套,全部叠在原版 AI 之上(速度脉冲/寻路改道),零新 Goal、零 mixin,新 `EliteCombatAI` 由 EliteHandler tick 循环驱动(逃跑接管时跳过感知/远程技能/瞬移):
- **跳劈**:持武器精英(第 N 天配刀或抢来的,EquipmentEnhancer.isWeapon 判定)目标 3~10 格且冷却毕(默 5s)→ 起跳扑向目标(照 m268 红蜘蛛猛扑三件套 addVelocity+grace+isOnGround),落地对 2.6 格内玩家/目标结算 `攻击力×eliteLeapDamageMult(1.6)` + 击退 + 烟尘重响——伤害确实比平砍高。
- **精英骷髅走位**:目标 14 格内每 5t 一次侧移脉冲(每 2 秒换向、按实体 id 错相=一群小白不同步跳舞),贴脸(<5 格)后撤拉开、过远微逼近、12% 概率小跳。纯 addVelocity 不与原版 BowAttackGoal 抢移动控制。
- **精英苦力怕自爆翻倍**:m304 单击钳制同款「取消+重放」——攻击者为精英苦力怕即 `伤害×eliteCreeperDamageMult(2.0)`,CREEPER_REAPPLY 守卫防递归。苦力怕只靠自爆输出,不必再判伤害类型。
- **血量低逃走、回复了又来**:血量 < 20% → 撒腿跑(每 tick 清目标压掉原版重锁 + 背向 24 格内最近玩家寻路逃离 12 格、找不到路 MoveControl 直线跑 + 速度Ⅱ + 逃跑烟尘),边逃边回血(每秒最大生命×5%);回到 90%(或逃超 15s)→ **咆哮杀回**(RAVAGER_ROAR+怒焰,重锁最近玩家),回归后 5s 内不再逃防临界血量打摆子。苦力怕不逃——它的活法是自爆。
- **跳搭(快速垫块爬高)**:近战精英(骷髅/女巫远程、蜘蛛会爬墙,三者排除)目标在头顶(高差≥2.5 水平≤5.5)→ 原地直上起跳、越过起跳格 1.05 即在脚下垫圆石,两跳间隔默 8t≈每秒 2.5 格(作者点名「速度很快」);受 mobGriefing 游戏规则约束,头顶两格有方块不起跳防撞头死循环,起跳被顶 grace 后放弃本次。
- 配置 +14,configVersion **86→87**(与 m310 合并跳 88)。
- 待编译验证 2 处均低险有退路:`GameRules.DO_MOB_GRIEFING`(常量首用,KEEP_INVENTORY 同族取法在树已编过;报错删那一行=不受规则约束)、`SoundEvents.BLOCK_STONE_PLACE`(标准常量首用;报错删那一句=无声垫块)。其余 API(addVelocity/velocityModified/takeKnockback+速度包/mobAttack/startMovingTo/isIdle/moveTo/setBlockState/isAir/getAttributeValue)全部在树先例逐字核对。

## m310 所有僵尸红眼+紫光(2026-07-28)
- 作者点名:所有僵尸眼睛变红、身上冒紫色光。纯客户端观感,零服务端零网络。
- **红眼**:新 `ZombieRedEyesFeatureRenderer`(照 m280 EliteSkinFeatureRenderer 叠皮同款重渲一层),贴图=除眼睛全透明的 64×64 叠层——僵尸/尸壳/溺尸共用僵尸脸位(前脸 (8,8)-(16,16),眼睛 v=12 行 u=9,10/13,14 亮红+上下行淡红辉光),僵尸村民单独一张(村民头 8×10,眼位 v=12~13);发光眼层(蜘蛛眼同款 RenderLayer.getEyes)+ 满亮 lightmap 0xF000F0 = **黑夜里也是两点红光**。注册按 EntityType 精确挂四类(ZOMBIE/HUSK/DROWNED/ZOMBIE_VILLAGER),僵尸猪灵是猪灵模型未纳入(要加另说)。隐身僵尸不画。
- **紫光**:渲染帧内 16% 概率客户端本地撒一粒 WITCH 魔粒(紫色)——只有被渲染(=看得见)的僵尸才冒,自动就近削减,零成本。
- 配置 +2(zombieRedEyes/zombiePurpleAura 均默开),configVersion **87→88**(与 m309 合并一次落盘)。
- 待编译验证 2 处低险:`RenderLayer.getEyes`(蜘蛛眼同款标准 API 首用;报错换 getEntityCutoutNoCull(tex) 一行=不叠加发光但满亮照样红)、`World.addParticle`+`ParticleTypes.WITCH`(客户端标准粒子入口/原版常量;报错删紫光段)。
- 已知取舍:僵尸村民眼位按村民 UV 推算,若实机偏 1px 改贴图像素即正(纯资源);溺尸外层贴图若恰在眼前有不透明像素可能局部遮红眼,实机看。
## m311+m312:全怪紫气分档+分档红眼 · 看板默认位改版(作者点名,2026-07-28)

**作者原话**:普通怪物眼睛浅红色、精英深红色;普通怪物带轻微紫气、精英中等、BOSS 高等,要注意优化;hudInfoAnchor=1 / hudInfoOffsetX=-2 / hudInfoOffsetY=14 设成默认。

### m311 全怪紫气分档 + 僵尸红眼分档
- **红眼分档**(仅僵尸系,m310 的四类):普通=浅红微光(255,120,105,α70)新贴图 `*_light.png`;精英=深红强光(185,0,12,α165)原两张就地重上色。判定与 EliteSkinFeatureRenderer 同口径(名字含「精英」)。RenderLayer.getEyes 已全绿。
- **紫气全怪化**:m310 僵尸专属紫气段删除,移交新建 `MobAuraFeatureRenderer`(挂所有活体、内部分档):
  - 档位:BOSS=五只皮肤 BOSS 实体类或名含佩恩/长门/HIM/「 BOSS」;精英=名含「精英」或毒液蜘蛛/巨蟹;普通=其余 Monster;非敌对零开销返回。
  - 密度:普通≈2粒/秒、精英≈6粒/秒(带补粒)、BOSS≈18粒/秒+贴体双螺旋盘升(age 驱动,出场演出同款母题)。
  - **优化三板斧**(作者点名):渲染驱动(看不见=不冒)+ 距离裁剪(16/32/64 格)+ 概率限流;纯客户端 WITCH 粒,零网络零服务端。
- 配置:zombiePurpleAura 语义扩为全怪总开关;新增 `mobAuraScale`(0~4 密度倍率,默认 1)。

### m312 看板出厂默认改版
- hudInfoAnchor 0→**1(左上)**、hudInfoOffsetX 0→**-2**、hudInfoOffsetY 0→**14**;照 m214/m222 老口径迁移——仅当三项均仍为旧默认(0,0,0)时改成新默认,自定义不动。
- configVersion **88→89**(m311 +1 字段与 m312 合并一次落盘)。

### 待编译验证
零新 API:getEyes/addParticle/WITCH(m310 全绿)、squaredDistanceTo/Entity.age/Monster/HostileEntity 均有在树先例;实体类 instanceof 为项目自有类。理论直接绿。

### 已知取舍
- 红眼分档仍限僵尸系——其他怪没有眼位叠层贴图(各模型 UV 不同,要做需逐模型画眼,另立里程碑)。
- 隐身怪不冒紫气(渲染驱动天然如此);紫气密度按 60fps 估算,低帧机器上会等比例变淡(不是变卡),属可接受方向。


## m313 MOD 图标更换(作者上传新图,2026-07-28)
- 作者提供新版《夜蚀》圆形徽标(源 377×377 RGBA,月蚀+紫黑城塞主题)。按仓库既有规格 Lanczos 升采样到 **512×512**、optimize 压缩后覆盖 `assets/yongye/icon.png`。
- **纯资源改动**:`fabric.mod.json` 的 `icon` 字段路径不变(仍 `assets/yongye/icon.png`),零代码、零配置,configVersion **不变(89)**。
- 验证:进游戏 mods 列表即显新图;圆形徽标自带深色外环,深色 mods 背景下同样清晰。嫌太大/太小只需重存不同尺寸覆盖同名文件,不用改代码。

## m314 mod 介绍改写(作者点名,2026-07-28)
- `fabric.mod.json` description 重写:保留"白天跑图/夜晚逃命/永夜追杀"核心钩子,精简堆砌,补入帅气战斗卖点(拔刀连招/格挡弹反/处决),删除突兀的 `技能书(V65535)`/封顶数字,统一为"无限强化"(作者点名删封顶)。纯元数据,零代码零配置,configVersion 不变(89)。

## m315 红眼贴图缩小 + 分档配色修正(作者点名"眼睛太大/不对",2026-07-28)
- 四张红眼贴图(zombie / zombie_villager 各 light+deep)每只眼从 **2×3 缩为 2×2**(x9-10 & x13-14,y11-12),整体上移半格更贴眼窝,不再是大块。
- 分档配色修正:普通=浅红 **(255,75,75,α220)** 清亮红不偏桃(原 255,120,105,α70 太肉发橙);精英=深红 **(185,0,12,α255)** 满实血红。
- 纯资源:渲染代码 / UV / getEyes 满亮自发光 / 配置全不动,configVersion 不变(89)。要更小/更大/更红说一声改数值即可。

## m316 MoBends 式疾跑姿态(作者:「跑步姿势别扭不够帅,去 GitHub 找个帅的」,2026-07-28)
- **选型**:重扒 Iwoplaza/MoBends(MIT)的 `SprintAnimationBit` 逐帧关键值——m243 已验证过它的发力手法,疾跑位它的观感就是社区公认的"帅"(前扑+拧身+泵臂)。THIRD_PARTY_NOTICES 已挂名。
- **四板斧移植(SlashPoseMixin 新 `yongye$sprintPose`,TAIL 叠加纯旋转)**:① 躯干随步幅左右大拧 cos(L)·−40°;② 前倾起伏 cos(2L)·10°+10°(步步向前扑);③ **头部全量反补**(灵魂:body 甩、视线死锁);④ 屈肘泵臂近似(无肘关节→前置 −27° 肘弯错觉+同频加幅至 ~1.55 rad+微外张±5°),腿步幅 1.4→~1.7 rad+前倾配重−5°+微分腿±2°。
- **频率关键决策**:MoBends 用 0.8× 慢频是因为它整体**替换**角度;我们是**叠加**,改用与原版步频(limbAngle·0.6662)同频、相位对齐(右臂+π/右腿 0),否则拍频手脚越跑越飘。
- **让位规则**:出刀瞬间(slashActive)拧身归零、前倾×0.6,上身交给挥砍七式(学 MoBends AttackStanceSprintBit 分工);幅度随 limbDistance 淡入淡出,起步/停步平滑。门:仅玩家、疾跑中、非骑乘/游泳/滑翔。
- 配置+2:`sprintPose`(默认开)/`sprintPoseScale`(默认 1.0,0.3~2.0 钳制),**configVersion 89→90**;Debug 战斗手感区+2 钮(跑步姿态·开/关)。
- **待编译验证(仅 2 项,低险)**:`entity.isSwimming()` / `entity.isFallFlying()` 首用(yarn 标准 Entity/LivingEntity 公法,与已在用的 isSprinting/hasVehicle 同族)。实机盯:第三人称疾跑看拧身泵臂节奏与视线是否稳、疾跑中出刀(突刺)上身是否顺滑让位、骑马/游泳/鞘翅不受影响、关 sprintPose 回原版。

## m317 背包「设置」按钮 + 视觉·手感集中设置屏(作者点名,2026-07-28)
- **入口**:背包面板**右侧**镜像位新增「设置」钮(左列 8 钮已满,右侧干净;YongyeButton 主题样式),点开新 `VisualFxScreen`。
- **设置屏**:照 DebugScreen 骨架(页签+4 列分区网格+clearAndInit 翻页,零新 API 面),两页把散落的观感项集中:①「镜头·特效」=打击震动(0/0.5/1/1.5)/BOSS 登场震/FOV 冲击/打击顿帧/战斗粒子/击杀闪光/击杀音效/刀光(开关·贴图·大小·亮度);②「姿态·怪物」=拔刀姿态/全身发力/姿态幅度/疾跑姿态(m316)/跑姿幅度/怪物红眼/怪物紫气/紫气浓度。
- **机制**:每钮=sendCommand("yongye config set …")(与 DebugScreen/爆率编辑器同一条即时生效+写盘链路);shouldPause=false 边调边看;「返回背包」回父界面。
- 零配置零版本号变更(本身就是设置 UI);待编译验证:无(全在树写法:Screens.getButtons/YongyeButton/ButtonWidget.builder/clearAndInit/sendCommand 均有先例)。实机盯:背包右侧見「设置」、两页签切换、点震动·关后挨打无震、跑姿开关即时生效、返回回背包。

## m318 强化洗掉原生属性修复(作者实机截图:钻石甲+8护甲强化后只剩+0.3,2026-07-28)
- **根因(已用 misode/mcmeta 1.21.1 官方数据 dump 实锤)**:1.21.1 原版装备的基础属性**不在 attribute_modifiers 组件里**(钻石胸甲默认组件 modifiers=[]),基础 +8/+2 挂在 `Item#getAttributeModifiers()` 上、仅当组件为空时兜底生效;强化显式 set 非空组件 → 兜底被绕过 → 原生数值蒸发。截图里槽位组从"穿在身上时"变"穿戴时"也是同一根因(armorSlotOf 在空基础里找不到胸甲槽只能回退通用槽)。
- **修复**:新 `baseOf(Item)`——组件非空(模组物品出厂已写)用组件,否则取 `Item.getAttributeModifiers()`(方法名已按 FabricMC/yarn 1.21.1 官方映射核对,method_7844,零编译风险);`withLevel` 与 `kindOf` 改从它起算。**存量已洗坏的装备再强化任意一次即自愈**(withLevel 本就每次从基础重算)。
- **深扒出的同根潜伏 bug 一并修好**:旧 `kindOf` 读空组件 → 原版剑/斧/三叉戟 hasDmg=false → 判 NONE **不可强化**;改 baseOf 后原版武器正常判 WEAPON 可强化、强化保留原生攻击力;盔甲槽位组回归"穿在身上时"等正确分组。
- 零配置零版本号;待编译验证:无(getAttributeModifiers 已核官方映射)。实机盯:钻石甲强化+1 应显示 +8.3 护甲/+2.1 韧性/+1 生命(基础+加成同槽合并)、旧的坏甲再喂一颗石头自愈、原版钻石剑现在可强化且保留 +7 攻击。

## m319 技能书/血量书合成改加法(作者:「两个LV5合成的是一个lv6」,2026-07-28)
- **旧版是亏级陷阱**:学书是加法(learn: cur+level),两本 V5 分开学=+10 级;旧合成 2×V_L→V_{L+1} 只给 V6,平白亏 4 级,越高级亏越狠。
- **改加法合并(两个配方同步)**:2 本**同类型**书(V_a+V_b,**等级可不同**)[+阶段材料按**结果级**取档] → 1 本 V_{a+b}(skillBookMaxLevel 封顶钳制;封顶后合成不涨级直接不匹配,防误合亏书);等级累加走 long 防边界溢出。
- 零配置零版本号(阶段材料阈值沿用现有配置);待编译验证:无(纯逻辑改写,零新 API)。实机盯:V5+V5=V10、V3+V7=V10、跨档材料要对(如结果级过 lifeCoreThreshold 要生命核心)、两本 V65535 不给合。

## m320 召唤物协同集火(作者:「召唤物应该玩家攻击什么它就攻击什么」+深扒,2026-07-28)
- **① 集火**:主人攻击 X → summonAssistRadius(默认 32 格)内三类己方召唤物(铁傀儡/肝帝/暗影分身,与 m299 免友伤同一套判定)全部强制切目标到 X;**② 护主**:主人挨打 → **闲着**(无活目标)的召唤物去支援,不打断正在集火的。
- 钩子口径全在树:集火=AttackEntityCallback(服务端侧回调);护主=ALLOW_DAMAGE 观察式(此版 fabric-api 无 AFTER_DAMAGE,照 HighHpCounterHandler 已踩坑口径恒放行)。零 mixin 不动 AI goal:直接 setTarget 交原版攻击 goal;铁傀儡这类 Angerable 额外 setAngryAt+setAngerTime(400) 压住自身仇恨 goal 防下 tick 抢回目标——Angerable/两方法名已按 yarn 1.21.1 官方映射核对(class_5354)零编译风险。不打玩家/己方召唤物(防倒戈),跨维度不响应。
- 配置+3:summonAssistFocus/summonAssistDefend(默认开)/summonAssistRadius(32),**configVersion 90→91**;SummonerHandler.ownerOf 改 public 复用。待编译验证:无。实机盯:召 5 傀儡打 A 再打 B 全队跟着换目标、被偷袭时闲置傀儡回防在打的不回头、关 summonAssistFocus 回旧行为。

## m321 技能 CD 全面审计(作者:「技能CD要检查一下」,2026-07-28)
- **审计范围**:武器主动技能 R/G/V(WeaponSkillManager:混沌斩 8s/深渊吞噬 15s/终焉降临 45s+升级减 CD 有下限)、职业小技能(ClassMinorSkillManager,15s)、职业大招(ClassUltimateManager,30s+晚安光环 reduceCooldown)。
- **结论:核心逻辑扎实,未发现可刷 CD 的洞**——SkillUsePayload 与 UpgradeWeaponSkillPayload 的 index 均有服务端越界校验(崩服向已防);冷却写入在施放成功后、读取在施放前,无竞态;升级减 CD 有 skillUpgradeCdFloor 下限防归零。
- **修 1 处口径不一致**:职业小技能/大招用 `world.getTime()`、武器技能用 `server.getTicks()`——两者会话内均单调等价,但混用埋隐患(未来任何跨表比较/HUD 同步都会踩),统一为 `server.getTicks()`。
- 零配置零版本号;待编译验证:无(`p.server.getTicks()` 武器技能同文件在用)。遗留(下次可做):CD 剩余目前只在按键时以 actionbar 提示,可考虑挂到看板 HUD 常显。

## m322 无配方物品「获取:」提示(作者点名,2026-07-28)
- 集中式 `SourceHints`(client)一张表+两个 instanceof(强化石十档/技能书多类型),新 ItemTooltipCallback 挂灰字「获取:…」;覆盖碎片/结晶/核心/血核/终焉精华/保护卷/自动双卷/永夜尘/裂隙残片/深渊魂晶/选职书,文案逐条对过 config 注释与 handler 实际口径;**有配方的物品不加**(配方书可查,防噪音)。配置+1 `itemSourceTooltips`,v91→92。

## m323 一键合书=升级机制(作者:「不应每个等级都重新合成」,2026-07-28)
- 背包右列「设置」下新增「合书」钮 → MergeBooksPayload → BookMerger(服务端权威):全部技能书/血量书**按类型各合成一本**,等级相加(long 防溢出+封顶钳制),按**结果档**自动扣 1 个阶段材料(与 m319 工作台同阈值),缺料该类跳过并提示、封顶不涨跳过防误亏;工作台 2 本合成保留当零头用。配置+1 `enableBookMerge`,v92→93。

## m324 深挖修复:强化归零组件残留(2026-07-28)
- `withLevel(x,0)`(被夺降级/指令归零路径)复制自旧强化件,残留 UNBREAKABLE(白嫖永不坏)与 MAX_DAMAGE(耐久上限回不去)。修:归零时 remove 两组件回物品默认,损耗钳默认上限内(`ItemStack.remove` 已核 yarn 官方映射 method_57381)。
- 顺手复核未见新问题:MobEnhancementHandler 不碰装备组件(不受 m318 波及);两书合成配方 remainder 走 SpecialCraftingRecipe 默认口径正确;enhanceFromInventory/两技能 payload 边界均已有校验。

## m325 逃离(走格)任务降频(作者点名,2026-07-28)
- pickType 由均匀抽改**加权抽**:FLEE(逃离原点 N 格)权重=新配置 `questFleeWeight`(默认 0.35,其余类型各 1.0,调 0=不再派);前期池(FLEE/SURVIVE/GATHER)与全池同一套 roll。

## m326 血量按比例保持(作者:「切武器到手血量马上就变」,2026-07-28)
- 根因:武器带 +最大生命(强化/肉盾/职业),切手瞬间 max 变 → 原版当前血要么被钳掉(丢血)要么百分比骤降(视觉残血)。修:PlayerUpkeepHandler 每 tick 记录上帧 max,变化时**当前血按百分比缩放**(下限 1 血防秒躺,重生满血窗口内不干预);学书涨上限也按比例补=手感更顺。配置 `healthKeepRatio`(默认开)。

## m327 疾跑武器样式:拖刀(作者:「跑步背刀不好看不帅,你想个帅的」,2026-07-28)
- 新默认**拖刀疾跑**:武器不再收背后,持械臂后下伸展、刀面外翻拖在身侧后方(动漫冲刺经典),副手加倍泵臂补节奏,与 m316 前扑拧身天然成套;`sprintWeaponStyle` 0=原版 1=收背后(旧,m247 逻辑原样保留) 2=拖刀(默认);视觉设置屏疾跑区+3 钮即点即换。shouldSheath 加样式门(背挂渲染与藏手一处共管),isWeapon 改 public 供拖刀姿态复用同套武器判定。
- 配置+3 合计,**configVersion 93→96**;待编译验证:无(全在树写法)。实机盯:疾跑看刀拖身后飒不飒、设置屏三键切换即时生效、切武器上下手血条百分比纹丝不动、连派几轮任务看走格频率明显降。

## m328 主线任务书(FTB Quests 风格内建版,作者:「加任务书,一系列任务,最终击杀末影龙」,2026-07-28)
- **不引外部依赖**(FTB Quests 是独立模组还要玩家另装),内建 **16 阶段线性主线**:破晓(活过第一夜)→开卷(技能V5)→锋芒(+10)→见血(杀20)→立命(选职业)→猎手(精英×3)→淬炼(+100)→百人斩→夜行者(永夜3层)→屠魔(首BOSS)→千锤(+1000)→学海(技能V500)→千人斩→弑神(佩恩)→远征(末影珍珠×8)→**终焉:讨伐末影龙**(血核×10+精华×20+强化石·拾,全服金字广播)。
- **入口三合一**:新物品「永夜·任务书」(右键开界面,首次进服自动发,书丢不丢进度)+背包右列「任务」钮+达成时 actionbar 提示;界面=左双列 16 阶段钮(✔/▶/□)+右侧目标/进度/奖励详情+「领取当前奖励」(服务端权威复核)。
- **架构**:阶段表静态数据(MainQuestLine.STAGES);进度=玩家附件 ×7(persistent+copyOnDeath,死亡不清);击杀计数挂 AFTER_DEATH,**归属复用 creditedKiller(召唤物击杀记主人,与掉落/看板同口径)**,精英按名判、BOSS 按五实体 instanceof、佩恩走 isPain 识别口、龙 instanceof EnderDragonEntity;数据流照爆率编辑器(Request→Sync→onSync 刷新);与随机限时任务(QuestManager)独立并行。
- 新增:QuestBookItem+16×16 贴图(暗紫封皮金星)+模型+双语 lang;payload×4;配置+2 enableMainQuest/giveQuestBook,**configVersion 96→97**。待编译验证:无(全在树写法,附件/payload/AFTER_DEATH/offerOrDrop 均有先例)。实机盯:新档进服拿到任务书、右键开界面、杀怪看进度涨、达成领奖进下一阶段、杀龙看全服广播+终焉大奖。

## m329 背包按钮全收左侧双列(作者:右侧与他模组UI打架且难看,2026-07-28)
- 11 钮左侧双列:内列(贴面板)成长/装备/饰品/天赋/强化/兑换,外列 学书/合书/任务/设置/本命;右侧代码全撤,不再与盾牌类模组 UI 叠。

## m330 新周目·永夜+(作者拍板方案①,2026-07-28)
- 讨伐末影龙自动开启二周目(幂等,全服紫字广播):怪物强度在**封顶之后**乘 ngPlusMobMult(默认 2.0,二周目就该破上限),掉落 lm/gm 双点乘 ngPlusLootMult(默认 2.0)。存档级持久化照 NightfallManager 口径(yongye_ngplus.json 存在性即状态,跨重启保持);enableNgPlus 总开关(状态仍持久,关=倍率不生效)。配置+3,v97→98。

## m331 龙魂锻造(作者拍板方案②,2026-07-28)
- 新物品**龙魂**(EPIC,每次讨伐末影龙掉 dragonSoulPerKill=1,SourceHints 已挂)+**龙魂之刃**(配方=夜蚀锭×6+龙魂+混沌之刃+终焉精华;基础攻击 63≈混沌两倍/攻速 −1.4/自带 +40 生命;继承混沌免解锁三技能——WeaponSkillManager 特判已扩——与破蛛网;可继续无限强化)。PIL 贴图两张(紫青漩涡灵珠/暗紫青芒对角剑,handheld)+双语 lang+模型+配方 json。配置+1,v98→99。

## m332 职业试炼支线(作者拍板方案③,2026-07-28)
- 任务书新「试炼」页签:三关递进(杀 300 / 精英 15+技能 V300 / 强化 +3000+BOSS×3),**标题按本命职业着味**(战士 百战→破军→武神,肉盾 坚壁→不动→山岳,刺客/剑客/术士/召唤师各一套),奖励=**本命职业武器直接强化 +300/+800/+2000**(withLevel 服务端权威;未带武器折算强化石·伍)。附件 CLASS_TRIAL_STAGE(persistent+copyOnDeath)+ClaimTrialPayload;配置+1 enableClassTrials,**v99→100**。

## m333 图鉴/成就页(作者拍板方案④,2026-07-28)
- 任务书第三页签「图鉴」:讨伐图鉴(总击杀/精英/BOSS/佩恩/末影龙)+成长统计(最高强化/技能总级/永夜层/生存天数)+征程(主线·试炼进度/永夜+状态)——主播开播现成炫耀面板。MainQuestSyncPayload 扩至 14 字段一包带全,零额外请求。
- m329~m333 待编译验证:无(全在树写法)。实机盯:背包左侧双列不再与他模组打架、杀龙看紫字广播+龙魂入包+图鉴永夜+点亮、锻龙魂之刃三技能直接可放、试炼领奖看本命武器等级直跳。

## m334 反卡BUG双机制(作者:「脚下一块下面悬空百分比扣血;泡水超1分钟召怪+掉血」,2026-07-28)
- **① 悬空卡怪**:支撑方块**正下方两格全空**(浮空平台/断桥;普通接地土柱不会触发)+ pillarCheeseMobRadius(16 格)内有敌对怪(和平建筑不误伤)→ 宽限 5s(半程 actionbar 警告)后每秒 -5% 最大生命(magic 源不吃护甲)。
- **② 泡水躲怪**:连续泡水超 waterCheeseGraceTicks(60s,半程警告,离水清零)→ 每秒 -3% 最大生命,且每 5s 在身旁水里召 waterCheeseSummonCount 只溺尸索敌(走 MobEnhancementHandler 全套成长缩放,越后期越疼)。
- 两机制跳过创造/旁观/骑乘;每秒一检零开销;配置 +9 全数值可调,**configVersion 100→101**。待编译验证:无(isTouchingWater/DROWNED/magic 源/refreshPositionAndAngles 全在树)。实机盯:浮空平台旁放只僵尸站 5s 看扣血、接地土柱不触发、泡水 60s 看溺尸出水+掉血、离水计时清零。

## m335 性能护栏:清理分帧+卡顿节流刷怪(作者双点名,2026-07-28)
- **清理卡顿修复**:旧 doCleanup 单 tick 全实体遍历+批量 discard=瞬时尖峰(「提示一出来卡一下」实为清理落地那一下)。改**分帧排水**:收集改 `getEntitiesByType(EntityType.ITEM,…)` typed 查询(快一个量级)只入队,tick 侧每帧删 itemCleanupBatchPerTick(默认 150)个,删完才播「已清除 N 个」;顺手消掉旧的 iterateEntities 待编译验证项。
- **卡顿护栏 LagGuard**:以 `server.getAverageTickTime()`(yarn method_54832 已核)为准——MSPT≤soft(35ms)全量、soft~hard(48ms)线性降量、≥hard 本波跳过先喘气。接入三处波次刷怪:夜袭尸潮(want 缩放)/烛光域爆发(burst 缩放)/自定义 BOSS·精英投放(硬闸);**任务刷怪不节流**(据点守卫等任务必须能完成)。
- 配置+4,**configVersion 101→102**;待编译验证:无(getEntitiesByType typed 全域查询为原版标准面,getServer/getAverageTickTime 均核过)。实机盯:清理到点看还卡不卡(应只见灰字无顿挫)、/forge tps 类工具压到 45ms+ 看尸潮明显变稀、松了自动回满。

## m336 学第二职业崩溃修复 + 职业跟手(作者崩溃截图+点名,2026-07-28)
- **崩溃根因**:`open_class_replace` 包编码 `writeString(null)` → EncoderException 直接踢连接(截图实锤)。职业列表附件中混入 null 元素所致。三层修复:payload 三字段 **null 免疫**(空串兜底)+ `learnedList` 源头 `removeIf(null)` **自愈已污染存档** + 客户端替换屏收到空串照常展示。
- **职业跟手**(`classFollowWeapon` 默认开):手持已学职业的武器 = 该职业**即时生效**(第二职业免等级门);新 `ClassManager.effectiveMain` —— **大招(X)与小技能同步改为按手中武器的职业施放**,拿战士剑放旋风斩、换术士杖放术士招,空手/持其它武器回退本命。配置+1,v102→103。

## m337 强化转移(作者点名,2026-07-28)
- **主手(来源)→ 副手(目标)**:副手等级 += 主手等级 × `enhanceTransferKeepFraction`(默认 1.0 **全额无损**,想要损耗调 0.8 等),主手按 m324 归零口径彻底还原(UNBREAKABLE/耐久上限一并回默认);long 钳 int 防溢出;两件都需可强化,提示齐全。
- 入口:命令 `/yongye transfer` + Debug 菜单手感区「强化转移(主→副)」钮。换新武器/毕业装不再心疼,强化投入全程跟人走。配置+1,**v103→104**;两笔待编译验证:无(setStackInHand/getOffHandStack 原版标准面)。实机盯:主手+500 旧剑/副手新剑 → 转移看副手 +500 主手归零耐久还原、学第二职业不再崩、拿第二职业武器直接放它的大招。

## m338 蚀域第1天+蚀矿硬化+烧制2分钟+锻造爆震+套装加强(作者五点名,2026-07-28)
- 自然侵蚀 blightStartDay **12→1**(11/12 旧默认均迁移;命令 /yongye blight 依旧不受限)。
- 蚀矿 strength **5/6 → 45/1200**:比远古残骸(30)还硬还慢,needs_diamond_tool tag 原本就挂着=必须钻石镐;抗爆对齐残骸防炸矿。
- 烧制 **200→2400 tick(整 2 分钟)**,高炉 100→1200(1 分钟)。
- **锻造爆震**(blightForgeBlast 默认开):夜蚀套装从合成结果格拿取瞬间——爆炸粒子+爆炸音+对锻造者 blightForgeBlastDamage(默认 6=3 心)magic 自伤+紫字"夜蚀之力暴走";零方块破坏(不用 createExplosion,全在树组合拳零编译风险;onCraftByPlayer 已核 yarn method_54465)。
- 套装属性再上调(爆震代价配得上):防 9/13/17/9 → **12/18/24/12**,韧性 12→**16**,每件击退抗 0.2→**0.25**。配置+2,v104→105。

## m339 皮肤BOSS第5天开刷+全线加强(作者:「技能都写了吗?最好强一些」,2026-07-28)
- **技能核查:五只全有完整技能包(m268)**——凤凰(烈焰吐息/火焰龙卷/浴火重生)、死亡法师(魂火锁定/凋零新星/闪现)、红蛛(蛛网/扑咬/半血分裂产卵)、托罗龙(龙息/俯冲)、阿努比斯(法术/召唤/狂暴),外加 BossAbilityHandler 通用层(召唤/震荡波)。
- 开刷天数 mobBossStartDay/anubisMinDay/wildDragonMinDay **10→5**;基础血量全线 **×1.5**(阿努 150 万/凤凰 60 万/法师 45 万/红蛛 37.5 万/托罗 90 万);技能伤害 **×1.5**、冷却 **−25%**(共 17 项数值改版)。全部走「仅旧默认迁移」老配置自动升级,自定义不动。v105→106。

## m340 强化转移按钮(作者点名,2026-07-28)
- 背包外列第 6 钮「转移」= /yongye transfer 同款(主手来源→副手目标);外列 6+内列 6 对称收官。

## m341 验证报告 P0~P3 全修 + 皮肤BOSS阶梯解锁(作者实测报告,2026-07-29)
- **P0 客户端硬崩**:BossBarStyleMixin 内部类 Group/Style 被运行时类加载(Mixin 禁止 mixin 包内类被直接加载,crash-2026-07-29_01.53.05 实锤)→ 移出为 `client/BossBarStyles`(public 嵌套 Style record + Group),mixin 改嵌套类 import,包内引用名不变改动最小。
- **P1 反卡系统双倍**:Yongye.init 两处 AntiCheeseHandler.register() → 删无注释那条,悬空/泡水反制不再双份 tick/伤害/召怪。
- **P2 选职包伪造**:ChooseClassPayload 改**先验书后选职**(创造豁免;无书红字拒绝),伪造裸包不再能白嫖选职+开局武器;成功后按 slot 精确扣书。
- **P3 强化继承 shift-click 断路**:装备槽已占时,已强化装备 shift 自动落**材料槽**走继承(与 canInsert 同一判定),不再直接吞点击。
- **P3 召唤师补丁收尾**:补 `class_weapon_summoner.json` 配方(魂晶×4+碎片×4+铁块=傀儡核意象,镜像术士版式);ModCommands 错误提示补 summoner。
- **皮肤 BOSS 阶梯解锁(作者:「第六天开始一天解锁一个」)**:红蛛 D6 → 死亡法师 D7 → 凤凰 D8 → 托罗龙 D9 → 阿努比斯 D10(弱→强压轴);替换 m339 的统一 5 天口径,迁移含 m339 短暂值(5)与更早旧默认(12/14/16/10)全部归位,自定义不动。**configVersion 106→107**。
- 作者侧 gradle clean build 已通过;本轮改动待编译验证:无(纯移动/删重/逻辑序调整)。实机盯:客户端能进主界面(P0)、泡水掉血速率回单倍(P1)、无书发包被拒(P2)、强化屏 shift 已强化装备入材料槽(P3)、第 6~10 天每天多一位新面孔。

## m342 编译修复:Style record→public字段类(作者build报告,2026-07-29)
- m341 把 Style 移出 mixin 后忘了 record 组件跨类是 private(同类内部才可字段直访),mixin 23 处 `st.xx` 全线编译失败。修:BossBarStyles.Style 改 **public final 字段普通类**(构造签名不变),mixin 零改动;Group 本就 public 字段无恙。

## m344 CI编译闭环+资源预检工具(引入 Jahrome907/minecraft-agent-skills,MIT,2026-07-29)
- **GitHub Actions 自动构建**(`.github/workflows/build.yml`,照其 minecraft-ci-release 模板):push/PR 到 main 即云端 gradle build 并上传 jar 工件——沙盒无法编译的死穴补上,AI 推送后轮询 api.github.com 的 actions runs 即可自主拿到 success/failure 与报错日志,编译验证不再依赖作者手动 build。
- **资源预检收编**:tools/validate-resource-pack.sh + validate-datapack.sh + jq-shim.mjs(推送前扫 JSON 合法性/贴图动画配对/配方引用;本仓库现状全 PASS,唯一 FAIL 是 pack.mcmeta——MOD 场景假阳性忽略)。其 fabric-api.md(389 行 1.21.x 注册/mixin/网络/GUI 速查)列为离线参考。THIRD_PARTY_NOTICES 已挂名;ONBOARDING 已写入新工作流。
- **落地注意**:当前 PAT 无 `workflow` scope,`.github/workflows/build.yml` 无法由 AI 推送(GitHub 硬性拒绝)。文件已单独交付:作者在 GitHub 网页 Add file 粘贴,或下次贴一个勾选了 workflow 权限的 PAT 由 AI 补推;工具脚本与文档本笔已入库。

## m345 复查报告三建议处理(作者复查:build通过/评分B,2026-07-29)
- **配置版本提示**:根因=版本号只在内存对齐、"下次保存"可能永不发生 → 提示每次启动重复(106≠107 现象)。修:版本不一致时**立即 save() 写盘**+提示措辞改自愈口径(默认值改版已按仅旧默认迁移自动完成,无需处理),warn 降 info,一次性出现。
- **data fixer 日志噪音**:文档化于 ModEntities 类头——build(String) 为 yarn 1.21.1 唯一重载,字符串仅用于开发环境 DataFixer 选型查询,模组不做跨版本存档升级无需注册 fixer,生产侧无此噪音;压制需 mixin 进 Util 日志路径,风险大于收益,判定为接受现状。
- **复杂度热点(部分)**:YongyeClient 背包 11 钮装配整体抽 `addInventoryButtons`(纯搬移零逻辑变更,onInitializeClient 减重 ~65 行)。**遗留**:ClassSkillHandler:111 与 YongyeConfig.load 迁移堆(1373 起)两处热点待下轮拆(前者建议按事件拆私有方法,后者建议迁移块抽 migrateDefaults());CI workflow(build.yml)仍待作者网页添加或带 workflow 权限 PAT 补推。

## m346 技能CD常显HUD(作者点名「R/G/V、X、C 剩余冷却常显,不要只在按键失败时提示」,2026-07-29)
- **服务端**:新 `SkillCdSyncHandler` 每 10t 把三套冷却表(武器技能 R/G/V=WeaponSkillManager、大招 X=ClassUltimateManager、小技能 C=ClassMinorSkillManager,m321 已统一 server.getTicks() 时基)读成剩余 tick,经新 `SkillCdPayload`(5 varint)发给本人;全就绪期间静默省流量,「在转→转完」边沿补发一包全 0 归位;三管理器各加 `remaining` 纯 getter(只读不写冷却表),WAS_ACTIVE 缓存挂 DISCONNECT 清理。
- **客户端**:收包入 5 槽缓存,本地每 tick 递减保平滑(两包之间秒数不跳格);渲染在血条面板左沿外**右对齐一列**(右缘 w/2-100,底行 h-50 逐行向上堆,与连击块/看板/永夜阶段名/核心箭头全不压)。持武器出 R/G/V 三行(未解锁整行深灰;混沌/龙魂免解锁同 m331 口径,ENHANCE_LEVEL 数据组件客户端本地可读),有职业(ClientStats.className)出大招/小技能两行;就绪=金键绿字「就绪」,冷却=灰键灰名+橙色剩余秒+行底 1px 蓝色恢复进度线(分母=收包时记录的峰值,免下发总 CD);键位标签走 `KeyBinding#getBoundKeyLocalizedText`(**yarn 1.21.1 官方映射已核=method_16007**,仓库首用),按键静态引用在注册处赋值,玩家改键 HUD 即时跟变。
- 配置+3(enableSkillCdHud 默认开/skillCdHudOffsetX/skillCdHudOffsetY),**configVersion 107→108**;顺手修作者点名的 YongyeClient 注释「外列(5)→外列(6)」(补上 m340 的「转移」)。括号自检 9 文件全平;待编译验证:无(getBoundKeyLocalizedText 已核映射,其余 fill/drawTextWithShadow/registerGlobalReceiver 全在树)。实机盯:持武器+有职业看左下五行常显、施放后秒数平滑倒数+蓝线涨满、改键位标签跟变、锁定技能深灰、专用服关 enableSkillCdHud 后 HUD 消失。

## m347 装备详情同步技能等级(作者点名「WeaponInfoScreen 直接显示技能等级、升级花费、可升级状态」,2026-07-29)
- **网络**:新 `RequestWeaponSkillPayload`(C2S unit,开面板即发)+ `WeaponSkillLvPayload`(S2C,10 varint=3 等级+3 升级花费+3 生效冷却+等级上限)。花费与冷却在服务端按**服务端配置**算好再发(WeaponSkillManager.syncLevels),专用服上客户端本地配置不同也不显错值;升级包(UpgradeWeaponSkillPayload)处理尾补发一次同步,升完面板即时刷新。
- **WeaponSkillManager**:抽 `effectiveCd(baseCd, skLv)` 统一冷却公式(基础CD − 等级×每级缩减钳下限;升级系统关=原样),use() 施放与面板显示同走此式——面板显示的就是实际生效值,不再是裸基础CD。
- **WeaponInfoScreen**:静态同步字段(-1=未收到)+ onSync 写入;技能行升级为「✦ 混沌斩 Lv.3  CD9s  升级:精华×4」,满级显示金色「已满级」并**禁用对应升级按钮**(按钮引用存字段,render 每帧核对);未收到同步的一瞬回落旧的配置基础CD显示(离线容错);顺手把面板的免解锁显示口径补上龙魂之刃(m331 施放口径 CHAOS_BLADE+DRAGON_BLADE,此前面板只认混沌显示不一致)。
- 零新配置(挂在既有 enableWeaponSkillUpgrade 下),configVersion 不变(仍 108);括号自检 6 文件全平;待编译验证:无(PacketCodec.of/unit、registerGlobalReceiver、ButtonWidget.active 全在树先例)。实机盯:开装备介绍看三行带 Lv/CD/花费、点升级看等级花费即时跳、升满看金字「已满级」+按钮变灰、龙魂之刃面板三技能不再显示未解锁。

## m348 新手前3天引导(作者点名「小目标提示『先选职→做书→强化→找核心』,降低进服懵住概率」,2026-07-29)
- 新 `NewbieGuideHandler`:引导期(游戏天数 < newbieGuideDays,天数走 ProgressionManager.gameDay m252 收口)内每 newbieGuideIntervalSeconds(默认 45s,下限 10 防手滑刷屏)actionbar 金字提示**第一件没做的事**——①没职业→「右键职业选择书选职」②没学过任何书(血量书累计+LEARNED_SKILLS 全零)→「背包『学书』一键学习」③全背包无强化件→「背包『强化』提升武器」;三件都做了轮播通用提示(核心箭头/任务书/第 5 天 BOSS 预警)。
- 每次登录(仍在引导期)另发一条**聊天版路线总纲**(①选职→②学书→③强化→④找核心+BOSS 预警),进聊天记录可回翻;过引导期整套静默零开销,旁观者跳过。
- 判定全用在树只读接口(learnedList/getLearnedHealth/LEARNED_SKILLS 附件/EquipmentEnhancer.getLevel),不写任何玩家状态;计时与轮播游标内存 Map 挂 DISCONNECT 清理。
- 配置+3(enableNewbieGuide 默认开/newbieGuideDays=3/newbieGuideIntervalSeconds=45),**configVersion 108→109**;括号自检 3 文件全平;推送前 validate-resource-pack/validate-datapack 已跑,除已知 pack.mcmeta 假阳性外全 PASS。待编译验证:无(JOIN/END_SERVER_TICK/DISCONNECT/sendMessage(Text,boolean) 全在树)。实机盯:新档进服看聊天总纲、45 秒一条金字引导且按进度换目标、三件做完看轮播三条、第 4 天起彻底安静、关 enableNewbieGuide 全无。

## m349 CI编译闭环落地(作者补贴 workflow 权限 PAT,2026-07-29)
- 补推 `.github/workflows/build.yml`(m344 方案原样):push/PR 到 main 即云端 gradle build(JDK 21 temurin,`./gradlew build --no-daemon`)并上传 jar 工件;此前两轮均被 GitHub 以 PAT 无 workflow scope 硬拒,本轮新 PAT 带该权限一次落地。
- 自此 AI 会话推送后轮询 `api.github.com/repos/qiaodaxian233/yongye/actions/runs` 查 conclusion(success/failure),failure 拉该 run 的 jobs 日志定位报错——**编译验证不再依赖作者手动 build**,m344 的死穴正式补上。本次推送同时会把 m346~m348 三笔一起过一遍云端编译。

## m350 任务书节点地图(FTB Quests 观感,clean-room 纯自写,作者:「按你的想法一个一个来」,2026-07-29)
- **授权口径先立住**:FTB-Library 1.21.1 分支 LICENSE.md=All Rights Reserved(visible source),**代码一行不碰**;本笔只学其**界面设计思想**(节点链/连线/状态色/悬停详情),实现全用在树画法(ctx.fill/drawCenteredTextWithShadow,方环照 comboRing、玻璃高光照 YongyeButton)。
- **主线页**:16 阶段改 4×4 **蛇形节点地图**(偶数行左→右奇数行右→左,路径连成 S 形);节点=完成墨绿底绿框✔/当前深蓝底**金色呼吸框**(System.currentTimeMillis 驱动)/未解锁暗紫底灰框灰号;相邻节点 2px 轴对齐连线,走过的线段金色、未到暗灰;点节点选中(青色外圈)右侧详情联动,悬停出浮条「N.标题 · 状态」;地图下方灰字「主线进度 N/16」。
- **试炼页**:同款 3 节点横链(标题按本命职业着味,悬停显示);图鉴页不动。
- **命中**:节点为自绘非控件,mouseClicked 覆写按同一套坐标公式命中,未命中回落 super(领奖/刷新/关闭按钮照常);选中只改字段零 clearAndInit。
- 配置+1 enableQuestNodeMap(默认开;**关=整套回旧双列按钮列表**,init/render 双分流旧代码原样保留),**configVersion 109→110**;括号自检 2 文件全平;待编译验证:无(mouseClicked(double,double,int) 覆写在树有 Screen 子类先例面,绘制全 proven)。实机盯:主线页看蛇形金线爬行观感、当前节点呼吸、点节点详情联动、悬停浮条不超屏、试炼三节点、config set enableQuestNodeMap false 回旧列表。

## m351 任务书 BOSS 图鉴页 + 主线 BOSS 口径修复(作者点名「Boss页:解锁天数/掉落/弱点/已击杀次数」,2026-07-29)
- **顺手修真 bug**:MainQuestLine.isBoss 此前把巨蟹(m170 定位=精英)计入 BOSS、却漏了红蛛——主线「屠魔(首BOSS)/弑神」与图鉴 BOSS 计数一直失真,本笔对齐 m339 五皮肤 BOSS 口径(红蛛入册、巨蟹归位精英)。
- **逐 BOSS 计数**:新附件 BOSS_KILL_MAP(Map<String,Integer>,persistent+copyOnDeath,codec 照 WEAPON_SKILL_LV),AFTER_DEATH 里按槽位 id 记(红蛛/死法/凤凰/托罗龙/阿努比斯/佩恩·isPain/末影龙·instanceof)。
- **网络零新请求**:新 `BossAtlasPayload`(7 击杀+7 解锁天,14 varint,槽位契约写死在类注释),**随 MainQuestLine.sync 一并下发**(开书/领奖/达成都会刷)——解锁天=**实时配置** minDay(红蛛/死法/凤凰/野龙/阿努/佩恩;末影龙 -1=末地),改配置图鉴自动跟。
- **界面**:任务书第 4 页签「BOSS」(enableBossAtlasPage 关=页签不显示且服务端不发包);左列 7 行「✔名字 ×N / □名字」,右侧详情=已讨伐次数/「解锁:第 minDay+1 天」(展示口径同 m289)/弱点打法(按 m268 技能包写实:红蛛半血产卵、法师魂火延迟+贴脸闪现、凤凰浴火一次、托罗俯冲前摇、阿努半血狂暴、龙三命+脱战回血先炸水晶)/掉落预览(按 LootCrateHandler 实况:皮肤四只=史诗箱、托罗龙/佩恩=传说+史诗箱+佩恩技能书×3、末影龙=龙魂+IS_BOSS 散装掉落+终焉大奖+开永夜+)。
- 配置+1 enableBossAtlasPage,**configVersion 110→111**;括号自检 7 文件全平;待编译验证:无(附件/payload/页签全在树模板)。实机盯:任务书第 4 签出现、杀只红蛛看行变「✔红蜘蛛 ×1」、详情解锁天随配置变、主线「屠魔」杀红蛛现在能推进、关开关页签消失。

## m352 事件限定天象视觉(作者点名「血月才红月、酸雨才绿雨,贴图常驻导致天天血月」,2026-07-29)
- **病根**:红月(moon_phases.png)/绿雨(rain.png)放在 assets/minecraft **常驻覆盖原版贴图**(m78),与服务端天象事件(NightfallWeatherHandler)完全脱钩——无论有没有血月事件,月亮永远是红的。
- **修**:两张贴图 git mv 进 yongye 命名空间(blood_moon_phases.png / acid_rain.png,原版月亮/雨自动回归);新 `SkyEventPayload`(1 varint,序数契约=Event 枚举 0无/1血月/2酸雨/3流星)事件开始/endEvent 归零各广播一次+JOIN 登录补发;新 `SkyTextureMixin` @Redirect 拦 renderSky 里 MOON_PHASES 与 renderWeather 里 RAIN 的静态字段读取(**yarn 1.21.1 已核:field_4098/field_20797/method_3257/method_22714**),事件中返回 yongye 贴图、平时返回自建原版路径 Identifier(原字段 private 不直引);require=0 映射不符=静默不挂退回原版月亮,永不崩。
- 配置+1 enableEventSkyVisuals(关=事件也用原版天空,玩法效果不受影响),**configVersion 111→112**;mixins.json client 数组+1;括号自检 6 文件全平。待编译验证:@Redirect FIELD 目标两处(官方映射已核,require=0 有退路)。实机盯:平时白月亮正常雨、血月广播瞬间月亮变红平息变回、酸雨期绿雨、中途上线也对、关开关全原版。

## m353 技能CD HUD 玻璃芯片重做+防遮挡(作者实机截图:压到面板「+N/s」与「格挡」标签且不好看,2026-07-29)
- **防遮挡**:右缘 w/2-100 → **w/2-180**(实机截图证实旧右缘伸进面板左侧标签区,压「+3.5/s 回血率」与「格挡」字),偏移配置照旧可微调。
- **观感重做**:文字裸行 → **玻璃芯片**(暗玻璃底+状态色描边+顶部高光,照 m142 HUD 手法):冷却=芯片内部**蓝色充能填充**从左涨满(替代原 1px 细线)+灰名橙秒;就绪=金框绿字+极淡绿底;**转好瞬间金框闪光 12t**(readyFlash 边沿检测:本地递减命零与收包边沿双路点亮);未解锁整片压暗。行距 11→16。
- 零新配置(复用 enableSkillCdHud/OffsetX/Y);纯客户端绘制全在树画法,待编译验证:无。实机盯:芯片列不再压面板标签、放技能看蓝色充能涨满、转好那下金框闪一记、锁定技能暗片。

## m354 切手血量蒸发修复(作者:「武器切到别的东西血量就掉没了」,2026-07-29)
- **病根(数学错误)**:上限**下调**瞬间(切下带 +生命的武器),原版 LivingEntity.tick 先把当前血钳到新上限,m326 的保血逻辑随后才跑——拿「**钳过的血** ÷ 旧上限」当占比,807/8337(9.7%)切下手被算成 9.7% 的 9.7%,血量二次蒸发;携带镜像(每 5t)补回上限时又按错误占比放大,越切越少。
- **修**:新 LAST_HP 缓存上帧血量,占比基数改 `max(当前血, min(上帧血, 旧上限))`——下调被钳时用上帧血还原真实占比,上调没被钳时当前血就是真值,同 tick 治疗不被旧值拉低;下限 1 血防秒躺照旧。顺手补 DISCONNECT 清理(LAST_MAX/LAST_HP/重生窗口,此前漏)。
- 零新配置(healthKeepRatio 门内);待编译验证:无。实机盯:残血持武器→切方块→切回,血量百分比纹丝不动;满血切来切去不掉;重生满血窗口不受干扰。

## m355 面板职业等级口径修复(作者:「我 571 级职业却只有 11 级怎么搞」,2026-07-29)
- **真相**:HudCompactMixin.yongye$classLevel 注释声称 levels 数组=各职业等级,但 sendStats 实际填的是 **SkillType 技能书各类型累计等级**(攻击/护甲/恢复…序)——「Lv.10 肉盾」显示的其实是**攻击书累计等级**,与职业无关,纯数组语义接错线(m209 引入 StatsPayload 时的历史遗留)。
- **修**:项目本无独立职业经验系统,面板等级统一改显**技能总级**(血量书累计 ClientStats.health + 全技能书累计求和),与任务书图鉴「技能总级 VN」完全同口径——作者的 571 从此面板/图鉴对得上,学书立涨。
- 零新配置零网络变更(纯客户端换算);待编译验证:无。实机盯:面板「Lv.571 肉盾」与图鉴 V571 一致、学一本书两处同步涨。

## m356 材料仓库(作者:「任务种类太多想存东西——选择存或不存、可以取出来、能检查强化石和技能书」,2026-07-29)
- **虚拟仓库**:新附件 VAULT_ITEMS(Map<键,数量>,persistent+copyOnDeath 死亡不丢,Codec.unboundedMap(STRING,LONG) 无限堆叠);键=物品 id,技能书追加「#等级」——**同书同级合并成一行计数**,强化石十档各一行,正是作者要的聚合检查视图。
- **白名单**:传统强化材料+全部强化石(EquipmentEnhancer.isMaterial)、两类技能书(带 SKILL_LEVEL 组件,重建走 new ItemStack+set 组件通吃血量书/职业书)、终焉精华、强化保护卷——只收成长物资不做万物箱。
- **服务端 VaultManager**:depositAll 一键扫主背包 36 格整叠入库;withdraw 按键取一叠(钳 maxCount,offerOrDrop 满包掉脚下);未知键(卸模组/坏档)取出时自动清理自愈;同步走「键=数量\n」多行字符串(照 ConfigValuesPayload 在树先例,零新 codec 面);Identifier.tryParse 已核 yarn(method_12829);enableVault 关=服务端全拒。
- **网络 4 包**:RequestVault/VaultDeposit(unit)+VaultWithdraw(String key,空值兜底)+VaultSyncPayload(String data);全部服务端权威。
- **界面 VaultScreen**:聚合列表(图标+名字+×紧凑数量+行内「取出」钮,斑马条),每页 9 行分页◀▶,顶排「存入全部材料/刷新/关闭」;背包外列第 7 钮「仓库」直开(外列注释 6→7)。
- 配置+1 enableVault,**configVersion 112→113**;括号自检 10 文件全平;待编译验证:无(tryParse 已核,其余全在树)。实机盯:背包塞一堆石头书点「存入全部材料」秒空、仓库里同级书合并一行、取出回背包、死亡重生仓库还在、V100 书取出名字带 V100。
- **m357 预留接线点**(下一笔):自动存(捡起钩子)+自动用(强化/学书/任务上交时仓库也算数自动扣)。

## m357 仓库自动存 + 学书直供(作者:「任务物品自动存数据,如果有就自动用」第一步,2026-07-29)
- **自动存**:VaultManager.register() 每 100t(5s)扫**背包区 9~35 格**,可入库材料静默整叠入库,有搬动才 actionbar 提示一次;**热栏 0~8 刻意豁免**——手上/热栏留的书石头代表玩家想手动用,自动收走会打断操作(=作者要的「选择存或不存」:想不存放热栏,或关 vaultAutoDeposit)。
- **学书直供**:useAllBooks 背包学完后,仓库里存的两类书(键含 #等级)也一并学掉——饱和乘防溢出(数量×等级走 m293 口径),学完删条目回同步;不接这条的话自动存会把书收走导致「学书」按钮扑空,两头闭环。提示行带「(仓库 N 本)」。
- 配置+2(vaultAutoDeposit 默认开/vaultAutoUseBooks 默认开),**configVersion 113→114**;括号自检 4 文件全平;待编译验证:无。实机盯:捡书 5s 内自动入库+紫字提示、点「学书」仓库书全学掉且面板等级涨、热栏那本不被收、关 vaultAutoDeposit 全手动。
- **遗留(m358 计划,待作者拍板范围)**:①强化直供=强化材料从仓库直接计数/扣除(EquipmentEnhancer 四入口接线,动 MaterialSum 分账须小心 m302 碎裂扣料语义);②搜集任务目标物入库+自动上交(仓库白名单扩到原版物品的口子);③精妙背包(Salandora Fabric 分支,1.21.x-fabric 对口 1.21.1)内容扫描——需读其源码定内容存储口径(上游 3.x=世界数据 contentsUuid)后接 InventoryDeepScan,软依赖装了才生效。

## m358 视觉设置屏「界面·HUD」页(作者:「技能CD能不能在设置里调整」,2026-07-29)
- VisualFxScreen 新增第三页签「界面·HUD」,全走既有 `yongye config set` 反射通道零新接线,HUD 每帧读配置**即点即改**:
  技能CD常显区=开/关+水平五档(左移40/20·默认·右移20/40)+垂直四档偏移预设;战况看板区=开/关+六档停靠位;天象区=事件天象(m352)开/关。
- 零配置零网络(纯客户端界面数据表扩充);待编译验证:无。实机盯:设置→界面·HUD 点几下看 CD 芯片列即时挪动。

## m359 强化仓库直供(自动用第二步,2026-07-29)
- **一键强化**(背包「强化」→EnhanceSelectScreen→enhanceFromInventory)现在把**仓库里的强化石/传统材料按同一分账语义并入账本**:传统材料并入 budget 且并入时即从仓库扣(碎裂不退,与背包老规矩逐字一致);强化石并入 direct、键先记下,**成功后才从仓库删**(m302 碎裂不消耗口径原样);贡献值饱和乘/钳半 Long 防溢出(m293 口径);无论成败强化后回发仓库快照,界面开着立即刷新。空料提示改「背包和仓库里都没有…」。
- **范围说明**:强化界面(EnhanceScreenHandler)与工作台配方是实体槽位交互,天然不适用仓库直供;自动卷轴(AutoScrollHandler)如需接同套下轮点名。
- 配置+1 vaultUseForEnhance(默认开),**configVersion 114→115**;括号自检 3 文件全平;待编译验证:无(全在树写法,MaterialSum 字段 m294 起 public)。实机盯:背包清空材料只留仓库存货→点强化看等级照涨、碎裂看仓库石头没少、传统材料碎裂也不退、仓库界面开着强化完数字即时变。

## m360 热修:m354 把玩家修成无敌(作者实机:「切武器不掉血了,但怪打都不掉血了」,2026-07-29)
- **病根**:m354 的占比基数 `basis = max(当前血, 上帧血)` 是**对称双向**的——本意只救「上限下调被钳」,但上限**上调**的 tick 它也拿上帧血(该 tick 伤害结算前的值)当基数,把同 tick 伤害整个回滚;携带镜像(每 5t 撤挂重挂 MAX_HEALTH 修饰)/自动吃书等系统会周期扰动上限,上限一抖,伤害就被反复回滚 → 实机表现=无敌。
- **修(非对称基数)**:上限**上调** → 当前血从没被钳、就是真值(含本 tick 伤害),**只信当前血**;上限**下调** → 只有「当前≈新上限 且 上帧血确实装不进新上限」= 真被钳过,才用上帧血还原占比(m354 本意保留,807/8337 切下手仍保 9.7%),没被钳照用当前血。五场景推演:切下保百分比✓ / 切回还原✓ / 静止挨打正常掉血✓ / 上限抖动 tick 伤害不回滚✓ / 唯一残余=「下调被钳的同 tick」伤害回滚(≤1 tick,罕见,较 m326 前无害)。
- 零配置零新 API;括号自检全平。实机盯:**站着让怪打必须正常掉血**、残血切方块切回百分比不动、边挨打边狂切武器血量仍在掉。

## m361 主线目标常显(玩家反馈「没有东西推着你前进」,2026-07-29)
- **前进牵引钉上屏幕**:战况看板加第 4 行「主线【见血】杀怪 13/20」——MainQuestLine 新 `hudGoal(p)` 服务端按 16 阶段各写紧凑进度短句(天数/技能V/强化+/杀怪/精英/永夜层/珍珠数全量覆盖),达成即转**亮绿「已达成!任务书领奖」**;HudInfoPayload 尾加 mainGoal 字段(空值兜底),KillStatsHandler 每 20t 带发,看板宽度/行数计算把第 4 行纳入。
- 配置+1 enableMainQuestHud(默认开;设置屏「界面·HUD」加开/关钮),**configVersion 115→116**。待编译验证:无。实机盯:看板多一行金字主线目标、杀怪数字实时爬、达成转绿、领奖后自动切下一阶段目标。

## m362 获取提示全量补齐(玩家反馈「很多东西不知道怎么获得」,2026-07-29)
- 对账全物品表 vs 配方目录 vs SourceHints:**漏网=战利品宝箱四档、七本职业书、任务书**(其余要么有配方进配方书、要么 m322 已覆盖)。补齐:宝箱按 LootCrateHandler.tierOf 实况写来源(普通=二次BOSS化怪/稀有=巨蟹毒蛛/史诗=红蛛死法凤凰阿努/传说=托罗龙佩恩+可出职业武器)+「右键开箱」用法;职业书=精英概率掉落+右键学第二职业(满2可替换);任务书=自动发放+丢失不影响(背包「任务」钮同功能)。
- 零配置零网络(纯客户端 tooltip 表);待编译验证:无(lootCrate/getClassBook/QUEST_BOOK 访问器全在树)。实机盯:悬停宝箱/职业书/任务书看到灰字「获取:」行。

## m363 渐进解锁(玩家反馈「太多了又很乱」方案A,作者拍板「开始做」,2026-07-29)
- **背包功能按钮随主线阶段逐个点亮**(未解锁=整个不建,列内自动上移补位,前期界面干净):常驻=成长/任务/设置;**阶段1(破晓完成)**=学书/合书/仓库;**阶段2**=强化/装备/兑换/转移;**阶段5**=饰品;**天赋/本命=选职即亮**(看 className 不看阶段——玩家可能提前选职,按阶段锁会气人)。每阶段都有「开新玩具」的爽点。
- **门控信号**:HudInfoPayload 尾加 mainStage(varint,m361 的 mainGoal 之后),KillStatsHandler 每 20t 带发;ClientStats.mainStage 缓存(-1=未同步→**全开防误锁**,开关关同全开=老玩家口径);升档瞬间客户端金字播报「◆ 新功能解锁:xxx(打开背包查看)」进聊天可回翻(1/2/5 三档播报表与门控表同源)。
- **联动**:仓库自动入库(m357)同步门控——阶段<1 不自动收,防「我东西去哪了」;仓库按钮点亮那一刻自动入库同帧生效。
- 配置+1 enableProgressiveUnlock(默认开;关=全按钮常驻),**configVersion 116→117**;括号自检 6 文件全平;待编译验证:无。实机盯:新档背包只有 3+2 钮、活过第一夜领奖看金字播报+学书仓库亮、选职看天赋本命亮、+10 领奖看强化四件亮、关开关全回来。

## m364 每日悬赏(玩家反馈「不多吧又没啥内容」方案B,作者拍板「开始做」,2026-07-29)
- **每天有事干的循环**:每个游戏日(第 2 天起,day 0 让位新手引导)每玩家从 4 池随机抽 3 张不重复悬赏——
  讨伐=杀怪 N(基数12+天数×2 封顶200)/猎首=杀精英 N(基数2+天数/4 封顶12,IS_ELITE 口径)/
  锻造=强化提升 N 级(基数30×当天石基准档面值 10^(档-1),跟随石头经济)/坚守=当日累计存活 N 分钟(默8,死亡当日进度清零)。
- **完成自动发奖**:强化石(基准档+1 精英档)×2 + 终焉精华 ×1,offerOrDrop 进包(配合 m357 自动入库落仓库),
  金字播报+升级音;**三张全清攒连击**——换日检定昨日全清 streak+1(封顶4)否则归零,奖励 ×(100+streak×25)%。
- **状态与同步**:新附件 BOUNTY_STATE(String「day;streak;type,target,prog,done;×3」持久死亡保留,坏档自愈重生成);
  同步零新包=HudInfoPayload 尾加 bounty 字段(m361/m363 追加口径,空值兜底),KillStatsHandler 每 20t 带发。
- **计数挂点**:击杀=自家 AFTER_DEATH(creditedKiller m300 口径,召唤物击杀记主人);
  锻造=EquipmentEnhancer.attempt 尾(RNG 成功级数,背包一键/强化界面/自动卷轴全走此漏斗)+ enhanceWith 强化石直加段,
  两行钩子四入口全覆盖;**工作台配方直加无玩家管线不计入(有意取舍)**;坚守=tick 每秒+1、玩家 AFTER_DEATH 清零。
- **任务书第 5 页签「悬赏」**:连击行+三张卡(标题/进度条暗槽金填充完成转绿/进度文字,坚守显示分钟、锻造大数走 NumFmt.compact),
  进度实时跟 HudInfoPayload;**顺手修隐患**:页签原来「数组下标=页号」,BOSS 页关闭+悬赏开启会错位——改「名字↔页号」双表。
- 配置+9(enableDailyBounty/bountyKillBase/EliteBase/EnhanceBase/SurviveMinutes/RewardStones/RewardEssence/StreakBonusPercent/StreakCap),
  **configVersion 117→118**;括号自检 10 文件全平;待编译验证:无(playSound/offerOrDrop/getServerWorld/附件读写全在树先例)。
- 实机盯:第 2 天起登录看金字「今日悬赏已刷新」、任务书悬赏页三张卡进度条、杀怪/强化/存活数字爬、
  完成看金字+石头精华进包、全清次日看连击 ×1 加成、死亡看坚守清零灰字、关 enableDailyBounty 页签消失。

## m365 BOSS血条整体缩小(作者点名「BOSS血条还是很大,要缩小」,2026-07-29)
- 根因:m181 画框血条槽宽钉死 182(=原版等长),但画框贴图带大幅装饰(框高最大 389 贴图px),整框在屏上约 264×90+ GUI 像素,再叠牌匾名与血量数字,观感过大占屏。
- 方案:布局层加全局缩放系数 `bossBarScale`(默认 0.7,渲染钳 0.3~1.5)——槽宽/框体(随 s 派生)/名字与血量字号(下限 0.5 保可读)/行距全体乘算,手感与布局逻辑零改动,只是等比变小。原版样式条(无画框)不受影响。
- 设置入口:设置屏「界面·HUD」新增「BOSS血条大小」五档预设(0.5/0.6/0.7/0.85/1.0),即点即改(mixin 每帧读配置);`/yongye config set bossBarScale N` 可逐级微调。
- 配置+1,v118→119。零新API零待编译验证(YongyeConfig.get() 在树,其余纯算术)。
- 实机盯:默认 0.7 观感是否合适(作者若有目标大小直接调档);多 BOSS 合并/降档场景下名字与血量数字不糊不叠;设置页点档血条立即变。

## m366 猎杀勋章:击杀里程碑三选一(作者定稿甲案「永久小加成/独立记账」,2026-07-29)
- 需求口径(承上轮对话拍板):升级三选一保留,但触发是**击杀数里程碑不是经验**(不升太快);不是独立模式、
  是加在现有永久存档长线上的新东西;绝不污染已有的动态对位/永久技能书/无限强化/每日悬赏。
- 玩法:累计击杀达到里程碑 → 弹三选一卡 → 选一枚**永久勋章**层数+1。阈值线性递增:第 k 次 = base + k×growth
  (默 10/6 → 10,16,22,28…),后期越杀越久才弹一次,不像经验刷屏。选卡期间击杀照常累计,选完若已够下一档
  **连锁再弹**(逐张选不会漏)。击杀口径 = Monster + creditedKiller(m300 归属统一,召唤物击杀记主人)。
- 六种勋章(全 ADD_MULTIPLIED_TOTAL,每层百分比可配):猛攻+2%攻 / 体魄+2%血 / 迅捷+1%移速 / 坚壁+2%护甲 /
  疾手+1.5%攻速 / 不屈+2%韧性。层数无上限,单层温和靠长线积累。
- **隔离三落地(作者最在意的不污染)**:
  ① 独立记账——层数存新附件 HUNT_MEDALS,绝不写 WEAPON_SKILL_LV / ENHANCE_LEVEL / LEARNED_* 旧成长数据;
  ② 独立修饰符——medal_* 前缀 temporary 修饰符每秒重挂(BlightSetHandler 逐字同款,值不变不重挂防血条闪,
     重生/登录由周期 tick 自动补零额外钩子);
  ③ 动态对位剔除——DynamicScaling 算玩家攻击/血量基准时**除掉勋章乘区**(MULTIPLIED_TOTAL 是独立因子,
     除法精确还原)=怪不因勋章跟涨,勋章是实打实净收益;反向也保证勋章不喂养怪物曲线。
- 交互:三选一屏屏蔽 ESC(三张全是纯增益无错项,一次点击就走,照选职/难度屏先例);收到包时正开着别的屏
  → 挂 pendingMedal 关屏补弹(照 pendingClassSelect);掉线/重启不丢 = HUNT_PENDING 持久附件 + JOIN 补推;
  /yongye medal = 有待选重开弹屏、无待选显示层数汇总与进度(命令树 OP 门,普通玩家靠自动弹屏+看板提醒)。
- HUD:HudInfoPayload 尾加 huntRemain 字段(m364 bounty 同款零新包;-1 关闭 / -2 有待选 / ≥0 剩余击杀),
  看板第 5 行「猎杀勋章:再杀 N 只」淡青牵引,待选转金字「◆ 勋章待选!」;紧凑模式短文案。
- 网络:OpenMedalChoicePayload(S2C,串"id:当前层数:每层pct|×3"——pct 用**服务端配置**拼好,专用服上客户端
  本地 config 是默认值不能当展示依据)+ ChooseMedalPayload(C2S,服务端权威复核必须在 HUNT_PENDING 候选内,
  否则视为造假静默忽略)。
- 附件+4:HUNT_MEDALS(Map)/HUNT_KILLS/HUNT_MILESTONE/HUNT_PENDING,全 persistent+copyOnDeath。
  刻意**不复用 TOTAL_KILLS**——老存档玩家已有几千击杀,复用会开局连弹几十次三选一;新线从 0 起最干净。
- 配置+9(enableHuntMedal/huntMilestoneBase=10/huntMilestoneGrowth=6/六种每层百分比),v119→120。
- 零新API零待编译验证:附件 unboundedMap=LEARNED_SKILLS 先例、属性重挂=BlightSetHandler 逐字、
  JOIN 取玩家=handler.getPlayer() 在树、ANVIL_USE/LEVELUP 音在树、Fisher-Yates 纯算术、屏结构照 ClassReplaceScreen。
- 实机盯:杀满 10 只弹卡、选卡后属性立即生效(F3/成长面板)、看板第 5 行牵引与待选金字、ESC 确认屏蔽、
  正开背包时达标→关背包自动补弹、掉线重连补弹、连杀攒两档逐张弹、关 enableHuntMedal 行消失加成卸下、
  拿勋章后新刷怪血量**不**因勋章上涨(对位剔除的验证点)。

## m367 肉盾护盾改「脱战回盾」(作者实机:一直被攻击血量不掉;「换手检查血量」旧病收口,2026-07-29)
- 病根两层,都在坦克被动护盾那句每秒 `addStatusEffect(ABSORPTION, 60, amp)`:
  ①**每秒回满**——1.21 里已有同效果时走升级路径(新时长 60t > 剩余),升级会重触发 onApplied 把吸收值
  当场回满(原版重吃金苹果回满金心同一机制)。等于坦克每秒免费回满一层 8~12 点盾,前中期怪物 DPS
  根本打不穿,红血纹丝不动=「一直被攻击血量不掉」。
  ②**换手触发回满**——amp = 基础 + (主手持镇魂 ? 1 : 0),镇魂进出主手令效果等级升降:升级立即
  onApplied 回满;降级先被忽视、旧效果 ≤3s 到期后按低级重新上效果又是一次全新 onApplied 回满。
  换手=按需回盾,这就是此前「换手检查血量」怪象的根子。
- 修=照刺客脱战加速在树先例挂 lastCombat 门(受击 line315 / 出手 line133 都刷新):战斗中不续不回,
  盾被打掉就是掉了,吃到红血;脱战 tankShieldCombatDelayTicks(默 100t=5s)后恢复每秒续盾。
  大招不动如山的临时大盾不受影响(战斗中照给,但过期后被动不再免费补)。
- 排除项(为什么不是 m366):DynamicScaling 伤害对位「只增不减」,新号剔除勋章除法=÷1.0 无副作用;
  HuntMedalHandler 只挂 AFTER_DEATH/JOIN/周期重挂,不碰受击链;HudCompactMixin 每帧读 getHealth 显示无陈旧。
- 配置+1(tankShieldCombatDelayTicks,0=回旧恒刷行为),v120→121。零新 API(getOrDefault/getTime 全在树先例同文件)。
- 实机盯:肉盾站桩挨打看金心被打空后红血开始掉、脱战 5 秒盾回满、战斗中反复按 F 换手确认金心不再回满、
  配置设 0 回旧行为、刺客/战士等其他职业不受影响。

## m369 三选一卡面美化(作者反馈"不够好看",2026-07-29)
- 旧卡面=纯色底+1px 框+四行字,确实素。新卡面(零新贴图,全在树 API:fillGradient/drawItem 均有先例):
  勋章色 3px 顶条 + 由上而下勋章色渐变罩(悬停加浓)+ 2 倍物品图标带淡色光晕
  (猛攻=下界合金剑/体魄=金苹果/迅捷=羽毛/坚壁=铁胸甲/疾手=钟/不屈=铁砧,与 IDS 同序)
  + 名字下饰线 + Lv.N →(金色)Lv.N+1 + 悬停整卡上浮 3px、金色"▶ 点击选取 ◀";
  标题区加暗色渐变横幅与两侧金饰线。卡片 104×96 → 112×140。命中区仍按原位判定(上浮纯视觉,防悬停抖动)。

## m370 镇魂持握姿势修正(作者反馈"拿武器的姿势太难看",2026-07-29)
- 病根:六把职业武器里五把(战士/剑客/术士等)用同一套「横持斜握」显示约定([90,0,±90]+合理位移),
  唯独镇魂是竖举旗杆式([0,90,-3]、握点 y=12.5、scale 2.8)——刀刃沿 Y 轴笔直朝天,巨大且无前倾角。
- 修:照在树同族约定改 class_weapon_tank.json 的 display:三人称 [90,0,±90]/[∓12,10,2]/1.5(大剑档,
  比剑客 1.2 大一档),一人称 [90,0,80]/[-8.5,9,2.5]/1.1 与 [90,0,-100]/[6.5,10.5,1.5]/1.1(左手镜像照剑客)。
  纯资源 JSON 零代码。口味微调点:rotation 第三位=倾斜角,translation y=握点高低,scale=大小。
- 实机盯:三选一卡图标/渐变/悬停浮起正常、点击命中区不变;镇魂一三人称都是斜持不再杵旗杆、拔刀七式动作不穿模。

## m372 3A 质感打磨路线图立项(作者:「优化成 3A 的那种 太糙了 列更新列表一点一点更新」,2026-07-30)
- 新增 `POLISH_ROADMAP.md`:12 项打磨清单(伤害飘字/受击方向指示/UI 动效底座/掉落光柱/永夜氛围粒子/升级转场/多杀弹字/页签过渡/命中音分层/美术占位/死亡转场/HUD 微动效),每项注明「防重复」列——立项前已对账现有系统(顿帧震屏 m239/m275、濒死渐晕 m287、连击全家桶 m273~284 均已有,不重做),各项只补缺失层。
- 后续会话按序号领项,一项=一个里程碑,做完在表内打勾记里程碑号;通用验收口径(开关全回退/高频场景零掉帧/BMP 字符/满 alpha/新 API 先核)写在表下。
- 纯文档,零代码零配置。

## m373 伤害飘字(3A 打磨路线图第 1 项,2026-07-30)
- **命中出数字**:玩家每一下打中怪,怪身上弹漂浮伤害数字——普通=暖白小字(0.022 基准,略小于名牌不喧宾),重击(≥怪最大生命 25%,与 CombatFxPayload.HEAVY 同口径)=金色大字 ×1.45;动效=弹出过冲(140ms 0.4→1.35 再 90ms 回 1.0)→ease-out 上浮 1 格带随机水平散布(防叠字)→末 260ms 淡出;数字口径=≥10 取整走 NumFmt.compact,<10 保一位小数(前期 2.5 伤取整成 2 是报假账)。
- **链路**:服务端 CombatFxHandler 命中观察者里先发新 DamageNumberPayload(S2C:xyz+amount+kind)——**刻意不吃 3t 手感节流**(数字漏帧=报假账),AOE 刷屏由独立限额兜住=每玩家每 tick 限发 8 条(DMG_NUM_BUDGET),客户端 DamageNumberManager 同屏 60 条上限+48 格外不画再兜一层;frac 计算前移一次算两用。
- **渲染**:AFTER_TRANSLUCENT(MagicCircle 同挂点),广告牌=手工 Matrix4f(translation→rotate(相机四元数)→scale(-s,-s,s) 名牌同约定)不碰 MatrixStack;文本走 TextRenderer.draw(String,…,Matrix4f,VertexConsumerProvider,TextLayerType,int,int)。**yarn 1.21.1 映射已核**:Camera.getRotation=method_23767 返 org.joml.Quaternionf、draw=method_27521 逐参对上;淡出走 alpha 高字节钳 [8,255](MC 对 <0x04 强制不透明),颜色满 alpha(m213 铁律)。
- 配置+2(enableDamageNumbers 默认开 / damageNumberScale 默认 1.0 渲染端钳 0.3~3.0),**configVersion 121→122**;设置屏「镜头·特效」页加 5 钮(开/关+小0.7/默认1/大1.4)。括号自检 7 文件全平;import 逐条比对在树先例全中。
- **待编译验证 1(极低险)**:TextRenderer.TextLayerType.NORMAL 常量名仓库首用(1.17 起未变;若报错换同枚举 SEE_THROUGH/POLYGON_OFFSET 任一)。
- 实机盯:砍怪看白字弹出上浮淡出、重击看金色大字、AOE 清一群看数字错开不铺屏、大数显示 1.5K/2.3M 紧凑、设置屏点「飘字·关」立即绝迹、点大小档立即变。

## m374 受击方向指示器(3A 打磨路线图第 2 项,2026-07-30)
- **挨打知方向**:玩家受击瞬间准星四周对应方向弹红色弧形指示——存的是**来源世界坐标**,方位角逐帧用「当前视角+来源坐标」重算(灾厄核心箭头 m283 逐字同口径 atan2(cross,dot)),转视角时弧段实时贴着来源走,背后偷袭一眼可辨;弧形=5 个绕准星各转 ±12° 的小矩形拼近似圆弧(fill 走矩阵栈旋转,在树先例),两端 alpha 递减羽化,中段稍厚出锥形;入场 120ms 半径 52→46 收拢,寿命 700ms 线性淡出,浓度随伤害占比上浮(0.55~1.0),同屏 8 条上限。
- **链路**:CombatFxHandler.register() 尾部新增第二个 ALLOW_DAMAGE 观察者(永远放行),受击者=ServerPlayerEntity 时发新 HurtDirectionPayload(来源水平 x/z+severity)——来源坐标攻击者优先、弹射物本体兜底(getSource 在树 m192 先例),无坐标环境伤害(摔落/中毒/凋零)天然跳过;**注册在格挡 m259/坦克真减伤 m208 之后**=被挡下/取消的伤害事件链短路不出指示,与「无效伤害不出打击感」口径一致;每玩家每 tick 限发 4 条防围殴包洪。
- **PVP 也给**:被玩家打同样弹指示(知道方向是防御信息,不算打击感偏袒)。来源贴脸重叠(<0.1 格)方位无意义不画。
- 配置+1(enableHurtDirectionFx 默认开),**configVersion 122→123**;设置屏「镜头·特效」页加开/关 2 钮。括号自检 7 文件全平;零新 API 零待编译验证(HudRenderCallback 全限定名在树×3、RotationAxis/fill/getScaledWindowWidth 全在树)。
- 实机盯:背后被僵尸摸一下看准星后方弹红弧、转身弧段跟着转到正前、被弓手远程射看弧指向弓手、格挡住的攻击不出弧、贴脸苦力怕自爆不出(无方位)、关开关绝迹。

## m375 UI 动效底座(3A 打磨路线图第 3 项,2026-07-30)
- **按钮三件套(YongyeButton 自包含,全部用它的界面自动吃到)**:①悬停过渡=进/出悬停时底/描边/文字 110ms 逐通道 ARGB 插值渐变不再硬切;②按压反馈=onPress 覆写记时刻(yarn 已核 ButtonWidget.method_25306 onPress()V),按下 90ms 内容下沉 1px+底色压暗+高光熄灭(点击音沿用 ButtonWidget 原版=天然统一);③入场动效=构造后 150ms 从下方 5px 上浮+淡入 ease-out——走 clearAndInit 重建按钮的界面(背包列/设置页签切换)自动获得开场动效。三者纯视觉,命中区按真实坐标(m369 同取舍)。alpha 全走 mulAlpha 钳 ≥8(<0x04 强制不透明坑)。
- **界面开场淡入(新 ScreenOpenFx,一处接线零逐界面改)**:AFTER_INIT 判屏幕类包名 com.yongye. → 给该实例注册 afterRender 画 150ms 由暗到透整屏罩(峰值 55% 主题深蓝黑,ease-out),实例级回调随屏幕关闭失效不泄漏;clearAndInit 重进 init 会叠注册,旧回调超时即早退无害。原版界面不碰。
- 配置+1(enableUiFx 默认开,关=按钮硬切+无淡入全回旧),**configVersion 123→124**;设置屏「界面·HUD」页顶加 UI 动效开/关 2 钮。括号自检 5 文件全平。
- **待编译验证 1(低险)**:ScreenEvents.afterRender(screen) 实例级事件+回调五参签名(与在树 AFTER_INIT 同类同包,官方 screen API v1 自 1.16 稳定;报错删 ScreenOpenFx.register() 内 afterRender 段=只损失界面淡入,按钮动效独立不受影响)。
- 实机盯:开背包看左列按钮浮现、鼠标扫过按钮渐亮离开渐灭、点按钮看下沉一闪、开任务书/设置屏看整屏淡入、设置页签切换按钮重新浮现、关 UI动效 全部回旧静态。

## m376 稀有掉落光柱(3A 打磨路线图第 4 项,2026-07-30)
- **地上好货一眼锁定**:稀有掉落物起品质色光柱(蓝=稀有/紫=史诗/金=传说),两组十字交叉竖面(内芯亮+2.6 倍宽淡外圈)顶端渐隐、呼吸脉动按实体 id 错相、20 秒一圈缓慢自转、底部菱形光晕;传说档更粗更亮。
- **纯客户端零插桩**:不给任何掉落点(LootHandler/BossHandler/宝箱/石掉落…十几处)插桩——每 10 客户端 tick 扫附近 ItemEntity(掉落物客户端天然同步)按物品定级,64 格外不扫、同屏 24 根上限;渲染 AFTER_TRANSLUCENT+getLightning(位置+颜色附加混合,m240 起在树已编)。
- **定级口径**:职业武器/神器/战利品宝箱=金;强化石 tier≥7(百万级)金、≥4(千级)紫;其余按原版 Rarity:EPIC 紫/RARE 蓝/更低不起柱(本模组物品注册普遍带 rarity 天然覆盖,EnhanceStoneItem.tier 字段 public 在树)。
- 配置+1(enableLootBeam 默认开),**configVersion 124→125**;设置屏「镜头·特效」页加开/关 2 钮。括号自检 4 文件全平;import 全在树。
- **待编译验证 2(低险,yarn 已核)**:ClientWorld.getEntities()=method_18112 返 Iterable、ItemStack.getRarity()=method_7932——均仓库首用,报错只在 scan() 两行,删之=功能整体退场无连带。
- **裁剪说明**:路线图里"入包确认音分档"这半句先不做(要挂拾取事件另一 API 面),观感主体是光柱;作者要的话下轮点名单独加。
- 实机盯:杀精英掉紫史诗物看紫柱、BOSS 掉宝箱/职业武器看金柱更粗、扔一颗 1 万级强化石看紫柱/百万级金柱、柱子呼吸自转顶端渐隐、捡走柱灭、64 格外走近才亮、关开关全灭。

## m377 永夜环境氛围粒子(3A 打磨路线图第 5 项,2026-07-30)
- **空气里有末世**:永夜等级≥1 玩家四周空中飘灰烬(90% ASH+10% WHITE_ASH 出层次),等级越高越浓——每 tick min(14, 2+等级×2)×浓度倍率颗(等级1≈4/t、5≈12、深渊封顶 14,远低于原版雨雪量级);落点=水平 4~18 格环带(贴脸 4 格不撒防糊镜头)+垂直 -2~+10 格;粒子是原版 ambient 型自带漂移。
- **纯客户端本地零流量**(m310 僵尸紫光同一路子):等级读 YongyeClient.nightfallLevel(NightfallSyncPayload 现成同步零新包);照常被 ParticleReducerMixin 全局闸管到,预算自觉。
- 配置+2(enableNightAmbientParticles 默认开/nightAmbientDensity 默认 1.0 钳 0~3),**configVersion 125→126**;设置屏「镜头·特效」页粒子区加 5 钮(开/关+淡0.5/默认1/浓1.5)。括号自检 4 文件全平。
- 零新 API 零待编译验证:ClientTickEvents/addParticle/ParticleTypes 全在树(MobAura WITCH 先例);ASH/WHITE_ASH 与在树 CRIT/CLOUD 同档 SimpleParticleType 常量。
- 实机盯:/yongye nightfall 1 看空中开始飘灰、拉到 3/5 看变浓、贴脸无粒子不糊镜头、设置屏点浓淡即变、等级归 0 即停、关开关绝迹。

## m378 打磨路线图第二批扩充(作者:「写一个优化列表」,2026-07-30)
- POLISH_ROADMAP.md 追加第二批 10 项(13~22):怪物微型血条/暴击处决飘字档/拾取通知卡/BOSS 讨伐演出/技能释放光晕/永夜音景/主菜单动效/强化结果演出/天赋加点脉冲/全 FX 统一预算闸;另设「大工程点单区」(疾跑残影/BOSS 慢动作/天气联动)不进常规批。逐项照第一批口径标防重复列。
- 纯文档,零代码零配置。第一批进度:1~5 已完成(m373~m377),6~12 待做。

## m379 路线图 v2(采纳外部评审,2026-07-30)
- 作者转来外部评审,全盘采纳:①**执行顺序重排 6→22→7→9→11→13→15→16→18→其余**(22 预算闸提前,否则后续 FX 项做完要回头逐个改管理器);②状态四态化 ☐/🛠/🧪/✅ 区分「CI 绿」与「实机验收」,m373~m377 全部降为 🧪 待作者实机;③顶部加 **Definition of Done** 九条(含"纯服务端不得加载 client 包类""时间驱动叠层到点必消""GUI Scale 1~4 实测");④第 6 项立专项验收卡(六边界:真实变化才播/首同步与维度切换不播/升降有别/跨级合并/暂停不残留/低刺激三配置);⑤第二批逐项补评审风险注记(13 性能约束/14 语义档不拼字/15 满包不误报/16 顿帧只做击杀者客户端/18 防疲劳冷却);⑥新增第三批 23~30(FX 调试面板/战斗日志/色盲低刺激/HUD 安全区/音效并发/新手提示/摄像机统一/回归测试场景)。
- 纯文档,零代码零配置。

## m380 永夜升级/消退转场演出(3A 打磨第 6 项,按 m379 专项验收卡六边界,2026-07-30)
- **升级**=整屏深红黑压暗(峰值 45%×强度,350ms 升峰→持稳→末 40% 渐出)+低音心跳(WARDEN_HEARTBEAT 音高 0.55,m287 起在树已编)+「☽ 永夜降临」导语+阶段名血红大字(矩阵缩放 2.2×),1.8s;**降级(赎夜)**=暗金微光(峰值 18%)+清铃(BELL_RESONATE 1.25 音高)+「☾ 永夜消退」金字短幕,1.2s——刻意好事观感,与升级不混用。
- **六边界落地**:①真实变化才播=客户端基线 lastLevel 与 NightfallSyncPayload 比对同值不播;②首同步/重登/维度切换不播=每客户端 tick 查 mc.world **引用**变化即重置基线为未知(-1),未知态下一次同步只记账——换维度/退主菜单世界对象必换,一个检测点吃掉三种场景,顺手掐掉跨世界残留演出;③跨级合并=客户端只见同步终值,1 直升 5 天然只播「灭世」;④暂停/开界面不残留=演出纯 nanoTime 时间驱动每帧按年龄算 alpha 到点必消;⑤GUI 缩放安全=坐标全出自 getScaledWindowWidth/Height+矩阵缩放居中;⑥低刺激=enableNightfallTransition/transitionIntensity(0~2 同时缩罩与音量)/reduceScreenFlash(**全局弱闪光**,罩浓度减半——后续所有闪光类 FX 均须查询此项,m379 评审要求现在建制)。
- **性能预算**:全屏演出同时最多 1 个,新来替换不叠加。接线=NightfallSyncPayload 接收器里**先比对再更新缓存**(顺序敏感,先更新就永远"同值")。
- 配置+3,**configVersion 126→127**;设置屏「镜头·特效」加 7 钮(转场开/关+柔0.5/默认1/重1.5+弱闪光开/关)。括号自检 4 文件全平;零新 API 零待编译验证(心跳/铃/矩阵画字/HudRenderCallback 全在树)。
- 实机盯(照验收卡矩阵):/yongye nightfall 2 看压暗+心跳+「永夜II·猎杀」大字、/yongye nightfall 1 看金光铃音消退幕、/yongye nightfall 5 从 1 直跳看只播一次灭世、演出中开背包/ESC 看叠层到点消失、重登与去下界回来不播、GUI Scale 1~4 各看居中、弱闪光开着看罩变淡、关转场开关绝迹、专用服上两个客户端各自独立播。

## m381 FX 统一预算闸(3A 打磨第 22 项,按 m379 评审提前落地,2026-07-30)
- **新 FxBudget(纯静态零状态)**:质量档 fxQuality 0=OFF/1=LOW/2=MEDIUM/3=HIGH(默认),即点即改;助手=on()/lowDetail()/amountScale(0/0.4/0.7/1)/scaleCount/scaleLife(LOW×0.7/M×0.9)/scaleDistSq(LOW 距离×0.6/M×0.85)。各效果不自判低配统一问闸,**动态降级而非硬丢**(评审口径)。
- **六个管理器接入**:夜尘=数量过 scaleCount;掉落光柱=OFF 不扫+可见距过 scaleDistSq+上限过 scaleCount(保底 4)+LOW 裁 2.6 倍外圈分段;伤害飘字=OFF 不收+同屏上限过闸(保底 12)+寿命过 scaleLife(淡出起点随寿命联动);受击弧=OFF 清空+LOW 裁两端子段(5→3);界面淡入/永夜转场/按钮动效=OFF 档整体让位(各自专属开关照常独立,双门都开才播)。
- **两头分工**:客户端按画质降(本闸),服务端按卡顿降(LagGuard m335,命中粒子/飘字发包限额已有各自节流)——各管各的互不越权;后续新效果一律接本闸(已写进 POLISH_ROADMAP DoD 第 5 条)。
- **裁剪说明**:评审提的"飘字合并同 tick 同目标"需 payload 加目标 id 字段(协议变更),本轮不动协议,LOW 档用短寿+缩上限达到同等观感密度;点名再上。刀光/魔法阵是 m240/m246 老系统各有独立开关与同屏上限,本轮不强并(防重复列口径),要并下轮点名。
- 配置+1(fxQuality 默认 3),**configVersion 127→128**;设置屏「镜头·特效」页顶新增"特效质量总档"区 4 钮。括号自检 10 文件全平;零新 API 零待编译验证(switch 表达式 m225 先例)。
- 实机盯:质量点"低1"看夜尘变稀/光柱变少变近无外圈/飘字短寿/受击弧变窄,点"全关0"看装饰特效全灭但血条看板等功能 HUD 照常、按钮回静态,点回"高3"全回满,/yongye config set fxQuality 2 命令通道同效。

## m382 击杀连锁演出(3A 打磨第 7 项,2026-07-30)
- **多杀弹字**:短时连杀弹中屏大字——双杀/三杀/四连杀/五连绝灭/七连屠戮/十连灭世(其后每 +5 报「N 连灭世」),◆ 缀字(BMP 安全字符);颜色沿用连击十档色表(comboColor 放宽包内可见,MultiKillFx 同包复用,十档以上自动吃彩虹流转);升调经验音(m279 同款声源,档位越高音越高封顶 2.0)。
- **零新网络零新计数源**:击杀信号复用 CombatFxPayload 的 KILL 包(m239 起每杀必发),客户端滚动窗口计链(距上一杀 ≤3s 链 +1,超时归 1);与连击(命中链)两条链互不相扰——连击看打了多少下,本项看杀了多少只。非播报档位(6/8/9/11…)链照涨不弹字,防 AOE 清怪刷屏;同屏最多 1 条新档替换。
- **DoD 口径**:纯 nanoTime 驱动到点必消;坐标全 scaled 尺寸 GUI 缩放安全;enableMultiKillFx 与 FxBudget.on() 双门;弹出=前 120ms 缩放 2.0→1.5 冲击落位,1.3s 末 300ms 淡出;位置 h/2-58 与永夜转场字幕(h/2-26 起)错层不叠。
- 配置+1,**configVersion 128→129**;设置屏「镜头·特效」加 2 钮。括号自检 4 文件全平;零新 API 零待编译验证。
- 实机盯:AOE 一刀清一群看「双杀→三杀→四连杀→五连绝灭」逐档弹出音调渐升、隔 4 秒再杀看链归 1 不弹、刷 10+ 看十连灭世彩虹字、开背包时演出到点自消、GUI Scale 1~4 居中、关开关绝迹、质量档 0 全关。

## m383 命中音材质分层(3A 打磨第 9 项,2026-07-30)
- **打在什么材质上听得出来**:命中怪物在其位置叠一层材质冲击音——骨(AbstractSkeletonEntity 全族)=骨块脆响(BLOCK_BONE_BLOCK_HIT 音高 ×1.1)/硬甲(getArmor()≥10,重甲怪、傀儡、多数 BOSS)=铁砧轻铿(BLOCK_ANVIL_LAND 音量压到 0.20 音高 1.7+占比)/肉(默认)=击退闷响(ENTITY_PLAYER_ATTACK_KNOCKBACK 音高 ×0.85);重击音量抬档、音高随伤害占比微升;叠在原版怪物受伤叫声之上出层次,零新音频资源。
- **限流口径(评审 27 号音效并发的预览版)**:与镜头手感共用既有 3t 节流——节流内不出音,AOE 连打天然不炸耳;真正的并发管理器(同类限流/优先级/ducking)按路线图第 27 项后续单独立项。
- **BOSS 战 stinger 判定为已有**:BossEntranceFx(m263)出场演出已含吼声+震屏+标题,按路线图防重复列不重做;若作者要"进入 BOSS 视野再来一记"另点名。
- 配置+1(enableCombatHitSound 默认开),**configVersion 129→130**;设置屏「镜头·特效」加 2 钮。括号自检 3 文件全平;getArmor()I 走 yarn 已核(method_6096),AbstractSkeletonEntity/playSound(null,…)/SoundCategory 全在树先例。
- **待编译验证 3(低险)**:BLOCK_BONE_BLOCK_HIT / BLOCK_ANVIL_LAND / ENTITY_PLAYER_ATTACK_KNOCKBACK 常量首用(注册表自动命名档,ANVIL 族在树 ANVIL_USE 已编;若个别报错=该行换族内在树常量即退)。
- 实机盯:砍骷髅听脆响、砍僵尸听闷响、砍铁傀儡/穿满甲精英听金属铿、连点节流内不叠音、重击音量略大音调略高、关开关只剩原版叫声。

## m384 死亡/重生转场(3A 打磨第 11 项,2026-07-30)
- **死亡**=600ms 黑幕渐入后维持暗纱(弱闪光模式上限 160→110),画在 HUD 层位于死亡界面按钮之下,「重生/返回标题」交互零遮挡;**重生**=1s 黑幕渐出(ease-in 前深后快散),渐出中屏幕中央淡入淡出「第 N 天 · <永夜阶段名>」暖灰金一行——一睁眼重建状况感知(天数=ProgressionManager.gameDay(mc.world)+1,在树 m288 同款客户端调用;阶段名=NightfallSync 缓存,空则"昼夜正常")。
- **状态机(纯客户端零网络)**:每 tick 跟踪本地玩家——活→死起渐入;死→活**或玩家实体引用更换**(重生必换实体,双信号兜底)起渐出;世界引用变化整体复位防跨世界残留(m380 同款检测点);演出 nanoTime 驱动到点必消;enableDeathTransition 与 FxBudget.on() 双门;死亡机制/掉落/重生逻辑零改动。
- 配置+1,**configVersion 130→131**;设置屏「镜头·特效」加 2 钮。括号自检 4 文件全平;零新 API 零待编译验证(isAlive/fill/gameDay/drawCentered 全在树)。
- 实机盯:摔死看黑幕渐入且死亡界面按钮能点、点重生看黑幕渐出中央出「第 N 天·永夜X」、keepInventory 开关都试、硬核档死亡界面正常、死亡瞬间退回主菜单再进不残留暗纱、弱闪光开着暗纱变淡、关开关全无。

## m385 怪物头顶微型血条(3A 打磨第 13 项·高风险性能项,按 m379 评审全约束,2026-07-30)
- **打了多少一眼可见**:最近命中的怪头顶显 3 秒微型血条后渐隐——普通怪=细红条(0.90×0.07 格);精英(名牌含「精英」,EliteSkinFeatureRenderer 在树同口径)=更宽更高紫条+顶部金线+左端菱形徽记(**形态差异不只换色**);深灰蓝底带描边余量;血量插值平滑=显示值每帧向真值收敛 ×min(1,dt×10)(真值走客户端数据追踪器同步的 getHealth,读取零开销)。
- **评审约束逐条落地**:只追踪最近命中(命中信号=DamageNumberPayload **尾加 targetId 字段**,自定义包与 mod 同版本锁定双端同批更新安全;不扫世界不轮询)/同时上限 12 条超出挤最久未命中(LOW 档随 FxBudget 再缩,保底 4)/24 格外只老化不画(近距才画兼作遮挡兜底)/隐身怪保留追踪跳过绘制/死亡卸载即撤条/名牌含 BOSS·佩恩·长门的主动剔除(走 m181 画框大条不重复)。
- **渲染**:AFTER_TRANSLUCENT+EntityTranslucentEmissive(新增 4×4 纯白贴图 textures/fx/white.png)顶点染色——半透明层能画深色底,规避 getLightning 加色混合画不出暗底的坑;相机四元数 transform 局部偏移出正对屏幕广告牌,双面绕序,顶点链照 MagicCircle 在树逐字。
- **服务端**:发送门放宽为(飘字 或 血条)任一开即发、客户端各取所需;每 tick 限 8 条既有预算照管。**m381 遗留顺手清一半**:targetId 进包=飘字同目标合并的前置口子已留好,合并本体下轮点名。
- 配置+1(enableMobHealthBar 默认开),**configVersion 131→132**;设置屏「镜头·特效」加 2 钮。括号自检 6 文件全平;关键符号脚本替换后 grep 回验全中(m300a 教训)。
- **待编译验证 2(低险,yarn 已核)**:World.getEntityById(I)=method_8469 首用(报错只在 render 一行);Entity.isInvisible() 首用(标准 API,报错删那行=隐身怪也显示,退路无害)。Quaternionf.transform=JOML 标准。
- 实机盯:砍一只僵尸看头顶细红条 3 秒渐隐、连打看条跟着掉且平滑不跳变、砍精英看紫条+金线+菱记明显不同、砍 BOSS 确认头顶不出小条、AOE 清 20 只看最多 12 条、给怪喝隐身看条消失、走远 25 格条不画走近回来、质量档低看条数变少、关开关绝迹。

## m386 拾取通知卡(3A 打磨第 15 项,按评审五约束,2026-07-30)
- **真进包才播报**:稀有+物品进包时屏幕右缘滑入品质色通知卡(暗玻璃底+品质左条+顶高光 m142 手法+物品图标+名字 ×N),2.5s 后淡出,150ms ease-out 滑入,竖向堆叠;定级复用 LootBeamManager.tierOf(放宽包内可见,评审点名别再写一套)。
- **背包差分口径(不挂拾取事件,五约束一次吃掉)**:每 10 客户端 tick 对背包(主 36+副手)tier>0 物品计数快照比对,某物品**计数增加**才发卡——①满背包没真进包=计数没变不提示;②被别的玩家截胡=自己计数没变不提示;③0.5s 窗口内同物品多次拾取=一次差分天然合并 ×N;④任务奖励/命令发放也提示=「获得即播报」语义;⑤世界引用变化快照清空首轮只记账,登录不刷屏。
- **队列≤5 按品质淘汰**:满了挤「品质最低、同品质最旧」的;队里全比新卡高级则新卡不进——神器/职业武器金档最后被挤(评审优先级要求)。nanoTime 到点必消;enablePickupNotice 与 FxBudget.on() 双门。扫描成本=每 0.5s 读 37 槽,可忽略。
- **过程纠错入册**:初版写了 trimToString,拉 yarn 映射核出正名 **trimToWidth(String,int)=method_27523**,推送前已改——「新方法先核映射」这条铁律又接住一次。
- 配置+1,**configVersion 132→133**;设置屏 2 钮。括号自检 5 文件全平;drawItem/getInventory/getStack/fill 全在树。
- 实机盯:杀精英捡紫装看右缘滑入紫卡、一次捡 3 颗同款强化石看 ×3 合并、背包塞满再捡看不提示、丢地上让队友捡看不提示、连捡 6 种看只留 5 张且金档不被挤、进服那一刻不刷屏、关开关绝迹。

## m387 BOSS 讨伐终结演出(3A 打磨第 16 项,按评审红线,2026-07-30)
- **杀 BOSS 那一刀有牌面**:金色闪光 400ms(reduceScreenFlash 减半)+「◆ 讨伐成功 ◆」金色大字缩放冲击 2.4→1.8 落位+BOSS 名白色副标+凯旋升级音,共 2.2s;顿帧/震屏走 **m275 既有通道加强档**(震 2.4/FOV 3.4/顿帧 6t×倍率,服务端折算配置后发)——**评审红线落地:只作用击杀者客户端,绝不冻结服务端 tick、不影响他人输入**;加强档发 HEAVY 不发 KILL,避免被 MultiKillFx 重复计一次多杀链。
- **BOSS 识别(isBossKill)**:五皮肤 BOSS 按类(BossRageHandler m274 同口径)+ MobBoss 化怪(IS_BOSS 附件)+ 名牌含 佩恩/BOSS 字面量(「xx BOSS」玩家皮肤 BOSS 一并覆盖);字幕名剥 ‖ 血量后缀(m187 血条协议)。新 BossKillFxPayload 空值兜底空串(writeString(null) 踢连接老坑)。
- 配置+1(enableBossKillFx 默认开),**configVersion 133→134**;设置屏 2 钮。括号自检 7 文件全平;零新 API(实体类/附件/playSound/矩阵画字全在树)。
- 实机盯:杀凤凰/阿努比斯看金闪+讨伐成功+名字副标+重顿帧、杀 0.8% BOSS 化僵尸也触发、旁边队友屏幕无演出且操作不卡、弱闪光开着金闪变淡、名字无 ‖数字尾巴、关开关只剩普通击杀反馈。

## m388 永夜环境音景(3A 打磨第 18 项,按评审防疲劳约束,2026-07-30)
- **空气里的声音**:永夜≥2 每 20~40s(≥4 级 12~28s)在玩家远处响一声氛围音,四池=洞穴幽响(AMBIENT_CAVE)/低鸣心跳(WARDEN_HEARTBEAT 在树)/夜魇远啼(PHANTOM_AMBIENT)/深海低吼(ELDER_GUARDIAN_AMBIENT),音高 0.6~0.9 随机压暗;补 m377 视觉粒子的听觉半边。
- **防疲劳四约束落地**:①同种不连播两次=记上次池号重抽同款顺移一位;②战斗中降概率=本地血量下降记 lastCombat,8s 内 75% 吞掉(攻击不算,安全刷怪不吞);③重要演出避让=永夜转场/讨伐演出进行中跳过(两类各加包内只读探针 isPlaying/isShowing);④不贴耳=落点水平 10~18 格环带垂直 ±3 **定位播放**(World.playSound 八参签名与在树 ChargeSlashHandler 服务端同方法,客户端世界同签名本地播),音量单独可调默认 0.6 刻意当底噪。
- 触发点先重掷间隔再判条件(不播也不每 tick 重试);世界引用变化重置计时+进世界先静 10s;enableNightAmbientSound 与 FxBudget.on() 双门。
- 配置+2,**configVersion 134→135**;设置屏 5 钮(开/关+轻0.3/默认0.6/响1.0)。括号自检 6 文件全平。
- **待编译验证 2(低险,各一行退路)**:AMBIENT_CAVE 按 RegistryEntry 型取 .value()(.value() 在树 Yongye:183;若实为普通 SoundEvent 报错=删 .value() 即退);PHANTOM_AMBIENT/ELDER_GUARDIAN_AMBIENT 常量首用(注册表自动命名档,报错换池内在树常量)。World.playSound 客户端调用同签名在树。
- 实机盯:/yongye nightfall 2 站着等半分钟听远处幽响且有方向感、连续两声不同款、被怪咬着打时基本听不到、转场演出播放中不插音、音量三档点着变、等级 1 无声、关开关绝迹。

## m388 勘误(2026-07-30)
- m388(e6ec79e)的 commit 标题误带「m389前置说明见m388」字样(拼提交信息时手滑),内容确为 m388 永夜音景本体;里程碑账以 DEVLOG/HANDOVER 为准,下一里程碑照常 m389。AMBIENT_CAVE.value()/两首用常量随本次 CI 编译通过,待编译验证清零。

## m389 评审修补小冲刺(作者转外部评审第二轮,全盘采纳,2026-07-31)
- 评审结论=m380~m388 工程状态健康、无回滚项,但实机验收前先清六处:一个物品数据正确性问题、一个渲染热路径优化、一处残留清理缺口、一处运行中关闭残留、两处文档定性/过期说明。本轮零新配置零协议变更,**configVersion 仍 135**。
- **①拾取卡保留真实组件(PickupNoticeFx)**:旧快照键=裸 Item 且卡片 new ItemStack(item) 重建,自定义名/强化/品质组件全被洗掉(不同强化的同基础武器误合并/卡片显默认名/tierOf 按裸栈失真);改=键=物品 id+显示名+Rarity+ENHANCE_LEVEL 组件指纹(**刻意不含耐久 DAMAGE**——计入则用工具掉耐久换指纹会被误报成拾取),快照存该指纹代表性 ItemStack 样本(copyWithCount(1),yarn 已核 method_46651),卡片图标/名字/定级全取真实组件;另加**每物品总量闸**=强化/砧上改名等纯组件变化(旧指纹-1 新指纹+1 总量不变)不发卡,只有该物品总数真涨才播报,同物品多指纹间按 allow 扣减不重复计入。
- **②血条热路径去分配+换世界即清+统一 BOSS 判定(MobHealthBarManager)**:quad/diamond 重写纯标量顶点(逐分量算 8 顶点,零 new float[][],多怪持续显示不再产短命数组防 GC 抖动);广告牌基向量改静态 scratch Vector3f(渲染单线程安全);render 头加世界引用比对,换维度立即 BARS.clear()(防新世界恰撞同实体网络 id 旧条短暂错绑);BOSS/精英剔除改复用 MobAuraFeatureRenderer.tierOf(放宽包内可见,评审点名别维护第二套名字表)——五皮肤 BOSS 类/HIM/「xx BOSS」后缀全覆盖(旧名字表会漏未改名的五类 BOSS 实体),精英口径连带覆盖毒蛛/巨蟹实体类(与精英光环/叠皮一致,行为微调已在注释注明)。
- **③讨伐演出运行中可关(BossKillFx)**:双门从「收包时查一次」改渲染入口每帧复核,演出中关 enableBossKillFx 或 fxQuality 调 0 字幕/金闪立即消(路线图「关开关=零残留」口径);副标 trimToWidth(w-40) 裁宽,超长自定义 BOSS 名不出屏(trimToWidth=method_27523 m386 已核已编)。
- **④音景换世界完整复位(NightAmbientSound)**:补 lastCombatNanos=0(上一世界的战斗抑制不带进新维度,原可残留最多 8s)与 lastIdx=-1(「同种不连播」参照一并复位)。
- **⑤FxBudget 定性修正(评审方案 A)**:m381 落地的是无状态质量档(on/lowDetail/amountScale/scaleCount/scaleLife/scaleDistSq),不是计数型运行时预算——类注释与 POLISH_ROADMAP 第 22 项改名「FX 统一质量档」,allow(类别,代价)/used/dropped 计数族并入第 23 项 FX 调试面板一起做(23 号价值栏已并入),现有各管理器接法不变不硬塞复杂度。
- **⑥过期说明清理(POLISH_ROADMAP)**:第 22 项「飘字同 tick 合并需协议变更暂裁」已过期——targetId 已于 m385 补进 DamageNumberPayload,改「协议缺口已清,待第 14 项或压力优化时实现合并窗口」;第 18 项措辞按实际实现改「重要全屏演出避让(m388=永夜转场+讨伐演出;BOSS 入场/持续音乐的真 ducking 留 27 号并发管理器)」。
- 括号自检 6 文件全平;关键符号脚本替换后 grep 回验全中(m300a 教训);待编译验证 1(极低险)=ItemStack.copyWithCount(I) 仓库首用(yarn 已核 method_46651,报错换 copy()+setCount 两行退)。本轮是修补不是验收,m373~m388 各项 🧪 状态不动;实机验收冲刺照评审分组:转场组 6/11/16、战斗组 1/2/7/9/13、掉落组 4/15、环境与性能组 5/18/22、UI 组 3,通过逐项 🧪→✅。
- 实机盯(本轮增量):捡两把同名不同强化武器看出两张独立卡且名字/图标对、强化手里武器看不弹「获得」卡、用稀有工具掉耐久看不误报、砍一群怪出血条后去下界回来看无残条、打未改名的五类 BOSS 看头顶不出小条(走画框大条)、打毒蛛/巨蟹看紫精英条、讨伐演出中途关开关看字幕立即消、超长名 BOSS 看副标不出屏、换维度刚受过伤看新维度环境音不被旧抑制吞、点质量档低看血条上限照缩。

## m390 CI 三 Action 升级维护(评审附带项,2026-07-31)
- 病根=GitHub Actions 提示 checkout@v4/setup-java@v4/upload-artifact@v4 面向 Node.js 20,runner 正在强制 Node.js 24,产生维护警告(不影响构建结果但迟早断供)。
- 修=三 Action 升官方当前主版本 **checkout@v7 / setup-java@v5 / upload-artifact@v7**;版本号不瞎猜——经 api.github.com 的 releases/latest 与 tags 双通道核实(checkout v7.0.1/setup-java v5.6.0/upload-artifact v7.0.1,浮动主标签 v7/v5/v7 均真实存在),按惯例钉浮动主标签自动吃补丁版。
- 纯 CI 配置零 Java 零资源,configVersion 仍 135;本次推送触发的工作流即为升级后首跑=自证验证,盯 conclusion=success 且日志无 Node 版本警告。
