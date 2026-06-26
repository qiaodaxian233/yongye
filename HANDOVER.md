# 永夜 · 项目交接文档（HANDOVER）

> 给接手这个项目的人（或新对话里的 AI 助手）。读完这份就能无缝接上，不用回头翻聊天记录。
> 仓库：https://github.com/qiaodaxian233/yongye　·　Minecraft **Fabric 1.21.1** · 纯 Java，无前置 mod（Fabric API 除外）。

---

## ⭐ 开发守则（置顶 · 所有协作者与 AI 助手必读必守）

```
以瞎猜接口为耻，以认真查询为荣。
以模糊执行为耻，以寻求确认为荣。
以臆想业务为耻，以人类确认为荣。
以创造接口为耻，以复用现有为荣。
以跳过验证为耻，以主动测试为荣。
以破坏架构为耻，以遵循规范为荣。
以假装理解为耻，以诚实无知为荣。
以盲目修改为耻，以谨慎重构为荣。
```

1. 不猜接口，先查文档。
2. 不糊里糊涂干活，先把边界问清楚。
3. 不臆想业务，先跟人类对齐需求并留痕。
4. 不造新接口，先复用已有。
5. 不跳过验证，先写用例再跑。
6. 不动架构红线，先守规范。
7. 不装懂，坦白不会。
8. 不盲改，谨慎重构。

> 本项目沙箱编译不了 Fabric/Mojang 依赖，所有改动由人类本地 IDEA + JDK21 编译验证。因此：拿不准的接口/签名一律先查仓库现有用法照抄；实在无法在本地核实的，明确标注「待编译验证」，绝不假装确定。

---

## 0. 一分钟速览

- 这是一个 **极难灾变生存** 玩法 mod：白天搜刮、夜晚逃命，任务失败会推高「永夜」等级，怪物锁定/挖墙/爬墙追杀；玩家靠 **装备血量 + 8 种技能书 + 10 种背包神器 + 饰品栏 + 职业 + 随机掉落** 反向变强,并按游戏天数推进难度(类「惊变」)。
- 代码量:**127 个 Java 文件 / 约 1.1 万行 / 约 385 项可调配置 / DEVLOG 146 个里程碑**。
- **项目完成度(估)≈ 99%**:核心玩法、成长线、世界节奏、Boss、HUD、饰品栏、时间进度、职业系统、天赋树(命令版+GUI版)、职业专属技能/武器/盾、开局选职业(卡图版)、职业大招、坦克真减伤、武僧全用途耐久惩罚均已落地;余下主要是整体数值平衡(配置全开放待实测)、若干美术占位替换、调试菜单、真弧形盾面渲染。

---

