---
name: yongye-mod
description: >
  在「永夜 / yongye」Minecraft Fabric 1.21.1 整合 mod(仓库 qiaodaxian233/yongye)上写代码、加功能、
  装贴图、改配置、提交推送时使用。这份不是流程手册,而是这个项目实打实踩过的坑、必须避开的写法、
  必须先查证的版本敏感 / 仓库自定义 API、沙箱能做不能做的事,以及交活前的自查清单。
  目的只有一个:别让作者再因为同样的错误难受一遍。
  改任何 .java、装任何贴图、改 YongyeConfig、提交前,先读这份。
---

# 永夜(yongye)mod —— 踩坑 & 自查手册

> 仓库:https://github.com/qiaodaxian233/yongye · Minecraft **Fabric 1.21.1** · 纯 Java,无前置(Fabric API 除外)。
> 配套文档:`HANDOVER.md`(接手须知 + 0.5 状态行=全里程碑历史)、`DEVLOG.md`(逐里程碑细节)。**动手前三份都扫一眼。**

---

## 0. 三条铁律(违反过,作者明确不满)

1. **不装懂、不臆想**。状态类结论(凭据是否有效 / 代码是否正确 / 某 API 存不存在)**先实测,或明确标「待编译验证」**,绝不凭印象下结论。历史上栽过:没核 1.21.1 格式就说配方"没毛病"(实际全坏)、没测就说"PAT 都活着"(实际失效)。
2. **话少**。作者原话:"你话确实多了""你推就完了"。交活给结论 + 必要提醒,别长篇复述自己干了啥、别反复确认。
3. **沙箱会被反复清空**。每轮都可能丢失:本地 repo、未提交的改动、`/mnt/user-data/uploads` 里上传的文件。所以:**先 commit+push 落盘,别攒着**;每轮开工先重拉;别假设上轮的东西还在。

---

## 1. 环境:能做 / 不能做

| 能做 | 不能做 |
|---|---|
| 静态自检(括号、import 路径、JSON 合法性) | **在沙箱里编译 Fabric**(没有 Fabric/Mojang Maven 源)→ 任何新 API 都只能标「待编译验证」,让作者本地 `./gradlew build` |
| 缩放 / 处理已有贴图、程序化生成几何贴图 | **画像素角色 / UV 皮肤图集**(精英怪皮肤那种,GPT 平面图也不行,见 §5) |
| 用作者临时给的 PAT 一次性 push | **凭沙箱自身凭据 push**(没有 GitHub 凭据)→ 没 PAT 就导 `.patch` 给作者 `git am` |
| 读代码、查仓库既有 API 用法 | **从记忆确认某 MC/Fabric API 的签名 / 包路径**(版本敏感,见 §4) |

**Fabric 编不了 ≠ 代码没错。** build 成功也只代表能装能跑,行为对不对仍须进游戏实测。

---

## 2. 开工前必做的核对(省下"重复劳动"那种大坑)

曾经因为 `git clone` 落在旧快照(m157),没看远端已有成品就把 m158/m159 整套**从头重做了一遍**,而且重做版更差(占位图)。教训:

1. 重拉后 **`git fetch` / `git ls-remote ... main` 确认远端 HEAD**,别假设本地=最新。
2. **动手重建任何东西前,先确认它是不是远端已经有了**。要做的功能,先 `git log --oneline` + grep 看是不是已实现。
3. 读 `HANDOVER.md` 的 **0.5 状态行**(超长单段,最新里程碑在最前,`· 上一里程碑` 分隔)——它就是全历史,能避免重复造轮子和理解错既有系统。
4. 确认 HEAD 对应的里程碑号,新里程碑在此基础上 +1。

---

## 3. 这个 mod 特有的易混点 / 坑

