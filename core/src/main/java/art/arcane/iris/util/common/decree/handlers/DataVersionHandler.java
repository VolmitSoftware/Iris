package art.arcane.iris.util.decree.handlers;

import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.decree.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class DataVersionHandler implements DirectorParameterHandler<DataVersion> {
    @Override
    public KList<DataVersion> getPossibilities() {
        return new KList<>(DataVersion.values()).qdel(DataVersion.UNSUPPORTED);
    }

    @Override
    public String toString(DataVersion version) {
        return version.getVersion();
    }

    @Override
    public DataVersion parse(String in, boolean force) throws DirectorParsingException {
        if (in.equalsIgnoreCase("latest")) {
            return DataVersion.getLatest();
        }
        for (DataVersion v : DataVersion.values()) {
            if (v.getVersion().equalsIgnoreCase(in)) {
                return v;
            }
        }
        throw new DirectorParsingException("Unable to parse data version \"" + in + "\"");
    }

    @Override
    public boolean supports(Class<?> type) {
        return DataVersion.class.equals(type);
    }
}
