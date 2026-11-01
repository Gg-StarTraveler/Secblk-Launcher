# 贡献指南

感谢你愿意为「第二街区启动器」付出时间。本文档说明**如何提交 Issue、如何提交 Pull Request，以及代码必须满足的硬性要求**。

> 参与本项目即表示你同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

---

## 我可以贡献什么

| 类型 | 是否需要先开 Issue | 说明 |
|---|---|---|
| 报告 Bug | 否，直接开 Issue | 请附复现步骤与日志 |
| 文档修正（错别字、表述不清） | 否 | 可直接提 PR |
| UI 文案 / 图标微调 | 否 | 可直接提 PR，附截图 |
| Bug 修复 | 建议先开 Issue 确认根因 | 避免修错方向 |
| 新功能 / 重构 | **必须**先开 Issue 讨论 | 避免无效工作量 |
| 第三方接口适配 | **必须**先开 Issue | 涉及合规与稳定性 |

---

## 开发流程

```powershell
# 1. Fork 仓库并克隆自己的副本
git clone https://github.com/<your-name>/Secblk-Launcher.git
cd Secblk-Launcher

# 2. 关联上游
git remote add upstream https://github.com/Gg-StarTraveler/Secblk-Launcher.git

# 3. 从最新的 main 拉出特性分支
git fetch upstream
git switch -c feat/whitelist-status upstream/main

# 4. 修改并自测
javac -encoding UTF-8 Launcher.java
java -cp . Launcher

# 5. 提交并推送
git add Launcher.java README.md
git commit -m "feat(service): 支持按状态筛选白名单申请"
git push -u origin feat/whitelist-status
```

### 分支命名

| 前缀 | 用途 | 示例 |
|---|---|---|
| `feat/` | 新功能 | `feat/ticket-reply` |
| `fix/` | Bug 修复 | `fix/bodycam-path` |
| `docs/` | 文档 | `docs/readme-faq` |
| `refactor/` | 不改变行为的重构 | `refactor/json-parser` |
| `chore/` | 构建、脚本、杂项 | `chore/inno-script` |

分支名统一小写、短横线分隔。

### 提交信息（Conventional Commits）

```
<类型>(<模块>): <简述>

<可选正文：为什么改，而不是改了什么>
```

- 类型：`feat` `fix` `docs` `refactor` `chore` `test` `style`
- 模块：`game` `stats` `service` `settings` `api` `build` `ui`
- 简述用中文或英文均可，一句话说清意图，不超过 50 字
- 一个提交只做一件事；不要混入无关的格式化改动

示例：

```
fix(game): 修正 BodyCam 录像目录指向
feat(service): 工单列表支持显示最后更新时间
docs: 补充 game.pack 自行打包步骤
```

---

## 代码规范

本项目刻意保持**极简**，请遵守以下约束：

1. **单文件、零依赖**：所有代码写在 `Launcher.java`，不引入 Maven/Gradle、不使用第三方库。JSON、HTTP、加密、UI 全部手写或使用 JDK 标准库
2. **不新增绝对路径**：所有路径一律通过 `Launcher.appPath(...)` 解析，禁止出现 `D:\`、`C:\` 等硬编码盘符（打包脚本与 `installer.iss` 也使用相对路径）
3. **中文注释**：新增逻辑请用中文注释说明"为什么"，不要逐行翻译代码
4. **UI 一致性**：颜色取 `Theme.*` 常量，按钮用 `FlatButton.primary/ghost/danger`，图标用 `VectorIcon`，禁止自行写死色值
5. **线程安全**：网络请求必须在后台线程，UI 更新一律回到 `SwingUtilities.invokeLater`
6. **异常处理**：网络层失败要给出用户可读的提示；401/403 需清除本地 token 并引导重新登录
7. **命名**：类用 `UpperCamelCase`，方法/变量用 `lowerCamelCase`，常量用 `UPPER_SNAKE_CASE`

---

## 提交前自测清单

在 PR 中逐项勾选（维护者也会按此复核）：

- [ ] `javac -encoding UTF-8 Launcher.java` 编译通过，无错误
- [ ] `java -cp . Launcher` 能正常启动，改动涉及的页面无异常堆栈
- [ ] 未提交 `data/`、`Game/`、`game.pack`、`*.class`、`dist/`、`*.exe`
- [ ] 未包含真实 token、账号、游戏 ID、个人目录路径
- [ ] 未新增绝对路径
- [ ] 未删除或破坏已有功能
- [ ] 涉及 UI 的改动附上截图（浅色/深色、窗口缩放到最小尺寸各一张）

---

## 对接第三方接口的注意事项

启动器通过 `SecblkApi` 直连官网 `https://secblk.com/v1`，改动相关代码时请特别注意：

