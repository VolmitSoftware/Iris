package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.util.BlockVector;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisObjectTranslate {

    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @DontObfuscate
    @Desc("The x shift in blocks")
    private int x = 0;

    @Required
    @MinNumber(-256) // TODO: WARNING HEIGHT
    @MaxNumber(256) // TODO: WARNING HEIGHT
    @DontObfuscate
    @Desc("The x shift in blocks")
    private int y = 0;

    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @DontObfuscate
    @Desc("Adds an additional amount of height randomly (translateY + rand(0 - yRandom))")
    private int yRandom = 0;

    @MinNumber(-128) // TODO: WARNING HEIGHT
    @MaxNumber(128) // TODO: WARNING HEIGHT
    @DontObfuscate
    @Desc("The x shift in blocks")
    private int z = 0;

    public boolean canTranslate() {
        return x != 0 || y != 0 || z != 0;
    }

    public BlockVector translate(BlockVector i) {
        if (canTranslate()) {
            return (BlockVector) i.clone().add(new BlockVector(x, y, z));
        }

        return i;
    }

    public BlockVector translate(BlockVector clone, IrisObjectRotation rotation, int sx, int sy, int sz) {
        if (canTranslate()) {
            return (BlockVector) clone.clone().add(rotation.rotate(new BlockVector(x, y, z), sx, sy, sz));
        }

        return clone;
    }
}
