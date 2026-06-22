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
