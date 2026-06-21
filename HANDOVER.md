# 亡途荒夜 · 项目交接文档（HANDOVER）

> 给接手这个项目的人（或新对话里的 AI 助手）。读完这份就能无缝接上，不用回头翻聊天记录。
> 仓库：https://github.com/qiaodaxian233/yongye　·　Minecraft **Fabric 1.21.1** · 纯 Java，无前置 mod（Fabric API 除外）。

---

## 0. 一分钟速览

- 这是一个 **极难灾变生存** 玩法 mod：白天搜刮、夜晚逃命，任务失败会推高「永夜」等级，怪物锁定/挖墙/爬墙追杀；玩家靠 **装备血量 + 8 种技能书 + 10 种背包神器 + 饰品栏 + 职业 + 随机掉落** 反向变强,并按游戏天数推进难度(类「惊变」)。
- 代码量:**62 个 Java 文件 / 约 6550 行 / 205 项可调配置 / DEVLOG 38 个里程碑**。
- **项目完成度(估)≈ 92%**:核心玩法、成长线、世界节奏、Boss、HUD、饰品栏、时间进度、职业系统、天赋树、职业专属技能、职业专属武器均已落地;余下主要是真·主动技能(keybind/GUI 触发)、选职/天赋 GUI、职业与天赋数值平衡、武器获取途径与专属贴图、若干美术占位替换。
- **构建状态**:饰品栏(m33)用户已确认 BUILD SUCCESSFUL 且可用;此后又叠加了 m34~m38(饰品扩容/进度系统/职业系统/佩恩修复等),**职业系统(m37)与进度系统(m36)用户尚未回报编译结果,需本地再 build 一次确认**(最可能编译点见第 5、6 节)。

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
- **职业天赋树(m40 已落地第一版,命令驱动)**:6 职业各 5 节点(4 属性 + 1 持续增益技能),`/talent` 玩家命令加点(总览/list/learn/reset/info),等级发点(默认 50 起每级 1 点,死亡保留),挂进 `ClassManager.applyClasses` 重刷链路仅对生效职业应用。**待续**:真·主动/触发型技能、天赋 GUI、数值平衡(节点数值/maxRank/发点速率需实测调整)、可考虑节点消耗随 rank 递增。
- **职业专属技能(m41 已落地,触发型,`system/ClassSkillHandler`,纯事件不依赖 mixin)**:战士 吸血+斩杀;坦克 嘲讽+护盾(减伤%由 m40 守护者天赋覆盖);刺客 背刺+闪避+脱战加速;术士 潜行牺牲生命换范围魔法伤害;武僧 空手连击+缴械;剑客 剑气波+格挡反击。**待续**:真·主动技能(需 keybind/客户端网络或 GUI 触发)、数值平衡(伤害/概率/半径均在配置里可调,未实测)、武僧「武器耐久×2」仍待 mixin、坦克若要真·百分比减伤可改 mixin(现用抗性近似)。
- **职业专属武器(m42 已落地,`item/ClassWeaponItem`)**:6 职业各一把,差异化基础属性 + 手持时强化本职业 m41 技能(协同);`/yongye classweapon <id>` 给予,已进创意标签。**待续**:获取途径(拟精英/Boss 稀有掉落或稀有材料合成)、数值平衡(贴图已配 32×32 handheld)。
- **选职业 GUI**:用户提过「开局选职业送武器」备选方案 + 一个选职界面,未做。
- **编译点**:m37 两交互距离属性、m40 四属性(`GENERIC_ATTACK_SPEED/KNOCKBACK_RESISTANCE/ARMOR_TOUGHNESS/LUCK`)均已随 build 验证通过;m41 `ClassSkillHandler` 主要看 `ServerLivingEntityEvents.ALLOW_DAMAGE` 在该 Fabric API 版本是否存在/签名一致(闪避·格挡反击用它否决伤害),其余 API 与 `WeaponCombatHandler`/`ClassManager` 既有用法一致(`target.timeUntilRegen=0` 追加伤害叠加法)。
- **饰品栏自定义背景**:界面读 `textures/gui/accessory_gui.png`(176×158),现为占位,等用户 GPT 出正式图覆盖。
- **职业书贴图占位**:`class_book_<id>.png` 现复制自技能书,待正式美术。
- **第一天长白天(进度系统)**:`ProgressionManager.tickFirstDay` 每 tick 强设主世界时间放慢首日白天到 24 分钟,未测试;若时间异常,把配置 `firstDayLong=false` 关掉即可(其余进度功能不受影响)。

---

## 7. 美术资源现状

- **物品图标**：18 个材料+神器图标已是用户用 GPT 做的 64×64 高清版；7 本技能书图标也是 64×64。文件在 `assets/yongye/textures/item/`，改图直接覆盖同名文件即可，**不用改代码**。
- 用户做图标的流程：给 GPT 现有 PNG 当参考图保持画风，出透明背景 PNG → 缩放后覆盖。各物品的提示词在聊天记录里有完整一份(材料 8 + 神器 10 + 技能书 7 + 精英皮 5 的提示词模板)。
- 实体贴图放 `assets/yongye/textures/entity/`，文件名固定:`elite_skeleton/elite_witch/elite_zombie/elite_creeper/elite_spider.png`、`pain_boss.png`。要加新精英种类:告诉 Claude 怪名,在 `EliteSkinFeatureRenderer` 加一行映射即可。
