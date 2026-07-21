package com.liuyue.svcextra.client.audio.rt;
import net.minecraft.world.level.block.SoundType;
import java.util.IdentityHashMap;
import static net.minecraft.world.level.block.SoundType.*;
public class BlockSoundProperty {
    public record AcousticProps(float absorption, float roughness, float hfGain) {}
    private static final IdentityHashMap<SoundType, AcousticProps> PROPS = new IdentityHashMap<>();
    static {
        put(STONE, 0.05f, 0.3f, 0.9f);
        put(WOOD, 0.15f, 0.6f, 0.7f);
        put(GRAVEL, 0.4f, 0.9f, 0.5f);
        put(GRASS, 0.5f, 0.8f, 0.4f);
        put(LILY_PAD, 0.5f, 0.8f, 0.4f);
        put(METAL, 0.02f, 0.2f, 0.95f);
        put(GLASS, 0.02f, 0.1f, 0.95f);
        put(WOOL, 0.7f, 0.7f, 0.2f);
        put(SAND, 0.5f, 0.8f, 0.3f);
        put(SNOW, 0.6f, 0.7f, 0.2f);
        put(POWDER_SNOW, 0.6f, 0.7f, 0.2f);
        put(CROP, 0.3f, 0.5f, 0.6f);
        put(STEM, 0.15f, 0.6f, 0.7f);
        put(VINE, 0.4f, 0.6f, 0.5f);
        put(NETHER_WART, 0.3f, 0.5f, 0.6f);
        put(ANVIL, 0.02f, 0.3f, 0.95f);
        put(SLIME_BLOCK, 0.3f, 0.4f, 0.6f);
        put(HONEY_BLOCK, 0.4f, 0.3f, 0.5f);
        put(NETHERITE_BLOCK, 0.02f, 0.1f, 0.98f);
        put(NETHER_GOLD_ORE, 0.02f, 0.2f, 0.95f);
        put(SOUL_SAND, 0.6f, 0.8f, 0.2f);
        put(SOUL_SOIL, 0.5f, 0.7f, 0.3f);
        put(BASALT, 0.05f, 0.4f, 0.85f);
        put(POLISHED_DEEPSLATE, 0.03f, 0.2f, 0.9f);
        put(CALCITE, 0.04f, 0.3f, 0.9f);
        put(DRIPSTONE_BLOCK, 0.06f, 0.5f, 0.85f);
        put(ROOTED_DIRT, 0.4f, 0.7f, 0.4f);
        put(MUD, 0.6f, 0.6f, 0.2f);
        put(MUD_BRICKS, 0.05f, 0.3f, 0.9f);
        put(MANGROVE_ROOTS, 0.3f, 0.6f, 0.5f);
        put(MUDDY_MANGROVE_ROOTS, 0.4f, 0.6f, 0.4f);
        put(PACKED_MUD, 0.3f, 0.5f, 0.6f);
        put(DECORATED_POT, 0.05f, 0.3f, 0.9f);
        put(BAMBOO, 0.2f, 0.5f, 0.7f);
        put(BAMBOO_WOOD, 0.15f, 0.6f, 0.7f);
        put(CHERRY_WOOD, 0.15f, 0.6f, 0.7f);
        put(CHERRY_SAPLING, 0.2f, 0.5f, 0.7f);
        put(CHISELED_BOOKSHELF, 0.2f, 0.5f, 0.7f);
        put(SUSPICIOUS_SAND, 0.4f, 0.7f, 0.4f);
        put(SUSPICIOUS_GRAVEL, 0.4f, 0.8f, 0.4f);
        put(COPPER, 0.02f, 0.2f, 0.95f);
        put(TUFF, 0.05f, 0.4f, 0.85f);
        put(DEEPSLATE, 0.05f, 0.4f, 0.85f);
        put(NETHERRACK, 0.1f, 0.5f, 0.8f);
        put(NETHER_BRICKS, 0.05f, 0.3f, 0.9f);
        put(WART_BLOCK, 0.3f, 0.5f, 0.6f);
        put(SCULK, 0.5f, 0.6f, 0.3f);
        put(SCULK_SENSOR, 0.4f, 0.5f, 0.4f);
        put(AMETHYST, 0.04f, 0.2f, 0.9f);
        put(COPPER_BULB, 0.02f, 0.2f, 0.95f);
        put(COPPER_GRATE, 0.05f, 0.3f, 0.9f);
    }
    private static void put(SoundType type, float absorption, float roughness, float hfGain) {
        PROPS.put(type, new AcousticProps(absorption, roughness, hfGain));
    }
    public static AcousticProps get(SoundType type) {
        return PROPS.getOrDefault(type, new AcousticProps(0.1f, 0.4f, 0.8f));
    }
}
