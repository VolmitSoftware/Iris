package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.engine.resolver.Resolvable;
import com.volmit.iris.platform.PlatformBiome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "biome")
public class IrisBiome extends EngineResolvable {
    private String name;

    @Builder.Default
    private IrisSurface surface = new IrisSurface();

    @Builder.Default
    private IrisRange height = IrisRange.flat(1);

    public PlatformBiome toPlatformBiome()
    {
        return null;
    }
}
