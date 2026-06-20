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

---

> 后续待办、已知边界与可做方向见 **[HANDOVER.md](HANDOVER.md)** 第 6 节。
