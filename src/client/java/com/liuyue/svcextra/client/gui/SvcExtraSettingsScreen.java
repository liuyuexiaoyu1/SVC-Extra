package com.liuyue.svcextra.client.gui;
import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.audio.AudioPipeline;
import com.liuyue.svcextra.config.SvcExtraConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
public class SvcExtraSettingsScreen extends Screen {
    private static final Component TITLE = Component.literal("SVC Extra 音频设置");
    private final Screen parent;
    private static List<SvcExtraConfig.NoiseCancelMode> availableModes;
    public SvcExtraSettingsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }
    private static List<SvcExtraConfig.NoiseCancelMode> getModes() {
        if (availableModes != null) return availableModes;
        availableModes = new ArrayList<>();
        availableModes.add(SvcExtraConfig.NoiseCancelMode.OFF);
        if (AudioPipeline.isWebrtcAvailable()) {
            availableModes.add(SvcExtraConfig.NoiseCancelMode.WEBRTC_LOW);
            availableModes.add(SvcExtraConfig.NoiseCancelMode.WEBRTC_MEDIUM);
            availableModes.add(SvcExtraConfig.NoiseCancelMode.WEBRTC_HIGH);
            availableModes.add(SvcExtraConfig.NoiseCancelMode.WEBRTC_AGGRESSIVE);
        }
        if (AudioPipeline.isNvidiaAvailable()) {
            availableModes.add(SvcExtraConfig.NoiseCancelMode.NVIDIA_AI);
        }
        return availableModes;
    }
    @Override
    protected void init() {
        super.init();
        var modes = getModes();
        int cy = height / 2 - 80;
        boolean ncAvail = modes.size() > 1;
        var ncBtn = toggleBtn(cy, "降噪: " + cn(SvcExtra.CONFIG.client.noiseCancelMode), () -> {
            int idx = modes.indexOf(SvcExtra.CONFIG.client.noiseCancelMode);
            if (idx < 0) idx = 0;
            SvcExtra.CONFIG.client.noiseCancelMode = modes.get((idx + 1) % modes.size());
            SvcExtra.CONFIG.save();
            return "降噪: " + cn(SvcExtra.CONFIG.client.noiseCancelMode);
        });
        if (!ncAvail) {
            ncBtn.active = false;
            ncBtn.setMessage(Component.literal("降噪: 关 (无可用引擎)"));
        }
        ncBtn.setTooltip(Tooltip.create(Component.literal(
                """
                        §7降噪引擎§r
                        WebRTC: 通用 CPU 降噪, 全平台可用
                        NVIDIA AI: 仅 RTX 显卡 + AFX SDK""")));
        addRenderableWidget(ncBtn);
        cy += 24;
        addRenderableWidget(new DuckingSlider(cx(), cy, 200, 20));
        cy += 24;
        addRenderableWidget(new AbsoluteLoudnessSlider(cx(), cy, 200, 20));
        cy += 24;
        var hpfBtn = toggleBtn(cy, "高通滤波: " + onOff(SvcExtra.CONFIG.client.highPassFilter), () -> {
            SvcExtra.CONFIG.client.highPassFilter = !SvcExtra.CONFIG.client.highPassFilter;
            SvcExtra.CONFIG.save();
            return "高通滤波: " + onOff(SvcExtra.CONFIG.client.highPassFilter);
        });
        hpfBtn.setTooltip(Tooltip.create(Component.literal(
                """
                        §7高通滤波器§r
                        过滤 80Hz 以下低频噪音 (风声、空调声等)
                        建议保持开启""")));
        addRenderableWidget(hpfBtn);
        cy += 24;
        var agcBtn = toggleBtn(cy, "自动增益: " + onOff(SvcExtra.CONFIG.client.autoGainControl), () -> {
            SvcExtra.CONFIG.client.autoGainControl = !SvcExtra.CONFIG.client.autoGainControl;
            SvcExtra.CONFIG.save();
            return "自动增益: " + onOff(SvcExtra.CONFIG.client.autoGainControl);
        });
        agcBtn.setTooltip(Tooltip.create(Component.literal(
                """
                        §7自动增益控制§r
                        自动调整麦克风音量到合适水平
                        说话声小时自动放大, 声大时自动减小""")));
        addRenderableWidget(agcBtn);
        cy += 24;
        addRenderableWidget(new FrameLengthSlider(cx(), cy, 200, 20));
        cy += 24;
        addRenderableWidget(new ToleranceSlider(cx(), cy, 200, 20));
        cy += 24;
        var reverbBtnRef = new Button[1];
        var rayBtnRef = new Button[1];
        var reverbBtn = toggleBtn(cy, "路径混响: " + onOff(SvcExtra.CONFIG.client.rayTraceAudio), () -> {
            SvcExtra.CONFIG.client.rayTraceAudio = !SvcExtra.CONFIG.client.rayTraceAudio;
            SvcExtra.CONFIG.save();
            boolean on = SvcExtra.CONFIG.client.rayTraceAudio;
            if (rayBtnRef[0] != null) {
                rayBtnRef[0].active = on;
                if (!on) {
                    SvcExtra.CONFIG.client.showRayTrace = false;
                    SvcExtra.CONFIG.save();
                    rayBtnRef[0].setMessage(Component.literal("射线可视化: 关"));
                }
            }
            return "路径混响: " + onOff(on);
        });
        reverbBtnRef[0] = reverbBtn;
        reverbBtn.setTooltip(Tooltip.create(Component.literal(
                """
                        §7路径追踪混响§r
                        模拟 Channel 模组的射线追踪声学反射
                        需要 OpenAL EFX 支持
                        对性能有较大影响""")));
        addRenderableWidget(reverbBtn);
        cy += 24;
        var rayBtn = toggleBtn(cy, "射线可视化: " + onOff(SvcExtra.CONFIG.client.showRayTrace), () -> {
            SvcExtra.CONFIG.client.showRayTrace = !SvcExtra.CONFIG.client.showRayTrace;
            SvcExtra.CONFIG.save();
            return "射线可视化: " + onOff(SvcExtra.CONFIG.client.showRayTrace);
        });
        rayBtnRef[0] = rayBtn;
        rayBtn.setTooltip(Tooltip.create(Component.literal(
                """
                        §7射线可视化§r
                        在游戏中显示声音反射射线 (调试用)
                        仅路径混响开启时生效""")));
        if (!SvcExtra.CONFIG.client.rayTraceAudio) rayBtn.active = false;
        addRenderableWidget(rayBtn);
        cy += 30;
        addRenderableWidget(Button.builder(Component.literal("返回"),
                btn -> minecraft.gui.setScreen(parent)
        ).bounds(cx(), cy, 200, 20).build());
    }
    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        ctx.fillGradient(0, 0, width, height, 0x80000000, 0x80000000);
        ctx.text(font, TITLE, (width - font.width(TITLE)) / 2, height / 2 - 100, 0xFFFFFF);
        super.extractRenderState(ctx, mx, my, delta);
    }
    @Override
    public boolean shouldCloseOnEsc() { return true; }
    private int cx() { return width / 2 - 100; }
    private static String onOff(boolean v) { return v ? "开" : "关"; }
    private static String cn(SvcExtraConfig.NoiseCancelMode m) {
        return switch (m) {
            case OFF -> "关";
            case WEBRTC_LOW -> "WebRTC 弱";
            case WEBRTC_MEDIUM -> "WebRTC 中";
            case WEBRTC_HIGH -> "WebRTC 强";
            case WEBRTC_AGGRESSIVE -> "WebRTC 激进";
            case NVIDIA_AI -> "NVIDIA AI";
        };
    }
    private Button toggleBtn(int y, String label, Supplier<String> updater) {
        return Button.builder(Component.literal(label),
                btn -> btn.setMessage(Component.literal(updater.get()))
        ).bounds(cx(), y, 200, 20).build();
    }
}
