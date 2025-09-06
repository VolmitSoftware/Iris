package com.volmit.iris.engine.object.annotations.functions;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.ResourceLoader;
import com.volmit.iris.engine.framework.ListFunction;
import com.volmit.iris.util.collection.KList;

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
