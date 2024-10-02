package com.volmit.iris.engine.object.annotations;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.ListFunction;
import com.volmit.iris.util.collection.KList;

public class StructureKeyFunction implements ListFunction<IrisData, KList<String>> {
    @Override
    public String key() {
        return "structure-key";
    }

    @Override
    public String fancyName() {
        return "Structure Key";
    }

    @Override
    public KList<String> apply(IrisData irisData) {
        return INMS.get().getStructureKeys();
    }
}
