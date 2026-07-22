package com.liuyue.svcextra.client.gui;
import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.gui.widgets.DebouncedSlider;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
public class FrameLengthSlider extends DebouncedSlider {
    private static final int MIN = 10, MAX = 60;
    public FrameLengthSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                (SvcExtra.CONFIG.client.frameLengthMs - MIN) / (double) (MAX - MIN));
        updateMessage();
        setTooltip(Tooltip.create(Component.literal(
                """
                        §7麦克风采集间隔§r
                        10ms = 最低延迟, 60ms = 最省资源
                        WebRTC 降噪内部按 10ms 处理, 不受此值影响""")));
    }
    @Override
    protected void updateMessage() {
        int ms = MIN + (int) Math.round(value * (MAX - MIN));
        setMessage(Component.literal("帧长: " + ms + "ms"));
    }
    @Override
    public void applyDebounced() {
        SvcExtra.CONFIG.client.frameLengthMs = MIN + (int) Math.round(value * (MAX - MIN));
        SvcExtra.CONFIG.save();
    }
}
