package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.TreeType;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Sapling override settings")
@Data
public class IrisSapling {

    @Required
    @Desc("The types of saplings overwritten")
    @ArrayType(min = 1, type = TreeType.class)
    private KList<TreeType> types;

    @RegistryListObject
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("List of objects to overwrite saplings with")
    private KList<String> replace = new KList<>();

    @Desc("The size of the square of saplings this applies to (two means a 2 by 2 sapling area)")
    @MinNumber(1)
    @MaxNumber(4)
    private int size = 1;
}