- **难度有两套,别搞混**:
  - `GameDifficulty`(世界难度):**自定义枚举,7 档**——`PLAY`(游玩0.5)/`EASY`(简单0.8)/`NORMAL`(适中1.0)/`HARD`(困难1.6)/`HELL`(地狱2.5)/`ABYSS`(深渊4.0)/`ETERNAL`(永夜6.0)。开局选一次、固定不变。倍率取 `DifficultyManager.mobMult()`。**不是原版 `net.minecraft...Difficulty.HARD`!**(曾误用原版,查代码才纠正。)
  - `NightfallManager.getLevel()`(永夜等级):0~5(0昼夜正常/1暗潮/2猎杀/3围城/4灾变/5永夜V·灭世,>5=深渊N层),**随游戏推进 / 任务失败往上爬**。需求里说"到了X才开启""越来越难"用的多半是这条会爬的线。
  - 判需求挂哪条:固定门槛→世界难度;随进度变→永夜等级。拿不准就问作者。

- **怪物血量有 4 层叠加,排查"怪太肉"先看全栈**(在 `MobEnhancementHandler` / `MobBossHandler` / `DynamicScaling`):①基础 `mobHealthMultiplier`(永远开)②进度倍率 `enableMobScaling`(每级永夜/每天/按玩家血,封顶×60)③永夜深渊层(永夜>5)④动态对位 `DynamicScaling`(永夜≥5 才开,血=玩家攻击×8/BOSS×45×难度倍率)。**世界难度倍率只出现在第④层**,永夜<5 时"地狱"对血量不起作用。放大器其实是**玩家自己的攻击力**(只增不减)。

- **改一个机制,先找全部入口**。强化机制就有 4 条:`enhanceFromInventory`(背包)、`EnhanceScreenHandler.applyUpgrade`(界面)、命令 `withLevel`(直设)、`EquipmentEnhanceRecipe.addLevels`(工作台)。漏一条就被绕过(差点让工作台绕过成功率)。

- **刷怪 handler 必须有全局实体上限**。`NightfallHordeHandler` 曾只按"每玩家24格内200只"补刷、无全局闸,多人高频传送→统计框跟人走→旧怪甩在原地无限累积→刷到数万只拖崩 TPS。对照 `HardcoreSurvivalHandler` 的 `globalMaxHostilesNearby`(默60)做全局预算。

- **配置「旧值盖新默认」陷阱(GSON)**:整对象反序列化时,旧 `yongye.json` 里的值会**盖过代码里改的新默认值**。
  - 新增**字段**安全(旧 json 缺该键→GSON 保留代码初值)。
  - 改**已有字段的默认值**对老存档无效(json 里已存旧值)→ 必须靠 `configVersion` 警示 + `/yongye config check` + 作者手动 reset。
  - 加 / 删字段 → **`CURRENT_CONFIG_VERSION` 必须 +1**。纯改默认值不算结构变化可不升。

---

## 4. 必须查证的 API(版本敏感 / 仓库自定义)—— 别从记忆写

**最大的坑:新建文件的 import 包路径。** 静态括号检查**抓不到错 import**。m157 build 失败就因为 `ServerEntityEvents` 写成了 `net.fabricmc.fabric.api.entity.event.v1`,正确是 `net.fabricmc.fabric.api.event.lifecycle.v1`。

→ **新建任何 .java,逐条把 import 跟仓库里已用同一符号的现有文件比对**(grep 那个类名找到正确包路径再抄)。

已知地雷:

- Fabric 事件包路径:`ServerEntityEvents` / `ServerLivingEntityEvents` 在 `net.fabricmc.fabric.api.event.lifecycle.v1`(不是 `entity.event.v1`)。
- 1.21.x 重命名:`getMiningSpeedMultiplier` → **`getMiningSpeed`**(m124 build 报错的根因)。
- 1.21 数据驱动附魔取等级:`world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.X).orElse(null)` + `EnchantmentHelper.getLevel(entry, stack)`;`addEnchantment(RegistryEntry,int)`。(m146/m154 已随作者本地 build 通过,可放心沿用这套写法。)
- 玩家移动是**客户端权威**:服务端 `setVelocity` 不会自动同步,必须 `player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player))` 才生效。

