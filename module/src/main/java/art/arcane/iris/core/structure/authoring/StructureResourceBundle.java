/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.structure.authoring;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class StructureResourceBundle {
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?"
    );

    private final StructureKey key;
    private final StructureSource source;
    private final StructureBackend backend;
    private final Set<StructureCapability> capabilities;
    private final List<StructureLoss> losses;
    private final Map<String, Resource> resources;

    private StructureResourceBundle(Builder builder) {
        key = builder.key;
        source = Objects.requireNonNull(builder.source, "source");
        backend = Objects.requireNonNull(builder.backend, "backend");
        capabilities = immutableCapabilities(builder.capabilities);
        losses = List.copyOf(builder.losses);
        resources = Collections.unmodifiableMap(new TreeMap<>(builder.resources));
        if (resources.isEmpty()) {
            throw new IllegalStateException("Structure resource bundle cannot be empty");
        }
    }

    public static Builder builder(StructureKey key) {
        return new Builder(key);
    }

    public StructureKey key() {
        return key;
    }

    public StructureSource source() {
        return source;
    }

    public StructureBackend backend() {
        return backend;
    }

    public Set<StructureCapability> capabilities() {
        return capabilities;
    }

    public List<StructureLoss> losses() {
        return losses;
    }

    public Map<String, Resource> resources() {
        return resources;
    }

    public static String validateRelativePath(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.endsWith("/")) {
            throw new IllegalArgumentException("Resource path must be a non-empty relative path: " + relativePath);
        }
        if (relativePath.indexOf('\\') >= 0 || relativePath.indexOf(':') >= 0 || relativePath.contains("//")) {
            throw new IllegalArgumentException("Resource path is not portable: " + relativePath);
        }
        String[] segments = relativePath.split("/");
        if (segments[0].equalsIgnoreCase(".iris")) {
            throw new IllegalArgumentException("Resource path uses the reserved .iris directory: " + relativePath);
        }
        for (String segment : segments) {
            validatePathSegment(relativePath, segment);
        }
        return relativePath;
    }

    private static Set<StructureCapability> immutableCapabilities(EnumSet<StructureCapability> capabilities) {
        if (capabilities.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    private static void validatePathSegment(String relativePath, String segment) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(".") || segment.endsWith(" ")) {
            throw new IllegalArgumentException("Resource path is not portable: " + relativePath);
        }
        if (WINDOWS_RESERVED_NAME.matcher(segment).matches()) {
            throw new IllegalArgumentException("Resource path uses a reserved file name: " + relativePath);
        }
        for (int i = 0; i < segment.length(); i++) {
            char character = segment.charAt(i);
            if (Character.isISOControl(character) || character == '"' || character == '*' || character == '<'
                    || character == '>' || character == '?' || character == '|') {
                throw new IllegalArgumentException("Resource path is not portable: " + relativePath);
            }
        }
    }

    public static final class Builder {
        private final StructureKey key;
        private final EnumSet<StructureCapability> capabilities;
        private final List<StructureLoss> losses;
        private final Map<String, Resource> resources;
        private final Map<String, String> portableResourcePaths;
        private StructureSource source;
        private StructureBackend backend;

        private Builder(StructureKey key) {
            this.key = Objects.requireNonNull(key, "key");
            capabilities = EnumSet.noneOf(StructureCapability.class);
            losses = new ArrayList<>();
            resources = new TreeMap<>();
            portableResourcePaths = new HashMap<>();
        }

        public Builder source(StructureSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        public Builder backend(StructureBackend backend) {
            this.backend = Objects.requireNonNull(backend, "backend");
            return this;
        }

        public Builder capability(StructureCapability capability) {
            capabilities.add(Objects.requireNonNull(capability, "capability"));
            return this;
        }

        public Builder capabilities(Collection<StructureCapability> capabilities) {
            Objects.requireNonNull(capabilities, "capabilities");
            for (StructureCapability capability : capabilities) {
                capability(capability);
            }
            return this;
        }

        public Builder loss(StructureLoss loss) {
            losses.add(Objects.requireNonNull(loss, "loss"));
            return this;
        }

        public Builder losses(Collection<StructureLoss> losses) {
            Objects.requireNonNull(losses, "losses");
            for (StructureLoss loss : losses) {
                loss(loss);
            }
            return this;
        }

        public Builder resource(String relativePath, byte[] content) {
            Resource resource = new Resource(relativePath, content);
            String portablePath = resource.relativePath().toLowerCase(Locale.ROOT);
            String previousPath = portableResourcePaths.putIfAbsent(portablePath, resource.relativePath());
            if (previousPath != null) {
                throw new IllegalArgumentException("Duplicate structure resource: " + relativePath);
            }
            resources.put(resource.relativePath(), resource);
            return this;
        }

        public Builder textResource(String relativePath, String content) {
            Objects.requireNonNull(content, "content");
            return resource(relativePath, content.getBytes(StandardCharsets.UTF_8));
        }

        public StructureResourceBundle build() {
            return new StructureResourceBundle(this);
        }
    }

    public static final class Resource {
        private final String relativePath;
        private final byte[] content;
        private final String contentHash;

        private Resource(String relativePath, byte[] content) {
            this.relativePath = validateRelativePath(relativePath);
            Objects.requireNonNull(content, "content");
            this.content = content.clone();
            contentHash = StructureHash.sha256(this.content);
        }

        public String relativePath() {
            return relativePath;
        }

        public byte[] content() {
            return content.clone();
        }

        public int size() {
            return content.length;
        }

        public String contentHash() {
            return contentHash;
        }

        byte[] contentForWrite() {
            return content;
        }
    }
}
