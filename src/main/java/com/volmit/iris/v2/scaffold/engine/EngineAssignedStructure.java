package com.volmit.iris.v2.scaffold.engine;

public abstract class EngineAssignedStructure extends EngineAssignedComponent implements EngineStructure {
    public EngineAssignedStructure(Engine engine) {
        super(engine, "Structure");
    }
}
