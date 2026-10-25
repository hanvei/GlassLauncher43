# GlassLauncher43 配套 HOME 修复固件

本分支用于保存 GlassLauncher43 与乐视 S50 Air 固件配套使用的说明、修改记录和验证工具。刷机包体积超过 GitHub 普通 Git 文件限制，实际文件放在本仓库的 **Releases** 中。

## 严格适用范围

只用于以下完全一致的设备和系统：

| 项目 | 值 |
| --- | --- |
| 实际测试电视型号 | Letv X3-43 |
| 实际产品名 | S243F |
| 设备代号 | mstarnapoli |
| 主板 | BD_MST181B_D01A |
| Android | 4.3 / API 18 |
| EUI | 6.0 |
| 固件版本 | 6.0.072N_0801 |
| 构建号 | V2202RCN02C060072B08012N |
| CPU ABI | armeabi-v7a |

这是一套修改过的第三方 ROM。刷机包内的 `build.prop` 将型号写成 **Letv S50 Air / S2-50F**，但实际测试电视通过 ADB 上报的是 **Letv X3-43 / S243F**。因此不能只看 ROM 显示的型号，必须同时核对实际硬件、主板 `BD_MST181B_D01A`、设备代号 `mstarnapoli` 和完整构建号。

不要用于其他尺寸、主板、设备代号或构建号。

## 与桌面的配套关系

配套桌面：[`tv.glasslauncher43`](https://github.com/hanvei/GlassLauncher43)，当前配套版本为 **1.6（versionCode 7）**。

**固件没有内置桌面 APK。刷机前必须先安装 GlassLauncher43，并确认它能正常打开。** 该升级不会主动清除 `/data`，所以已安装的桌面通常会保留；如果执行了恢复出厂设置，桌面 APK 会被删除，需要通过 ADB 或 U 盘重新安装。

推荐顺序：

1. 安装并打开 GlassLauncher43。
2. 按 HOME 键，在系统选择框中选择 Glass Home，并选“始终”。
3. 确认遥控器方向键、确定、返回、菜单键和应用启动正常。
4. 备份原始固件和重要数据。
5. 再刷入本配套固件。

## 固件实际修改

### 1. HOME 键改为标准 Android HOME 解析

修改文件：`/system/framework/framework-letv.jar`

厂商的 `letv.desktop.DesktopManagerService#getLauncherIntent()` 原本执行：

```java
intent.setPackage(DesktopManager.LAUNCHER_PACKAGENAME);
```

这会把 HOME Intent 强制限定到系统桌面 `com.stv.launcher`。本固件移除了这一行，保留标准的：

```text
ACTION_MAIN + CATEGORY_HOME
```

因此系统可以使用 Android 的默认 HOME 解析结果，用户选择的 Glass Home 才能接收 HOME 键。系统桌面仍保留，必要时可以重新选择或用于回退。

### 2. 开机完成后启动 GlassLauncher43

新增文件：`/system/etc/install-recovery-2.sh`

脚本最多等待 `sys.boot_completed=1` 120 秒，完成后再等待 3 秒，然后执行：

```sh
am start --user 0 -n tv.glasslauncher43/.MainActivity
```

这样可以避开厂商桌面在开机阶段抢占前台的问题。若 GlassLauncher43 没有安装，命令会静默失败，不会删除系统桌面。

### 3. 重新封装和校验

- 只重建 system 分区对应的前三段 LZO 数据。
- 自动更新 MStar 升级脚本中的 payload 偏移、长度和 system 解压长度。
- 重新计算 16 KiB 头部 CRC、数据区 CRC 和尾部签名字段。
- 校验结果：magic、header CRC、bin CRC、header signature 均通过。
- system 以外的后三个 payload 与基础包逐段 SHA-256 一致。

## 没有包含的改动

- 没有把 GlassLauncher43 APK 写入 `/system/app`。
- 没有删除系统桌面 `com.stv.launcher`。
- 没有清除用户数据或替换应用布局数据。
- 没有把后来通过 ADB 处理的开机广告文件封装进本固件。
- 没有额外改写 ADB、网络、信号源、电视接收器或系统服务配置。

这些边界很重要：本固件只解决开机进入 Glass Home 和 HOME 键被厂商桌面强制接管的问题。

## 下载与校验

从本仓库 Releases 下载：

```text
LetvUpgrade-GlassLauncher43-HomeFix-v2.bin
```

SHA-256：

```text
F21E73B242EB63975BEBB8A76F20DCCBD23C61A01AFB9A7A3C0EB06099FB60A2
```

刷机介质上应将文件重命名为：

```text
LetvUpgrade.bin
```

Windows 校验命令：

```powershell
Get-FileHash .\LetvUpgrade.bin -Algorithm SHA256
```

## 刷机步骤

1. 确认电视型号、主板、设备代号和构建号与上表完全一致。
2. 确认 GlassLauncher43 已安装并可正常启动。
3. 准备此前已在本机验证可用于升级的 U 盘，将其保持原有文件系统格式。
4. U 盘根目录只放重命名后的 `LetvUpgrade.bin`，并核对 SHA-256。
5. 使用本机原有的 USB 强刷进入方式启动升级；这台测试机此前使用的是 **3695 + 开机键** 流程。
6. 出现升级进度后不要断电、拔 U 盘或操作遥控器。
7. 首次启动时间可能比平时长。进入系统后测试开机桌面和 HOME 键。

## 刷机后检查

- 开机完成后应进入 Glass Home。
- 按 HOME 应停留或返回 Glass Home，不应被直接强制送回系统桌面。
- 若出现 HOME 选择框，选择 Glass Home 并设为“始终”。
- 若进入系统桌面，先确认 `tv.glasslauncher43` 仍已安装，再清除当前系统桌面的默认设置并重新按 HOME 选择。
- 保留 ADB 时可检查：

```sh
adb shell pm list packages | grep tv.glasslauncher43
adb shell am start -n tv.glasslauncher43/.MainActivity
```

## 回退

本固件保留系统桌面，因此 Glass Home 异常时仍可从系统设置或 HOME 选择框切回 `com.stv.launcher`。要完整恢复 HOME 框架行为，应重新刷入已备份的原始、且与本机型号和构建号完全匹配的固件。

不要使用 `invalid-homefix-v1.bin`；它是早期无效测试产物，不属于发布内容。

