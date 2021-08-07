package com.volmit.iris.engine.object.location;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.villager.IrisVillagerOverride;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@Desc("Settings for overriding /locate structure locations with iris objects")
public class IrisLocations {

    @Desc("Setting this to true disables /locate")
    private boolean preventLocate = true;

    @Desc("Cartographer map trade overrides")
    private IrisVillagerOverride patchCartographers = new IrisVillagerOverride().setDisableTrade(false);
}
