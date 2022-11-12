package com.volmit.iris.util.matter;

import lombok.Data;

import java.util.Map;

@Data
public class MatterStructurePOI {

    public static final MatterStructurePOI BURIED_TREASURE = new MatterStructurePOI("buried_treasure");

    private static final MatterStructurePOI UNKNOWN = new MatterStructurePOI("unknown");
    private static final Map<String, MatterStructurePOI> VALUES = Map.of(
            "buried_treasure", BURIED_TREASURE
    );

    private final String type;

    public static MatterStructurePOI get(String id) {
        MatterStructurePOI poi = VALUES.get(id);
        return poi != null ? poi : new MatterStructurePOI(id);
    }
}
