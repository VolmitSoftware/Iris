package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("jigsaw-structure-distance")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents the min distance between jigsaw structure placements")
@Data
public class IrisJigsawDistance {
    @Required
    @RegistryListResource(IrisJigsawStructure.class)
    @Desc("The structure to check against")
    private String structure;

    @Required
    @MinNumber(0)
    @MaxNumber(5000)
    @Desc("The min distance in blocks to a placed structure\nWARNING: The performance impact scales exponentially!")
    private int distance;
}
