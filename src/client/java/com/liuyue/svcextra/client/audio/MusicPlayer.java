package com.liuyue.svcextra.client.audio;
import com.liuyue.svcextra.SvcExtra;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.lwjgl.openal.AL10.*;
public class MusicPlayer {
    private static MusicTrack currentTrack = null;
    private static float smoothDir = 1.0f; 
    public static String play(Player target, String fileName, float volume) {
        stop();
        Path musicDir = FabricLoader.getInstance().getConfigDir().resolve("svc-extra").resolve("music");
        if (!Files.exists(musicDir)) {
            try {
                Files.createDirectories(musicDir);
            } catch (IOException _) {}
        }
        Path filePath = musicDir.resolve(fileName);
        if (!Files.exists(filePath)) {
            return "§c文件未找到: config/svc-extra/music/" + fileName;
        }
        DecodeResult result;
        try {
            result = decodeToPcm(filePath);
        } catch (Exception e) {
            SvcExtra.LOGGER.error("decode failed", e);
            return "§c解码失败: " + e.getMessage();
        }
        int source = AL11.alGenSources();
        int buffer = AL11.alGenBuffers();
        if (source == 0) return "§c创建音源失败";
        short[] data = toShorts(result.data);
        AL11.alBufferData(buffer, AL_FORMAT_MONO16, data, result.sampleRate);
        AL11.alSourceQueueBuffers(source, buffer);
        alSourcei(source, AL_LOOPING, AL_TRUE);
        alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
        if (SvcExtra.CONFIG.client.rayTraceAudio) {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                    RayTracedReverb.getAuxSlot(), 0, EXTEfx.AL_FILTER_NULL);
        }
        alSourcePlay(source);
        currentTrack = new MusicTrack(target.getUUID(), source, buffer, volume);
        SvcExtra.LOGGER.info("Playing '{}' on {}", fileName, target.getDisplayName().getString());
        return "§a播放 §e" + fileName + "§a 于 §e" + target.getDisplayName().getString();
    }
    public static String stop() {
        if (currentTrack == null) return "§e无播放中的音乐";
        alSourceStop(currentTrack.source);
        AL11.alDeleteSources(currentTrack.source);
        AL11.alDeleteBuffers(currentTrack.buffer);
        currentTrack = null;
        return "§a已停止";
    }
    public static void tick() {
        if (currentTrack == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Player target = mc.level.getPlayerByUUID(currentTrack.playerId);
        if (target == null) return;
        Vec3 earPos = target.getEyePosition();
        Vec3 listenerEar = mc.player.getEyePosition();
        float dist = mc.player.distanceTo(target);
        AL11.alSource3f(currentTrack.source, AL_POSITION, (float) earPos.x, (float) earPos.y, (float) earPos.z);
        if (SvcExtra.CONFIG.client.rayTraceAudio) {
            AL11.alSource3i(currentTrack.source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                    RayTracedReverb.getAuxSlot(), 0, EXTEfx.AL_FILTER_NULL);
            Vec3 vel = PlayerVelocityTracker.getVelocity(currentTrack.playerId);
            AL11.alSource3f(currentTrack.source, AL_VELOCITY, (float) vel.x, (float) vel.y, (float) vel.z);
            float gain = computeGain(dist, earPos, target, listenerEar) * currentTrack.volume;
            AL11.alSourcef(currentTrack.source, AL_GAIN, gain);
        } else {
            AL11.alSource3i(currentTrack.source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                    EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
            AL11.alSource3f(currentTrack.source, AL_VELOCITY, 0f, 0f, 0f);
            AL11.alSourcef(currentTrack.source, AL_GAIN, currentTrack.volume * Math.min(1f, 1f / (1f + dist * 0.04f)));
        }
    }
    private static float computeGain(float dist, Vec3 srcPos, Player srcPlayer, Vec3 earPos) {
        float g = 1f;
        var mc = Minecraft.getInstance();
        g *= 1f / (1f + dist * 0.04f);
        float absStr = SvcExtra.CONFIG.client.absoluteLoudness;
        if (absStr > 0.001f) {
            float k = absStr * absStr * 0.05f;
            g *= Math.min(1f, (1f / (1f + k * dist * dist)) * 4f);
        }
        int si = fluidOrd(mc.level.getBlockState(BlockPos.containing(srcPos)).getFluidState());
        int di = fluidOrd(mc.level.getBlockState(BlockPos.containing(earPos)).getFluidState());
        if (si != di) g *= XM_GAIN[si][di];
        Vec3 look = srcPlayer.getLookAngle();
        Vec3 toLis = earPos.subtract(srcPos).normalize();
        float dot = (float) look.dot(toLis);
        float rawDir = 0.2f + 0.8f * Math.max(0f, dot);
        smoothDir += (rawDir - smoothDir) * 0.15f;
        g *= smoothDir;
        if (dist > 2f) {
            Vec3 dir = earPos.subtract(srcPos).normalize();
            Vec3 origin = srcPos.add(dir.scale(0.5));
            var ctx = new ClipContext(origin, earPos, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty());
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() != HitResult.Type.MISS) {
                float p = Math.min(1f, (float) hit.getLocation().distanceTo(origin) / 10f);
                if (p > 0.05f) g *= 1f - p * 0.5f;
            }
        }
        return Math.max(0.001f, Math.min(1f, g));
    }
    private static int fluidOrd(net.minecraft.world.level.material.FluidState f) {
        if (!f.isEmpty()) {
            if (f.getType().isSame(net.minecraft.world.level.material.Fluids.LAVA)
                    || f.getType().isSame(net.minecraft.world.level.material.Fluids.FLOWING_LAVA))
                return 2;
            return 1;
        }
        return 0;
    }
    private static final float[][] XM_GAIN = {
        { 1f, 0.12f, 0.20f },
        { 0.12f, 1f, 0.35f },
        { 0.20f, 0.35f, 1f },
    };
    private static DecodeResult decodeToPcm(Path path) throws Exception {
        String n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".wav")) return decodeWav(path);
        if (n.endsWith(".mp3")) return decodeMp3(path);
        throw new RuntimeException("仅支持 .wav / .mp3");
    }
    private static DecodeResult decodeWav(Path path) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat tf = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 1, 2, 48000, false);
            AudioInputStream conv = AudioSystem.isConversionSupported(tf, ais.getFormat())
                    ? AudioSystem.getAudioInputStream(tf, ais) : ais;
            var baos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = conv.read(b)) != -1) baos.write(b, 0, n);
            byte[] raw = baos.toByteArray();
            if (conv.getFormat().getChannels() == 2) raw = stereoToMono(raw);
            return new DecodeResult(raw, 48000);
        }
    }
    private static DecodeResult decodeMp3(Path path) throws IOException, JavaLayerException {
        try (InputStream fin = Files.newInputStream(path)) {
            Bitstream bs = new Bitstream(fin);
            Decoder d = new Decoder();
            var pcm = new ByteArrayOutputStream();
            int sr = 48000;
            while (true) {
                Header h = bs.readFrame();
                if (h == null) break;
                SampleBuffer ob = (SampleBuffer) d.decodeFrame(h, bs);
                bs.closeFrame();
                short[] buf = ob.getBuffer();
                int len = ob.getBufferLength();
                int ch = ob.getChannelCount();
                if (ch == 2) {
                    for (int i = 0; i < len; i += 2) {
                        int m = (buf[i] + buf[i + 1]) / 2;
                        pcm.write(m & 0xFF); pcm.write((m >> 8) & 0xFF);
                    }
                } else {
                    for (int i = 0; i < len; i++) {
                        pcm.write(buf[i] & 0xFF); pcm.write((buf[i] >> 8) & 0xFF);
                    }
                }
                if (sr == 48000) sr = h.frequency();
            }
            bs.close();
            return new DecodeResult(pcm.toByteArray(), sr);
        }
    }
    private static byte[] stereoToMono(byte[] s) {
        short[] ss = new short[s.length / 2];
        for (int i = 0; i < ss.length; i++)
            ss[i] = (short) ((s[i * 2] & 0xFF) | (s[i * 2 + 1] << 8));
        int samples = ss.length / 2;
        byte[] m = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            int mix = (ss[i * 2] + ss[i * 2 + 1]) / 2;
            m[i * 2] = (byte) (mix & 0xFF);
            m[i * 2 + 1] = (byte) ((mix >> 8) & 0xFF);
        }
        return m;
    }
    private static short[] toShorts(byte[] b) {
        short[] s = new short[b.length / 2];
        for (int i = 0; i < s.length; i++)
            s[i] = (short) ((b[i * 2] & 0xFF) | (b[i * 2 + 1] << 8));
        return s;
    }
    private record DecodeResult(byte[] data, int sampleRate) {}
    private record MusicTrack(UUID playerId, int source, int buffer, float volume) {}
}