## 0.5 当前状态(截至 **m153**(m153 修永夜尸潮实体爆炸(下界多人传送刷到数万只拖崩 TPS):现象=玩家上传崩溃日志,杀死实体数 7477→8899→43622→49835 持续暴涨+「Can't keep up 813 ticks behind」最终掉线;存档「幸运方块单方块生存」当时在下界 the_nether_128、3 玩家 qiaodaxian/wuyainhe/FK_GK 高频互相传送;根因=NightfallHordeHandler(永夜尸潮,永夜≥1 每 40tick 在每玩家 nightfallHordeRadius=24 格内补刷至 target=min(base100×永夜,max200))**只统计 24 格框内的怪(existing)、无任何全局实体上限**——怪出生即 setTarget(player),玩家一传送那个统计框跟人走、老的几百只被甩在原地不再计数→新落点 existing≈0 又补满 target→反复传送旧怪在地图无限累积(叠动态对位拔成肉盾杀不动+第10天起 mobBossChance 部分 BOSS 化+BOSS 召唤小怪,日志满屏「进入狂暴」即此群下游);对比硬核 HardcoreSurvivalHandler.ambushSpawn 有 globalMaxHostilesNearby=60/globalHostileRadius=28 全局预算闸,尸潮完全没有;修法=尸潮 per-player 循环里(锚石检查后、existing 统计前)复用硬核同款全局预算:gbox=expand(globalHostileRadius) 统计 Monster 数 globalHostiles≥globalMaxHostilesNearby 则 continue,且 want 再 min(globalMaxHostilesNearby−globalHostiles) 防单 tick 顶过量→总量硬闸 ~60/玩家与单点 target 双保险(注:全局 60 比单点 200 紧,生效后尸潮实际被钳在 ~60/玩家,要更密的潮改 globalMaxHostilesNearby 而非 nightfallHordeMax);已修是 yongye 实打实 bug,但存档是幸运方块整合包+下界,幸运方块自身亦可能刷怪、日志无法 100% 切割两者占比,本次仅摁住 yongye 份额;遗留=已在老存档地图上累积的怪本修不清(玩家 /kill @e 即可),向后修复防再爆+vanilla 远距 despawn 渐清;静态自检 NightfallHordeHandler 括号{}8/8·()89/89 配平+globalMaxHostilesNearby/globalHostileRadius 配置存在+Box/MobEntity/Monster import 齐全;全用既有 API/配置无新接口无待编译验证点;无新文件改 1 Java(NightfallHordeHandler)无配置变更 configVersion 不变(仍9) · 上一里程碑 m152 细柱传送后把玩家撞下柱(m151 续):需求=单格高柱躲正上方僵尸上不去时,僵尸 TP 到人旁边后要把人撞下去(否则只传上柱顶玩家照样站着躲);改法=在 m151 细柱兜底传送成功(teleportOntoPlayer 返 true)后给玩家水平冲量 player.setVelocity(cos(ang)*kb,0.2,sin(ang)*kb)(随机水平方向+轻微上抬脱离柱顶)kb=pillarCheeseKnockback 默认0.6(柱仅1格宽足以推出边缘坠落,0=只传不撞回退m151);关键=玩家移动客户端权威,服务端改 setVelocity 不自动同步必须显式 player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player)) 速度才真生效+置 velocityModified=true;静态自检 PursuitHandler 括号{}42/42·()280/280配平+新字段 pillarCheeseKnockback 定义↔引用一致+import已加;待编译验证(本轮唯一)=EntityVelocityUpdateS2CPacket 仓库首次用的S2C包(new EntityVelocityUpdateS2CPacket(player)读速度+ServerPlayerEntity.networkHandler.sendPacket 均1.21.1标准、仓库客户端侧已用 mc.player.networkHandler.sendCommand 字段同名)风险低但首用故标;改 PursuitHandler.java(import+1+击退段)+YongyeConfig.java(新增 pillarCheeseKnockback)configVersion **8→9** · 上一里程碑 m151 细柱兜底传送(修「1×1 高柱躲正上方」僵尸上不去):现象=玩家用单格高柱躲怪正上方,僵尸既搭不上也传不上;根因三条路全卡=①搭柱 mobPillarUp+挖墙都卡 ProgressionManager.canMobsDig(第5天才解锁)前期不搭②现有「卡住传送」即便触发,teleportNear 是去玩家四周找有实心地面的落脚点——玩家站细柱顶时四周同高全空气、底下无实心块→找不到落点→传送失败③爬墙需精英/BOSS/永夜≥3 普通怪前中期不触发;改法=加独立兜底传送 pursuitTeleportPillarCheese(默认开),触发=玩家水平距离≤pillarCheeseMaxHorizontal(2.5 近乎正上方)且高出≥pillarCheeseMinHeight(4.0)且持续无进展达 pursuitStuckTicks(3s)→teleportOntoPlayer 直接把怪传到玩家所在格(站玩家脚下方块=柱顶)不找地面/不依赖墙/不依赖解锁日;放在现有传送后、挖墙前,先给搭塔/爬墙3秒机会(4格略高于搭塔触发的3格)仍上不去才兜底;复用 STUCK 卡住跟踪+pursuitMaxTeleportsPerTick 限流+!anchor(世界之锚仍免疫);新辅助 teleportOntoPlayer 与 teleportNear 区别=不找四周地面直接 refreshPositionAndAngles(player.getX/Y/Z)+清速度落距+双端末影传送粒子音效;静态自检 PursuitHandler 括号{}41/41·()271/271配平+用到的 hasVehicle/stopRiding/spawnParticles/getNavigation/refreshPositionAndAngles/playSound 本文件均已用过+dx/dz/wallAhead 作用域内+新字段定义↔引用一致;无「待编译验证」点全用既有/标准API;改 PursuitHandler.java(配置3字段已在 m150 随 configVersion 8 加入) · 上一里程碑 m150 难度高奖励也高(世界难度→掉落倍率):需求=难度越高奖励越高(此前世界难度只让怪变强不影响掉落);改法=LootHandler 引入难度奖励倍率 `dm=enableDifficultyLootBonus?max(difficultyLootFloor,DifficultyManager.mobMult()):1.0` 挂**世界难度**(游玩~永夜 mobMult 0.5~6.0)保底 difficultyLootFloor 默认1.0(低难度不减、只困难以上加成);概率掉落把 dm 折进综合倍率 `lm=baseLm*dm`→下游11处 `*lm` 全自动含难度加成无需逐处改;精英必爆数量 `gm=(dynamicLootScaleGuaranteed?baseLm:1.0)*dm`→防滚雪球仍按 baseLm 缩减、难度加成单独乘且不被该开关吞掉;倍率 HARD×1.6/HELL×2.5/ABYSS×4.0/ETERNAL×6.0 与怪强度同源,ETERNAL≈6×部分概率超1.0必掉=有意「最高难度最丰厚」嫌多调低 floor 或后续加帽;挂世界难度而非永夜(永夜刚用于 m148 怪血门控、且语义指开局选的难度线);静态自检 LootHandler 括号{}34/34·()235/235配平+mobMult()返回double同包无需import+新字段定义↔引用一致;无「待编译验证」点全用既有/标准API;改 LootHandler.java+YongyeConfig.java(难度奖励2字段+顺带定义 m151 细柱传送3字段)configVersion **7→8** · 上一里程碑 m149 技能书等级上限 65535→10亿:需求=技能书 V1~65535 提到 10亿(与 m127 属性上限 10亿 对齐)。根因/坑=上限不止一处——配置 `skillBookMaxLevel`(各处 clamp 都读它:HealthSkillBookItem/SkillBookItem/PlayerSkillManager/SkillEffectManager/两个合成 recipe,改这里它们自动跟)+ ModCommands 两处**写死** `IntegerArgumentType.integer(1, 65535)`(`/yongye book` 健康技能书、`/yongye skillbook` 职业技能书),光改配置不改命令仍卡 65535;改法=配置默认 65535→1000000000、两处命令上限同步→1000000000、两处注释(Yongye.java「V1~V65535」/HealthSkillBookItem「V1~V65535」)改为 V10亿;10亿 < int 上限约 21.4 亿装得下、封顶后属性受 m127 的 10亿 上限钳不溢出;`/yongye artifact` 的 `integer(1,6)` 是神器等级不动;静态自检 4 文件括号全配平(YongyeConfig/ModCommands/Yongye/HealthSkillBookItem)+全仓库 `65535` 残留 0;无新接口纯值/注释替换,build 风险极低(待本地 build 但 m148 已 BUILD SUCCESSFUL、本轮仅改字面量);无新文件改 4 现有,configVersion 不变(改默认值非加删字段)。**注:m148(689455a)作者本地 `./gradlew build` 已 SUCCESSFUL ✅**,m141-m148 全部「待编译验证」点(含 m146 1.21 附魔取等级 API、m145 getServer/getPlayerList)**均已编过、标记可清** · 上一里程碑 m148 「按玩家攻击拔怪物血量」门控从世界难度改为永夜等级≥5(永夜V·灭世):需求=m147 挂世界难度「困难+」仍太难,改成「永夜5 才开启」。关键=「永夜5」指 NightfallManager 的**永夜等级 5**(等级 0~5 有名:0昼夜正常/1永夜I暗潮/2永夜II猎杀/3永夜III围城/4永夜IV灾变/5永夜V灭世,>5 为「深渊N层」),**非** GameDifficulty 那个世界难度——两套系统:世界难度开局选一次固定,永夜等级随游戏推进/任务失败往上爬,「才开启」用永夜这条会爬的线才对;修法=把 m147 的 `DifficultyManager.getLevel()>=GameDifficulty.HARD.ordinal()` 换成 `com.yongye.system.NightfallManager.getLevel()>=5`(5=永夜V·灭世;≥5 含其后深渊N层),前中期(永夜<5)怪不按攻击拔血,世界沉入永夜V后才堆血;`diffMult`(=DifficultyManager.mobMult(),第44行)仍保留——那是「缩放幅度」倍率,与「是否开启」的门是两码事,开启后世界难度仍调幅;伤害对位段仍未受此门约束(玩家只点名血量);静态自检括号 {}7/7·()44/44 配平+NightfallManager.getLevel() 返回 int 真实存在+无 GameDifficulty 残留无悬空 import;全用 repo 既有/标准 API(getLevel() int+int>=int)无新接口无「待编译验证」点;无新文件改 1 Java(DynamicScaling.java)无配置变更 configVersion 不变(仍7) · 上一里程碑 m147 「按玩家攻击拔怪物血量」加困难+难度门(普通打不过修复):根因=DynamicScaling.scaleToNearestPlayer 的血量对位段只受 config enableDynamicMobScaling 管、不分难度,普通(适中 NORMAL)也按 玩家攻击×targetHits×diffMult 把怪血往上堆,玩家越强怪越肉→普通也被堆成肉盾打不过(diffMult 只改幅度不会关掉缩放本身);修法=血量段(原46-56)前加硬门 hpScalingOn=DifficultyManager.getLevel()>=GameDifficulty.HARD.ordinal()(≥3=困难起),仅 HARD/HELL/ABYSS/ETERNAL 才按攻击拔血,PLAY/EASY/NORMAL 及未设定(getLevel()=-1→按适中处理)都不拔;关键:难度档非原版 net.minecraft Difficulty,而是自定义 GameDifficulty 七档(0游玩/1简单/2适中/3困难/4地狱/5深渊/6永夜,HARD=ordinal 3)——之前设想的 Difficulty.HARD 是错的,查 DifficultyManager+GameDifficulty 才纠正;门加在方法内血量段,一处覆盖 MobEnhancementHandler(普通怪)+MobBossHandler(BOSS化)两个调用点;伤害对位段(58-68,按玩家血量拔怪伤)未受此门约束——用户只点名「血量」没说伤害,不擅自扩范围,仍照旧 diffMult(若也要按难度门控需另说);两处新引用走全限定名(同既有 mobMult() 风格)无需补 import;静态自检括号 {}7/7·()42/42 配平+getLevel() 返回 int 真实存在+GameDifficulty.HARD 常量存在;本轮全用 repo 既有/标准 API(getLevel() int、HARD.ordinal() 标准枚举方法、int>=int 比较)无新接口、无「待编译验证」点,比 m141-m146 干净;无新文件改 1 Java(DynamicScaling.java),无配置变更 configVersion 不变(仍 7) · 上一里程碑 m146 横扫之刃对自定义武器生效(B方案手搓+补标签):根因=ChaosBladeItem/ClassWeaponItem均extends Item非SwordItem,1.21.1横扫在PlayerEntity.attack判instanceof SwordItem才发动→普通Item永不横扫,横扫之刃只放大横扫伤害故无东西可放大=没效果;且sweeping_edge的supported_items=#minecraft:enchantable/sword,武器不在标签里附魔台/铁砧附不上(只能/enchant硬塞挂名);补标签只解决「附得上」非「有效果」(横扫开关在SwordItem标签管不到)。修法B(不重构武器类避开SwordItem构造+属性冲突):WeaponCombatHandler已有AttackEntityCallback,暴击门槛之前(横扫不依赖暴击)加trySweep——蓄满+在地面+非疾跑读武器横扫之刃等级,对主目标周围LivingEntity补一圈AOE,伤害=1+攻击力×level/(level+1)贴近原版,带击退+SWEEP_ATTACK粒子+横扫音效,只给本mod武器(调用前isWeapon过滤、非SwordItem不与原版双重);新建data/minecraft/tags/item/enchantable/sword.json(replace:false追加chaos_blade+5职业武器共6把)让横扫之刃附得上;静态自检花括号10/10圆括号104/104配平+trySweep定义调用各1+新import全被引用+tag JSON合法;待编译验证1.21数据驱动附魔取等级(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.SWEEPING_EDGE).orElse(null)+EnchantmentHelper.getLevel(RegistryEntry,ItemStack);get报错改getOrThrow、getEntry返回类型按IDEA调)+getNonSpectatingEntities/takeKnockback/isTeammate标准API;新文件+1(tag非Java)改1Java,无配置变更configVersion不变。另:镐子不显示经查非本mod(无注册镐/无覆盖原版物品模型/无物品渲染mixin,客户端mixin仅HUD/标题/粒子),疑外部资源包或渲染mod,待作者确认现象 · 上一里程碑 m145 玩家皮肤僵尸BOSS第一步(链路+jiemoLI打底)：僵尸被MobBoss BOSS化时做成"玩家皮肤BOSS"——复用EliteSkinFeatureRenderer(已挂所有生物渲染器按名牌叠皮肤)+MobBoss命名/血条,僵尸模型UV兼容玩家皮肤64x64;资源jiemo_li.png(作者上传的打底/fallback皮肤);YongyeConfig加enablePlayerSkinZombieBoss(默true)、CONFIG_VERSION 6→7;MobBossHandler加SKIN_BOSS_OWNER Map(每在线玩家同时最多一只)+pickSkinTarget(选无活皮肤BOSS的在线玩家,取前清死项)+makeMobBoss僵尸分支(选到玩家→名牌「<名> BOSS」+记Map,否则默认【BOSS】怪名),import ZombieEntity/MinecraftServer/UUID;EliteSkinFeatureRenderer.textureFor加分支(僵尸+名牌endsWith" BOSS"且非「【」开头→jiemo_li.png);第二步(下轮build验证后)把jiemo_li换成按玩家名查在线官方皮肤Identifier拿不到再jiemoLI——客户端取皮肤API(getPlayerListEntry→SkinTextures)仓库首次用故隔离;静态自检三文件括号配平+符号一致+import齐+资源在;待编译验证mob.getServer()(报错改mob.getWorld().getServer())/getPlayerList/getGameProfile().getName()多标准风险中低;无新文件改3个+1资源,配置+1 · 上一里程碑 m144 关磁吸+关材料/书堆叠(Sophisticated Backpacks自带)：磁吸=Yongye主类注释掉LootMagnetHandler.register()直接不挂载彻底失效(光改enableLootMagnet默认false对老存档无效——json已存true会盖新默认那个老坑,故走停挂载;开关默认同步改false+标停用当前无效保留)；堆叠=ModItems去掉10处.maxCount(99)(8材料+血量书+技能书)回归原版64,maxCount(16)护符/守护书与maxCount(1)职业书/选职书不动；影响=磁吸立即失效不看配置、老存档已堆99材料不丢但上限回64之后不能堆过64；静态自检三文件括号配平+register无激活行+enableLootMagnet=false+maxCount(99)残留0；无新接口风险极低待build；无新文件改3个现有,configVersion不变 · 上一里程碑 m143 开局口粮：玩家首次进入发20个面包。YongyeConfig 新增 giveStartingFood(默true)+startingFoodCount(默20,0=不发)、CURRENT_CONFIG_VERSION 5→6；ModAttachments 新增 GOT_STARTING_FOOD(persistent BOOL+copyOnDeath,每人首次只发一次、死亡保留→重生不再发,照GOT_STARTING_KIT模板)；StartingKitHandler 在 JOIN 里、背包逻辑之前(背包未装SophisticatedBackpacks会return跳过)加发放段,按64一组拆叠 new ItemStack(Items.BREAD,n),发完打标记；语义=首次进入发一次,非每次重生(要每次重生发需另走AFTER_RESPAWN不打标记)；旧json缺键GSON保留初值无盖默认问题；静态自检三文件括号配平+符号定义引用一致+import齐；全标准API无新接口风险极低待build；无新文件(改3个现有),配置+2 · 上一里程碑 m142 HUD精致玻璃化(方案A)：HudCompactMixin 把全直角纯色fill的HUD改出玻璃质感——底衬0xCC14406E直角块→yongye$panel(2px切角圆角+玻璃描边0xFF2E7AD0+顶亮底暗渐变0xCC1B5288→0xCC0C2C50+顶内高光)；血条纯红→渐变0xFFE83030→0xFF8B0000+顶高光+末端光头0xFFFF7070+底槽内阴影(金吸收保留)；食物/资源条改用yongye$bar(底槽+内阴影+渐变+高光+末端光头)；新增辅助yongye$lerp/gradV/panel/bar(全ctx.fill无新接口)；配色基准红血/黄食/蓝资源不变只加层次；查明截图里快捷栏每格紫箭头非本mod(代码无、hotbar.png无，来自外装mod/资源包)；静态自检括号29/29·208/208配平+方法定义调用齐；预览docs/hud/m142_preview.png(mockup非实机)；HUD另有方案B发光/C极简待作者看A实机再定；无配置变更；待本地build+进游戏实测观感 · 上一里程碑 m141 副手框换145×120正确比例修m140拉伸变形：副手 hotbar_offhand_left/right.png 从 m140 那张 1254×1204 中心透明大图（比例1.04，被原版InGameHud固定绘制到约29×24整张stretch→比选中框又大又横向拉宽变形＝“框太大别扭”根因）换成作者 145×120 专用图（比例1.208＝29:24，拉进副手框不变形）；照原版偏移做left/right镜像（left框靠左右留白32、right框靠右左留白35；原版那侧留白是朝快捷栏间隔、框本体对准副手物品；曾误居中已纠正）；offhand/selection sprite 均无自定义渲染纯原版InGameHud；只改副手两张，选中框 hotbar_selection.png 仍 1254×1204 不动（与蓝玻璃副手风格暂不统一，待后续定换不换）；无Java/配置改动、configVersion不变；待本地build＋进游戏实测槽位对齐 · 上一里程碑 m140 删永夜之翼+选中框/副手玻璃框:选中框 hud/hotbar_selection.png 与副手 hotbar_offhand_left/right.png 放入作者玻璃框(中心透明,三处同图),攻击指示不动;删 NightWingItem(ElytraItem背饰)——ModItems去注册+import、ModItemGroups去创造栏、ModCommands去/yongye nightwing、DebugScreen去按钮、欢迎书页改名去飞行句、AccessoryScreenHandler 第11槽去NIGHT_WING仅留原版鞘翅、删 AccessoryGliderMixin(唯一用途是让翼滑翔)+从mixins.json移除条目(原版鞘翅穿胸甲照常滑翔)、删模型/配方/贴图/lang;旧存档翼物品失效、饰品栏鞘翅格滑翔取消;文件129→127,无配置变更 · 上一里程碑 m139 HUD配色修正(m138理解反了)+底衬对齐快捷栏:HudCompactMixin 底衬黑0xC0000000→半透明蓝0xCC14406E 且宽度 left-2..+BAR_W+2→left..left+BAR_W 与原版182宽居中快捷栏左右对齐;血条撤销蓝改回红(0xFF3B0000/0xFFCC1010);食物撤销绿改黄(0xFF332600/0xFFE6C42A,保持6px+血条下方);资源条 mpColors 取消分职业、六职业统一蓝{0xFF0A1E38,0xFF2E7AD0,0xFF7FCFFF};最终=红血/黄食/蓝资源/蓝底衬全职业一致;无新文件/配置 · 上一里程碑 m138 血量HUD改蓝+食物条加粗上移:HudCompactMixin 血条深红底0xFF3B0000→深蓝0xFF06223F、鲜红填充0xFFCC1010→亮蓝0xFF2E86D8、高光→青玻璃0x66BFE6FF(金吸收保留);食物条 FOOD_H3→6 加粗、从最底上移到血条正下方(顺序血→食→MP)、改草绿0xFF8FBF4A 与蓝血条/琥珀MP区分(其实食物条早有但3px又在底部没被认出);底衬覆盖校验过;待作者发图接入 hud/hotbar_selection/offhand/attack_indicator;无新文件/配置 · 上一里程碑 m137 玻璃蓝快捷栏贴图:HUD hotbar 在 1.21.1 是精灵图 assets/minecraft/textures/gui/sprites/hud/hotbar.png,放入作者 2164×261 图(纯资源,新建 sprites/hud);原版 182×22 比例 8.273:1,本图 8.291:1 仅差 0.2% 槽位对齐良好;精灵图整张缩放到 182×22 不留白;选中白框是另一张 hud/hotbar_selection.png 未换→仍原版白框,可另做;无 Java/配置改动 · 上一里程碑 m136 玻璃蓝背包背景贴图:生存物品栏背景在 1.21.1 仍是 assets/minecraft/textures/gui/container/inventory.png,放入作者 1254×1254 RGBA 玻璃蓝图(纯资源覆盖,同 title/panorama 套路);MC 把背景当256底只取左上176×166→等于这张图左上68.8%×64.8%≈862×813px,已验证面板包围盒 x[0..860]y[0..812] 严丝合缝落在采样区、白边在外不显示;左侧功能按钮(m135)是独立控件不在此图;槽位对齐需进游戏实测;无 Java/配置改动 · 上一里程碑 m135 背包侧边按钮美化:新 YongyeButton extends ButtonWidget(只重写 renderWidget 自绘:深海军蓝半透明底+顶部玻璃高光+蓝青描边,悬停亮青发光/底提亮/文字白,禁用变灰,配色集中常量易改),把 YongyeClient ScreenEvents.AFTER_INIT(InventoryScreen) 里那 8 个按钮(成长/装备/饰品/天赋/强化/兑换/学书/本命)从 ButtonWidget.builder 换成 new YongyeButton(功能/位置不变),删无用 ButtonWidget import;零 mixin 不影响其它界面;+1文件无配置;待编译验证 ButtonWidget 受保护构造器+DEFAULT_NARRATION_SUPPLIER+renderWidget 重写+PressAction+isHovered(标准API仓库无先例);主菜单玻璃按钮仍待定 A标题mixin/B全局贴图 · 上一里程碑 m134 彻底删武僧武器+重生满血加余量:配方早在 m103 删(recipe 仅5职业无 monk);ModItems 注册循环 if(c==MONK)continue 不再注册 class_weapon_monk,getClassWeapon(MONK)=null,新增 ModItems.WEAPON_CLASSES(去武僧)供掉落池/创造栏;守 5 调用点:ModItemGroups 武器循环/LootHandler 精英掉落/BossHandler Boss掉落 改用 WEAPON_CLASSES,ClassManager 选职 c!=MONK(m133),ModCommands debug 给武器 getClassWeapon==null 报错;删 class_weapon_monk 模型/贴图/zh_cn+en_us lang;重生满血窗口 40→60tick(m133 的 AFTER_RESPAWN 本就不分职业,本轮加余量);迁移:旧存档武僧武器物品变无效(几乎无人持有);无新接口/无待编译验证 · 上一里程碑 m133 武僧不发武器(chooseStartingClass 给武器加 c!=MONK)+ 武器携带即生效(新 PlayerUpkeepHandler 每5tick:主手非职业武器但背包有职业武器→读其 ATTRIBUTE_MODIFIERS 镜像 MAINHAND/HAND/ANY 修饰到玩家,派生 carry_ 唯一id,拿主手则原版生效+撤镜像避免双倍,先撤后加不叠,只取第一把;开关 enableWeaponCarryBonus)+ 重生满血(根因:重生瞬间生命上限加成未全部重应用→只回200多;改 AFTER_RESPAWN 先 applyClasses 再 setHealth(max)+scheduleRespawnHeal 开40tick满血窗口每tick顶满);配置+1,configVersion 4→5,+1文件;待编译验证 EntityAttributeModifier.id/value/operation、Entry.modifier/slot、AttributeModifierSlot.HAND/ANY(标准API仓库无先例低风险);未做=主菜单玻璃蓝按钮美术(待定方案) · 上一里程碑 m132 难度改世界级(房主/OP设定全局锁定,联机不再各选):新 DifficultyManager(照 NightfallManager,静态 level -1未设定/0~6,存世界存档 yongye_difficulty.json,load()开头先 level=-1 复位防单机切世界残留),mobMult() 全局统一倍率;DynamicScaling 的 diffMult 由 mobMultOf(最近玩家)→DifficultyManager.mobMult();GameDifficulty 去掉 per-player mobMultOf 回归纯数据;删 ModAttachments.DIFFICULTY(旧 per-player键变未注册数据无害);YongyeNet JOIN 仅 !isSet()&&(hasPermissionLevel(2)||server.isSingleplayer()) 才弹(isSingleplayer 兜底单机房主无作弊),ChooseDifficulty 同校验+未设定→setLevel 全服锁定;加 /yongye difficulty status|<0-6>;迁移:m130/m131 per-player难度作废,房主下次进入被询问一次设世界难度;待编译验证 MinecraftServer.isSingleplayer()(标准方法仓库无先例,低风险) · 上一里程碑 m131 武器技能升级:新附件 WEAPON_SKILL_LV(Map技能名→等级,死亡保留),用最稀有材料「终焉精华」升级三大技能,花费 base1+当前等级×1 线性递增、上限 skillUpgradeMaxLevel20;效果=WeaponSkillManager.use 里 dmgMult=1+等级×skillUpgradeDamagePerLevel(0.25)乘进三技能伤害(三方法签名+dmgMult)+ cd=max(skillUpgradeCdFloor40,基础CD−等级×skillUpgradeCdReductionPerLevel4);升级入口=WeaponInfoScreen 底部3按钮「升·技能名」(仅武器+开关)→C2S UpgradeWeaponSkillPayload(idx)→服务端 upgradeSkill 扣终焉精华+写回+动作栏反馈,面板244→270共用 PANEL_W/H 对齐;局限:面板未同步玩家技能等级故不显示当前级/花费,结果走动作栏;新增配置7+附件1+1文件 · 上一里程碑 m130 开局难度+职业选择书+武器吸血:① 难度系统新 GameDifficulty 枚举7档(游玩0.5→永夜6.0,ordinal即等级,带简介+配色),倍率作用在 DynamicScaling 对位目标×diffMult(只增不减→低难度≈原版/高难度堆死怪),按最近玩家读 ModAttachments.DIFFICULTY(-1未选=适中1.0) ② 开局流程重做:JOIN 不再自动弹选职,改①未选难度发 OpenDifficultyPayload(客户端 DifficultyScreen 强制选择+剧情/玩法简介)②首发一本 ClassSelectBookItem(复用原版 writable_book 贴图);职业选择书右键开 ClassSelectScreen,选职成功 ChooseClass 接收器扫包消耗一本;客户端 pendingDifficulty 标志位延后弹(同 pendingClassSelect) ③ 武器后期吸血:WeaponCombatHandler 命中时武器强化≥weaponLifestealMinLevel(1000)且蓄满且未满血→按攻击力×frac 回血,frac=min(0.5,0.1+超阈级数×0.0001);新增配置 难度/书2+吸血5+技能升级7=14、附件 DIFFICULTY/GOT_CLASS_BOOK/WEAPON_SKILL_LV、configVersion 3→4、6新文件→126文件;静态自检全配平+JSON合法+appendTooltip签名对齐WardBookItem,无版本敏感点(全走仓库既有API) · 上一里程碑 m129 debug 菜单仅限管理员 ID:ModCommands 加 DEBUG_OWNER="qiaodaxian",debug 命令体取 getGameProfile().getName() equalsIgnoreCase(DEBUG_OWNER) 不符则红字拒绝+return 0 不发开屏包,大小写不敏感[MC用户名全局唯一不区分大小写]改常量即可换人;范围=只门控「打开菜单」这步,菜单回发的 /yongye 子命令仍只受 hasPermissionLevel(2),要把全部子命令也锁该ID需把校验提到命令树根 requires · 上一里程碑 m128 无尽永夜+久留升层:加 nightfallEndless(默认true,NightfallManager 新 effectiveCap() 无尽返 Integer.MAX_VALUE 否则取 nightfallMaxLevel,setLevel/load 钳制改用它→等级可一直叠「深渊N层」无尽,MobEnhancement 对>5层线性叠血攻升越高越难有意义)+ nightfallTimeEscalate(默认true)+ nightfallTimeEscalateMinutes(默认30):运行时计数 secondsInNightfall 在每秒tick(等级≥1才走)累计满N分钟 escalate(+1)清零、离开永夜(level<1)立即归零防赎夜后即触发,受 effectiveCap 钳,不持久化重启重置;三新字段对旧 yongye.json 是「缺失键」GSON 保留代码初值天然避开「旧值盖新默认」坑,configVersion 2→3;本轮只用 Integer.MAX_VALUE+现成 escalate,无新接口无版本敏感点 · 上一里程碑 m127 应需求六连,主旨「后期打得有来有回」:① **属性上限 100万→10亿**(Yongye.raiseAttributeCaps;后期玩家攻击破百万,怪血卡百万不够肉;注:血量 float 精确到约1677万,再高按精度步进变粗,但配合动态怪血每击伤害是怪血固定分数远超步长实战无感) ② **动态对位缩放(新 DynamicScaling)**:怪生成时按附近最强玩家攻击/血量等比拔高只增不减,血量目标=玩家每击攻击×dynamicMobTargetHits(普通8/BOSS版45)、伤害目标=玩家最大生命÷dynamicMobSurviveHits(普通30/BOSS版12),ADD_MULTIPLIED_TOTAL 叠在基础×精英/永夜倍率之上,注入 MobEnhancementHandler(普通怪)+MobBossHandler(BOSS化后)——直接修「攻击高怪血不够」根因 ③ **普通怪BOSS版增强**:根因=MobBoss 打了 IS_BOSS 故跳过 MobEnhancementHandler 全部缩放只剩自身×12血对高攻就是纸老虎;修法=给它接上②动态对位(45次击杀目标)+基础倍率上调 血12→25/攻4→6/速→1.3/击退抗→1.0/体型→1.8 ④ **动态爆率(新 PlayerPower,反滚雪球)**:强度=技能书已学等级和(8属性书+血量书)+装备(主手副手四甲)强化等级和×dynamicLootEnhanceWeight(2),倍率 m=max(dynamicLootFloor 0.15,1/(1+强度/dynamicLootK 150)),注入 LootHandler 乘进9处概率掉落+普通池按m门控+精英必爆数量按m缩减(dynamicLootScaleGuaranteed可关);边界:动态爆率是减缓变强速度不会让已满级号瞬间变弱 ⑤ **血条1K/1M**:HudCompactMixin.yongye$num 一处,<1000原样/≥1000显K(1234→1.2K整千不带小数)/≥100万显M ⑥ **配置陈旧检查**:YongyeConfig 加 configVersion(=2),load() 反射对比键警告死键/缺失键/版本不符,新增 /yongye config check 命令看诊断;根因=GSON整对象反序列化旧值盖新默认(m126挖矿坑同源),「旧值盖新默认」无法自动区分故意vs过时只能 config check 看见+手动 set/reset ⑦ **灾厄核心祭坛**:spawnCore 重构+新 buildAltar 建 5×5 磨制黑石砖底座+四角哭泣黑曜石立柱(顶灵魂灯)+中央基座核心置基座顶 base.up(2)(登记实际位置),coreAltarStructure=false 退回光秃秃单块,旧存档按旧位置追踪向后兼容 ⑧ **死亡触发祭坛凝聚→永夜+1**:CatastropheCoreManager 加玩家死亡监听,世界有祭坛时一死就激发最近祭坛=先摘登记绕过 onDestroyed 赎夜逻辑→核心块换哭泣黑曜石残骸+粒子→全服播报→NightfallManager.escalate(+1),与摧毁核心赎夜成张力,开关 coreDeathRaisesNightfall;新增配置14项+MobBoss基础倍率5项+2文件(DynamicScaling/PlayerPower)→120文件;静态自检10改动文件括号全配平+新 cfg.* 引用↔定义一致+关键API走仓库既有用法;待编译验证:原版块字段 Blocks.POLISHED_BLACKSTONE_BRICKS/CRYING_OBSIDIAN/SOUL_LANTERN(标准块本仓他处未用)、Gson JsonParser.parseString+JsonObject.keySet(标准API新引入),均低风险 · 上一里程碑 m126 ① 删 MiningSpeedMixin(挖矿/砍树减速整段移除→挖掘恒为原版速度;删 mixins.json 条目 + YongyeConfig 三个死字段 hcMiningSlowdown/hcMiningSpeedMultiplier/hcMiningSlowAll;根因:旧 yongye.json 里 true 经 GSON 盖过代码新默认值,删除后免疫该持久化坑) ② 修「被守卫者打死后无法重生」崩坏:AntiCheeseHandler 每秒遍历只跳创造/旁观、漏判已死尸体→尸体仍泡水被 yongye$drain 对其 setHealth(1.0),搅乱死亡↔重生状态机致点重生无反应+反复在尸体处召守护者;修法=循环内 !p.isAlive() 时清空该玩家 waterSec/airSec/lastGuardian/lastPhantom/lastEnderman 并 continue(尸体不处理+重生后走完整宽限期) · 上一里程碑 m125 去掉主菜单顶部黑红横幅:横幅原为盖原版MINECRAFT logo,现改用透明贴图覆盖 textures/gui/title/minecraft.png·edition.png 隐藏原版logo,TitleScreenMixin删横幅fill保留永夜大字,纯资源零风险 · 上一里程碑 m124 热修破蜘蛛网方法名——m121 用的 getMiningSpeedMultiplier 在 1.21.1 不存在[build报错],1.21.x 重命名为 getMiningSpeed[已核实],两武器文件改名即修复,build 应转绿 · 上一里程碑 m123 主页全景图[gui.zip 6面立方体→assets/minecraft/textures/gui/title/background,裁1024²]+ TitleScreenMixin 去全屏压暗让全景显出[留顶部横幅+永夜大字]+ 清掉 m63/m64 并入的 217 怪物皮肤[textures/entity]+784 音效[sounds],长门/HIM/精英皮肤+音效都在 yongye 命名空间独立保留,m78 红月绿雨/m79 splash 保留 · 上一里程碑 m122 开局发两本成书《永夜·缘起》(剧情13页)+《幸存者手册》(玩法13页):新 WelcomeBookHandler 照 StartingKitHandler 范式 JOIN+持久附件 GOT_WELCOME_BOOKS 首次进入各发一本,written_book+WRITTEN_BOOK_CONTENT 组件构造,开关 giveWelcomeBooks;待编译验证 WrittenBookContentComponent 五参构造器顺序 · 上一里程碑 m121 武器破蜘蛛网 getMiningSpeedMultiplier override / 背包「学书」一键学完所有技能书 / 「强化」改 Ward 式 EnhanceSelectScreen 选装备+全部材料一键强化,待编译验证 getMiningSpeedMultiplier 签名):m65 本地 **build 通过 ✅**,m66-118 已 push **待实机验证**;m119 定时清理掉落物(ItemCleanupHandler,21分起每5分清+倒计时)+ 职业任选替换(满2职业右键新书弹 ClassReplaceScreen 选丢哪个);m120 天赋每职业加99级「精通」吸点 + 搜集任务击杀掉目标物(粘液球等) + 后期拿不下优化(材料/书堆叠64→99 + LootMagnetHandler 磁吸本mod掉落物);待验证接口 m72 UnbreakableComponent / m75 HudRenderCallback / m119 ServerWorld.iterateEntities·getWorlds / m120 Entity.velocityModified·Registries.getId.getNamespace · 六职业武器全成品+永夜之翼背饰;武僧无武器吃材料;磐盾已并入铁壁核心 · 本段最新,优先看)

