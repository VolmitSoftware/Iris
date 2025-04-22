package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an image map")
@Data
public class IrisPlacementExclusion {

    @Desc("Excludes the structure from all ocean biomes defined in region")
    private boolean excludeOceanBiomes;

    @Desc("Excludes the structure from all shore biomes defined in region")
    private boolean excludeShoreBiomes;

    @Desc("Excludes the structure from all land biomes defined in region")
    private boolean excludeLandBiomes;

    @RegistryListResource(IrisBiome.class)
    @Desc("tbd")
    @ArrayType(min = 1, type = String.class)
    private KList<String> excludeBiomes = new KList<>();

}
