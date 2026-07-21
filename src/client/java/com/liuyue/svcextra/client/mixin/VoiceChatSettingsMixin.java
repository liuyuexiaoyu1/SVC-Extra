package com.liuyue.svcextra.client.mixin;
import com.liuyue.svcextra.client.gui.SvcExtraSettingsScreen;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(VoiceChatSettingsScreen.class)
public class VoiceChatSettingsMixin extends VoiceChatScreenBase {
    protected VoiceChatSettingsMixin(Component title, int xSize, int ySize) {
        super(title, xSize, ySize);
    }
    @Unique
    private static final Component SVC_BTN = Component.literal("§6§lSVC Extra §r");
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void addSvcExtraButton(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean inGame = mc.level != null;
        int y = guiTop + (inGame ? 209 : 230);
        addRenderableWidget(Button.builder(SVC_BTN,
                _ -> mc.gui.setScreen(new SvcExtraSettingsScreen((VoiceChatSettingsScreen) (Object) this))
        ).bounds(guiLeft + 10, y, xSize - 20, 20).build());
    }
}