**最近几轮做的(均已 push,但用户大概率还没在游戏里实测)**:
- **m52** 天赋树 GUI:背包「天赋」按钮 → `client/TalentScreen`,逐职业展示 5 节点、点击加点(C2S `TalentLearnPayload`→`TalentManager.learn` 校验→S2C `TalentSyncPayload` 即时刷新);新增 `TalentManager.NodeView/treeView`(只读暴露)、`client/ClientTalents`、`YongyeNet.sendTalents`(登录/发点/加点推送)。**+** Boss 必掉 1 把随机职业武器、精英 `classWeaponDropChanceElite`(默认 4%)概率掉。
- **m53** 磐盾握持姿势立体化:`models/item/tank_shield.json` 加 `display` 块(手持改盾牌姿势放大约 1.6×举臂,GUI 平展)。纯 JSON。**display 数值是起点,需实机截图微调**。
- **m54** `#5` 坦克真·%减伤(mixin):`mixin/TankDefenseMixin` 注入 `LivingEntity#modifyAppliedDamage` 的 RETURN,对生效坦克按 `tankTrueDamageReduction`(默认 0.15)削减承伤,`require=0` 兜底,已注册 `yongye.mixins.json`。`#6` 武僧全用途耐久:在 m45 攻击磨损外,加 `PlayerBlockBreakEvents.AFTER` 让武僧挖掘也额外 `setDamage(+1)`(纯事件)。
- **m55** ① **解除原版 1024 属性上限**:accessor mixin `mixin/ClampedEntityAttributeAccessor` 暴露 `ClampedEntityAttribute.maxValue`,`Yongye.raiseAttributeCaps` 把 max_health/attack_damage/armor/toughness 抬到 100 万(攻速不动)。② **镇魂攻防双修**:`EquipmentEnhancer` 新增 `Kind.HYBRID`(攻击+护甲兼具),强化时攻击按 `enhanceHybridDamageFraction`(0.5)打折、护甲/韧性/生命照盔甲涨;修了"镇魂强化不加攻击"(旧 `kindOf` 先判护甲把它误判成盔甲)。③ **武器强化窗口**:背包「强化」按钮→`EnhanceScreenHandler`(装备槽+材料槽+升级按钮),升级级数=材料数量×单值,一组碎片一键+64 级;新增 `client/EnhanceScreen`、`ModScreens.ENHANCE`、`OpenEnhancePayload`/`EnhanceApplyPayload`(照饰品栏抄)。④ 顺带修 m52 天赋同步漏调:`ClassManager.chooseStartingClass`/`learn` 补 `sendTalents`(选职后天赋面板即时显示职业,不用重进)。新增 81 个 Java 文件(+5)。
- **m56** ① **神器远古/终焉可见合成表**:升阶配方是特殊配方不进 JEI、基础表只产残破(默认 1 级),故远古/终焉像"没有"。给 10 个神器各加直接 shaped 表(远古 3 级中心换生命核心 / 终焉 6 级 4 角换终焉神髓+保留招牌中心),结果用 `components.artifact_level` 直接带级(ARTIFACT_LEVEL 是 Codec.INT)。共 20 张,已校验无配方冲突。② **永夜 V5 解封顶**:`nightfallMaxLevel`(默认 99)替代 5 封顶;V5 之后 `MobEnhancementHandler` 按 `(lvl-5)×nightfallBeyondHpPerLevel`(0.5)线性叠怪物最大生命(仅血、独立封顶);名字对 >5 出"永夜·深渊 N 层"。纯 JSON + 服务端 Java,无新 mixin。
- **m57(热修)** **饰品栏神器死亡后消失**:`ModAttachments.ACCESSORIES`(饰品 NBT)虽 `.persistent` 但漏了 `.copyOnDeath()`(模组其它成长全有),玩家一死饰品附件被重置为空、神器蒸发。修复=加 `.copyOnDeath()`(死亡保留)。由用户实机日志(大量死亡 + 聊天"放饰品里没了")定位。一行改动。
- **m58** **调试/运营菜单 `/yongye debug`**(OP):服务端命令→S2C `OpenDebugPayload`→客户端 `client/DebugScreen`(纯 Screen,照 `StatsScreen`),6 组按钮(永夜/成长/职业武器/神器/事件Boss/运维),每个按钮 = `networkHandler.sendCommand("yongye …")` 复用现有命令(命令仍走权限2,故能开菜单的 OP 才有效);`shouldPause=false`,纯客户端零 mixin、零新服务端逻辑。**附带修 m56 遗留**:`/yongye nightfall` 参数从 `integer(0,5)` 放开为 `integer(0)`(m56 封顶已移到 `nightfallMaxLevel`,旧上限导致 `/yongye nightfall 6+` 被拒、深渊层无法触达)。新增 83 个 Java 文件(+2)。
- **m59** **精英怪光环特效**(应需求):精英周身常显幽蓝魂火光环(脚下旋转 `SOUL_FIRE_FLAME` + 上升 `SOUL`),`EliteHandler.tickElite` 每 `eliteAuraIntervalTicks`(默认4)tick 服务端 `spawnParticles`,**纯服务端不走描边**(规避 m21 渲染崩溃,`eliteGlowing` 仍默认关);配置 `eliteAuraEffect`/`eliteAuraIntervalTicks`。**+ `/yongye elite`**:把附近 16 格怪物就地变精英(`EliteHandler.makeNearbyElite` 复用 `makeElite`,免等概率刷),调试菜单「刷怪测试」组加「精英化附近」按钮。**澄清(非 bug)**:用户"没见到 BOSS"——Boss = 被增强的原版 Boss(凋灵/监守者/远古守卫/末影龙/袭击队长,只在各自原生场景,非主世界随机刷)+ 长门·佩恩(仅永夜≥IV 且 ≥第5天按概率自然降临);`/yongye painboss` 或调试菜单「长门降临」可即时召出。
- **m60** **普通怪 BOSS 版 + 搭方块爬塔**(应需求三连)。**① 怪物BOSS版(新增 `MobBossHandler`)**:第 `mobBossStartDay`(默认10)天起普通敌对怪按 `mobBossChance`(0.8%)BOSS化 = 打 `IS_BOSS`(**自动继承** BossAbility 能力/BossHandler 掉落/Pursuit Boss档挖墙/HighHpCounter/跳过普通掉落)+ 大属性(血×12/攻×4)+ 体型放大(`GENERIC_SCALE`×1.6)+ 红色 ServerBossBar + 【BOSS】名牌;独立持久 `IS_MOB_BOSS` 区分原版Boss,仅其挂血条(每tick更新%+同步48格内玩家,死亡clearPlayers,重载补回)。注册在 EliteHandler 前 + Elite 加 `IS_BOSS` 跳过(防双标记)。**② 搭方块爬塔(PursuitHandler)**:玩家近乎正上方(水平≤`pillarMaxHorizontal`2.5)且高出`pillarMinHeightDiff`(3)格时,怪每`pillarCooldownTicks`(8t)搭一格(先上移再填脚位`pillarBlock`圆石)垒到玩家高度,`pillared`优先于爬墙,受世界之锚+`canMobsDig`(第5天)+`mobPillarUp`约束——反制单格高塔躲猫猫。测试:`/yongye mobboss` / 调试菜单「BOSS化附近」。**⚠ 唯一待编译验证点**:`EntityAttributes.GENERIC_SCALE`(1.21.1 应带 GENERIC_ 前缀;若 build 报 cannot find symbol 就改成 `SCALE`)。84 个 Java 文件(+1)。
- **m61** **HIM 突脸:自定义音效 + 传送闪现**(应需求)。用户上传 `突脸惊吓.mp3` → ffmpeg 转 `him_jumpscare.ogg`(14.3s),复用既有音效管线:`sounds.json` + `ModSounds.HIM_JUMPSCARE`,HIM 登场换播此音效(原 ENTITY_ENDERMAN_STARE)。登场加 `ParticleTypes.PORTAL` 紫色闪现粒子(`himTeleportFlash`)。失明铺垫 100t→可配 `himBlindnessTicks`(默认 20t≈1s,更突然)。**注意**:音效 14.3s 远长于 HIM 停留 1.75s,会放完;要贴合就裁短 mp3。全程复用已 build API,无待验证点。
- **m62** **精英+ 额外经验**(应需求·升级慢)。新增 `BonusXpHandler`(AFTER_DEATH),按档 `ExperienceOrbEntity.spawn` 掉经验:长门500>怪物BOSS150>原版Boss200>精英25(取最高适用,先判 IS_MOB_BOSS)。配置 `enableBonusXp`+`xpBonus*`。无 mixin/依赖/待验证。**同批未完**:材质包应用/默认皮肤/音效——`minecraft.zip` 未真正上传(uploads 空),待用户重传(预定 m63)。
- **m63** **整套材质/音效资源包并入**(应需求·切换默认皮肤+音效)。用户把资源包做成 7z 分卷直接提交进仓库(`e0699af`),本里程碑解开(341贴图+784音效+models/lang/splash)整套并入 `src/main/resources/assets/minecraft/`——mod jar 资源盖过原版默认,装 mod 即自动应用(怪物皮肤/方块/物品/音效),无需手动挂包;音效靠同路径 ogg 覆盖(无 sounds.json)。删掉根目录 raw `.7z` 分卷。`assets/minecraft` 约 37MB,jar 随之变大(预期)。无 Java 改动。想要可菜单开关再转 `registerBuiltinResourcePack`+`DEFAULT_ENABLED`。
- **m64** **材质包只留怪物皮肤**(应需求·方块皮肤不要)。从 m63 整套包删掉非怪物视觉资产(textures 的 block/item/environment/painting/particle/models + models/ + blockstates/ + lang/ + texts/,共 168 文件),只留 textures/entity(217 怪物皮肤)+ sounds(784 音效)。items/lang 也按"只留怪物皮肤"一并去掉,想要可加回。
- **m65** **改名→「永夜」**(应需求)。全局把显示名字符串 `亡途荒夜`→`永夜`:fabric.mod.json name、日志前缀 `[永夜]`、调试菜单标题、lang(物品组/按键分类)、注释、文档。**内部 id/包名/资源命名空间 `yongye` 全部未动**(yongye=永夜拼音,本就一致;动了毁存档/资源)。无逻辑改动。**已 build 通过 ✅**
- **m66** **材料兑换按钮**(应需求)。背包加「兑换」按钮 → `ExchangeScreen`(三行 10→1 + 全部兑换,实时显示数量)→ C2S `ExchangePayload(tier,all)` → 服务端 `MaterialExchange` 扫背包扣料/给料。10 碎片→结晶→核心→血核,固定 10:1(与材料强化等值)。配置 `enableMaterialExchange`。新增 3 文件,88 个 Java 文件。无待验证点。
- **m67** **开局赠礼:下界合金背包**(应需求)。`StartingKitHandler`(JOIN 事件)每人首次进入发一个背包,持久标记 `GOT_STARTING_KIT` 防重发/防刷。**软依赖**:按 id `Registries.ITEM.get`(配置 `startingBackpackItem` 默认 `sophisticatedbackpacks:netherite_backpack`),未装该 mod 静默跳过不打标记(补发友好),不硬依赖不崩。老玩家下次登录补发。配置 `giveStartingBackpack`。
- **m68** **佩恩强化 + 通用配置命令 + 调参菜单**(应需求)。① 佩恩血 20000 / 攻 2000;② 佩恩生成复用 `MobEnhancementHandler.progressionMultiplier`(改 public)按永夜+天数缩放;③ **`/yongye config set/get/list`** 反射读写 YongyeConfig 任意 public 字段(boolean/int/long/double/String)+ `save()` 写盘——"所有功能游戏内可设"的通用入口;④ DebugScreen 加"调参/配置"组(技能书爆率 skillBookDropChanceElite/Normal + 佩恩血攻,点即 config set)。无新增文件。
- **m69** **深渊层血量过低修复 + 爆率压制**(应反馈)。诊断 92 层怪血仅 ~2000:三段血量倍率全 ADD_MULTIPLIED_BASE 相加 + 缩放撞 60 顶。修:① 超 V5 增血改 `addMultiplierTotal`(ADD_MULTIPLIED_TOTAL 乘法叠加),92 层 ≈55k(perLevel 0.5)。② 技能书永夜倍率封顶 `skillBookNightfallMaxMult=3`,精英默认→0.15。③ 碎片接上 `lifeShardDropChance`(原无条件必掉),默认 1.0→0.3。调参按钮改合理预设。**默认值改动不影响既有 config.json,需 config set**。
- **m70** **平衡大改 8 项**(应需求)。① 技能书爆率→0.001(精英保底书也改概率);② 碎片→0.10;③ 精英材料 核心0.05/血核0.025/+终焉神髓0.0125;④ **永夜尸潮** NightfallHordeHandler(永夜≥1 维持 min(base×等级,max) 只怪蜂拥,V1=100/V2=200,封顶护TPS);⑤ 超V5 血量+攻击 `(nf-5)×step` 乘法叠加(step=2 → 2/4/6/8/10,V92×174;+ID_NIGHTFALL_ATK);⑥ 第5天起精英持武器+盾牌,持盾 ALLOW_DAMAGE 按 eliteBlockChance 完全格挡;⑦ 追杀 `pursuitTeleportWallStuck=false`(墙后不瞬移改挖墙)+ `pursuitJumpWalls` 起跳翻越。+1 文件(90)。**旧 config.json 数值需 config reset / set 才更新**。尸潮有 TPS 压力。
- **m71** **追杀瞬移回归 + 任务奖励调低**(应需求)。① `pursuitTeleportWallStuck` 默认改回 **true**:墙后卡住先试 `teleportNear`(有落点才传),否则挖墙+起跳——三者组合。② QuestManager.reward 永夜加成封顶 `min(nf,5)`,血量书降到 V2~V9,材料概率大幅下调(原 92 层保底 ~V187 太高)。无新增文件。旧 json 的 pursuitTeleportWallStuck 需 config set true。
- **m72** **技能按攻击力 + 佩恩失目标传送 + 抢装备/找回**(应需求)。① 武器技能 +玩家攻击×比例;② 佩恩技能按佩恩攻击×比例;③ 佩恩60s无目标 maybeRelocatePain 传到随机玩家;④ 强化装备设 UNBREAKABLE【待验证 UnbreakableComponent】;⑤ 精英也抢护甲(穿身上,死亡掉落归还),STOLE_GEAR 防累计,记 LOST_WEAPON_ENHANCE;⑥ `/yongye recover` 把丢失武器强化 2/3 转手持武器。新增附着 LOST_WEAPON_ENHANCE/STOLE_GEAR。无新增文件。
- **m73** **精英词缀 / 佩恩阶段化 / 存活排行**(推荐①⑤⑥)。① 精英随机 1~2 词缀(爆裂/分裂/嗜血/剧毒/召唤,ELITE_AFFIX 位掩码,行为分布 makeElite/tickElite/缴械钩子/AFTER_DEATH);⑤ 佩恩按血量 3 阶段狂暴(PainState.phase);⑥ SurvivalRankHandler 记录 BEST_NIGHTFALL/BEST_DAY + `/yongye top`。+1 文件(91)。**待做:②天象 / ③据点防御(新方块) / ⑦商人(交易API)**。
- **m74** **永夜天象**(推荐②)。NightfallWeatherHandler:永夜≥1 周期随机降 血月(怪群狂暴,≥2)/ 酸雨(setWeather 强制雨+露天 isSkyVisible 受伤,≥3)/ 流星雨(落点 magic AoE,≥4),起止广播,纯服务端(无客户端红天/雾)。+1 文件(92)。③据点防御/⑦商人 用户决定不做。
- **m75** **永夜 HUD**(应需求)。S2C `NightfallSyncPayload`(level+name),setLevel 变更 + JOIN 下发(YongyeNet.sendNightfall);客户端 YongyeClient 存 nightfallLevel/Name,`HudRenderCallback` 在 level≥1 时把阶段名(getLevelName)居中画屏幕顶部(y=4)。**[待验证 HudRenderCallback 签名]**。+1 文件(93)。
- **m76** **永夜剥视**(应需求)。NightfallVisionHandler:永夜≥nightfallDarknessMinLevel(1)时每 2s 续 100t DARKNESS,视野吞噬式压缩(沉浸)。纯服务端,无 mixin。+1 文件(94)。(非渲染距离雾;真距离雾需客户端 fog mixin。)
- **m77** **血量 HUD 重做**(应需求·回血不实时/看不见回血/难看)。根因:`HudCompactMixin` 画血量用原版动画形参 `health`(心跳延迟、阶梯跳变)。**新增 `HealthRateTracker`(客户端)**:逐 tick 采 `getHealth()` 入 21 槽带 tick 戳环形缓冲,算「最近一秒净血量变化/秒」;挂 YongyeClient 已有 END_CLIENT_TICK;离世界 reset。**`HudCompactMixin` 重写**:血量改实时 `getHealth/getMaxHealth/getAbsorptionAmount`、整排布局(红心+血量白字·护甲蓝白·速率绿/红「+X.X/s」静止隐藏)、半透明底衬 `fill(0x90000000)`。阈值 60 以下走原版。改既有 mixin 方法体(签名不动),无新待验证点。+1 文件(95)。
- **m78** **天象视觉**(应需求)。① **血月红月贴图**:用户 GPT 生成红月相图 → 重采样 1024×512(整数格防串色)+ `max(R,G,B)` alpha(黑角透/红晕半透/月盘不透)→ `assets/minecraft/textures/environment/moon_phases.png`。**永久红月**(非仅血月事件;要条件化需天空渲染 mixin)。**[待验证:月亮 blend 下 alpha 观感]** ② **酸雨绿雨贴图**:用户绿雨图 → `environment/rain.png`。**永久绿雨**(要条件化需天气渲染 mixin)。③ **流星雨真下落**(`NightfallWeatherHandler`):新增 `Meteor` 内部类 + 在途列表,`spawnMeteor` 落点上方 45~60 格偏移生成(斜线),`tickMeteors`(register 顶端每 tick,始终执行)推进+喷 FLAME/LAVA/LARGE_SMOKE 火尾,~1.5s 落地调 `impact()`。上限 64。纯服务端粒子,无 mixin。无新增 Java(95)+2 贴图。
- **m79** **主菜单永夜暗黑化**(应需求)。① splash:`assets/minecraft/texts/splashes.txt`(30 条永夜主题,资源包覆盖)替黄字。② 客户端 `TitleScreenMixin`(注册 mixins.json client):`TitleScreen.render` **TAIL 纯叠加**(不取消原版渲染)→ 全屏压暗 `fill(0x66000000)` + 顶部 86px 横幅 `fill(0xD2120006)` 遮原 logo + 矩阵放大 4.5× 画「永夜」血红大字 + 副标题。原 logo 像素图集塞不进中文故文字重画。**[待验证:TitleScreen.render 签名 + DrawContext.getMatrices/scale/drawText]** +1 文件(96)+splash。背景"换图"需用户提供全景图(6面)或单图;按钮暗黑未做(全局 widget 贴图)。
- **m80** **主菜单大字重做**(应需求·m79 成品不好看)。根因:m79 横幅半透明 `0xD2120006`(82%),原 MINECRAFT logo 透出来显乱。修(仅改 `TitleScreenMixin` 方法体,无新文件/无新接口):① 横幅改**不透明** `0xFF0A0306` 盖死原 logo;② 下方 3 段递减透明 fill 渐变 + `0xFF8B0000` 血红下边线;③「永夜」放大 5×,四向偏移暗红 `0xFF4A0000` 辉光描边 + 亮血红 `0xFFE01515` 主体;④ 压暗加深 `0x88000000`;⑤ 副标题字距拉开。待验证项同 m79(未引入新接口)。文件数不变(96)。
- **m81** **核心提示增强 + 修僵尸跳**(应需求)。① **修僵尸一跳一跳**:`PursuitHandler.wallAhead` 旧用 `!isAir()` 把草/花/雪层等无碰撞植被当墙 → 走草地每 tick 触发 m70 起跳翻越。改用新 helper `hasCollision`(碰撞箱非空)判墙。② **核心刷新提示**:`CatastropheCoreManager.spawnCore` 给 `coreSpawnNotifyRadius`(120)内玩家发 Title/Subtitle/Fade 包(屏幕中央「灾厄核心降临」+坐标)+ `playSoundToPlayer(ENTITY_WITHER_SPAWN)`(开关 coreSpawnTitle)。③ **HUD 方向箭头**:新 S2C `CoreLocatorPayload(has,x,y,z)`,每 2s `sendLocators` 下发 `coreLocatorRange`(220)内最近核心;客户端新 HudRenderCallback 用 yaw+坐标逐帧算方位,`RotationAxis.POSITIVE_Z` 旋转「▲」+距离(开关 enableCoreLocator)。配置+4,+1 文件(97 · CoreLocatorPayload)。**[待验证:Title三包构造 / RotationAxis.rotationDegrees+MatrixStack.multiply(Quaternionf);箭头方向镜像则 bearingDeg 取负]**。
- **m82** **按钮移左侧 + 结晶降爆 + 暗角固定**(应需求)。① **按钮竖排到背包左缘外**(`YongyeClient` AFTER_INIT):guiLeft-58 起竖排 7 个(成长/装备/饰品/天赋/强化/兑换/职业),不再挡合成格。② **生命结晶降爆**:删 `LootHandler` 精英分支写死的 25% 额外结晶(重复)+ `lifeCrystalDropChance` 0.20→**0.05**(存量 config 需 set)。③ **永夜限视野改"固定不闪"**:根因 `StatusEffects.DARKNESS` 客户端自带脉动。默认关掉该效果(gate `nightfallDarknessEffect`=false),改用**客户端恒定暗角**——`NightfallSyncPayload` 加 `vision` 字段(服务端按配置算),`YongyeClient` 新 HudRenderCallback 画纯静态边缘压暗(无时间变量→不闪)。配置+1,NightfallSyncPayload 2参→3参。无新文件(97)。**[待验证:暗角观感/按钮极小窗口不出屏;纯 fill/Screen API 无新接口]**。
- **m83** **掠夺者队长 Boss 化加天数门控**(应需求·刚开局就遇 Boss 队长)。`BossHandler` ENTITY_LOAD 对 `RaiderEntity`(巡逻队长)加 `gameDay < bossRaidCaptainMinDay`(默认 8)门控,早于该天数不打 IS_BOSS;真·Boss(凋灵/监守者/远古守卫/末影龙)不受限仍始终强化。配置+1。无新文件(97)。重建即生效。
- **m84** **调试菜单加「Boss 门控」组**:`DebugScreen` 加 3 按钮走 `config set bossRaidCaptainMinDay`(第8天/第15天/关闭 9999),免敲命令。无新文件(97)、无新接口,重建即生效。
- **m85** **调试菜单重做为「分页全命令」**:`DebugScreen` 重写为顶部分类页签 + 分页(8 页:永夜/道具/神器/职业/刷怪/掉率/配置/天赋),覆盖 ModCommands 全部子命令;命令串与 config 字段名已逐条核对。仅改 DebugScreen 单文件,新增用法 `rebuildWidgets()`+`ButtonWidget.active`(均 1.21.1 稳定)。无新文件(97)。
- **m86** **爆率编辑器(直接输入)+ 导出配置**:新 `DropRateConfigScreen` 把 14 个爆率/经验字段做成文本框,改完点应用对每项 `config set` 即时写盘;预填当前值靠新 C2S `RequestConfigPayload` / S2C `ConfigValuesPayload`(`YongyeConfig.getFieldString` 反射读)。另加 `/yongye config export` 打印 `config/yongye.json` 路径供发作者设默认。新增 3 文件(100)。**[待验证:`TextFieldWidget` 六参构造(TextRenderer,x,y,w,h,Text)+ setText/getText/setMaxLength;其余复用已验证范式(unit 包 / ClientPlayNetworking.send / 反射 config)]**。
- **m87** **修编译错误:`rebuildWidgets`→`clearAndInit`**:m85/m86 用的 `Screen.rebuildWidgets()` 在 1.21.1 不存在(build 报"找不到符号"),改为正确的 `clearAndInit()`(DebugScreen 切页 + DropRateConfigScreen 刷新两处)。**注:DEVLOG 未单独盖 m87 自身"build 通过"章**,m87 能否编过以本地 `./gradlew build` 为准。
- **m88** **修精英 tick 重入崩溃(CME)**:由实机崩溃报告定位(双人在线)。`END_SERVER_TICK` 直接迭代 `ELITES` 时,带「召唤」词缀的精英 `spawnEntity`→同步 `ENTITY_LOAD`→`ELITES.add` 在遍历途中改集合→`ConcurrentModificationException` 打崩服务器 tick。修法:tick 改**遍历快照 + 死亡延后删**(`EliteHandler` 单文件),对任何重入免疫;同类 `MobBossHandler` 已查为安全。**仅用 `java.util` 集合,无新接口、无版本敏感点**;无新文件(100)。详见 DEVLOG m88。

