package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.JarScanner;
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
        JarScanner scanner = new JarScanner(IrisPlatforms.get().pluginJar(), "art.arcane.iris.engine.mantle.components");
        J.attempt(scanner::scan);
        return scanner.getClasses()
                .stream()
                .filter(c -> c.isAnnotationPresent(ComponentFlag.class))
                .filter(MantleComponent.class::isAssignableFrom)
                .map(c -> c.getDeclaredAnnotation(ComponentFlag.class))
                .filter(Objects::nonNull)
                .map(ComponentFlag::value)
                .map(MantleFlag::toString)
                .collect(KList.collector());
    }
}
