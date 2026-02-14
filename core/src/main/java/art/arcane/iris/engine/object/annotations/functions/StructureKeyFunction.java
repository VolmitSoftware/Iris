package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.volmlib.util.collection.KList;

public class StructureKeyFunction implements ListFunction<KList<String>> {
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
        return INMS.get().getStructureKeys().removeWhere(t -> t.startsWith("#"));
    }
}