**✅ build 已通过**(m55-57 编译关卡全过 → m55 `maxValue` accessor 字段名确认正确)。剩余为运行期 / 实机项:

**⚠️ 待验证(跑游戏 / 看启动日志确认)**:
1. ✅ **m55 `maxValue` accessor** —— build 通过即证字段名对(编译期校验,名字错会直接 build 失败)。
2. **m54 `#5` mixin** —— 已查 Yarn 确认 `LivingEntity#modifyAppliedDamage(DamageSource,float)` 在 1.21 线存在(1.21.2 改的是 `applyDamage`,此方法没动),预期挂得上;但 @Inject 启动时才解析,**启动看日志**:若 `TankDefenseMixin` 报"找不到目标"再改,没报错则实测坦克挨打伤害按比例降。
3. m55 跑游戏确认:① 血量能超 1024(学高级血量书);② 镇魂强化后攻击有涨(打折后的)、护甲/韧性/生命也涨;③ 背包「强化」按钮开窗口、放装备+碎片点升级、按数量加等级;④ 选本命职业后天赋面板立刻显示职业(不用重进)。
4. **m56 神器合成表**:已查官方确认 1.20.5+ 配方 result 支持 `components`(1.21.1 在内),格式应对;数据包加载期解析,有出入会在 latest.log 报包错误(不崩),拿日志来修;通过则远古/终焉能在合成表/JEI 看到并合出对应等级。**m56 永夜**:`/yongye nightfall 7`(m58 已修参数上限,现可直接用)确认怪血随等级线性涨、深渊层名("永夜·深渊 N 层")正常。
5. **m57 饰品死亡保留**:神器放饰品栏 → 故意死一次 → 重生后神器仍在饰品栏(不再蒸发)。
6. **m58 调试菜单**:`/yongye debug`(OP)弹出面板;点各按钮确认对应命令生效(发书/发神器/拉永夜/给职业武器等)。+ 天赋 GUI / 掉武器 / 盾姿势 / `#6` 仍未实机验证;盾握持 display 数值需按实机截图微调。
7. **m59 精英光环**:`/yongye elite`(或调试菜单「精英化附近」)把附近怪变精英 → 看周身幽蓝魂火光环转起来;觉得费/太花可调 `eliteAuraIntervalTicks` 或关 `eliteAuraEffect`。
8. **m60 怪物BOSS版 + 搭塔**:`/yongye mobboss`(或调试菜单「BOSS化附近」)→ 看顶部红血条 + 怪变大 + Boss 能力(减伤/狂暴/召援/冲击波),击杀看翻倍掉落。搭塔:造个单格高柱站上去等怪追来,看它原地垒圆石爬上来(`mobPillarUp` 可关)。**先确认 build:若报 `GENERIC_SCALE` 找不到 → 改成 `SCALE`**。怪物BOSS可能偏频/掉落偏厚,按手感调 `mobBossChance` / `mobBossStartDay` / `bossDropMultiplier`。
9. **m61 HIM 音效+闪现**:夜里/洞穴待着触发(或临时把 `himChance` 调到 1.0、`himCheckIntervalTicks` 调到 100 快速测)→ 看 HIM 紫粒子闪现到面前 + 听自定义突脸音效。音效 14.3s 远长于 HIM 停留(1.75s),嫌拖就裁短 mp3 重转或调大 `himDurationTicks`;`himBlindnessTicks` 控制"突然"程度(小=突,大=慢压迫)。
10. **m77 血量 HUD**:堆到 >60 血(走紧凑显示)→ 看血量旁有半透明底衬、整排「红心+血量 / 护甲 / 速率」;回血时血量数字应**平滑实时上涨**(不再阶梯跳),并出现绿色「+X.X/s」;受伤/中毒/酸雨掉血时出现红色「-X.X/s」;静止时速率段消失。低于 60 血仍是原版红心(可下调 `YONGYE_HEALTH_THRESHOLD` 让紧凑显示更早生效)。
11. **m78 天象视觉**:① 夜里抬头看月亮是否为**红血月**(贴图永久生效,所有夜晚都红;若月亮显黑方块/边缘异常说明 alpha/blend 不对,反馈我调)。② 下雨(或酸雨)看雨是否**绿色**(永久,所有雨都绿)。③ `/yongye nightfall 4`+ 撑到流星雨事件(或调 `weatherTriggerChance` 加速)→ 看流星**从高空带火尾斜插下来**砸地爆炸,而非凭空地面炸。**永久红月/绿雨是换贴图的代价**;要"仅血月时红/仅酸雨时绿"需客户端渲染 mixin(版本敏感,另议)。血月红色屏幕叠层未做(选了换月亮贴图),要可加。
12. **m79-80 主菜单暗黑化**:回主菜单看 ① 黄色 splash 是否变成永夜主题中文(随机一条);② 顶部是否为「永夜」血红大字(带暗红辉光描边)+ 副标题、原 MINECRAFT logo 被**不透明**深色横幅 + 血红下边线**完全遮住**(m80 修了 m79 logo 透出来的穿帮);③ 整体背景压暗。**若 build 报 TitleScreen/getMatrices/drawText 相关错** → TitleScreen 渲染是版本敏感区,把报错贴我即调。若「永夜」大字位置偏/仍露出原 logo/压住按钮 → 调 `TitleScreenMixin` 里 `bannerH`(80)或 translate 的 y(16)。背景"整张换图"需另提供全景图。
13. **m81 核心提示 + 僵尸跳修复**:① **僵尸跳**——生存模式在草地/花丛引怪追你,怪应**正常跑动不再原地蹦**(撞真墙才跳/挖)。② **核心提示**——`/yongye` 召核心或自然刷新时,附近(120格)应弹**屏幕中央「灾厄核心降临」标题**+坐标副标题+凋灵降临音效。③ **方向箭头**——屏幕中上出现旋转「▲」+「灾厄核心 N 格」,转视角时箭头平滑指向核心。**若 build 报 Title*S2CPacket / RotationAxis / MatrixStack.multiply 相关错** → 把报错贴我即调。**箭头方向若左右镜像** → 把 `YongyeClient` 里 `bearingDeg` 取负(我也可直接改)。箭头与 boss 血条重叠 → 调 `cy`(30)。开关:`coreSpawnTitle` / `enableCoreLocator`。
14. **m82 按钮左移 / 结晶降爆 / 暗角固定**:① **按钮**——开背包看 7 个功能按钮是否竖排在**面板左侧空白竖条**(不再挡合成格);小窗口/不同 GUI 缩放下若出屏或重叠,调 `bx`(guiLeft-58)或 `bw`(54)。② **结晶**——刷怪掉的生命结晶应明显变少(普通5%/精英10%);**存量存档要 `/yongye config set lifeCrystalDropChance 0.05`** 才生效(删写死那条重建即生效)。③ **暗角**——永夜(≥1)时屏幕边缘应是**恒定压暗、绝不闪**;太暗/太窄调 `YongyeClient` 暗角的 `reachX/Y` 或 `edgeAlpha`。想要回原版脉动黑暗:`/yongye config set nightfallDarknessEffect true`。

