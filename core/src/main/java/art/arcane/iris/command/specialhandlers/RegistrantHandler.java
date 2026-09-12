package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.command.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public abstract class RegistrantHandler<T extends IrisRegistrant> implements DirectorParameterHandler<T> {
    private final Class<T> type;
    private final String name;
    private final boolean nullable;

    public RegistrantHandler(Class<T> type, boolean nullable) {
        this.type = type;
        this.name = type.getSimpleName().replaceFirst("Iris", "");
        this.nullable = nullable;
    }

    @Override
    public KList<T> getPossibilities() {
        KList<T> p = new KList<>();
        Set<String> known = new HashSet<>();
        IrisData data = data();
        if (data != null) {
            for (T j : data.getLoader(type).loadAll(data.getLoader(type).getPossibleKeys())) {
                known.add(j.getLoadKey());
                p.add(j);
            }
        }

        for (File i : PackDirectoryResolver.listVisiblePackDirectories(
                IrisPlatforms.get().packsFolder())) {
            data = IrisData.get(i);
            for (T j : data.getLoader(type).loadAll(data.getLoader(type).getPossibleKeys())) {
                if (known.add(j.getLoadKey()))
                    p.add(j);
            }
        }

        return p;
    }

    @Override
    public String toString(T t) {
        return t != null ? t.getLoadKey() : "null";
    }

    @Override
    public T parse(String in, boolean force) throws DirectorParsingException {
        if (in.equals("null") && nullable) {
            return null;
        }
        KList<T> options = getPossibilities(in);
        if (options.isEmpty()) {
            throw new DirectorParsingException("Unable to find " + name + " \"" + in + "\"");
        }

        return options.stream()
                .filter((i) -> toString(i).equalsIgnoreCase(in))
                .findFirst()
                .orElseThrow(() -> new DirectorParsingException("Unable to filter which " + name + " \"" + in + "\""));
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(this.type);
    }
}
