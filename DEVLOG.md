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
