package art.arcane.volmlib.nativelib.common.structure;

import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;

public final class StructureReaderFixture {
    private StructureReaderFixture() {
    }

    public static NativeStructureReader.Element element(Object handle) {
        return new ReflectiveStructureReader.ElementView(handle, null, ReflectiveStructureReader::readDefaultConnectors);
    }
}
