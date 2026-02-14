package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.volmlib.util.collection.KList;

public class ResourceLoadersFunction implements ListFunction<KList<String>> {
    @Override
    public String key() {
        return "resource-loader";
    }

    @Override
    public String fancyName() {
        return "Resource Loader";
    }

    @Override
    public KList<String> apply(IrisData data) {
        return data.getLoaders()
                .values()
                .stream()
                .filter(rl -> ResourceLoader.class.equals(rl.getClass()))
                .map(ResourceLoader::getFolderName)
                .collect(KList.collector());
    }
}
