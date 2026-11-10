package art.arcane.iris.core.structure.export;

import java.nio.file.Path;
import java.util.Objects;

public final class VanillaJigsawExportRequest {
    private final VanillaJigsawExportSource source;
    private final Path output;
    private final String namespace;
    private final String resourcePath;
    private final String description;
    private final VanillaJigsawExportFormat format;
    private final VanillaJigsawExportSettings settings;
    private final boolean replaceExisting;

    private VanillaJigsawExportRequest(Builder builder) {
        source = builder.source;
        output = builder.output.toAbsolutePath().normalize();
        namespace = builder.namespace;
        resourcePath = builder.resourcePath;
        description = builder.description;
        format = builder.format;
        settings = builder.settings;
        replaceExisting = builder.replaceExisting;
    }

    public static Builder builder(VanillaJigsawExportSource source, Path output) {
        return new Builder(source, output);
    }

    public VanillaJigsawExportSource source() {
        return source;
    }

    public Path output() {
        return output;
    }

    public String namespace() {
        return namespace;
    }

    public String resourcePath() {
        return resourcePath;
    }

    public String description() {
        return description;
    }

    public VanillaJigsawExportFormat format() {
        return format;
    }

    public VanillaJigsawExportSettings settings() {
        return settings;
    }

    public boolean replaceExisting() {
        return replaceExisting;
    }

    public static final class Builder {
        private final VanillaJigsawExportSource source;
        private final Path output;
        private String namespace = "iris";
        private String resourcePath;
        private String description = "Iris vanilla jigsaw export for Minecraft 26.2";
        private VanillaJigsawExportFormat format = VanillaJigsawExportFormat.DIRECTORY;
        private VanillaJigsawExportSettings settings = VanillaJigsawExportSettings.defaults();
        private boolean replaceExisting;

        private Builder(VanillaJigsawExportSource source, Path output) {
            this.source = Objects.requireNonNull(source);
            this.output = Objects.requireNonNull(output);
            resourcePath = source.structureKey();
        }

        public Builder namespace(String value) {
            namespace = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder resourcePath(String value) {
            resourcePath = Objects.requireNonNull(value).trim();
            return this;
        }

        public Builder description(String value) {
            description = Objects.requireNonNull(value);
            return this;
        }

        public Builder format(VanillaJigsawExportFormat value) {
            format = Objects.requireNonNull(value);
            return this;
        }

        public Builder settings(VanillaJigsawExportSettings value) {
            settings = Objects.requireNonNull(value);
            return this;
        }

        public Builder replaceExisting(boolean value) {
            replaceExisting = value;
            return this;
        }

        public VanillaJigsawExportRequest build() {
            return new VanillaJigsawExportRequest(this);
        }
    }
}
