package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.exceptions.DecreeWhichException;

import java.io.File;

public class ObjectHandler implements DecreeParameterHandler<IrisObject> {
    @Override
    public KList<IrisObject> getPossibilities() {
        KMap<String, IrisObject> p = new KMap<>();

        //noinspection ConstantConditions
        for (File i : Iris.instance.getDataFolder("packs").listFiles()) {
            if (i.isDirectory()) {
                IrisData data = IrisData.get(i);
                for (IrisObject j : data.getObjectLoader().loadAll(data.getObjectLoader().getPossibleKeys())) {
                    p.putIfAbsent(j.getLoadKey(), j);
                }

                data.close();
            }
        }

        return p.v();
    }

    @Override
    public String toString(IrisObject irisObject) {
        return irisObject.getLoadKey();
    }

    @Override
    public IrisObject parse(String in) throws DecreeParsingException, DecreeWhichException {
        try {
            KList<IrisObject> options = getPossibilities(in);

            if (options.isEmpty()) {
                throw new DecreeParsingException("Unable to find Object \"" + in + "\"");
            } else if (options.size() > 1) {
                throw new DecreeWhichException();
            }

            return options.get(0);
        } catch (DecreeParsingException e) {
            throw e;
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to find Object \"" + in + "\" because of an uncaught exception: " + e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(IrisObject.class);
    }

    @Override
    public String getRandomDefault() {
        return "object";
    }
}
