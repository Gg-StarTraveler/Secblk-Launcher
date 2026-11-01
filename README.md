# 第二街区启动器（Secblk Launcher）

一个面向「第二街区」Minecraft 服务器的第三方**桌面启动器**，使用 Java Swing 手绘深色主题界面，零第三方依赖、单文件源码（`Launcher.java`），可一键打包为 Windows 可执行程序与安装包。

> 本项目与 Mojang Studios、Microsoft 及 SECBLK 官方无隶属关系，详见文末[免责声明](#免责声明)。

---

## 项目简介

「第二街区」服务器的玩家原先需要通过命令行或多套工具分别完成登录、启动游戏、申请白名单、查看角色数据、提交工单等操作。本项目把这些高频动作收拢到一个轻量启动器里：

- **开箱即用**：内置整合包解压安装流程，首次启动自动从 `game.pack` 还原游戏目录
- **离线优先**：仅使用离线登录（输入自己的游戏 ID），不依赖正版账号接口
- **与官网数据打通**：登录 SECBLK 官网账号后，可直接在启动器内查看角色资产、申请白名单、提交工单

代码刻意保持**单文件、零依赖**：不引入 Maven/Gradle、不依赖任何第三方库（JSON 解析、HTTP、PoW 求解、UI 框架全部手写），便于社区阅读与二次开发。

---

## 功能特性

| 模块 | 功能 |
|---|---|
| **启动** | 离线登录（输入游戏 ID，自动生成离线 UUID）；自动定位 Java；可配置内存（`-Xmx`）；启动/停止游戏进程；打开游戏日志 |
| **主页** | 服务器实时状态（在线人数/延迟/MOTD，60 秒轮询）；官网、QQ 群、KOOK 频道快捷入口；BodyCam 录像目录一键打开 |
| **统计** | 登录官网账号后展示角色卡片：皮肤头像、角色名、金币/银行/点券余额、执照（驾驶/枪械/商业）、等级经验、组织与职位、房产与产业 |
| **服务** | 白名单申请（提交 / 查看审核状态 / 撤回待审核申请）；工单（提交问题、查看处理状态） |
| **设置** | 自定义 Java 路径；关闭启动器；删除游戏文件（保留 `game.pack`）；删除所有本地数据（危险操作，二次确认） |
| **安装** | 游戏文件以 `game.pack` 分发，首次启动自动解压并写入完成标记，避免"半成品"被当作已安装 |

### 技术要点

- 官网登录需先完成 **PoW 工作量证明**（`SHA-256(token:challenge:nonce)` 前 `difficulty` 位为零），本项目已完整复刻该流程
- 官网接口鉴权仅接受 `Authorization: Bearer <token>`（Cookie 无效）
- 所有本地路径统一通过 `Launcher.appPath()` 锚定到程序根目录，避免工作目录漂移（`user.dir` 在部分 JDK 下不可靠）
- 白名单/工单接口的细节说明见 [CONTRIBUTING.md](CONTRIBUTING.md#对接第三方接口的注意事项)

---

## 项目结构

```
.
├── Launcher.java               # 全部源码（单文件，约 2900 行）
├── run.bat                     # 开发模式：编译并运行
├── installer.iss               # Inno Setup 安装包脚本（相对路径，可复用）
├── res/
│   ├── hero.jpg                # 主页背景图
│   └── icon.ico                # 应用图标（PNG-in-ICO）
├── LICENSE                     # GPL-3.0
├── README.md
├── CONTRIBUTING.md             # 贡献指南（Issue / PR 规范）
├── CODE_OF_CONDUCT.md          # 贡献者行为准则
├── MAINTAINERS.md              # 维护者操作手册
├── .github/workflows/build.yml # CI：编译 + 敏感文件守卫
│
├── Game/                       # 整合包目录（不入库，需自行准备）
├── game.pack                   # 游戏文件压缩包（不入库，本地生成 / Releases 下载）
└── data/                       # 本地配置与登录 token（不入库，自动生成）
```

> `Game/`、`game.pack`、`Game.zip`、`data/`、`dist/`、`*.class` 均已列入 [.gitignore](.gitignore)。

---

## 环境要求

| 项 | 要求 |
|---|---|
| 操作系统 | Windows 10 / 11（x64）。UI 为 Swing，跨平台理论可行，但路径与打包脚本面向 Windows |
| JDK | **21 及以上**（实测 JDK 25.0.3 LTS）；需同时具备 `javac` 与 `jpackage` |
| 游戏本体 | 自备 Minecraft 1.21.11 + Fabric 整合包（本项目不附带任何游戏资源） |
| 可选 | Inno Setup 6（仅在生成安装包时需要） |

检查环境：

```powershell
java -version
javac -version
```

---

## 快速开始（开发模式）

```powershell
# 1. 获取源码
git clone https://github.com/Gg-StarTraveler/Secblk-Launcher.git
cd Secblk-Launcher

# 2. 编译并运行
javac -encoding UTF-8 Launcher.java
java -cp . Launcher
```

或直接双击 `run.bat`（脚本会先编译，失败则暂停以便查看报错）。

> 源码为 UTF-8 且含中文，编译时 **必须** 带 `-encoding UTF-8`，否则 Windows 默认 GBK 会导致乱码或编译失败。

---

## 游戏资源准备与 `game.pack`

仓库**不包含**任何游戏文件。首次使用前需自行准备整合包（Minecraft、Fabric、模组、资源包等各自遵循其原许可）。

### 方式一：使用已发布的 `game.pack`

从 GitHub Releases 下载对应版本的 `game.pack`，放到启动器根目录（与 `SecblkLauncher.exe` 同级），启动后会自动解压到 `Game/`。

### 方式二：自行打包（推荐贡献者 / 服务器管理者使用）

1. 准备整合包目录，按如下结构放置：

   ```
   Game/
   └── Secblk-第二街区/
       ├── versions/        # 1.21.11 + Fabric
       ├── mods/
       ├── libraries/
       ├── assets/
       └── options.txt
   ```

2. 在项目根目录执行打包命令：

   ```powershell
   java Launcher --pack
   ```

   该命令会把整个 `Game/` 打成 `game.pack`（zip 格式），并自动排除：
   - `logs/`（含个人聊天与调试信息）
   - `camplus_recordings/`（BodyCam 录像，体积巨大）

3. 打包完成后终端会输出条目数与最终体积。

### 解压与安装机制

- 启动器检测 `Game/.install-ok` 标记；标记存在即视为已安装
- 解压全部完成后才写入该标记，中途失败会自动重试，不会留下"看似已安装"的残缺目录
- 安装过程带进度窗口，可看到当前条目与总体进度

---

## 配置文件说明

启动器首次运行会自动创建 `data/launcher.properties`：

| 键 | 默认值 | 说明 |
|---|---|---|
| `launcher.name` | 第二街区 | 窗口标题与品牌文案 |
| `launcher.version` | 1.0.0 | 启动器版本号 |
| `server.version` | 1.21.11 | 用于主页文案展示 |
| `server.slogan` | （见源码） | 主页副标题 |
| `offline.username` | 空 | 上次使用的游戏 ID |
| `game.memory` | 2G | JVM 最大堆内存 |
| `java.path` | 空 | 自定义 Java 路径；留空则自动探测 |
| `secblk.token` | 空 | **官网登录令牌（敏感）**，401 后自动清除 |
| `secblk.name` | 空 | 官网账号显示名 |

> ⚠️ `secblk.token` 等价于账号凭据，**仅在本地明文保存**，请勿提交、截图或分享 `data/` 目录。

---

## 打包发布

### 1. 生成可执行程序（jpackage app-image）

```powershell
# 编译并打 jar
javac -encoding UTF-8 Launcher.java
jar cfe Launcher.jar Launcher Launcher*.class

# 生成 app-image（输出 dist\SecblkLauncher\）
jpackage --type app-image ^
  --name SecblkLauncher ^
  --app-version 1.0.0 ^
  --input . ^
  --main-jar Launcher.jar ^
  --main-class Launcher ^
  --icon res\icon.ico ^
  --dest dist

# 把游戏包放到生成目录旁
copy game.pack dist\SecblkLauncher\
```

生成结果：`dist\SecblkLauncher\SecblkLauncher.exe` + `app\Launcher.jar` + `runtime\`（自带 JDK 运行时，目标机无需安装 Java）。

### 2. 生成安装包（Inno Setup）

安装 [Inno Setup 6](https://jrsoftware.org/isdl.php) 后：

```powershell
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" installer.iss
```

产物为 `dist\SecblkLauncher-Setup.exe`，特性：

- 简体中文界面，默认安装至 `%LOCALAPPDATA%\Programs\SecblkLauncher`（免管理员，保证可写 `Game/` 与 `data/`）
- 自动排除开发机上的 `Game/`、`data/` 测试残留
- 可选创建桌面快捷方式，安装完成后可立即启动

---

## 隐私与数据安全

- 仓库中**不含**任何真实账号、令牌、Cookie 或个人信息；`data/` 已被 `.gitignore` 排除
- 游戏 `logs/` 与录像默认只在本地保留，打包 `game.pack` 时自动排除
- 提交 Issue / PR 或发布截图前，请自查是否暴露了 `secblk.token`、游戏 ID、聊天记录
- `secblk-api-feasibility.md`（含第三方站点接口调研细节）**保留在本地、不入库**，如需公开请先评估合规风险

---

## 贡献指南

我们欢迎任何形式的参与：报告 Bug、提出功能建议、改进文档、提交代码。

**提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)**，核心要求摘要：

1. **Issue 先行**：较大的改动请先开 Issue 讨论方案，避免无效工作量
2. **分支规范**：从 `main` 拉出 `feat/xxx`、`fix/xxx`、`docs/xxx`
3. **提交规范**：使用 Conventional Commits（`feat:` / `fix:` / `docs:` / `refactor:` / `chore:`）
4. **保持单文件零依赖**：新增功能请写在 `Launcher.java` 内，不引入第三方库
5. **自测清单**：`javac -encoding UTF-8 Launcher.java` 必须零警告零错误；不得新增绝对路径；不得提交 `data/`、`Game/`、`*.class`
6. **PR 描述**：说明"改了什么、为什么改、如何验证"，涉及 UI 的请附截图

### Issue 规范

- **Bug 报告**：标题写明模块（如 `[服务页] 撤回白名单报错`），正文包含复现步骤、预期结果、实际结果、JDK 版本、日志片段
- **功能建议**：说明使用场景与期望交互，标注是否为破坏性变更
- 请勿在 Issue 中粘贴令牌、账号密码或完整日志中的个人路径

---

## 贡献者行为准则

参与本项目的所有交流与贡献，均须遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。摘要：

- **尊重他人**：禁止人身攻击、歧视性言论、骚扰与恶意挑衅
- **专业沟通**：批评针对代码与方案，不针对个人；对新人问题保持耐心
- **不越权**：不得未经授权修改他人分支、删除他人提交或强行推送
- **不泄密**：不得公开他人账号凭据、私人信息或未公开的服务端数据
- **理性分歧**：技术分歧以事实、文档与实际验证结果为准，由维护者裁定
- **零容忍**：涉及违法内容、恶意代码、后门或数据窃取的行为，将立即被永久移出项目

违规处理：警告 → 临时禁言 → 永久封禁，由维护者根据严重程度裁定。举报请联系维护者（见 [MAINTAINERS.md](MAINTAINERS.md)）。

---

## 维护者

作为项目所有者，审核与合并社区贡献的标准流程见 **[MAINTAINERS.md](MAINTAINERS.md)**，包含：

- Pull Request 的审核检查项与合并方式（Squash / Rebase）
- Issue 的标签体系、优先级判定与关闭规范
- 版本号、Release 与 `game.pack` 附件发布流程
- 安全问题的私密处理渠道

---

## 常见问题

**Q：首次启动提示「未检测到游戏文件，且缺少 game.pack」？**
A：把 `game.pack` 放到启动器根目录（与 exe 同级）后重启；或在有 `Game/` 整合包的开发机上执行 `java Launcher --pack` 生成。

**Q：游戏内无法切换简体中文？**
A：多为 `assets` 中缺少 `zh_cn` 语言对象，或资源包索引与实际文件不一致。可检查 `Game/assets/indexes/<版本>.json` 里是否存在 `zh_cn` 条目及其对应对象文件。

**Q：统计页提示「登录状态已失效」？**
A：官网 token 过期（接口返回 401/403），启动器会自动清除本地 token，重新点击右上角头像登录即可。

**Q：白名单申请提交失败？**
A：官网白名单支持「文本」与「问卷」两种模式。若官网当前启用问卷模式，启动器内的文本申请会被服务端拒绝，请前往官网提交。

**Q：启动器里的余额/执照数据是实时的吗？**
A：进入统计页或点击「刷新数据」时拉取一次；页面不会自动轮询角色数据。

---

## 免责声明

- 本项目为**非官方**第三方启动器，与 Mojang Studios、Microsoft、SECBLK 官方均无隶属或背书关系
- 项目**不附带**任何 Minecraft 游戏文件、模组、资源包或皮肤素材，使用者需自行准备并遵守其各自许可协议
- 启动器调用的第三方站点接口可能随时变更或限制访问；若接口调整导致功能失效，本项目不承担由此产生的责任
- 请勿将本项目用于商业售卖、绕过服务端规则或任何违反服务条款的行为

---

## 许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 开源发布。任何衍生作品同样须以 GPL-3.0 发布并保留原作者版权声明。
