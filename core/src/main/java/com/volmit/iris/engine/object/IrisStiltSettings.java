package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("stilt-settings")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Defines stilting behaviour.")
@Data
public class IrisStiltSettings {
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Defines the maximum amount of blocks the object stilts verticially before overstilting and randomRange.")
    private int yMax;
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Defines the upper boundary for additional blocks after overstilting and/or maxStiltRange.")
    private int yRand;
    @MaxNumber(64)
    @MinNumber(0)
    @Desc("If the place mode is set to stilt, you can over-stilt it even further into the ground. Especially useful when using fast stilt due to inaccuracies.")
    private int overStilt;
    @Desc("If defined, stilting will be done using this block palette rather than the last layer of the object.")
    private IrisMaterialPalette palette;

}