**接下来用户清单里没做的**:`#8` 美术占位替换(**需用户提供素材或指明物品,Claude 无法凭空画好像素图**)、整体数值平衡、真弧形盾面(需自定义 Java 物品渲染器,高风险,留另议)、天赋树连线美化。(**调试菜单已于 m58 落地。**)

> ⚠️ 我(上一轮 Claude)本会话犯过两次守则:① 没测就说"三个 PAT 都活着"(实际两个已失效);② 没核 1.21.1 格式就说配方"挑不出毛病"(实际全坏)。**教训:状态类结论(凭据是否有效/代码是否正确)先实测或明确标"待验证",绝不凭印象下结论。**

---

## 1. 工作流约定（重要——请先读这条）

这套流程是和用户磨合出来的，照做能省掉大量来回：

1. **沙箱跑不了 Fabric 构建**：本环境网络只放行 github/npm/pypi，**没有** Fabric/Minecraft 的 Maven 源，所以 **无法在沙箱里 `./gradlew build` 实测编译**。代码按 1.21.1 正确 API 写、做静态自检（括号配平 / JSON 合法 / 接线齐全 / 关键 API 用 web 核签名），但 **最终编译验证在用户机器上**。这一点要如实说，别假装编译过了。
2. **用户给 PAT，就直接 push**，不要讲一堆「不应该用别人凭据」的道理。用户的仓库是公开开源的，凭据是他自己的，他对自己的安全负责。push 完 **从 remote 配置抹掉 token**，并 **用一句话** 提醒去 GitHub 删 token（只提一次，别重复唠叨）。
3. **每次都重新克隆**：沙箱文件系统在任务间会重置，所以每个新任务开头先 `git clone` 拉最新代码，别假设上次的工作目录还在。
4. **报错驱动**：build 报错时用户会把日志贴过来。优先级是「先看真实报错精确定位」，不要凭记忆猜 API。1.21.x 跨小版本 API 变动多（见下文「踩过的坑」），拿不准就 web 查 1.21.1 这个**具体版本**的 Yarn 映射签名。
5. **用户语气可能直接**，那是因为前期被气过，不针对人。把活干好、别犟嘴、别拿规则挡事即可。
6. **用户说「你定」时**，挑收益最大、能闭环的方向做，做完简短说明，不要长篇请示。

