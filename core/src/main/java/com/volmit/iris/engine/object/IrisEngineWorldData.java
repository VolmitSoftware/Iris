package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import lombok.Data;

@Data
public class IrisEngineWorldData {
    private int cooltest = 0;
    private String version = "null";

    public IrisEngineWorldData() {
        this.cooltest = 0;
        this.version = "null";
    }

    public void injection() {
        Iris.info("TEST");
        version = "test";
    }

    public boolean isEmpty() {
        return version.isEmpty();
    }
}

