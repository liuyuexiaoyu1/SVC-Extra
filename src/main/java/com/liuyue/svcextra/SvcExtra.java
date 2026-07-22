package com.liuyue.svcextra;
import com.liuyue.svcextra.command.MusicCommand;
import com.liuyue.svcextra.config.SvcExtraConfig;
import com.liuyue.svcextra.network.PacketUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class SvcExtra implements ModInitializer {
    public static final String MOD_ID = "svc-extra";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static SvcExtraConfig CONFIG;
    @Override
    public void onInitialize() {
        CONFIG = SvcExtraConfig.load();
        PacketUtil.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            MusicCommand.register(dispatcher);
        });
    }
}
