package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.command.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public final class IrisStructureHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        Set<String> keys = new LinkedHashSet<>();
        IrisData activeData = data();
        if (activeData != null) {
            addStructureKeys(keys, activeData);
        }
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(
                IrisPlatforms.get().packsFolder())) {
            addStructureKeys(keys, IrisData.get(pack));
        }
        return new KList<>(keys);
    }

    @Override
    public String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String parse(String input, boolean force) throws DirectorParsingException {
        for (String option : getPossibilities(input)) {
            if (option.equalsIgnoreCase(input)) {
                return option;
            }
        }
        throw new DirectorParsingException("Unable to find Iris structure \"" + input + "\"");
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == String.class;
    }

    private static void addStructureKeys(Set<String> keys, IrisData data) {
        for (String key : data.getStructureLoader().getPossibleKeys()) {
            keys.add(key);
        }
    }
}
