package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.structure.object.IObjectLoot;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.world.entity.IrisEnchantment;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootMode;
import art.arcane.iris.world.loot.IrisLootReference;
import art.arcane.iris.world.loot.IrisLootTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class LootCompiler {
    private static final long WEIGHT_CEILING = 1_000_000L;
    private static final String TABLE_FOLDER = "iris/";
    private static final String BINDING_FOLDER = "object_loot/";
    private static final Set<String> CONTAINERS = Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:dispenser", "minecraft:dropper", "minecraft:hopper");
    private static final Set<String> DAMAGEABLE_SUFFIXES = Set.of("_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_helmet",
            "_chestplate", "_leggings", "_boots", "_spear", "bow", "trident", "shield", "fishing_rod", "shears",
            "flint_and_steel", "elytra", "mace", "_on_a_stick", "brush");

    private final String namespace;
    private final IrisData data;
    private final Map<String, JsonObject> tables = new TreeMap<>();
    private final Map<String, String> tableIds = new TreeMap<>();

    public LootCompiler(String namespace, IrisData data) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "loot namespace");
        this.data = data;
    }

    public static boolean container(VanillaBlockState state) {
        return CONTAINERS.contains(state.name()) || state.name().endsWith("shulker_box");
    }

    public Optional<String> binding(IrisObjectPlacement placement, String containerBlock, LootScope scope, List<ExportDiagnostic> diagnostics) {
        List<JsonObject> pools = new ArrayList<>();
        JsonObject objectPool = objectPool(placement, containerBlock, diagnostics);
        if (objectPool != null) {
            pools.add(objectPool);
        }
        if (objectPool == null || !placement.isOverrideGlobalLoot()) {
            boolean objectDefined = !placement.getLoot().isEmpty() || !placement.getVanillaLoot().isEmpty();
            for (IrisLootTable table : environment(scope, objectDefined)) {
                JsonObject pool = new JsonObject();
                pool.add("entries", single(reference(table, diagnostics)));
                pool.addProperty("rolls", 1);
                pools.add(pool);
            }
        }
        if (pools.isEmpty()) {
            return Optional.empty();
        }
        JsonObject binding = chestTable(pools);
        String id = namespace + ":" + BINDING_FOLDER + ContentIds.hash(binding);
        tables.put(id, binding);
        return Optional.of(id);
    }

    public JsonObject table(IrisLootTable table, List<ExportDiagnostic> diagnostics) {
        String resource = "loot/" + table.getLoadKey();
        List<IrisLoot> storage = new ArrayList<>();
        for (IrisLoot loot : table.getLoot()) {
            if (loot != null && loot.getSlotTypes() == InventorySlotType.STORAGE) {
                storage.add(loot);
            }
        }
        BigInteger common = BigInteger.ONE;
        for (IrisLoot loot : storage) {
            BigInteger rarity = BigInteger.valueOf(Math.max(1, loot.getRarity()));
            common = common.multiply(rarity).divide(common.gcd(rarity));
        }
        boolean exact = common.compareTo(BigInteger.valueOf(WEIGHT_CEILING)) <= 0;
        JsonArray entries = new JsonArray();
        for (IrisLoot loot : storage) {
            Optional<String> item = item(loot.getTypeKey());
            if (item.isEmpty()) {
                diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.CUSTOM_ITEM, resource,
                        "Loot item '" + loot.getTypeKey() + "' is not a Minecraft item and is not exported"));
                continue;
            }
            int rarity = Math.max(1, loot.getRarity());
            long weight = exact ? common.longValue() / rarity : Math.max(1, Math.round(WEIGHT_CEILING / (double) rarity));
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", item.get());
            entry.addProperty("weight", weight);
            List<JsonObject> functions = functions(loot, item.get());
            if (!functions.isEmpty()) {
                entry.add("modifier", list(functions));
            }
            entries.add(entry);
        }
        JsonObject pool = new JsonObject();
        if (table.getRarity() > 1) {
            JsonObject chance = condition("minecraft:random_chance");
            chance.addProperty("chance", 1.0f / table.getRarity());
            pool.add("condition", chance);
        }
        pool.add("entries", entries);
        pool.add("rolls", range(Math.min(table.getMinPicked(), table.getMaxPicked()), Math.max(table.getMinPicked(), table.getMaxPicked())));
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.LOOT_RARITY, resource,
                "Loot rarity exports as proportional entry weight"));
        return chestTable(List.of(pool));
    }

    JsonObject appendLoot(Map<VanillaBlockState, String> bindings, FeatureJson json) {
        JsonArray rules = new JsonArray(bindings.size());
        for (Map.Entry<VanillaBlockState, String> binding : bindings.entrySet()) {
            JsonObject modifier = new JsonObject();
            modifier.addProperty("type", "minecraft:append_loot");
            modifier.addProperty("loot_table", binding.getValue());
            JsonObject input = new JsonObject();
            input.addProperty("predicate_type", "minecraft:blockstate_match");
            input.add("block_state", json.syntax().blockState(binding.getKey()));
            JsonObject location = new JsonObject();
            location.addProperty("predicate_type", "minecraft:always_true");
            JsonObject rule = new JsonObject();
            rule.add("block_entity_modifier", modifier);
            rule.add("input_predicate", input);
            rule.add("location_predicate", location);
            rule.add("output_state", json.syntax().blockState(binding.getKey()));
            rules.add(rule);
        }
        JsonObject processor = new JsonObject();
        processor.addProperty("processor_type", "minecraft:rule");
        processor.add("rules", rules);
        return processor;
    }

    public List<JsonResource> resources() {
        List<JsonResource> resources = new ArrayList<>(tables.size());
        for (Map.Entry<String, JsonObject> table : tables.entrySet()) {
            String path = table.getKey().substring(namespace.length() + 1);
            resources.add(new JsonResource("data/" + namespace + "/loot_table/" + path + ".json", table.getValue()));
        }
        return resources;
    }

    private JsonObject objectPool(IrisObjectPlacement placement, String containerBlock, List<ExportDiagnostic> diagnostics) {
        List<JsonObject> global = new ArrayList<>();
        List<JsonObject> filtered = new ArrayList<>();
        List<IObjectLoot> sources = new ArrayList<>();
        sources.addAll(placement.getVanillaLoot());
        sources.addAll(placement.getLoot());
        int vanillaCount = placement.getVanillaLoot().size();
        for (int index = 0; index < sources.size(); index++) {
            IObjectLoot loot = sources.get(index);
            if (loot == null || loot.getWeight() <= 0 || loot.getName() == null) {
                continue;
            }
            String id;
            if (index < vanillaCount) {
                id = normalizeId(loot.getName()).orElse(null);
            } else {
                IrisLootTable table = data == null ? null : data.getLootLoader().load(loot.getName(), false);
                id = table == null ? null : reference(table, diagnostics);
            }
            if (id == null) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:loot_table");
            entry.addProperty("value", id);
            entry.addProperty("weight", loot.getWeight());
            if (loot.getFilter().isEmpty()) {
                global.add(entry);
            } else if (filterMatches(loot, containerBlock)) {
                filtered.add(entry);
            }
        }
        List<JsonObject> chosen = filtered.isEmpty() ? global : filtered;
        if (chosen.isEmpty()) {
            return null;
        }
        JsonObject pool = new JsonObject();
        pool.add("entries", list(chosen));
        pool.addProperty("rolls", 1);
        return pool;
    }

    private List<IrisLootTable> environment(LootScope scope, boolean objectDefined) {
        List<IrisLootTable> sources = new ArrayList<>();
        double multiplier = 1;
        List<IrisLootReference> references = new ArrayList<>(3);
        if (scope.dimension() != null) {
            references.add(scope.dimension().getLoot());
        }
        if (scope.region() != null) {
            references.add(scope.region().getLoot());
        }
        if (scope.biome() != null) {
            references.add(scope.biome().getLoot());
        }
        for (IrisLootReference reference : references) {
            if (reference == null) {
                continue;
            }
            multiplier *= reference.getMultiplier();
            inject(sources, load(reference), reference.getMode(), !objectDefined);
        }
        int target = (int) Math.round(sources.size() * multiplier);
        if (target == sources.size() || sources.isEmpty()) {
            return sources;
        }
        List<IrisLootTable> scaled = new ArrayList<>(Math.max(0, target));
        for (int index = 0; index < target; index++) {
            scaled.add(sources.get(index % sources.size()));
        }
        return scaled;
    }

    private static void inject(List<IrisLootTable> sources, List<IrisLootTable> additions, IrisLootMode mode, boolean fallback) {
        if (mode == IrisLootMode.FALLBACK && !fallback) {
            return;
        }
        if (mode == IrisLootMode.CLEAR) {
            sources.clear();
            return;
        }
        if (mode == IrisLootMode.REPLACE) {
            sources.clear();
        }
        sources.addAll(additions);
    }

    private List<IrisLootTable> load(IrisLootReference reference) {
        List<IrisLootTable> loaded = new ArrayList<>();
        if (data == null || reference.getMode() == IrisLootMode.CLEAR) {
            return loaded;
        }
        for (String key : reference.getTables()) {
            IrisLootTable table = key == null ? null : data.getLootLoader().load(key, false);
            if (table != null) {
                loaded.add(table);
            }
        }
        return loaded;
    }

    private String reference(IrisLootTable table, List<ExportDiagnostic> diagnostics) {
        String key = table.getLoadKey();
        String existing = tableIds.get(key);
        if (existing != null) {
            return existing;
        }
        String id = namespace + ":" + TABLE_FOLDER + VanillaResourceIdentifier.sanitizePath(key);
        tableIds.put(key, id);
        tables.put(id, table(table, diagnostics));
        return id;
    }

    private static boolean filterMatches(IObjectLoot loot, String containerBlock) {
        for (IrisBlockData filter : loot.getFilter()) {
            String state = ContentGate.normalizeState(filter.stateKey());
            if (state != null && ContentGate.baseKey(state).equals(containerBlock)) {
                return true;
            }
        }
        return false;
    }

    private List<JsonObject> functions(IrisLoot loot, String item) {
        List<JsonObject> functions = new ArrayList<>();
        int minAmount = Math.min(loot.getMinAmount(), loot.getMaxAmount());
        int maxAmount = Math.max(loot.getMinAmount(), loot.getMaxAmount());
        if (minAmount != 1 || maxAmount != 1) {
            JsonObject count = function("minecraft:set_count");
            count.add("count", range(minAmount, maxAmount));
            functions.add(count);
        }
        if (damageable(item) && (loot.getMinDurability() != 1 || loot.getMaxDurability() != 1)) {
            JsonObject damage = function("minecraft:set_damage");
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "minecraft:uniform");
            provider.addProperty("max", (float) clampUnit(Math.max(loot.getMinDurability(), loot.getMaxDurability())));
            provider.addProperty("min", (float) clampUnit(Math.min(loot.getMinDurability(), loot.getMaxDurability())));
            damage.add("damage", provider);
            functions.add(damage);
        }
        if (loot.getDisplayName() != null && !loot.getDisplayName().isBlank()) {
            JsonObject name = function("minecraft:set_name");
            name.addProperty("name", loot.getDisplayName());
            name.addProperty("target", "item_name");
            functions.add(name);
        }
        if (!loot.getLore().isEmpty()) {
            JsonObject lore = function("minecraft:set_lore");
            JsonArray lines = new JsonArray(loot.getLore().size());
            for (String line : loot.getLore()) {
                lines.add(line);
            }
            lore.add("lore", lines);
            lore.addProperty("mode", "replace_all");
            functions.add(lore);
        }
        for (IrisEnchantment enchantment : loot.getEnchantments()) {
            Optional<String> id = enchantment == null ? Optional.empty() : normalizeId(enchantment.getEnchantment());
            if (id.isEmpty()) {
                continue;
            }
            JsonObject enchant = function("minecraft:set_enchantments");
            JsonObject levels = new JsonObject();
            levels.add(id.get(), range(Math.min(enchantment.getMinLevel(), enchantment.getMaxLevel()),
                    Math.max(enchantment.getMinLevel(), enchantment.getMaxLevel())));
            enchant.add("enchantments", levels);
            enchant.addProperty("add", true);
            if (enchantment.getChance() < 1) {
                JsonObject chance = condition("minecraft:random_chance");
                chance.addProperty("chance", (float) Math.max(0, enchantment.getChance()));
                enchant.add("condition", chance);
            }
            functions.add(enchant);
        }
        return functions;
    }

    private JsonObject function(String type) {
        JsonObject function = new JsonObject();
        function.addProperty("type", type);
        return function;
    }

    private JsonObject condition(String type) {
        JsonObject condition = new JsonObject();
        condition.addProperty("type", type);
        return condition;
    }

    private static JsonObject chestTable(List<JsonObject> pools) {
        JsonObject table = new JsonObject();
        table.add("pools", list(pools));
        table.addProperty("type", "minecraft:chest");
        return table;
    }

    private static JsonArray single(String id) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:loot_table");
        entry.addProperty("value", id);
        return list(List.of(entry));
    }

    private static JsonElement range(int min, int max) {
        if (min == max) {
            return new JsonPrimitive(min);
        }
        JsonObject range = new JsonObject();
        range.addProperty("type", "minecraft:uniform");
        range.addProperty("max", max);
        range.addProperty("min", min);
        return range;
    }

    private static JsonArray list(List<JsonObject> values) {
        JsonArray array = new JsonArray(values.size());
        for (JsonObject value : values) {
            array.add(value);
        }
        return array;
    }

    private static Optional<String> item(String type) {
        Optional<String> id = normalizeId(type);
        return id.isPresent() && id.get().startsWith("minecraft:") ? id : Optional.empty();
    }

    private static Optional<String> normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        String id = lowered.indexOf(':') < 0 ? "minecraft:" + lowered : lowered;
        return VanillaResourceIdentifier.validIdentifier(id) ? Optional.of(id) : Optional.empty();
    }

    private static boolean damageable(String item) {
        for (String suffix : DAMAGEABLE_SUFFIXES) {
            if (item.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static double clampUnit(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record LootScope(IrisDimension dimension, IrisRegion region, IrisBiome biome) {
    }
}
