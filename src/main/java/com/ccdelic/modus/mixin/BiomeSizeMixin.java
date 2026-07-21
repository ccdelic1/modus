package com.ccdelic.modus.mixin;

import com.ccdelic.modus.config.BiomeSizeConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Decoder;
import java.io.Reader;
import java.util.Set;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * {@link RegistryDataLoader#loadElementFromResource} is the single generic choke point every
 * registry entry from every data pack (vanilla's own, and every installed mod's) passes through
 * on its way from raw JSON to a decoded, typed value - this injects right after the JSON has
 * been parsed but before {@link Decoder#parse} turns it into a {@code NormalNoise.NoiseParameters}
 * record, and for exactly two resources - {@code minecraft:temperature} and
 * {@code minecraft:vegetation} under {@code worldgen/noise/} - rewrites the raw JSON's
 * {@code firstOctave} field before decoding continues.
 * <p>
 * Why just those two, and why this works with zero biome-specific or mod-specific code at all:
 * vanilla's climate sampler picks a biome for a given world position from five noise values
 * (temperature, humidity/vegetation, continentalness, erosion, weirdness) it samples from these
 * same shared, globally-registered noise fields - not per-biome, not per-dimension. Every mod
 * that adds biomes to the Overworld, whether through TerraBlender's region system or vanilla's
 * own multi-noise biome source directly, still has to go through that same shared sampler to
 * get temperature/vegetation values for a position; neither approach replaces the noise fields
 * themselves; both act on the same registry entries this mixin edits before anything's built
 * from them. So scaling temperature/vegetation's own effective spatial frequency scales every
 * biome's footprint together, vanilla and modded alike, without this mixin (or Modus generally)
 * needing to know anything about the specific mod adding a given biome. Continentalness and
 * erosion (which shape terrain/elevation more than biome variety) are deliberately left alone.
 */
@Mixin(RegistryDataLoader.class)
public class BiomeSizeMixin {
    private static final Set<ResourceLocation> SCALED_NOISE = Set.of(
        ResourceLocation.withDefaultNamespace("temperature"),
        ResourceLocation.withDefaultNamespace("vegetation")
    );

    @Inject(
        method = "loadElementFromResource",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
            remap = false
        ),
        locals = LocalCapture.CAPTURE_FAILEXCEPTION
    )
    private static <E> void modus$scaleBiomeNoise(
        WritableRegistry<E> registry,
        Decoder<E> decoder,
        RegistryOps<JsonElement> ops,
        ResourceKey<E> resourceKey,
        Resource resource,
        RegistrationInfo registrationInfo,
        CallbackInfo ci,
        Decoder capturedDecoder,
        Reader reader,
        JsonElement jsonElement
    ) {
        if (!BiomeSizeConfig.ENABLED.get()) {
            return;
        }
        if (!SCALED_NOISE.contains(resourceKey.location()) || !(jsonElement instanceof JsonObject json) || !json.has("firstOctave")) {
            return;
        }
        int modifier = BiomeSizeConfig.SIZE_MODIFIER.get();
        if (modifier == 0) {
            return;
        }
        json.addProperty("firstOctave", json.get("firstOctave").getAsInt() - modifier);
    }
}
