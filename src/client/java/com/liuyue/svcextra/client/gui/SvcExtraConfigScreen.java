package com.liuyue.svcextra.client.gui;

import com.liuyue.svcextra.SvcExtra;
import com.liuyue.svcextra.audio.AudioPipeline;
import com.liuyue.svcextra.config.SvcExtraConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SvcExtraConfigScreen {
    public static Screen create(Screen parent) {
        var config = SvcExtra.CONFIG;
        var builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("svcextra.config.title"))
                .setSavingRunnable(config::save);

        var eb = builder.entryBuilder();

        var general = builder.getOrCreateCategory(Component.translatable("svcextra.category.general"));

        general.addEntry(eb.startEnumSelector(
                Component.translatable("svcextra.option.noiseCancelMode"),
                SvcExtraConfig.NoiseCancelMode.class,
                config.client.noiseCancelMode
        ).setDefaultValue(SvcExtraConfig.NoiseCancelMode.OFF)
         .setEnumNameProvider(v -> Component.translatable("svcextra.enum.noiseCancelMode." + v.name()))
         .setTooltip(Component.translatable("svcextra.tooltip.noiseCancelMode"))
         .setSaveConsumer(v -> config.client.noiseCancelMode = v)
         .build());

        general.addEntry(eb.startBooleanToggle(
                Component.translatable("svcextra.option.highPassFilter"),
                config.client.highPassFilter
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("svcextra.tooltip.highPassFilter"))
         .setSaveConsumer(v -> config.client.highPassFilter = v)
         .build());

        general.addEntry(eb.startBooleanToggle(
                Component.translatable("svcextra.option.autoGainControl"),
                config.client.autoGainControl
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("svcextra.tooltip.autoGainControl"))
         .setSaveConsumer(v -> {
             config.client.autoGainControl = v;
             AudioPipeline.resetAgc();
         })
         .build());

        general.addEntry(eb.startBooleanToggle(
                Component.translatable("svcextra.option.echoCancel"),
                config.client.echoCancel
        ).setDefaultValue(false)
         .setTooltip(Component.translatable("svcextra.tooltip.echoCancel"))
         .setSaveConsumer(v -> {
             config.client.echoCancel = v;
             if (v) AudioPipeline.startAec();
             else AudioPipeline.stopAec();
         })
         .build());

        general.addEntry(eb.startIntSlider(
                Component.translatable("svcextra.option.aecDelayMs"),
                config.client.aecDelayMs,
                50, 500
        ).setDefaultValue(120)
         .setTextGetter(v -> Component.literal(v + "ms"))
         .setTooltip(Component.translatable("svcextra.tooltip.aecDelayMs"))
         .setSaveConsumer(v -> {
             config.client.aecDelayMs = v;
             AudioPipeline.updateAecDelay(v);
         })
         .build());

        var reverbCat = builder.getOrCreateCategory(Component.translatable("svcextra.category.reverb"));

        reverbCat.addEntry(eb.startBooleanToggle(
                Component.translatable("svcextra.option.rayTraceAudio"),
                config.client.rayTraceAudio
        ).setDefaultValue(false)
         .setTooltip(Component.translatable("svcextra.tooltip.rayTraceAudio"))
         .setSaveConsumer(v -> config.client.rayTraceAudio = v)
         .build());

        reverbCat.addEntry(eb.startIntSlider(
                Component.translatable("svcextra.option.rayTraceIntervalMs"),
                config.client.rayTraceIntervalMs,
                5, 3000
        ).setDefaultValue(500)
         .setTextGetter(v -> Component.literal(v + "ms"))
         .setTooltip(Component.translatable("svcextra.tooltip.rayTraceIntervalMs"))
         .setSaveConsumer(v -> config.client.rayTraceIntervalMs = v)
         .build());

        reverbCat.addEntry(eb.startBooleanToggle(
                Component.translatable("svcextra.option.showRayTrace"),
                config.client.showRayTrace
        ).setDefaultValue(false)
         .setTooltip(Component.translatable("svcextra.tooltip.showRayTrace"))
         .setSaveConsumer(v -> config.client.showRayTrace = v)
         .build());

        reverbCat.addEntry(eb.startIntSlider(
                Component.translatable("svcextra.option.absoluteLoudness"),
                Math.round(config.client.absoluteLoudness * 100),
                0, 100
        ).setDefaultValue(0)
         .setTextGetter(v -> Component.literal(v + "%"))
         .setTooltip(Component.translatable("svcextra.tooltip.absoluteLoudness"))
         .setSaveConsumer(v -> config.client.absoluteLoudness = v / 100f)
         .build());

        var advCat = builder.getOrCreateCategory(Component.translatable("svcextra.category.advanced"));

        advCat.addEntry(eb.startIntSlider(
                Component.translatable("svcextra.option.duckingLevel"),
                Math.round(config.client.duckingLevel * 100),
                0, 100
        ).setDefaultValue(30)
         .setTextGetter(v -> Component.literal(v + "%"))
         .setTooltip(Component.translatable("svcextra.tooltip.duckingLevel"))
         .setSaveConsumer(v -> config.client.duckingLevel = v / 100f)
         .build());

        advCat.addEntry(eb.startIntSlider(
                Component.translatable("svcextra.option.frameLengthMs"),
                config.client.frameLengthMs,
                10, 50
        ).setDefaultValue(20)
         .setTextGetter(v -> Component.literal(v + "ms"))
         .setTooltip(Component.translatable("svcextra.tooltip.frameLengthMs"))
         .setSaveConsumer(v -> config.client.frameLengthMs = v)
         .build());

        advCat.addEntry(eb.startIntSlider(
                Component.translatable("svcextra.option.networkTolerance"),
                config.client.networkTolerance,
                100, 500
        ).setDefaultValue(200)
         .setTextGetter(v -> Component.literal(v + "ms"))
         .setTooltip(Component.translatable("svcextra.tooltip.networkTolerance"))
         .setSaveConsumer(v -> config.client.networkTolerance = v)
         .build());

        return builder.build();
    }
}