**凡是仓库里没出现过的 MC/Fabric 接口,一律在 DEVLOG / 提交说明里标「待编译验证」**,并告诉作者 build 时盯这一处。仓库已用过的(随上次 build 编译通过的)才算 proven、可直接用。

---

## 5. 贴图装法

- **缩放算法**:插画 / AI 生图 / 照片 → **LANCZOS**;像素图 → **NEAREST**。
- **目标尺寸**:物品 / 书 64×64(`textures/item/`);方块 64×64 也行(MC 支持 HD 方块贴图,比 16×16 清楚);GUI 按界面实际像素。
- **画不了的**:精英怪皮肤(`elite_creeper/skeleton/spider/witch`)是 **UV 皮肤图集**(64×32 / 64×64,各区块贴到 3D 模型不同部位),GPT 出的平面"怪图"包上去会错位,**不能用**。只能:皮肤编辑器手改,或拿原版皮肤(`1.21.1.jar` 的 `assets/minecraft/textures/entity/`)程序化染色(照已有 `elite_zombie` 风格)。
- **GUI 背景**(如 `accessory_gui`)的格子必须对齐代码里的槽位坐标,AI 生图对不齐 → 读界面代码按真实坐标程序化画。
- 装之前**确认这物品/方块真在用**(grep 注册:`ModItems` / `ModBlocks` / `ModItemGroups` + 实际放置/发放处),别给死物装图。
- 大多数 `models/item|block/*.json` 的贴图路径已经指 `yongye:...`,**换贴图文件即生效,不用动 model/代码**。装完用 NEAREST 放大复核一眼细节没糊。

---

## 6. 交活前自查清单

- [ ] **括号配平**:所有改动 .java。注意**假阳性**——CJK 注释、`(claimed, lvl]` 这种数学区间、`1)` `2)` 序号会让粗暴计数误报(`TalentManager`/`ProtectScrollHandler` 都中过)。报警时剔除注释/字符串再数,或直接肉眼核对那段代码。
- [ ] **新文件 import 路径**逐条比对仓库既有用法(见 §4,这是最容易漏又最致命的)。
- [ ] **JSON 合法**:改 / 新建的 model、blockstate、tag、recipe、lang 全部 `json.load` 校验。
- [ ] **机制改动覆盖全部入口**(见 §3 强化那条)。
- [ ] **「待编译验证」点**:本轮用了哪些仓库没出现过的接口,列出来告诉作者。没有就明说"无新接口"。
- [ ] **configVersion**:加 / 删字段了吗?+1 了吗?
- [ ] 别声称"行为正确",只说"静态通过 / 待本地 build + 实机验证"。

---

## 7. 提交 & 推送

- git 身份(沿用历史,别用别的):`user.name="yongye-dev"`,`user.email="dev@yongye.local"`。
- **细粒度里程碑**:一件事一个里程碑提交。深度耦合、中间态编译不过的几处,可合并为一个原子提交(里程碑号可记两条)。
- 提交前同步两份文档:
  - `HANDOVER.md` 0.5 状态行:把头部 `**m{N}**` 推进,新里程碑详情**插到最前**,旧的接 `· 上一里程碑` 降级。
  - `DEVLOG.md`:**尾部追加** `## 里程碑 N — 标题` + 要点。
- 推送:作者临时给 PAT 时,**一次性带 token 推,绝不写进 git config**(`git push "https://<PAT>@github.com/qiaodaxian233/yongye.git" main`),输出里把 token 过滤掉。推完确认远端 HEAD。
- **PAT 暴露提醒**:作者把 token 贴进对话=已暴露,用完提醒去 GitHub Revoke(说一次即可,别反复念——作者嫌烦)。
- 推不上去(没 PAT / 失效)→ `git format-patch` 导 `.patch`,让作者本地 `git am`。

---

## 8. 收尾沟通模板(简洁)

交活只说四样:**改了什么(一两句)→ 要作者验的点(build + 进游戏测什么)→ 本轮有无「待编译验证」→ 还挂着的遗留**。不复述过程、不邀功、不反复确认。
