package com.volmit.iris.v2.scaffold.engine;

public abstract class EngineAssignedStructureManager extends EngineAssignedComponent implements EngineStructureManager {
    public EngineAssignedStructureManager(Engine engine) {
        super(engine, "Structure");
    }
}
