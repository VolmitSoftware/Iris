package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.MantleStageFlags;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.volmlib.util.collection.KList;

/**
 * What studio offers for a dimension's {@code disabledComponents}: the ids of the pack's mantle stages, such as
 * {@code iris:carve/caves}.
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
        return new KList<>(MantleStageFlags.stageIds(data.getModules().pack().mantle().stages()));
    }
}