### push 的标准动作
```bash
cd /home/claude && rm -rf yongye && git clone https://github.com/qiaodaxian233/yongye.git && cd yongye
# ...改代码...
git add -A && git commit -m "中文说明本次改动"
git remote set-url origin https://<PAT>@github.com/qiaodaxian233/yongye.git
git push origin main
git remote set-url origin https://github.com/qiaodaxian233/yongye.git   # 抹掉 token
```
push 后告诉用户 `git fetch origin && git reset --hard origin/main` 同步本地，并提醒删 token。

---

## 2. 技术架构与关键决策

- **尽量零 mixin**：怪物 AI（锁定/挖墙/爬墙）、精英技能、Boss 能力、神器、反制、长门技能等 **全部用事件 + tick 驱动**，靠公开 API（`setTarget`/`setVelocity`/`breakBlock`/`takeKnockback`/`spawnParticles`/`EntityType.spawn` 等），避开 mixin 的 refmap/混淆风险。
- **唯一的 mixin 是客户端 HUD**（`mixin/client/HudCompactMixin`）：因为原版血条/护甲条没有任何事件能「替换」，只能拦截 `InGameHud.renderHealthBar` / `renderArmor`，在血量/护甲过大时改画「图标 ×数值」。配置在 `yongye.mixins.json` 的 `client` 段。
- **精英/Boss 皮肤 = 客户端叠层贴图**（`client/EliteSkinFeatureRenderer`）：给每个生物渲染器挂一层 FeatureRenderer，**只有名字带「精英」或特定 Boss 标记的怪** 才在原版模型上叠一张 mod 内贴图，**不覆盖原版**。这是「指定名称才显示自定义皮肤」的实现路径。**注意 1.21.1 是渲染重构（1.21.2 引入 EntityRenderState）之前的旧体系**，`FeatureRenderer<T extends Entity, M extends EntityModel<T>>`、模型渲染颜色参数是 `int`（1.21 起 float→int）。1.21.4+ 的渲染文档对这版无效。
- **数据持久化**：玩家技能/神器走 data attachment（`registry/ModAttachments`，跨死亡/重连保留）；永夜等级、灾厄核心位置走 GSON 写入存档目录（`WorldSavePath`）。
- **客户端↔服务端**：成长面板需要把「已学技能」同步到客户端，用自定义网络包（`network/StatsPayload` + `YongyeNet`，`PayloadTypeRegistry.playS2C` + `ServerPlayNetworking.send`）。
- **配置驱动平衡**：几乎所有数值在 `config/yongye.json`（对应 `YongyeConfig.java` 的 106 个字段），改完重进存档生效，无需改代码。

