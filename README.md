# SVC Extra — Simple Voice Chat 增强模组

将 **Channel** 模组的 TCP 原生通信、NVIDIA Broadcast 降噪和路径追踪混响集成到 **Simple Voice Chat (SVC)** 中。

## 功能

### 1. 双协议传输 (TCP / UDP)
| 协议 | 描述 | 来源 |
|------|------|------|
| **UDP** | SVC 原生 UDP 通信 (DatagramSocket) | SVC |
| **TCP** | Netty + Minecraft Varint21 帧协议 + AES-GCM 加密 | Channel |

通过在 `svc-extra.json` 中设置 `transport` 字段选择。
- TCP 模式使用 Minecraft 原生的帧同步协议，适合高稳定性环境
- UDP 模式保持 SVC 原生低延迟通信

### 2. 噪声消除
| 模式 | 描述 | 来源 |
|------|------|------|
| `OFF` | 不处理 | - |
| `WEBRTC_LOW/MEDIUM/HIGH/AGGRESSIVE` | WebRTC 噪声抑制 | Channel (WebRTC) |
| `NVIDIA_AI` | NVIDIA Broadcast AI 降噪 (需 RTX 显卡) | Channel (NVIDIA AFX SDK) |

### 3. 路径追踪混响 (Ray-Traced Reverb)
基于 **Channel** 的路径追踪算法，使用 OpenAL EFX EA-Reverb：

- **Fibonacci 球面射线分布** - 300 条/帧，均匀采样空间
- **区块弹射追踪** - 最多 5 次反弹，64 格距离
- **实时计算参数**：
  - `Density` — 混响密度（基于旅程标准差）
  - `Diffusion` — 扩散度（基于表面粗糙度）
  - `RT60` — 混响衰减时间（基于吸收系数）
  - `Gain HF` — 高频衰减（空气吸收 + 表面吸收）
  - `Early Reflections` — 早期反射（增益/延迟/方位）
  - `Late Reflections` — 晚期混响（增益/延迟/方位）
  - `Echo Time/Depth` — 回声参数
- **水下效果** — 自动检测并调整混响特性

### 4. 高质量音频管线
```
Mic → 48kHz 采样 → WebRTC NS/AGC/HPF → NVIDIA AFX → Resample → Opus 编码
```
- 48kHz 麦克风采样率
- 可配置帧长 (10/20/40/60ms)
- 自动增益控制 + 目标电平调节
- 高通滤波器

## 运行时要求

### TCP 模式
- 服务端和客户端都必须启用 TCP 模式
- 需要 32 字节 hex AES-256 加密密钥（自动生成或手动配置）
- 使用 Netty（Minecraft 已内置）

### NVIDIA AI 降噪
- **Windows 10/11**
- **NVIDIA RTX 系列显卡** (驱动 ≥ 570)
- **NVIDIA Audio Effects SDK** 或 NVIDIA Broadcast 已安装
- 支持的采样率: 16kHz 或 48kHz

## 构建

```bash
./gradlew build
```

生成的 JAR 在 `build/libs/svc-extra-1.0.jar`。

## 依赖

- Fabric Loader
- Fabric API
- Simple Voice Chat
- Minecraft 26.2

## 致谢

本模组整合了以下项目的代码/设计：

- **[Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat)** by henkelmax — 基础语音聊天框架
- **[Channel](https://github.com/USS-Shenzhou/Channel)** by USS_Shenzhou — TCP 传输协议、WebRTC 音频处理、NVIDIA AFX 集成、路径追踪混响算法
- **NVIDIA Audio Effects SDK** — AI 降噪
