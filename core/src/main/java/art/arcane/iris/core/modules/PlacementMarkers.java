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

package art.arcane.iris.core.modules;

import com.volmit.config.Level;
import com.volmit.placement.PlacementOwner;
import com.volmit.placement.PlacementRecord;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The text Iris keeps beside a placed block in its mantle to say which placement the block belongs to: {@code key@id}
 * for an object placed on its own, and a delimiter-safe {@code @iris-structure:v1:...} form for a piece of a structure.
 * Either form may end in {@code #biome:...} or {@code #region:...}, naming the biome or region that placed the object.
 * Worlds already generated hold these texts, so the forms never change. A module hands Iris a {@link PlacementRecord},
 * and Iris reads one back from the text with the pack's keys and ids.
 */
public final class PlacementMarkers {
    private static final String FAMILY_PREFIX = "@iris-structure:";
    private static final String VERSION_PREFIX = FAMILY_PREFIX + "v1:";
    private static final char PLACEMENT_SEPARATOR = '@';
    private static final char OWNER_SEPARATOR = '#';
    private static final char OWNER_LEVEL_SEPARATOR = ':';

    private PlacementMarkers() {
    }

    /** The text of an object placed on its own. */
    public static String encode(String objectKey, int placementId) {
        return requireKey(objectKey, "objectKey") + PLACEMENT_SEPARATOR + placementId;
    }

    /** The text of a piece of a structure. */
    public static String encodeStructure(String objectKey, int placementId, String structureKey) {
        String normalizedObjectKey = requireKey(objectKey, "objectKey");
        String normalizedStructureKey = requireKey(structureKey, "structureKey");
        return VERSION_PREFIX
                + encodeKey(normalizedObjectKey)
                + ":" + placementId
                + ":" + encodeKey(normalizedStructureKey);
    }

    /** The text of {@code record}, with the pack keys of its object, structure and owner. */
    public static String encode(PlacementRecord record, IrisPackResources resources) {
        return encodePlacement(record, resources) + record.owner().map(owner -> encodeOwner(owner, resources)).orElse("");
    }

    private static String encodePlacement(PlacementRecord record, IrisPackResources resources) {
        String objectKey = resources.keyOf(PackResources.OBJECTS, record.object());
        if (record.structure().isEmpty()) {
            return encode(objectKey, record.placementId());
        }
        return encodeStructure(objectKey, record.placementId(), resources.keyOf(record.structure().get()));
    }

    private static String encodeOwner(PlacementOwner owner, IrisPackResources resources) {
        String ownerKey = resources.keyOf(kindOf(owner.level()), owner.source());
        return OWNER_SEPARATOR + owner.level().name().toLowerCase(Locale.ROOT) + OWNER_LEVEL_SEPARATOR + encodeKey(ownerKey);
    }

    private static ResourceKind<?> kindOf(Level ownerLevel) {
        return ownerLevel == Level.REGION ? PackResources.REGIONS : PackResources.BIOMES;
    }

    /** What the text says, or empty for a text that names no placement. */
    public static Optional<PlacementRecord> recordOf(String marker, IrisPackResources resources) {
        Decoded decoded = decode(marker);
        if (decoded == null) {
            return Optional.empty();
        }
        ResourceId object = resources.idOf(decoded.objectKey());
        Optional<ResourceId> structure = decoded.structureAware()
                ? Optional.of(resources.idOf(decoded.structureKey()))
                : Optional.empty();
        Optional<PlacementOwner> owner = Optional.ofNullable(decoded.owner())
                .map(ownerKey -> new PlacementOwner(ownerKey.level(), resources.idOf(ownerKey.key())));
        return Optional.of(new PlacementRecord(object, decoded.placementId(), structure, owner));
    }

    public static Decoded decode(String marker) {
        if (marker == null || marker.isBlank()) {
            return null;
        }
        int ownerStart = marker.lastIndexOf(OWNER_SEPARATOR);
        OwnerKey owner = ownerStart > 0 ? decodeOwner(marker.substring(ownerStart + 1)) : null;
        Decoded placement = decodePlacement(owner == null ? marker : marker.substring(0, ownerStart));
        return placement == null || owner == null ? placement : placement.ownedBy(owner);
    }

    /** The owner a text's end names, or null when the end names none. */
    private static OwnerKey decodeOwner(String text) {
        int levelEnd = text.indexOf(OWNER_LEVEL_SEPARATOR);
        if (levelEnd <= 0) {
            return null;
        }
        try {
            Level level = Level.valueOf(text.substring(0, levelEnd).toUpperCase(Locale.ROOT));
            String key = decodeCanonicalKey(text.substring(levelEnd + 1));
            if (key.isBlank() || (level != Level.BIOME && level != Level.REGION)) {
                return null;
            }
            return new OwnerKey(level, key);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Decoded decodePlacement(String marker) {
        if (marker.startsWith(FAMILY_PREFIX)) {
            return decodeStructure(marker);
        }
        return decodeLegacy(marker);
    }

    private static Decoded decodeStructure(String marker) {
        if (!marker.startsWith(VERSION_PREFIX)) {
            return null;
        }
        String[] fields = marker.split(":", -1);
        if (fields.length != 5 || !fields[0].equals("@iris-structure") || !fields[1].equals("v1")) {
            return null;
        }
        try {
            String objectKey = decodeCanonicalKey(fields[2]);
            int placementId = Integer.parseInt(fields[3]);
            String structureKey = decodeCanonicalKey(fields[4]);
            if (objectKey.isBlank() || structureKey.isBlank()) {
                return null;
            }
            return new Decoded(objectKey, placementId, structureKey, null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Decoded decodeLegacy(String marker) {
        int separator = marker.indexOf(PLACEMENT_SEPARATOR);
        if (separator <= 0 || separator != marker.lastIndexOf(PLACEMENT_SEPARATOR) || separator == marker.length() - 1) {
            return null;
        }
        String objectKey = marker.substring(0, separator);
        if (objectKey.isEmpty() || objectKey.equals("null")) {
            return null;
        }
        try {
            return new Decoded(objectKey, Integer.parseInt(marker.substring(separator + 1)), null, null);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String requireKey(String key, String name) {
        String required = Objects.requireNonNull(key, name);
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }

    private static String encodeKey(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeCanonicalKey(String key) {
        String decoded = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
        if (!encodeKey(decoded).equals(key)) {
            throw new IllegalArgumentException("Non-canonical marker key");
        }
        return decoded;
    }

    /**
     * A placement text read as pack keys; {@code structureKey} is null for an object placed on its own, {@code owner}
     * null when the text names no biome or region that placed it.
     */
    public record Decoded(String objectKey, int placementId, String structureKey, OwnerKey owner) {
        public boolean structureAware() {
            return structureKey != null;
        }

        /** The object and placement as {@code key@id}, the form the block inspection tools show. */
        public String placementKey() {
            return objectKey + PLACEMENT_SEPARATOR + placementId;
        }

        Decoded ownedBy(OwnerKey placedBy) {
            return new Decoded(objectKey, placementId, structureKey, placedBy);
        }
    }

    /** The biome or region that placed an object, by its pack key; {@code level} is {@link Level#BIOME} or {@link Level#REGION}. */
    public record OwnerKey(Level level, String key) {
    }
}