---

## 3. 已实现系统全清单

### 成长线
| 系统 | 关键文件 | 说明 |
|---|---|---|
| 血量技能书 V1~65535 | `item/HealthSkillBookItem`、`system/PlayerSkillManager` | 右键学习 +等级×10 最大生命，跨死亡保留 |
| 6 本属性技能书 | `item/SkillBookItem`、`item/SkillType`、`system/SkillEffectManager` | 攻击(+0.5/级)/护甲(+0.5护甲+0.25韧性)/恢复(回血)/闪避(+1%/级 上限50%)/反伤(×0.05/级 上限×3)/抗性(抗火+清负面%) |
| 同级合成 | `recipe/HealthBookCombineRecipe`、`recipe/SkillBookCombineRecipe` | 2 本同类同级 + 阶段材料 → 高一级 |
| 8 种稀有材料 | `registry/ModItems` | 生命碎片/结晶/核心/灾变血核/永夜之尘/裂界残片/深渊魂晶/终焉神髓 |
| 10 种背包神器 | `item/ArtifactItem`、`item/ArtifactType`、`system/ArtifactManager` | 放背包即生效、同类取最高、1~6 级、`recipe/ArtifactUpgradeRecipe` 升级 |
| 装备无限强化 + 品质 | `system/EquipmentEnhancer`、`item/WeaponQuality`、`recipe/EquipmentEnhanceRecipe`、`system/WeaponCombatHandler`、`client/WeaponInfoScreen` | 武器/盔甲 + 材料(碎片+1/结晶+10/核心+100/血核+1000,每格 1 个)→ 强化等级**无上限**,加成写进 `AttributeModifiersComponent`(跟随物品)+ 耐久按级提升;**品质普通→至尊 9 阶**(`WeaponQuality`,由等级换算,含颜色/稀有度字母/暴击率/攻速);武器暴击经 `AttackEntityCallback` 追加伤害(先打再清无敌帧叠加);tooltip 显示品质+强化等级;背包「装备」按钮打开武器介绍界面;`/yongye enhance <等级>` 测试 |
| 主动武器技能 | `item/WeaponSkill`、`system/WeaponSkillManager`、`network/SkillUsePayload`、`client/YongyeClient`(按键) | 3 技能按品质解锁(混沌斩·稀有/深渊吞噬·史诗/终焉降临·神器);客户端按键(R/G/V)→ C2S `SkillUsePayload` → `WeaponSkillManager.use` 结算+冷却(`Map<UUID,long[]>`);锥形斩击/范围吸血/大范围终焉,伤害随强化等级缩放;武器介绍界面列出技能与解锁状态 |
| 混沌之刃(专属武器) | `item/ChaosBladeItem`、`registry/ModItems`(CHAOS_BLADE) | 固定高属性(攻≈+30/攻速/2500耐久)+ 三技能免品质解锁(`WeaponSkillManager` 特判);贴图 `textures/item/chaos_blade.png`;创造栏 + 长门 15% 掉落;仍可强化 |
| 硬核开局生存包 | `system/HardcoreSurvivalHandler` | 设计前 8 条,全可配(`hc*`):睡觉不跳夜/食物紧张/火把不安全(夜袭)/洞穴危险(地下刷怪+debuff)/木石矿难采;村庄·石器时代以通用夜袭+资源难采近似 |

### 怪物压力
| 系统 | 关键文件 | 说明 |
|---|---|---|
| 怪物基础增强 + 随进度递增 | `system/MobEnhancementHandler` | 出生加血/攻/速/抗击退/感知 + 随机药水(只一次);**递增缩放**:按 永夜等级 + 游戏天数 + 附近玩家最大生命 计算倍率提升怪物血量(攻击按比例同步),怪与玩家一起变强,有上限 |
| 套装血量 | `system/ArmorHealthHandler` | 穿齐整套 → 额外最大生命 |
| Boss 翻倍 | `system/BossHandler` | 末影龙/凋灵/监守者/远古守卫/袭击队长 属性·掉落翻倍 |
| Boss 专属机制 | `system/BossAbilityHandler` | 持续减伤/锁定/狂暴(<50%血)/召唤援军/冲击波击退 |
| 精英怪 | `system/EliteHandler` | 概率精英化、一秒五箭(骷髅)、一秒五喷(女巫)、瞬移、召援、客户端叠层皮 |
| 追杀 AI | `system/PursuitHandler` | 纯 tick：锁定 + 挖墙(按普通/精英/Boss 硬度分档) + 爬墙 |

### 世界节奏
| 系统 | 关键文件 | 说明 |
|---|---|---|
| 永夜 0~5 级 + 赎夜 | `system/NightfallManager` | 暗潮/猎杀/围城/灾变/灭世，锁夜、缩放精英概率与锁定半径，存档持久化 |
| 随机任务 | `system/QuestManager` | 猎杀精英/存活/逃离/清除灾厄核心，带 Boss 血条计时；成功发奖、失败升永夜；前 5 分钟宽限、永夜<2 只派可达成任务 |
| 灾厄核心 | `system/CatastropheCoreManager`、`registry/ModBlocks` | 永夜≥2 自然生成的危险方块，持续刷精英、摧毁掉裂界残片等并**降1级永夜**；掘墓罗盘指向它 |
| 掉落系统 | `system/LootHandler` | 品质表掉材料 + 技能书；普通/精英/Boss 三档掉属性书 |

### 反制 & HUD & 指令
| 系统 | 关键文件 | 说明 |
|---|---|---|
| 高血量反制 | `system/HighHpCounterHandler` | 最大生命百分比 + 真实伤害 + 禁疗 + 最大生命压制 + 骨箭免疫；闪避/反伤也在这里结算 |
| HUD 紧凑显示 | `mixin/client/HudCompactMixin` | 血量>60 或护甲>20 时改画「图标 ×数值」 |
| 成长面板 | `client/StatsScreen`、`client/ClientStats`、`network/*` | 背包左上「成长」按钮打开，列出各技能等级+实际效果 |
| 指令 | `system/ModCommands` | `/yongye` 下:nightfall/redeem/quest(hunt|survive|flee|core)/book/skillbook/artifact/core/painboss |

