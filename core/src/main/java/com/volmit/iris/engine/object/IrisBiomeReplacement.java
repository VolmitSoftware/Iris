package com.volmit.iris.engine.object;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.bukkit.block.Biome;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class IrisBiomeReplacement extends IrisBiomeCustom {
    private Biome biome;
}
