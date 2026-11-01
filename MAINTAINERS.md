# 维护者操作手册

面向**项目所有者 / 维护者**。说明如何审核与合并社区 PR、如何处理 Issue、如何发版，以及安全问题的处置流程。

---

## 1. 角色与权限

| 角色 | 权限 |
|---|---|
| Owner | 仓库设置、分支保护、Release 发布、成员与权限管理 |
| Maintainer | 审核并合并 PR、管理 Issue 与标签、关闭无效讨论 |
| Contributor | 提交 Issue 与 PR（分支需经审核才能进入 `main`） |

建议在 GitHub 上开启 `main` 分支保护：

- 禁止直接 push（包括 Owner，便于留下评审记录）
- 合并前至少 1 个 approve
- 必须通过 `build` 检查（见第 6 节）
- 合并后自动删除 head 分支

---

## 2. 审核 Pull Request

### 2.1 收到 PR 后的 24 小时内

1. 打标签：`type/*` + `area/*`（见第 4 节）
2. 判断是否对应已有 Issue；无关紧要的小改动（错别字）可直接合并
3. 未关联 Issue 的**新功能/重构类 PR**：先要求补 Issue 并说明方案，再进入代码审核

### 2.2 代码审核检查项

按顺序逐项确认，任何一项不满足则请求修改（用 `Request changes`）：

**正确性**
- [ ] 能否用一两句话说明"改了什么、为什么改"
- [ ] 改动是否真正修复了根因，而不是绕过现象
- [ ] 边界情况：空 token、401/403、网络超时、空列表、字段缺失
- [ ] 线程模型：网络在后台线程、UI 在 EDT

**项目约束**
- [ ] 仍保持单文件 `Launcher.java`、零第三方依赖
- [ ] 无新增绝对路径（全部走 `appPath`）
- [ ] UI 使用 `Theme` / `FlatButton` / `VectorIcon`，无色值硬编码
- [ ] 未删除或削弱已有功能

**安全与合规**
- [ ] 无真实 token、账号、游戏 ID、个人路径（重点检查 `.properties`、日志片段、截图）
- [ ] 未提交 `data/`、`Game/`、`game.pack`、`*.class`、`dist/`
- [ ] 未引入第三方站点的大规模抓取脚本或逆向资料

**可维护性**
- [ ] 命名清晰、无死代码、无注释掉的旧实现
- [ ] 新增逻辑有中文注释解释"为什么"
- [ ] 无过度设计（未为不存在的需求抽象）

### 2.3 本地验证

审核 UI 相关 PR 时，务必本地跑一遍：

```powershell
git fetch origin pull/<PR号>/head:pr-<PR号>
git switch pr-<PR号>
javac -encoding UTF-8 Launcher.java
java -cp . Launcher
```

> 维护者注意：**不要依赖贡献者"我测过了"的描述**，UI 改动必须自己看到效果。

### 2.4 合并方式

| 场景 | 方式 |
|---|---|
| 常规功能/修复（多提交） | **Squash and merge**，压缩为一条符合 Conventional Commits 的提交 |
| 贡献者已整理为若干干净提交且需保留历史 | **Rebase and merge** |
| 禁止 | Merge commit（保持线性历史） |

合并时的提交信息示例：

```
feat(service): 工单列表显示最后更新时间 (#142)
```

合并后：删除源分支 → 在 PR 中回复"已合入 `main`，感谢贡献" → 如有 Release 计划，记入下个版本的变更清单（见第 5 节）。

### 2.5 需要拒绝或关闭的情况

- 与项目定位不符（如把启动器改成通用 MC 启动器、外包登录到正版验证）
- 引入第三方库或破坏单文件结构
- 含敏感数据且贡献者拒绝清理
- 30 天无响应（标 `stale`）、60 天无进展（关闭，并在评论中说明可随时重开）

---

## 3. 处理 Issue

### 3.1 首次响应（7 天内）

对每条新 Issue：

1. 确认是否可复现 / 需求是否清晰；信息不足则用评论提出**具体**补充项（环境、步骤、日志）
2. 打标签并设置优先级
3. 可复现的 Bug → 移入待修复清单；功能建议 → 与 Owner 评估是否进入路线图
4. 重复的 → 引用原 Issue 后关闭

### 3.2 标签体系