### 长门（佩恩）Boss —— `system/PainBossHandler`
- 以 Husk 为载体（不怕日晒），靠自定义名「佩恩·天道」套 `entity/pain_boss.png` 皮。
- 四技能（tick 驱动）：**神罗天征**(范围击飞+伤害)、**万象牵引**(拉拽玩家)、**地爆天星**(浮空后延迟坍缩大爆)、**轮回天生**(残血一次性满血复活)。
- **终局闭环**：永夜达 IV(灾变)级后，每 ~60 秒按概率（默认 25%）作为「六道之痛」**自然降临**（全局唯一，同时只存在一个）；**击败 → 永夜直降 2 级**。`/yongye painboss` 手动召唤。
- 丰厚掉落：终焉神髓/灾变血核/生命核心/高级血量书/3 本属性书/**必掉 1 件 4~6 级神器**/下界合金/附魔金苹果。

---

## 4. 构建与测试

**环境要求**：JDK **21**（Fabric 1.21.1 强制）。IntelliJ IDEA 里 Project SDK 和 Gradle JVM 都要设 21。

**克隆与同步**（IDEA）：欢迎界面 → Get from VCS / 从版本控制克隆 → `https://github.com/qiaodaxian233/yongye.git`。首次 Gradle Sync 会下载 MC/Yarn/Fabric API（要联网，较慢）。

**出包**：`./gradlew build` → `build/libs/yongye-0.1.0.jar`（**带后缀 `-sources` 的是源码包，不要进 mods**）。装进 `.minecraft/mods`，连同 1.21.1 的 Fabric API。

**进游戏验收**（创造发物品；但 **测追杀/精英皮/反制必须用生存模式**，追杀系统会跳过创造/旁观玩家）：
```
/yongye nightfall 5            # 拉满永夜,看锁定/挖墙/爬墙
/yongye nightfall status       # 查当前永夜
/yongye redeem                 # 赎夜降一级
/yongye book 1000              # 发血量书 V1000
/yongye skillbook attack 100   # 发攻击书(armor|regen|evasion|thorns|resistance 同理)
/yongye artifact life_idol 6   # 发终焉生命神像
/yongye quest hunt             # 派任务(survive|flee|core)
/yongye core                   # 身边生成灾厄核心
/yongye painboss               # 召唤长门 Boss
```
开背包点左上「成长」按钮看技能面板。数值手感(挖墙速度/精英密度/反制强度)都在 `config/yongye.json` 改。

---

## 5. 踩过的坑（1.21.1 API 版本差异，避免重犯）

- `ServerEntityEvents` 在 `event.lifecycle.v1`，**不是** `entity.event.v1`。
- `CraftingRecipeInput` 用 **`getSize()`** 不是 `size()`。
- `SpecialRecipeSerializer` 在 1.21.1 是 **顶层类** `net.minecraft.recipe.SpecialRecipeSerializer`，1.21.4 才挪进 `SpecialCraftingRecipe` 成嵌套类。
- `PersistentProjectileEntity.setPunch(int)` 公开方法在 1.21.1 **已删除**（击退改走武器附魔组件）。
- `ServerLivingEntityEvents.AFTER_DAMAGE` 是 **1.21.2(fabric-api 0.106.x)才有**；本项目 fabric-api 0.105.0+1.21.1 **没有**，反制逻辑用 `ALLOW_DAMAGE`（签名 `(entity, source, amount)`）。
- `build.gradle` 取 archivesName 要写 `project.base.archivesName.get()`（不是 `project.archivesName`）。
- 实体渲染是 1.21.2 重构（EntityRenderState）**之前** 的旧体系；模型渲染颜色参数为 `int`。
- 旧版 64×32 皮肤套现代模型会缺左手左腿，需先转 64×64（把右肢复制到左肢区）。

---

## 6. 已知问题 / 待办

- **精英皮肤是占位**：`assets/yongye/textures/entity/elite_{skeleton,witch,zombie,creeper,spider}.png` 多为纯色/简易占位，等用 GPT「编辑原版贴图、保持 UV、同尺寸」做正式贴图覆盖（骷髅/苦力怕/蜘蛛 64×32，僵尸/女巫 64×64）。`pain_boss.png` 是长门皮（已转 64×64，左肢为右肢复制，侧面可能轻微镜像，要精确需逐面转换）。
- **灾厄核心方块贴图是占位**：`assets/yongye/textures/block/catastrophe_core.png` 暗红占位，可换正式图。
- **长门全局唯一靠静态字段**：服务器重启时 `activePain` 静态字段会重置，而长门实体是 persistent 的——极端情况下重启后可能再刷一个。属罕见边界，若要彻底解决可在自然降临前扫描已加载的 IS_PAIN 实体，或把 activePain 也持久化。
- **「防御%减伤」技能书**：设计文档有纯百分比减伤，但 1.21.1 伤害事件只能否决不能改数值,目前用「护甲强化(真实护甲)」覆盖需求；要精确百分比减伤需改伤害管线。
- **抗性书可细分**：设计 13.4 有抗火/毒/凋零/爆炸/箭/摔落分项,目前合成一本「抗性强化」。
- **Boss 阶段化/专属掉落表** 可继续深化(现已有 BossAbilityHandler 的狂暴/召唤/冲击波)。
- 可做方向(用户认可的优先级):核心进阶(高永夜出更强「灾变核心」) / 安全区据点机制 / 更多任务种类 / 给长门做专属模型特效。

### 新增待办(最近几轮产生,优先级高→低)
- **职业天赋树(m40 已落地第一版,命令驱动)**:6 职业各 5 节点(4 属性 + 1 持续增益技能),`/talent` 玩家命令加点(总览/list/learn/reset/info),等级发点(默认 50 起每级 1 点,死亡保留),挂进 `ClassManager.applyClasses` 重刷链路仅对生效职业应用。**天赋 GUI(m52 已落地)**:背包「天赋」按钮 → `client/TalentScreen`,逐职业展示 5 节点、点击加点(C2S `TalentLearnPayload`→`learn`→S2C `TalentSyncPayload` 即时刷新)。**待续**:数值平衡(节点数值/maxRank/发点速率)、连线式树状美化、节点消耗随 rank 递增。
- **职业专属技能(m41 已落地,触发型,`system/ClassSkillHandler`,纯事件不依赖 mixin)**:战士 吸血+斩杀;坦克 嘲讽+护盾(减伤%由 m40 守护者天赋覆盖);刺客 背刺+闪避+脱战加速;术士 潜行牺牲生命换范围魔法伤害;武僧 空手连击+缴械;剑客 剑气波+格挡反击。**待续**:数值平衡(伤害/概率/半径均在配置里可调,未实测)、坦克若要真·百分比减伤可改 mixin(现用抗性近似)。
- **职业大招(m47 已落地,主动技能,`system/ClassUltimateManager`)**:按键「职业大招」(默认 X,`key.yongye.ultimate`)→ C2S `ClassUltimatePayload` → 施放本命职业主动技能,带冷却(`ultimateCooldownTicks`)。旋风斩/不动如山/影遁/灭世/百裂拳/万剑归一,数值 `ult*` 全可调。纯事件不依赖 mixin。
- **职业设定缺口已补(m45)**:武僧「武器耐久×2」——持非拳套武器攻击时额外 `setDamage(+1)`(纯事件、非 mixin,只覆盖攻击磨损,正合「逼你用拳」本意);刺客「更易暴击」——职业暴击概率(`assassinCritChance`,持影刺再+15%,追加 攻击力×`assassinCritBonusFraction`)。至此六职业原始设定全部落地。配置:`monkWeaponDurabilityPenalty`/`assassinCritChance`/`assassinCritBonusFraction`。
- **职业专属武器(m42 已落地,`item/ClassWeaponItem`)**:6 职业各一把,差异化基础属性 + 手持时强化本职业 m41 技能(协同);`/yongye classweapon <id>` 给予,已进创意标签。**获取途径(m46 已落地)**:稀有材料合成配方(`data/yongye/recipe/class_weapon_<id>.json`,主料/副料按职业区分 + 对应基底)。**获取(m52 补全)**:Boss 必掉 1 把随机职业武器、精英 `classWeaponDropChanceElite`(默认 4%)概率掉。**待续**:数值平衡。
- **开局选职业(m43 已落地)**:进度模型=出生即本命职业(第一职业,0 级生效、永不掉级失去),职业书改为第二职业(需 classLevel2)。登录未选过则弹 `ClassSelectScreen`(按钮版,强制选、屏蔽 ESC),C2S `ChooseClassPayload` → `ClassManager.chooseStartingClass`。配置 `enableStartingClassSelect`/`startingClassGiveWeapon`(**默认送**该职业专属武器,m48 起)。选职界面 m47 已**卡图化**(`ClassSelectScreen` 卡图版,3×2 卡牌点选,`drawTexture` 用 `AccessoryScreen` 确认的签名,卡图统一 96×132)。m48:**背包显示当前职业**——职业 id 经 `StatsPayload`(新增 className)同步到 `ClientStats`,背包 `ScreenEvents.AFTER_INIT` 钩子加「本命·<职业>」标签按钮。
- **坦克专属盾·磐盾(m44 已落地)**:`item/TankShieldItem` 继承 `ShieldItem`,副手 +护甲/韧性/击退抗性/生命;坦克持盾护盾再+1级、格挡反震(`tankShieldReflect`)。已配 32×32 平面贴图,`/yongye tankshield` 给予;合成配方 `recipe/tank_shield.json`(catastrophe_blood_core+life_core+盾)。**待续**:立体盾面(`builtin/entity` 渲染管线,现为平面图标)。
- **编译点**:m37 两交互距离属性、m40 四属性(`GENERIC_ATTACK_SPEED/KNOCKBACK_RESISTANCE/ARMOR_TOUGHNESS/LUCK`)均已随 build 验证通过;m41 `ClassSkillHandler` 主要看 `ServerLivingEntityEvents.ALLOW_DAMAGE` 在该 Fabric API 版本是否存在/签名一致(闪避·格挡反击用它否决伤害),其余 API 与 `WeaponCombatHandler`/`ClassManager` 既有用法一致(`target.timeUntilRegen=0` 追加伤害叠加法)。
- **饰品栏自定义背景**:界面读 `textures/gui/accessory_gui.png`(176×158),现为占位,等用户 GPT 出正式图覆盖。
- **职业书贴图占位**:`class_book_<id>.png` 现复制自技能书,待正式美术。
- **第一天长白天(进度系统)**:`ProgressionManager.tickFirstDay` 每 tick 强设主世界时间放慢首日白天到 24 分钟,未测试;若时间异常,把配置 `firstDayLong=false` 关掉即可(其余进度功能不受影响)。

---

## 7. 美术资源现状

- **物品图标**：18 个材料+神器图标已是用户用 GPT 做的 64×64 高清版；7 本技能书图标也是 64×64。文件在 `assets/yongye/textures/item/`，改图直接覆盖同名文件即可，**不用改代码**。
- 用户做图标的流程：给 GPT 现有 PNG 当参考图保持画风，出透明背景 PNG → 缩放后覆盖。各物品的提示词在聊天记录里有完整一份(材料 8 + 神器 10 + 技能书 7 + 精英皮 5 的提示词模板)。
- 实体贴图放 `assets/yongye/textures/entity/`，文件名固定:`elite_skeleton/elite_witch/elite_zombie/elite_creeper/elite_spider.png`、`pain_boss.png`。要加新精英种类:告诉 Claude 怪名,在 `EliteSkinFeatureRenderer` 加一行映射即可。
