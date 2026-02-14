package art.arcane.iris.util.decree.handlers;

import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

public class DataVersionHandler implements DecreeParameterHandler<DataVersion> {
    @Override
    public KList<DataVersion> getPossibilities() {
        return new KList<>(DataVersion.values()).qdel(DataVersion.UNSUPPORTED);
    }

    @Override
    public String toString(DataVersion version) {
        return version.getVersion();
    }

    @Override
    public DataVersion parse(String in, boolean force) throws DecreeParsingException {
        if (in.equalsIgnoreCase("latest")) {
            return DataVersion.getLatest();
        }
        for (DataVersion v : DataVersion.values()) {
            if (v.getVersion().equalsIgnoreCase(in)) {
                return v;
            }
        }
        throw new DecreeParsingException("Unable to parse data version \"" + in + "\"");
    }

    @Override
    public boolean supports(Class<?> type) {
        return DataVersion.class.equals(type);
    }
}
