package com.liuyue.svcextra.audio;
import com.liuyue.svcextra.SvcExtra;
import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig;
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig.NoiseSuppression.Level;
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
public class WebRtcNative {
    private static AudioProcessing apm;
    private static AudioProcessingStreamConfig cfg;
    private static int currentLevel = -1;
    private static boolean nativeLoaded = false;
    private static synchronized boolean loadNativeLibrary() {
        if (nativeLoaded) return true;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            String libName;
            String platformDir;
            if (os.contains("win")) {
                libName = "webrtc-java-windows-x86_64.dll";
                platformDir = "windows-x86_64";
            } else if (os.contains("mac")) {
                if (arch.contains("aarch64") || arch.contains("arm64")) {
                    libName = "libwebrtc-java-macos-aarch64.dylib";
                    platformDir = "macos-aarch64";
                } else {
                    libName = "libwebrtc-java-macos-x86_64.dylib";
                    platformDir = "macos-x86_64";
                }
            } else { 
                if (arch.contains("aarch64") || arch.contains("arm64")) {
                    libName = "libwebrtc-java-linux-aarch64.so";
                    platformDir = "linux-aarch64";
                } else if (arch.contains("arm") || arch.contains("aarch32")) {
                    libName = "libwebrtc-java-linux-aarch32.so";
                    platformDir = "linux-aarch32";
                } else {
                    libName = "libwebrtc-java-linux-x86_64.so";
                    platformDir = "linux-x86_64";
                }
            }
            String classifierJarPath = "/META-INF/jars/webrtc-java-0.14.0-" + platformDir + ".jar";
            try (InputStream jarIs = WebRtcNative.class.getResourceAsStream(classifierJarPath)) {
                if (jarIs == null) {
                    SvcExtra.LOGGER.error("Classifier JAR not found: {}", classifierJarPath);
                    return false;
                }
                try (ZipInputStream zis = new ZipInputStream(jarIs)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String entryName = entry.getName();
                        if (!entryName.equals(libName) && !entryName.endsWith("/" + libName)) {
                            continue;
                        }
                        Path tempDir = Files.createTempDirectory("svcextra-webrtc");
                        Path tempLib = tempDir.resolve(libName);
                        Files.copy(zis, tempLib, StandardCopyOption.REPLACE_EXISTING);
                        tempLib.toFile().deleteOnExit();
                        tempDir.toFile().deleteOnExit();
                        System.load(tempLib.toAbsolutePath().toString());
                        nativeLoaded = true;
                        SvcExtra.LOGGER.info("Loaded native library: {} from {}", libName, tempLib);
                        return true;
                    }
                    SvcExtra.LOGGER.error("{} not found inside {}", libName, classifierJarPath);
                    return false;
                }
            }
        } catch (Throwable e) {
            SvcExtra.LOGGER.error("Failed to load native library", e);
            return false;
        }
    }
    public static boolean load() {
        if (apm != null) return true;
        try {
            if (!loadNativeLibrary()) {
                SvcExtra.LOGGER.warn("Native library load failed, WebRTC disabled");
                return false;
            }
            apm = new AudioProcessing();
            var config = new AudioProcessingConfig();
            config.noiseSuppression.enabled = true;
            config.noiseSuppression.level = Level.MODERATE;
            config.gainControl.enabled = false;
            config.echoCanceller.enabled = false;
            apm.applyConfig(config);
            cfg = new AudioProcessingStreamConfig(48000, 1);
            SvcExtra.LOGGER.info("webrtc-java AudioProcessing ready");
            return true;
        } catch (Throwable e) {
            SvcExtra.LOGGER.error("webrtc-java init failed", e);
            return false;
        }
    }
    public static boolean init(int nsLevel) {
        if (apm == null) return load();
        try {
            var config = new AudioProcessingConfig();
            config.noiseSuppression.enabled = true;
            config.noiseSuppression.level = switch (nsLevel) {
                case 0 -> Level.LOW;
                case 1 -> Level.MODERATE;
                case 2 -> Level.HIGH;
                default -> Level.VERY_HIGH;
            };
            config.gainControl.enabled = false;
            config.echoCanceller.enabled = false;
            apm.applyConfig(config);
            currentLevel = nsLevel;
            return true;
        } catch (Throwable e) {
            SvcExtra.LOGGER.error("WebRTC setLevel failed", e);
            return false;
        }
    }
    public static void process(short[] audio, int sampleRate) {
        if (apm == null) return;
        int frameSize = sampleRate / 100;
        if (frameSize <= 0) {
            SvcExtra.LOGGER.error("Invalid sample rate: {}", sampleRate);
            return;
        }
        int totalSamples = (audio.length / frameSize) * frameSize;
        if (totalSamples == 0) {
            return;
        }
        if (totalSamples != audio.length) {
        }
        if (cfg == null || cfg.sampleRate != sampleRate) {
            cfg = new AudioProcessingStreamConfig(sampleRate, 1);
        }
        for (int offset = 0; offset < totalSamples; offset += frameSize) {
            try {
                byte[] in = new byte[frameSize * 2];
                ByteBuffer.wrap(in).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().put(audio, offset, frameSize);
                byte[] out = new byte[in.length];
                apm.processStream(in, cfg, cfg, out);
                ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(audio, offset, frameSize);
            } catch (Throwable e) {
                SvcExtra.LOGGER.error("WebRTC processFrame failed at offset {}", offset, e);
            }
        }
    }
    public static void close() {
        if (apm != null) {
            try { apm.dispose(); } catch (Throwable ignored) {}
            apm = null;
        }
    }
    public static int getLevel() { return currentLevel; }
}
