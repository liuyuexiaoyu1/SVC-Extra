package com.liuyue.svcextra.client.gui;

import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.gui.widgets.DebouncedSlider;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class RayTraceIntervalSlider extends DebouncedSlider {

    private static final int MIN = 5, MAX = 3000;

    public RayTraceIntervalSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                (SvcExtra.CONFIG.client.rayTraceIntervalMs - MIN) / (double) (MAX - MIN));
        updateMessage();
        setTooltip(Tooltip.create(Component.literal(
                "射线重算间隔\n5ms = 最灵敏 (高CPU)\n3000ms = 最省 (响应慢)")));
    }

    @Override
    protected void updateMessage() {
        int ms = MIN + (int) Math.round(value * (MAX - MIN));
        setMessage(Component.literal("射线间隔: " + ms + "ms"));
    }

    @Override
    public void applyDebounced() {
        SvcExtra.CONFIG.client.rayTraceIntervalMs = MIN + (int) Math.round(value * (MAX - MIN));
        SvcExtra.CONFIG.save();
    }
}
