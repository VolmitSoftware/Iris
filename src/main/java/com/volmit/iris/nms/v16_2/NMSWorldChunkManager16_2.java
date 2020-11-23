package com.volmit.iris.nms.v16_2;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import net.minecraft.server.v1_16_R2.*;
import org.bukkit.block.Biome;

public class NMSWorldChunkManager16_2 extends WorldChunkManager {
    public static final Codec<NMSWorldChunkManager16_2> e = RecordCodecBuilder.create((var0) -> {
        return var0.group(Codec.LONG.fieldOf("seed").stable().forGetter((var0x) -> {
            return var0x.h;
        }), Codec.BOOL.optionalFieldOf("legacy_biome_init_layer", false, Lifecycle.stable()).forGetter((var0x) -> {
            return var0x.i;
        }), Codec.BOOL.fieldOf("large_biomes").orElse(false).stable().forGetter((var0x) -> {
            return var0x.j;
        }), RegistryLookupCodec.a(IRegistry.ay).forGetter((var0x) -> {
            return var0x.k;
        })).apply(var0, var0.stable((a,b,c,d) -> new NMSWorldChunkManager16_2(null, "", a, b, c, d)));
    });
    private final long h;
    private final boolean i;
    private final boolean j;
    private final EngineCompositeGenerator compound;
    private final IRegistry<BiomeBase> k;

    public NMSWorldChunkManager16_2(EngineCompositeGenerator compound, String wn, long var0, boolean var2, boolean var3, IRegistry<BiomeBase> var4) {
        super(compound.getAllBiomes(wn).convert((v)->  v.getDerivative().getKey().getKey()).stream().map((var1) -> {
            return () -> {
                return (BiomeBase)var4.d(ResourceKey.a(IRegistry.ay, new MinecraftKey(var1)));
            };
        }));
        this.compound = compound;
        this.h = var0;
        this.i = var2;
        this.j = var3;
        this.k = var4;
    }

    protected Codec<? extends WorldChunkManager> a() {
        return e;
    }

    public BiomeBase getBiome(int var0, int var1, int var2) {
        try
        {
            return (BiomeBase) INMS.get().getBiomeBase(k, compound.getComposite().getDefaultEngine().getSurfaceBiome(var0, var2).getVanillaDerivative());
        }

        catch(Throwable e)
        {
            return (BiomeBase) INMS.get().getBiomeBase(k, Biome.THE_VOID);
        }
    }
}
