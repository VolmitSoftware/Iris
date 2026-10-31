package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.Arrays;

public record BinaryResource(String path, byte[] bytes) {
    public BinaryResource {
        VanillaResourceIdentifier.requirePath(path, "binary resource path");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BinaryResource resource && path.equals(resource.path) && Arrays.equals(bytes, resource.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * path.hashCode() + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "BinaryResource[path=" + path + ", bytes=" + bytes.length + "]";
    }
}
