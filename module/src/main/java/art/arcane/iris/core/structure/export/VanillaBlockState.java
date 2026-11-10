package art.arcane.iris.core.structure.export;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

record VanillaBlockState(String name, Map<String, String> properties) {
    private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PROPERTY_VALUE = Pattern.compile("[a-z0-9_.-]+");

    VanillaBlockState {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    static VanillaBlockState parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Block state must not be null");
        }
        String source = value.trim();
        int propertiesStart = source.indexOf('[');
        String name = propertiesStart < 0 ? source : source.substring(0, propertiesStart);
        if (!VanillaResourceIdentifier.validIdentifier(name)) {
            throw new IllegalArgumentException("Invalid block identifier '" + name + "'");
        }
        if (!name.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Vanilla cannot resolve non-Minecraft block '" + name + "'");
        }
        if (propertiesStart < 0) {
            return new VanillaBlockState(name, Map.of());
        }
        if (!source.endsWith("]") || source.indexOf('[', propertiesStart + 1) >= 0) {
            throw new IllegalArgumentException("Invalid property block in '" + value + "'");
        }
        String body = source.substring(propertiesStart + 1, source.length() - 1);
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Empty property block in '" + value + "'");
        }
        Map<String, String> properties = new TreeMap<>();
        for (String property : body.split(",", -1)) {
            int separator = property.indexOf('=');
            if (separator <= 0 || separator != property.lastIndexOf('=') || separator == property.length() - 1) {
                throw new IllegalArgumentException("Invalid block property '" + property + "'");
            }
            String propertyName = property.substring(0, separator);
            String propertyValue = property.substring(separator + 1);
            if (!PROPERTY_NAME.matcher(propertyName).matches() || !PROPERTY_VALUE.matcher(propertyValue).matches()) {
                throw new IllegalArgumentException("Invalid block property '" + property + "'");
            }
            if (properties.put(propertyName, propertyValue) != null) {
                throw new IllegalArgumentException("Duplicate block property '" + propertyName + "'");
            }
        }
        return new VanillaBlockState(name, properties);
    }

    String canonical() {
        if (properties.isEmpty()) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    CompoundTag toNbt() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        if (!properties.isEmpty()) {
            CompoundTag propertyTag = new CompoundTag();
            for (Map.Entry<String, String> property : properties.entrySet()) {
                propertyTag.putString(property.getKey(), property.getValue());
            }
            state.put("Properties", propertyTag);
        }
        return state;
    }
}
