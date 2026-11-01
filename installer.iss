; ============================================================
;  第二街区启动器 —— Inno Setup 安装包脚本
;  用法：先按 README 生成 dist\SecblkLauncher\（jpackage app-image），
;        再执行 ISCC installer.iss
;  说明：脚本内所有路径均为相对路径（相对本文件所在目录），
;        可在任意目录 / 任意机器上复用。
; ============================================================

#define MyAppName "第二街区启动器"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "第二街区"
#define MyAppExeName "SecblkLauncher.exe"

[Setup]
AppId={{B7E2A914-3C5F-4D68-9A1E-2F6B8C0D5E77}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppVerName={#MyAppName} {#MyAppVersion}
VersionInfoVersion={#MyAppVersion}
VersionInfoDescription={#MyAppName} 安装程序
; 启动器需在安装目录旁写入 Game/ 与 data/，必须免管理员且目录可写
PrivilegesRequired=lowest
DefaultDirName={localappdata}\Programs\SecblkLauncher
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=SecblkLauncher-Setup
SetupIconFile=res\icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
; game.pack 本身已是压缩包，使用 fast 级别以缩短构建时间
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; 排除 Game/ 与 data/：开发机上的测试残留不应进入安装包
; （其中 Game\.install-ok 会让启动器误判为"已安装"）
Source: "dist\SecblkLauncher\*"; DestDir: "{app}"; \
    Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "Game\*,data\*"

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "安装完成后启动 {#MyAppName}"; \
    Flags: nowait postinstall skipifsilent
