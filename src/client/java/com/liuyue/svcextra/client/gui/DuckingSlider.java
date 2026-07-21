package com.liuyue.svcextra.client.gui;
import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.gui.widgets.DebouncedSlider;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
public class DuckingSlider extends DebouncedSlider {
    public DuckingSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                SvcExtra.CONFIG.client.duckingLevel);
        updateMessage();
        setTooltip(Tooltip.create(Component.literal(
                """
                 §7语音闪避 (Ducking)§r
                 有人说话时自动降低游戏音量
                 """)));
    }
    @Override
    protected void updateMessage() {
        int pct = (int) Math.round(value * 100);
        if (pct <= 0) {
            setMessage(Component.literal("闪避: 关闭"));
        } else {
            setMessage(Component.literal("闪避: " + pct + "%"));
        }
    }
    @Override
    public void applyDebounced() {
        SvcExtra.CONFIG.client.duckingLevel = (float) Math.max(0, Math.min(1, value));
        SvcExtra.CONFIG.save();
    }
}
