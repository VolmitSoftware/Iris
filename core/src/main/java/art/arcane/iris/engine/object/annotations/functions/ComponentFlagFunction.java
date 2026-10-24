package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.MantleStageFlags;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;

/**
 * What studio offers for a dimension's {@code disabledComponents}: the flags of Iris's own mantle stages, such as
 * {@code CARVED} for caves, then the ids of the pack's mantle stages.
 */
public class ComponentFlagFunction implements ListFunction<KList<String>> {
    @Override
    public String key() {
        return "component-flag";
    }

    @Override
    public String fancyName() {
        return "Component Flag";
    }

    @Override
    public KList<String> apply(IrisData data) {
        KList<String> names = MantleStageFlags.builtIn().stream().map(MantleFlag::toString).collect(KList.collector());
        names.addAll(MantleStageFlags.packStageIds(data.getModules().pack().mantle().stages()));
        return names;
    }
}
