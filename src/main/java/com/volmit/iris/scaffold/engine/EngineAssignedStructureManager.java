package com.volmit.iris.scaffold.engine;

public abstract class EngineAssignedStructureManager extends EngineAssignedComponent implements EngineStructureManager {
    public EngineAssignedStructureManager(Engine engine) {
        super(engine, "Structure");
    }
}
