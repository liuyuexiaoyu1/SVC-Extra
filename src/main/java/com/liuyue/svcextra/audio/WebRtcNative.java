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
    private static volatile boolean nativeLoaded = false;

    private final AudioProcessing apm;
    private final AudioProcessingStreamConfig cfg;

    public WebRtcNative(int nsLevel, boolean echoCancel) {
        loadNativeOnce();
        apm = new AudioProcessing();
        var config = new AudioProcessingConfig();
        config.noiseSuppression.enabled = true;
        config.noiseSuppression.level = switch (nsLevel) {
            case 0 -> Level.LOW;
            case 1 -> Level.MODERATE;
            case 2 -> Level.HIGH;
            default -> Level.VERY_HIGH;
        };
        config.gainControl.enabled = false;
        config.echoCanceller.enabled = echoCancel;
        config.echoCanceller.enforceHighPassFiltering = echoCancel;
        apm.applyConfig(config);
        cfg = new AudioProcessingStreamConfig(48000, 1);
    }

    public void process(short[] audio, int sampleRate) {
        if (apm == null) return;
        int frameSize = sampleRate / 100;
        int total = (audio.length / frameSize) * frameSize;
        if (total == 0) return;
        var c = cfg;
        for (int off = 0; off < total; off += frameSize) {
            try {
                byte[] in = new byte[frameSize * 2];
                ByteBuffer.wrap(in).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().put(audio, off, frameSize);
                byte[] out = new byte[in.length];
                apm.processStream(in, c, c, out);
                ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(audio, off, frameSize);
            } catch (Throwable e) {
                SvcExtra.LOGGER.error("webrtc process failed at {}", off, e);
            }
        }
    }

    public void close() {
        if (apm != null) try { apm.dispose(); } catch (Throwable ignored) {}
    }

    private static synchronized void loadNativeOnce() {
        if (nativeLoaded) return;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            String libName, platformDir;
            if (os.contains("win")) { libName = "webrtc-java-windows-x86_64.dll"; platformDir = "windows-x86_64"; }
            else if (os.contains("mac")) {
                if (arch.contains("aarch64") || arch.contains("arm64")) { libName = "libwebrtc-java-macos-aarch64.dylib"; platformDir = "macos-aarch64"; }
                else { libName = "libwebrtc-java-macos-x86_64.dylib"; platformDir = "macos-x86_64"; }
            } else {
                if (arch.contains("aarch64") || arch.contains("arm64")) { libName = "libwebrtc-java-linux-aarch64.so"; platformDir = "linux-aarch64"; }
                else { libName = "libwebrtc-java-linux-x86_64.so"; platformDir = "linux-x86_64"; }
            }
            String jarPath = "/META-INF/jars/webrtc-java-0.14.0-" + platformDir + ".jar";
            try (InputStream jarIs = WebRtcNative.class.getResourceAsStream(jarPath)) {
                if (jarIs == null) throw new RuntimeException("Classifier JAR not found: " + jarPath);
                try (ZipInputStream zis = new ZipInputStream(jarIs)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.getName().equals(libName) && !entry.getName().endsWith("/" + libName)) continue;
                        Path tmpDir = Files.createTempDirectory("svcextra-webrtc");
                        Path libPath = tmpDir.resolve(libName);
                        Files.copy(zis, libPath, StandardCopyOption.REPLACE_EXISTING);
                        libPath.toFile().deleteOnExit(); tmpDir.toFile().deleteOnExit();
                        System.load(libPath.toAbsolutePath().toString());
                        nativeLoaded = true;
                        return;
                    }
                    throw new RuntimeException(libName + " not found");
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("WebRTC native load failed", e);
        }
    }
}