| 类型 | 含义 |
|---|---|
| `type/bug` | 可复现的功能异常 |
| `type/feature` | 新功能或交互改进 |
| `type/docs` | 文档问题 |
| `type/question` | 使用咨询，非缺陷 |
| `area/game` `area/stats` `area/service` `area/settings` `area/api` `area/build` | 涉及模块 |
| `priority/p0` | 阻断（启动失败、数据丢失、凭据泄露） |
| `priority/p1` | 主要功能不可用 |
| `priority/p2` | 次要功能异常或有可用替代 |
| `priority/p3` | 体验优化、文案 |
| `good first issue` | 适合新人上手 |
| `help wanted` | 需要社区协助 |
| `stale` | 长期无进展 |
| `duplicate` / `wontfix` / `invalid` | 关闭原因 |

### 3.3 关闭规范

- 修复类：合并 PR 后由 PR 的 `Closes #N` 自动关闭；手动关闭时注明对应提交/PR 号
- 不修复：说明原因（不兼容设计 / 上游接口限制 / 成本收益）
- 关闭不等于否定，措辞保持中立

---

## 4. 版本与发布

### 4.1 版本号

采用语义化版本 `主版本.次版本.修订号`：

- **主**：配置键改名、文件结构变更等破坏性改动
- **次**：新增功能（向下兼容）
- **修订**：Bug 修复、文案与样式微调

同步更新位置：`Launcher.java` 中的 `launcher.version` 默认值、`installer.iss` 的 `MyAppVersion`、README 的打包命令示例。

### 4.2 发布流程

```powershell
# 1. 确保 main 干净且 CI 通过
git switch main && git pull

# 2. 编译、打 jar
javac -encoding UTF-8 Launcher.java
jar cfe Launcher.jar Launcher Launcher*.class

# 3. 生成 app-image
jpackage --type app-image --name SecblkLauncher --app-version 1.0.0 ^
  --input . --main-jar Launcher.jar --main-class Launcher ^
  --icon res\icon.ico --dest dist

# 4. 放入 game.pack（本地生成或沿用上个版本的包）
copy game.pack dist\SecblkLauncher\

# 5. 生成安装包
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" installer.iss
```

GitHub Release 附件清单：

| 附件 | 说明 |
|---|---|
| `SecblkLauncher-Setup.exe` | 安装包（含运行时与 game.pack） |
| `Launcher.jar` | 纯程序包（供已有游戏目录的进阶用户） |
| `game.pack` | 单独提供，便于增量更新（体积大，必要时分卷） |

Release 说明模板：

```markdown
## 更新内容
- feat: ...
- fix: ...

## 升级提示
- 覆盖安装即可，`data/` 会被保留
- 若游戏文件有变更，请替换新的 `game.pack` 并在设置页执行"删除游戏文件"后重启以触发重新解压
```

### 4.3 发布前检查

- [ ] 版本号四处一致（源码默认值 / iss / README / Release 标题）
- [ ] 安装包在**干净环境**（无 JDK 的机器或新建用户目录）实测可启动
- [ ] 首次启动能从 `game.pack` 完成解压
- [ ] 官网登录、统计、白名单、工单四条链路各点一遍
- [ ] Release 附件不含 `data/`、日志、测试残留

---

## 5. 安全问题处置

涉及凭据泄露、任意代码执行、路径穿越、供应链风险的问题，**不走公开 Issue**：

1. 通过仓库的 **Security → Report a vulnerability**（私密安全通告）接收
2. 48 小时内确认并给出初步评估
3. 修复在私有分支完成，合并后再发布 Release 说明（不含利用细节）
4. 视严重程度为报告者署名致谢

---

## 6. 自动化检查建议

在 `.github/workflows/build.yml` 中至少包含：

```yaml
name: build
on: [push, pull_request]
jobs:
  compile:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - run: javac -encoding UTF-8 Launcher.java
```

可选追加的守卫检查（防止敏感文件被误提交）：

- 禁止提交 `data/`、`game.pack`、`Game/`、`*.class`
- 扫描疑似令牌字符串（如 `Authorization: Bearer <长串>`）

---

## 7. 联系

- 日常讨论：仓库 Issue / Discussions
- 玩家社群：QQ 群 1013247828、KOOK 频道 9754356030552272
- 安全与准则举报：通过 GitHub 私密安全通告或 Owner 邮箱
