package com.volmit.iris.util.matter.slices.container;

import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.Position2;
import org.jetbrains.annotations.Unmodifiable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JigsawStructuresContainer {
    private final Map<String, List<Position2>> map = new KMap<>();

    public JigsawStructuresContainer() {}

    public JigsawStructuresContainer(DataInputStream din) throws IOException {
        int s0 = din.readInt();
        for (int i = 0; i < s0; i++) {
            int s1 = din.readInt();
            KList<Position2> list = new KList<>(s1);
            for (int j = 0; j < s1; j++) {
                list.add(new Position2(din.readInt(), din.readInt()));
            }
            map.put(din.readUTF(), list);
        }
    }

    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(map.size());
        for (String key : map.keySet()) {
            List<Position2> list = map.get(key);
            dos.writeInt(list.size());
            for (Position2 pos : list) {
                dos.writeInt(pos.getX());
                dos.writeInt(pos.getZ());
            }
            dos.writeUTF(key);
        }
    }

    @Unmodifiable
    public Set<String> getStructures() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Unmodifiable
    public List<Position2> getPositions(String structure) {
        return Collections.unmodifiableList(map.get(structure));
    }

    @ChunkCoordinates
    public void add(IrisJigsawStructure structure, Position2 pos) {
        map.computeIfAbsent(structure.getLoadKey(), k -> new KList<>()).add(pos);
    }
}
