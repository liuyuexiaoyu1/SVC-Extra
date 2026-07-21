# SVC Extra — Simple Voice Chat 音质增强模组

将 **Channel** 模组的 TCP 原生通信、NVIDIA Broadcast 降噪和路径追踪混响集成到 **Simple Voice Chat (SVC)** 中。

## 架构概览

```
┌──────────────────────────────────────────────────────────────────┐
│                     SVC Extra (Fabric Mod)                       │
│                                                                  │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────────┐  │
│  │  Transport  │  │    Audio      │  │      Reverb              │  │
│  │  Layer    │  │  Pipeline    │  │  (Ray-traced)            │  │
│  │           │  │              │  │                           │  │
│  │  ┌─────┐ │  │ ┌──────────┐ │  │ ┌───────────────────────┐ │  │
│  │  │ UDP │ │  │ │ WebRTC   │ │  │ │ Fibonacci Sphere Rays │ │  │
│  │  │     │ │  │ │ NS/AGC   │ │  │ │ → Block Hit Tracing   │ │  │
│  │  └─────┘ │  │ │ /HPF/VAD │ │  │ │ → EA-Reverb EFX       │ │  │
│  │  ┌─────┐ │  │ └──────────┘ │  │ │   (Density/Diffusion/ │ │  │
│  │  │ TCP │ │  │ ┌──────────┐ │  │ │    RT60/Early+Late    │ │  │
│  │  │     │ │  │ │ NVIDIA   │ │  │ │    Reflections/Echo)  │ │  │
│  │  │  ┌─ │ │  │ │ AFX      │ │  │ └───────────────────────┘ │  │
│  │  │  │  │ │  │ │ Denoiser │ │  │                           │  │
│  │  │  └─ │ │  │ └──────────┘ │  │                           │  │
│  │  └─────┘ │  └──────────────┘  └───────────────────────────┘  │
│  └──────────┘                                                    │
└──────────────────────────────────────────────────────────────────┘
```

## 功能

### 1. 双协议传输 (TCP / UDP)
| 协议 | 描述 | 来源 |
|------|------|------|
| **UDP** | SVC 原生 UDP 通信 (DatagramSocket) | SVC |
| **TCP** | Netty + Minecraft Varint21 帧协议 + AES-GCM 加密 | Channel |

通过在 `svc-extra-server.json` 中设置 `transport` 字段选择。
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

## 配置

### 客户端配置 (`config/svc-extra-client.json`)
```json
{
  "transport": "UDP",            // "UDP" 或 "TCP"
  "noiseCancelMode": "NVIDIA_AI", // "OFF"/"WEBRTC_LOW/MEDIUM/HIGH/AGGRESSIVE"/"NVIDIA_AI"
  "aiNoiseCancelRatio": 0.5,     // NVIDIA 降噪强度 [0..1]
  "highPassFilter": true,
  "autoGainControl": true,
  "echoCancel": false,
  "targetLevelDbfs": -5,
  "maxGain": 20,
  "rayTraceAudio": true,         // 启用路径追踪混响
  "raysPerFrame": 300,
  "maxBounce": 5,
  "maxRayDistance": 64,
  "micSampleRate": 48000,
  "networkSampleRate": 48000,
  "frameLengthMs": 20,
  "networkTolerance": 200,
  "nvidiaDllPath": ""            // 自定义 NVIDIA AFX DLL 路径
}
```

### 服务端配置 (`config/svc-extra-server.json`)
```json
{
  "transport": "UDP",
  "tcpPort": 0,                  // 0 = 使用 MC 服务器端口
  "tcpBindAddress": "",
  "tcpEncryptionKey": "",        // 32 字节 hex AES-GCM 密钥
  "udpPort": 24454
}
```

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

### 路径追踪混响
- 需要 OpenAL Soft (支持 EFX)
- 推荐 CPU: 多核处理器 (使用 ForkJoinPool 并行化)

## 文件结构

```
svc-extra/
├── src/main/java/com/liuyue/svcextra/
│   ├── SvcExtra.java                    # Mod 主入口
│   ├── config/
│   │   ├── SvcExtraConfig.java          # 客户端配置
│   │   └── SvcExtraServerConfig.java    # 服务端配置
│   ├── transport/
│   │   ├── VoiceTransport.java          # 传输层接口
│   │   ├── UdpTransport.java            # UDP 实现
│   │   └── tcp/
│   │       ├── TcpTransport.java        # TCP 服务端
│   │       ├── TcpClientTransport.java  # TCP 客户端
│   │       └── TcpPacketCodec.java      # AES-GCM 编解码
│   ├── audio/
│   │   ├── AudioPipeline.java           # 音频处理管线
│   │   └── NativeLoader.java            # 原生库加载器
│   └── mixin/
│       ├── VoicechatServerMixin.java    # 服务端传输重定向
│       └── VoicechatClientConnectionMixin.java
├── src/client/java/com/liuyue/svcextra/client/
│   ├── SvcExtraClient.java              # 客户端入口
│   ├── audio/
│   │   └── RayTracedReverb.java         # 路径追踪混响
│   └── mixin/
│       └── VoicechatClientAudioMixin.java
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## 构建

```bash
./gradlew build
```

生成的 JAR 在 `build/libs/svc-extra-1.0.jar`。

## 依赖

- Fabric Loader ≥ 0.16.9
- Fabric API ≥ 0.110.0+1.21.1
- Simple Voice Chat (fabric-2.5.26+)
- Minecraft 1.21.1

## 致谢

本模组整合了以下项目的代码/设计：

- **[Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat)** by henkelmax — 基础语音聊天框架
- **[Channel](https://github.com/USS-Shenzhou/Channel)** by USS_Shenzhou — TCP 传输协议、WebRTC 音频处理、NVIDIA AFX 集成、路径追踪混响算法
- **NVIDIA Audio Effects SDK** — AI 降噪
