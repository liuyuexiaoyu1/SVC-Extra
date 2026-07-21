package com.liuyue.svcextra.client.gui;
import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.gui.widgets.DebouncedSlider;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
public class ToleranceSlider extends DebouncedSlider {
    private static final int MIN = 50, MAX = 500;
    public ToleranceSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                (SvcExtra.CONFIG.client.networkTolerance - MIN) / (double) (MAX - MIN));
        updateMessage();
        setTooltip(Tooltip.create(Component.literal(
                """
                        §7网络抖动容忍度§r
                        值越大越不容易卡顿, 但延迟更高
                        网络不稳定时可适当调高""")));
    }
    @Override
    protected void updateMessage() {
        int ms = MIN + (int) Math.round(value * (MAX - MIN));
        setMessage(Component.literal("语音容忍度: " + ms + "ms"));
    }
    @Override
    public void applyDebounced() {
        int ms = MIN + (int) Math.round(value * (MAX - MIN));
        SvcExtra.CONFIG.client.networkTolerance = ms;
        SvcExtra.CONFIG.save();
    }
}
