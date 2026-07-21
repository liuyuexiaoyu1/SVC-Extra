package com.liuyue.svcextra.client.gui;
import com.liuyue.svcextra.SvcExtra;
import de.maxhenkel.voicechat.gui.widgets.DebouncedSlider;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
public class AbsoluteLoudnessSlider extends DebouncedSlider {
    public AbsoluteLoudnessSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                SvcExtra.CONFIG.client.absoluteLoudness);
        updateMessage();
        setTooltip(Tooltip.create(Component.literal(
                """
                        §7绝对响度模拟§r
                        基于反平方定律的物理距离衰减
                        0% = 关闭 (使用 SVC 默认音量)
                        100% = 完全物理衰减 (1/r²)""")));
    }
    @Override
    protected void updateMessage() {
        int pct = (int) Math.round(value * 100.0);
        setMessage(Component.literal("绝对响度: " + pct + "%"));
    }
    @Override
    public void applyDebounced() {
        SvcExtra.CONFIG.client.absoluteLoudness = (float) value;
        SvcExtra.CONFIG.save();
    }
}
