package com.liuyue.svcextra.client.mixin;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("gainBySource")
    Object2FloatMap<SoundSource> gainBySource();
}
