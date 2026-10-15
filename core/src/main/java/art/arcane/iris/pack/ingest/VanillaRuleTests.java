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

package art.arcane.iris.pack.ingest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

final class VanillaRuleTests {
    private static final Map<String, List<String>> BLOCK_TAGS = Map.of(
            "minecraft:stone_ore_replaceables", List.of("minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite"),
            "minecraft:deepslate_ore_replaceables", List.of("minecraft:deepslate"),
            "minecraft:height_specific_ore_replaceables", List.of("minecraft:tuff"),
            "minecraft:base_stone_overworld", List.of("minecraft:stone", "minecraft:granite", "minecraft:diorite",
                    "minecraft:andesite", "minecraft:tuff", "minecraft:deepslate"),
            "minecraft:base_stone_nether", List.of("minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone"));

    private VanillaRuleTests() {
    }

    static HostSet hosts(JsonElement ruleTest, Map<String, List<String>> blockTags) throws IngestConversionException {
        JsonObject object = VanillaJson.object(ruleTest, "ore target predicate");
        String type = VanillaJson.type(object, "predicate_type");
        return switch (type) {
            case "minecraft:always_true" -> HostSet.ANY;
            case "minecraft:tag_match" -> tag(VanillaJson.namespaced(VanillaJson.string(object.get("tag"), "tag_match.tag")), blockTags);
            case "minecraft:block_match" -> HostSet.of(Set.of(VanillaJson.namespaced(VanillaJson.string(object.get("block"), "block_match.block"))));
            case "minecraft:blockstate_match" -> HostSet.of(Set.of(VanillaBlockStates.irisBlock(object.get("block_state")).get("block").getAsString()));
            case "minecraft:any_of" -> anyOf(rules(object), blockTags);
            case "minecraft:all_of" -> allOf(rules(object), blockTags);
            default -> throw new IngestConversionException("unsupported ore target predicate " + (type.isEmpty() ? object.toString() : type));
        };
    }

    private static HostSet tag(String tag, Map<String, List<String>> blockTags) throws IngestConversionException {
        List<String> blocks = blockTags.containsKey(tag) ? blockTags.get(tag) : BLOCK_TAGS.get(tag);
        if (blocks == null) {
            throw new IngestConversionException("ore target uses block tag " + tag + " whose members the catalog did not capture");
        }
        return HostSet.of(Set.copyOf(blocks));
    }

    private static HostSet anyOf(List<JsonElement> rules, Map<String, List<String>> blockTags) throws IngestConversionException {
        SortedSet<String> union = new TreeSet<>();
        for (JsonElement rule : rules) {
            HostSet hosts = hosts(rule, blockTags);
            if (hosts.any()) {
                return HostSet.ANY;
            }
            union.addAll(hosts.blocks());
        }
        return HostSet.of(union);
    }

    private static HostSet allOf(List<JsonElement> rules, Map<String, List<String>> blockTags) throws IngestConversionException {
        HostSet positive = null;
        SortedSet<String> negative = new TreeSet<>();
        for (JsonElement rule : rules) {
            JsonObject ruleObject = VanillaJson.object(rule, "all_of rule");
            String type = VanillaJson.type(ruleObject, "predicate_type");
            if (type.equals("minecraft:height_match")) {
                continue;
            }
            if (type.equals("minecraft:not")) {
                HostSet excluded = hosts(ruleObject.get("rule"), blockTags);
                if (excluded.any()) {
                    throw new IngestConversionException("ore target predicate never matches");
                }
                negative.addAll(excluded.blocks());
                continue;
            }
            positive = intersect(positive, hosts(ruleObject, blockTags));
        }
        if (positive == null) {
            throw new IngestConversionException("ore target all_of has no block constraint");
        }
        if (positive.any()) {
            if (!negative.isEmpty()) {
                throw new IngestConversionException("ore target excludes blocks from an unrestricted host set");
            }
            return HostSet.ANY;
        }
        SortedSet<String> result = new TreeSet<>(positive.blocks());
        result.removeAll(negative);
        return HostSet.of(result);
    }

    private static HostSet intersect(HostSet current, HostSet next) {
        if (current == null || current.any()) {
            return next;
        }
        if (next.any()) {
            return current;
        }
        SortedSet<String> result = new TreeSet<>(current.blocks());
        result.retainAll(next.blocks());
        return HostSet.of(result);
    }

    private static List<JsonElement> rules(JsonObject object) throws IngestConversionException {
        JsonElement value = object.has("rules") ? object.get("rules") : object.get("predicates");
        JsonArray array = VanillaJson.array(value, "ore target predicate rules");
        List<JsonElement> rules = new ArrayList<>(array.size());
        for (JsonElement rule : array) {
            rules.add(rule);
        }
        return rules;
    }

    record HostSet(boolean any, SortedSet<String> blocks) {
        static final HostSet ANY = new HostSet(true, Collections.emptySortedSet());

        static HostSet of(Set<String> blocks) {
            return new HostSet(false, Collections.unmodifiableSortedSet(new TreeSet<>(blocks)));
        }
    }
}