- **不要硬编码任何令牌、账号或角色 ID**，运行时一律从 `data/launcher.properties` 读取
- 鉴权仅接受请求头 `Authorization: Bearer <token>`；Cookie 方式实测无效（403）
- 登录前必须先完成 PoW：`GET /api/auth/login/pow` → 求解 `SHA-256(token:challenge:nonce)` 前 `difficulty` 位为零 → 提交 `proofToken`
- 白名单存在「文本 / 问卷」两种模式；问卷模式下 `POST /api/users/whitelist-requests` 会失败，需向用户解释并引导到官网
- 接口随时可能调整。新增依赖接口时请：
  1. 先用裸 HTTP 请求验证响应结构
  2. 对缺失字段做容错（用 `Json.text(...)` 的默认值重载，不直接 `get` 后强转）
  3. 在 PR 描述里贴出实测响应示例（**务必脱敏**）
- 请勿提交大规模抓取脚本、站点 JS 打包产物或逆向资料到公开仓库

---

## Issue 规范

### Bug 报告

标题格式：`[模块] 一句话现象`，例如 `[服务页] 撤回白名单后列表未刷新`

正文需包含：

1. **环境**：Windows 版本、JDK 版本（`java -version`）、启动器版本（设置页可见）
2. **复现步骤**：编号列出，每一步可独立验证
3. **预期结果** / **实际结果**
4. **日志**：控制台堆栈或 `Game/Secblk-第二街区/logs/latest.log` 相关片段（**先脱敏**）
5. **截图**（如涉及 UI）

### 功能建议

标题格式：`[建议] 简述`，正文说明：

- 使用场景（为什么需要）
- 期望交互（最好附草图或参考截图）
- 是否涉及破坏性变更（如配置键改名、接口不兼容）

### Issue 会被这样处理

维护者会在 7 天内给出首次响应，并打上类型与优先级标签；判定为 Bug 且可复现的会进入待修复队列。详见 [MAINTAINERS.md](MAINTAINERS.md)。

---

## Pull Request 规范

### PR 标题

与提交信息同格式，例如 `feat(service): 工单列表显示最后更新时间`。

### PR 描述模板

```markdown
## 变更说明
<!-- 这个 PR 解决什么问题 -->

## 关联 Issue
Closes #123

## 改动内容
- 
- 

## 验证方式
1. 
2. 

## 截图（涉及 UI 时必填）
<!-- 拖入图片 -->

## 自测清单
- [ ] 编译通过
- [ ] 功能验证通过
- [ ] 无敏感数据
```

### 审查与合并

- 至少 **1 名维护者** 批准后合并；涉及架构或接口改动的需 2 名
- 合并方式：`Squash and merge`，合并后删除源分支
- 若你的 PR 与 `main` 冲突，请自行 rebase（`git fetch upstream && git rebase upstream/main`）后再推送，不要使用 `merge` 制造无意义的合并节点
- 维护者可能直接在 PR 上请求修改；超过 30 天无响应的 PR 会被标记 `stale`，60 天后关闭（可随时重新打开）

---

## 许可证与版权

提交 PR 即表示你同意以 [GPL-3.0](LICENSE) 授权你的贡献，并确认该代码为你本人原创或已获得相应授权。
