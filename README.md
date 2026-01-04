# Extended Note Block (扩展音符盒)

[![Fabric](https://img.shields.io/badge/modloader-Fabric-blue?style=for-the-badge)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/minecraft-1.20.1-green?style=for-the-badge)](https://www.minecraft.net)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/badge/download-Modrinth-00AF5C?style=for-the-badge)](https://modrinth.com/mod/extendednoteblock)

**Extended Note Block** 是一个专为 Minecraft Fabric 平台设计的模组，致力于为游戏引入更专业、更强大的音乐创作体验。它不仅升级了原有的音符系统，还引入了类似现代数字音频工作站 (DAW) 的高级控制功能。

---

## 核心功能

### 1. 扩展音符盒 (Extended Note Block)
这是模组的核心方块，相比原版音符盒，它提供了更细致的参数控制：
- **音高 (Pitch)**: 支持 0-127 的 MIDI 标准音高。
- **力度 (Velocity)**: 控制声音的强弱 (0-127)。
- **时值 (Sustain)**: 自定义声音的持续时间。
- **延迟 (Delay)**: 支持毫秒级的播放延迟。
- **渐变 (Fade)**: 内置渐入 (Fade In) 与渐出 (Fade Out) 效果。

### 2. 高级模式 (Advanced Mode)
开启高级模式后，玩家可以访问更深度的声音调制功能：
- **可视化包络线 (Visual Curves)**: 
    - **音量包络线**: 通过点击 and 拖拽曲线，自由控制声音在播放过程中的音量变化。
    - **弯音包络线**: 精确控制播放过程中的音高偏移（Pitch Bend）。
- **空间音效移动 (Sound Movement)**: 
    - 使用**数学表达式** (Math Expressions) 驱动声源的动态移动。
    - 支持 X、Y、Z 三轴独立的表达式。
    - 提供变量 `t` (时间百分比) 和 `d` (Tick 计数) 以及丰富的数学函数 (sin, cos, exp 等)。

### 3. 无线红石系统
- **传输器 (Transmitter)** & **接收器 (Receiver)**: 提供全局范围的无线红石信号传输，极大简化了大型音乐电路的布线。

---

## 工具与命令

### 指挥棒 (Conductor's Wand)
专为大规模创作设计的工具：
- **区域选择**: 右键选择两个端点确定操作区域。
- **批量编辑**: 通过 Conductor GUI 对选区内的音符盒进行批量数值修改。
- **智能模式**: 支持“设置”、“增加”、“乘法”等多种操作模式，轻松完成音阶爬升或动态调整。

### SmoothMove 命令
提供服务器 Tick 级的平滑移动逻辑：
- `smoothmove <targets> <direction> <speed> [duration]`
- 允许实体根据服务器当前的真实 TPS 自动进行平滑插值移动，完美适配 Carpet 变速等复杂场景。无需手动指定 TPS。

---

## 音色包管理
模组内置了强大的音色包管理器：
- **自定义采样**: 玩家可以轻松导入自己的 wav/mp3 采样。
- **包管理界面**: 在游戏中直接创建、编辑和切换不同的音色包。
- **Zip 支持**: 支持直接读取压缩包格式的音色包，方便分享和安装。

---

## 配置文件
模组提供详细的配置项，包括：
- 采样加载策略。
- 弯音调节范围。
- 客户端与服务端同步参数。

---

## 开发与贡献
本项目基于 Fabric API 1.20.1 开发。如果你有任何建议或发现了 BUG，欢迎在 GitHub 提交 Issue 或 Pull Request。

**License**: [MIT](LICENSE)