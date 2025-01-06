package com.volmit.iris.engine.object;


import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Static Placements")
@Data
public class IrisStaticPlacements {
    @Desc("List of static jigsaw structures")
    @ArrayType(type = IrisStaticStructurePlacement.class)
    private KList<IrisStaticStructurePlacement> structures = new KList<>();

    @Desc("List of static objects")
    @ArrayType(type = IrisStaticObjectPlacement.class)
    private KList<IrisStaticObjectPlacement> objects = new KList<>();

    @ChunkCoordinates
    public KList<IrisStaticStructurePlacement> getStructures(int chunkX, int chunkZ) {
        return filter(structures.stream(), chunkX, chunkZ);
    }

    @ChunkCoordinates
    public KList<IrisStaticObjectPlacement> getObjects(int chunkX, int chunkZ) {
        return filter(objects.stream(), chunkX, chunkZ);
    }

    @ChunkCoordinates
    public KList<IrisStaticPlacement> getAll(int chunkX, int chunkZ) {
        return filter(Stream.concat(structures.stream(), objects.stream()), chunkX, chunkZ);
    }

    private <T extends IrisStaticPlacement> KList<T> filter(Stream<T> stream, int chunkX, int chunkZ) {
        return stream.filter(p -> p.shouldPlace(chunkX, chunkZ))
                .collect(Collectors.toCollection(KList::new));
    }
}
