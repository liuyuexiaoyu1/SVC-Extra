package com.liuyue.svcextra.config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.liuyue.svcextra.SvcExtra;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
public class SvcExtraConfig {
    public Client client = new Client();
    public Server server = new Server();
    public static class Client {
        public NoiseCancelMode noiseCancelMode = NoiseCancelMode.OFF;
        public float aiNoiseCancelRatio = 0.5f;
        public boolean highPassFilter = true;
        public boolean autoGainControl = true;
        public boolean echoCancel = false;
        public float targetLevelDbfs = -5f;
        public int rayTraceIntervalMs = 500;
        public float maxGain = 20f;
        public float duckingLevel = 0.3f;
        public int frameLengthMs = 20;
        public int micSampleRate = 48000;
        public int networkTolerance = 200;
        public String webrtcLibPath = "";
        public String nvidiaDllPath = "";
        public String nvidiaModelDir = "";
        public boolean rayTraceAudio = false;
        public boolean showRayTrace = false;
        public float absoluteLoudness = 0f;
    }
    public static class Server {
        public Transport transport = Transport.UDP;
    }
    public enum Transport { UDP, MC_CHANNEL }
    public enum NoiseCancelMode { OFF, WEBRTC_LOW, WEBRTC_MEDIUM, WEBRTC_HIGH, WEBRTC_AGGRESSIVE, NVIDIA_AI }
    private static SvcExtraConfig instance;
    public static SvcExtraConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("svc-extra.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (Files.exists(path)) {
            try { instance = gson.fromJson(Files.readString(path), SvcExtraConfig.class); }
            catch (IOException e) { SvcExtra.LOGGER.error("Failed to load config", e); }
        }
        if (instance == null) instance = new SvcExtraConfig();
        instance.save();
        return instance;
    }
    public void save() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("svc-extra.json"), gson.toJson(this));
        } catch (IOException e) {
            SvcExtra.LOGGER.error("Failed to save config", e);
        }
    }
}
