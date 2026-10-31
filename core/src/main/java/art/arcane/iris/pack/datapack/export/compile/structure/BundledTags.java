package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

final class BundledTags {
    private static final String BIOME_TAGS = "/tags/worldgen/biome/";
    private static final String JSON_SUFFIX = ".json";

    private BundledTags() {
    }

    static void write(PackCompileContext context, SortedMap<String, TagFile> tags, ExportedBiomes biomes) {
        List<JsonResource> resources = context.jsonResources();
        for (Map.Entry<String, TagFile> entry : tags.entrySet()) {
            String path = entry.getKey();
            int index = indexOf(resources, path);
            TagFile existing = index < 0 ? null : TagFile.parse(resources.get(index).json());
            JsonResource resource = new JsonResource(path, merge(path, entry.getValue(), existing, biomes).json());
            if (index < 0) {
                resources.add(resource);
            } else {
                resources.set(index, resource);
            }
        }
    }

    private static TagFile merge(String path, TagFile bundled, TagFile existing, ExportedBiomes biomes) {
        if (biomeTag(path)) {
            return joinBiomes(tagId(path), bundled, existing, biomes);
        }
        return existing == null ? bundled : bundled.then(existing);
    }

    private static TagFile joinBiomes(String tagId, TagFile bundled, TagFile existing, ExportedBiomes biomes) {
        Set<String> members = bundled.directMembers();
        List<JsonElement> joiners = new ArrayList<>();
        for (Map.Entry<String, IrisBiome> biome : biomes.byId().entrySet()) {
            String derivative = biome.getValue().getVanillaDerivativeKey();
            if (derivative != null && members.contains(TagFile.member(derivative))) {
                joiners.add(new JsonPrimitive(biome.getKey()));
            }
        }
        if (!bundled.replace()) {
            return (existing == null ? bundled : existing.then(bundled)).plus(joiners);
        }
        List<JsonElement> authored = new ArrayList<>();
        if (existing != null) {
            for (JsonElement value : existing.values()) {
                IrisBiome biome = value.isJsonPrimitive() ? biomes.byId().get(value.getAsString()) : null;
                if (biome != null && customTags(biome).contains(tagId)) {
                    authored.add(value);
                }
            }
        }
        return bundled.plus(joiners).plus(authored);
    }

    private static Set<String> customTags(IrisBiome biome) {
        SortedSet<String> tags = new TreeSet<>();
        if (!biome.isCustom()) {
            return tags;
        }
        IrisBiomeCustom custom = biome.getCustomDerivitives().getFirst();
        if (custom.getTags() == null) {
            return tags;
        }
        for (String tag : custom.getTags()) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            String normalized = TagFile.member(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed);
            if (VanillaResourceIdentifier.validIdentifier(normalized)) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    private static boolean biomeTag(String path) {
        int separator = path.indexOf('/', "data/".length());
        return separator > 0 && path.startsWith(BIOME_TAGS, separator);
    }

    private static String tagId(String path) {
        int separator = path.indexOf('/', "data/".length());
        String namespace = path.substring("data/".length(), separator);
        return namespace + ":" + path.substring(separator + BIOME_TAGS.length(), path.length() - JSON_SUFFIX.length());
    }

    private static int indexOf(List<JsonResource> resources, String path) {
        for (int index = 0; index < resources.size(); index++) {
            if (resources.get(index).path().equals(path)) {
                return index;
            }
        }
        return -1;
    }
}
