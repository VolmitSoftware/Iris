package art.arcane.iris.pack.datapack.export;

import java.nio.file.Path;
import java.util.Objects;

public record WorldgenExportRequest(
        Path pack,
        String dimension,
        String version,
        String mode,
        String namespace,
        String output,
        String format,
        boolean objects,
        boolean replace,
        Path exportRoot
) {
    public static final String DEFAULT_MODE = "overworld";
    public static final String DEFAULT_NAMESPACE = "iris_export";
    public static final String DEFAULT_FORMAT = "zip";

    public WorldgenExportRequest {
        if (pack == null) {
            throw new IllegalArgumentException("Worldgen export request requires a pack directory");
        }
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("Worldgen export request requires a dimension");
        }
        if (exportRoot == null) {
            throw new IllegalArgumentException("Worldgen export request requires an export root");
        }
        Objects.requireNonNull(version);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(output);
        Objects.requireNonNull(format);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path pack;
        private String dimension = "";
        private String version = "";
        private String mode = DEFAULT_MODE;
        private String namespace = DEFAULT_NAMESPACE;
        private String output = "";
        private String format = DEFAULT_FORMAT;
        private boolean objects = true;
        private boolean replace;
        private Path exportRoot;

        private Builder() {
        }

        public Builder pack(Path value) {
            pack = Objects.requireNonNull(value);
            return this;
        }

        public Builder dimension(String value) {
            dimension = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder version(String value) {
            version = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder mode(String value) {
            mode = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder namespace(String value) {
            namespace = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder output(String value) {
            output = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder format(String value) {
            format = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder objects(boolean value) {
            objects = value;
            return this;
        }

        public Builder replace(boolean value) {
            replace = value;
            return this;
        }

        public Builder exportRoot(Path value) {
            exportRoot = Objects.requireNonNull(value);
            return this;
        }

        public WorldgenExportRequest build() {
            return new WorldgenExportRequest(pack, dimension, version, mode, namespace, output, format, objects, replace, exportRoot);
        }
    }
}
