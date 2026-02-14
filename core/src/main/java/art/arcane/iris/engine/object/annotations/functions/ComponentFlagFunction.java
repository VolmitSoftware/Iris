package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;

import java.util.Objects;

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
        var engine = data.getEngine();
        if (engine != null) return engine.getMantle().getComponentFlags().toStringList();
        return Iris.getClasses("art.arcane.iris.engine.mantle.components", ComponentFlag.class)
                .stream()
                .filter(MantleComponent.class::isAssignableFrom)
                .map(c -> c.getDeclaredAnnotation(ComponentFlag.class))
                .filter(Objects::nonNull)
                .map(ComponentFlag::value)
                .map(MantleFlag::toString)
                .collect(KList.collector());
    }
}
