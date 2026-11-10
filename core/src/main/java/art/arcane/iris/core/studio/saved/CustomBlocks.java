/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.saved;

import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.link.CustomBlockSource;
import art.arcane.iris.spi.IrisServices;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Blocks added by plugins. They are saved as the vanilla block states they borrow, such as a tripwire for grass, so
 * what a saved block really is has to be looked up from the state it is saved as.
 */
final class CustomBlocks {
    static final CustomBlocks NONE = new CustomBlocks(List.of());
    private static final char PROPERTIES_START = '[';
    private static final String PROPERTY_SEPARATOR = ",";
    private static final String VALUE_SEPARATOR = "=";

    private final Map<String, String> namesByShownState = new HashMap<>();
    private final Map<String, String> shownBlocksByName = new HashMap<>();
    private final Set<String> shownBlocks = new HashSet<>();

    CustomBlocks(List<CustomBlockLook> looks) {
        for (CustomBlockLook look : looks) {
            String shownBlock = blockOf(look.shownAs());
            String name = look.id().toString();
            namesByShownState.putIfAbsent(keyOf(shownBlock, propertiesOf(look.shownAs())), name);
            shownBlocksByName.putIfAbsent(name, shownBlock);
            shownBlocks.add(shownBlock);
        }
    }

    /** The plugin blocks the server has now; empty on a platform that has none. */
    static CustomBlocks platform() {
        CustomBlockSource source = IrisServices.getOrNull(CustomBlockSource.class);
        return source == null ? NONE : new CustomBlocks(source.blocks());
    }

    /**
     * The name of the plugin block saved as this vanilla state, or the state's own block name when none is.
     *
     * @param properties the state's properties, only asked for when a plugin block borrows {@code blockName}
     */
    String nameSavedAs(String blockName, Supplier<Map<String, String>> properties) {
        if (!shownBlocks.contains(blockName)) {
            return blockName;
        }
        return namesByShownState.getOrDefault(keyOf(blockName, properties.get()), blockName);
    }

    /** The vanilla block the plugin block {@code name} borrows its shape from, or empty when {@code name} is not one. */
    Optional<String> shownBlockOf(String name) {
        return Optional.ofNullable(shownBlocksByName.get(name));
    }

    private static String blockOf(String state) {
        int propertiesStart = state.indexOf(PROPERTIES_START);
        return propertiesStart < 0 ? state : state.substring(0, propertiesStart);
    }

    private static Map<String, String> propertiesOf(String state) {
        int propertiesStart = state.indexOf(PROPERTIES_START);
        if (propertiesStart < 0) {
            return Map.of();
        }
        Map<String, String> properties = new HashMap<>();
        for (String property : state.substring(propertiesStart + 1, state.length() - 1).split(PROPERTY_SEPARATOR)) {
            String[] nameAndValue = property.split(VALUE_SEPARATOR, 2);
            if (nameAndValue.length == 2) {
                properties.put(nameAndValue[0], nameAndValue[1]);
            }
        }
        return properties;
    }

    private static String keyOf(String block, Map<String, String> properties) {
        return block + new TreeMap<>(properties).entrySet().stream()
                .map(property -> property.getKey() + VALUE_SEPARATOR + property.getValue())
                .collect(Collectors.joining(PROPERTY_SEPARATOR, String.valueOf(PROPERTIES_START), "]"));
    }
}
