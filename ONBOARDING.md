# 《夜蚀》项目 · 新对话对接文档

> 用法:开新对话时把本文档整个粘贴(或上传)给 AI,然后直接说任务。本文档只含**不变的规则**,
> 一切**当前状态**以仓库里的 HANDOVER.md / DEVLOG.md 为准——所以它永远不过期,下次下下次都能用。

## 一、项目是什么

- 《夜蚀 NightBlight》:Minecraft **Fabric 1.21.1** 模组,极难灾变生存(白天跑图/夜晚逃命/永夜追杀,无限强化反向变强)。
- 仓库:`https://github.com/qiaodaxian233/yongye`,主分支 `main`。
- 语言:代码注释、commit、文档全部**简体中文**。

## 二、开工三步(每个新会话固定动作)

1. `git clone --depth 1 https://github.com/qiaodaxian233/yongye.git`
2. **先读 `HANDOVER.md`**(最新状态一行)和 `DEVLOG.md` **尾部几条**(最近里程碑),不要全量扫项目。
3. 按任务定向读文件(grep/sed 取行,headroom 省 token 口径),然后开干。

## 三、工作流铁律(违者返工)

1. **做一步推一步**:每个功能/修复 = 一个里程碑 `mNNN`(编号接着 DEVLOG 最后一条往下排),独立 commit,
   commit message 用详尽中文一行(格式参考 git log)。完成即推送 `main`。
2. **PAT 规矩**:**不要向作者索要 PAT**。作者会在对话里直接贴一串 `github_pat_...`,贴了就用
   (`git push https://qiaodaxian233:<PAT>@github.com/qiaodaxian233/yongye.git HEAD:main`),
   推完**不要提醒撤销**。没有可用 PAT → 本地提交 + `git format-patch` 导出 patch 文件交付,不专门开口要。
3. **每笔记账**:DEVLOG.md 追加 `## mNNN 标题(作者点名,日期)` 条目(根因/方案/配置变更/待编译验证/实机盯什么);
   HANDOVER.md 的 `> 最新:` 状态行同步改写。
4. **配置铁律**:所有新功能必须带配置项(开关 + 关键数值),不允许硬编码;
   改默认值必须走**"仅旧默认迁移"**(YongyeConfig.load() 里 `if (字段==旧默认) 字段=新默认;`,自定义值不动);
   有配置变更就把顶部 `CURRENT_CONFIG_VERSION` +1 并在注释里记 mNNN。
5. **编译风险控制**(沙盒无法跑 gradle,fabric maven 不可达):
   - 新 API 一律先核名:在树先例(grep 项目里已用过)优先;否则拉 **FabricMC/yarn 1.21.1 官方映射**
     (`raw.githubusercontent.com/FabricMC/yarn/1.21.1/mappings/net/minecraft/.../Xxx.mapping`)确认方法名;
   - 核不到的列进 DEVLOG「待编译验证」;每次改完做括号平衡自检;
   - 作者本地 `gradlew build / runClient` 流程可用,会回贴编译错误和崩溃报告,按报告修。
   - **CI 编译闭环(m344 起)**:仓库带 GitHub Actions(`.github/workflows/build.yml`),每次推送自动云端 build;
     推送后轮询 `https://api.github.com/repos/qiaodaxian233/yongye/actions/runs?per_page=1` 查 conclusion
     (success/failure),failure 时拉该 run 的 jobs 日志定位报错——**AI 会话可自主完成编译验证,不必等作者**。
   - 推送前预检:`bash tools/validate-resource-pack.sh --root src/main/resources` 与
     `bash tools/validate-datapack.sh --root src/main/resources`(missing pack.mcmeta 一条是 MOD 场景假阳性,忽略)。
6. **交付文件**:文档输出 Word(.docx),图片输出 PNG(项目内的 md/json/贴图除外)。

## 四、踩过的坑(新会话必读,别再踩)

- **Mixin 包内不得有会被运行时类加载的内部类**(内部 record/class 要移到普通包,mixin 用嵌套类 import 引用)。
- **record 组件跨类是 private**:被别的类以 `obj.field` 字段式访问的结构,用 public final 字段普通类,别用 record。
- **1.21.1 原版装备基础属性不在 attribute_modifiers 组件里**(默认组件 modifiers=[]),基础值挂
  `Item#getAttributeModifiers()`、仅空组件时兜底生效——任何"从基础起算"必须走 `EquipmentEnhancer.baseOf(Item)`,
  直接 set 非空组件会把 +8 护甲这类原生数值洗掉。
- **此版 fabric-api(0.105.0)没有 `AFTER_DAMAGE`**:受击钩子用 `ALLOW_DAMAGE` 观察式(恒 return true)。
- 网络包 `writeString(null)` 会 EncoderException 踢连接:所有字符串字段空值兜底;玩家附件列表读取处防 null 自愈。
- C2S 包一律服务端权威复核(先验资格/材料再执行,别信客户端)。
- UI 按钮全在背包**左侧双列**(YongyeClient 的 AFTER_INIT 块),右侧留给其它模组。
- 大批量实体操作分帧(参考 ItemCleanupHandler);波次刷怪接 `LagGuard.scale()` 按 MSPT 节流。
- emoji(如 🔒)MC 字体渲染不出,界面文本只用 BMP 内字符(✔ ▶ □ ◆ ☽ 可用)。

## 五、关键文件地图(按需读,别全读)

| 文件 | 管什么 |
|---|---|
| `YongyeConfig.java` | 全部配置 + load() 里的旧默认迁移块 + CURRENT_CONFIG_VERSION |
| `Yongye.java` | 服务端各系统 register()(注意别重复注册) |
| `client/YongyeClient.java` | 客户端注册、背包左侧双列按钮、S2C 接收、tooltip 回调 |
| `system/EquipmentEnhancer.java` | 无限强化/baseOf/withLevel/转移 |
| `system/MainQuestLine.java` | 主线 16 阶段 + 职业试炼 + 图鉴数据(任务书) |
| `client/QuestBookScreen.java` | 任务书三页签界面 |
| `client/VisualFxScreen.java` / `DebugScreen.java` | 视觉设置屏 / 调试菜单(都走 `yongye config set` 命令) |
| `mixin/client/SlashPoseMixin.java` | 挥砍七式 + 疾跑姿态(MoBends 移植,叠加通道须与原版同频) |
| `network/YongyeNet.java` | 全部 payload 注册与接收器 |
| `THIRD_PARTY_NOTICES.md` | 借鉴过的开源项目挂名(MoBends/AvaritiaNeo 等,MIT 需保留声明) |

## 六、给新会话 AI 的开场白模板(复制改任务即可)

```
这是《夜蚀》Minecraft Fabric 1.21.1 模组项目,规则见我贴的对接文档,严格遵守。
先 clone 仓库读 HANDOVER.md 和 DEVLOG.md 尾部接上状态,然后完成以下任务(做一步推一步):
1. ……
2. ……
```
