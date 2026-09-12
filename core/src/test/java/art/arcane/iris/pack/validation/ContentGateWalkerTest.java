package art.arcane.iris.pack.validation;

import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawn;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.world.entity.IrisEffect;
import art.arcane.iris.world.entity.IrisEnchantment;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectReplace;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.RegistryListItemType;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContentGateWalkerTest {
    private static ContentGate gate(String... missingBlocks) {
        return new ContentGate(CompatFixtures.registries(missingBlocks), Map.of(), new PackCompatReport());
    }

    private static ContentGate gate(CompatFixtures.FakeRegistries registries) {
        return new ContentGate(registries, Map.of(), new PackCompatReport());
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    private static IrisBiomePaletteLayer layer(String... blocks) {
        KList<IrisBlockData> palette = new KList<>();
        for (String block : blocks) {
            palette.add(new IrisBlockData(block));
        }
        return new IrisBiomePaletteLayer().setPalette(palette);
    }

    private static IrisLoot loot(String item) {
        return new IrisLoot().setType(item);
    }

    /** Reads a field directly; several registrant getters resolve through Bukkit registries that are absent here. */
    private static Object rawField(Object owner, String name) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static CompatFinding only(CompatStatus status) {
        assertEquals(status.reasons().toString(), 1, status.reasons().size());
        return status.reasons().getFirst();
    }

    private static void assertFinding(CompatFinding finding, CompatRegistry registry, String key, CompatAction action,
                                      String subjectType, String subjectKey, String detail) {
        assertEquals(registry, finding.registry());
        assertEquals(key, finding.key());
        assertEquals(action, finding.action());
        assertEquals(subjectType, finding.subjectType());
        assertEquals(subjectKey, finding.subjectKey());
        assertEquals(detail, finding.detail());
    }

    public static final class Probe extends IrisRegistrant {
        @RegistryListBlockType
        private String block = "minecraft:stone";
        @RegistryListItemType
        private KList<String> items = new KList<>();
        private KList<IrisObjectReplace> edit = new KList<>();
        private IrisObjectPlacement placement = null;
        private IrisLoot gear = null;
        private Probe self = null;
        private KList<Probe> children = new KList<>();
        private KList<IrisDecorator> decorators = new KList<>();

        @Override
        public String getFolderName() {
            return "probes";
        }

        @Override
        public String getTypeName() {
            return "Probe";
        }

        void setBlock(String block) {
            this.block = block;
        }

        KList<String> getItems() {
            return items;
        }

        void setItems(KList<String> items) {
            this.items = items;
        }

        void setEdit(KList<IrisObjectReplace> edit) {
            this.edit = edit;
        }

        void setPlacement(IrisObjectPlacement placement) {
            this.placement = placement;
        }

        IrisLoot getGear() {
            return gear;
        }

        void setGear(IrisLoot gear) {
            this.gear = gear;
        }

        void setSelf(Probe self) {
            this.self = self;
        }

        void setChildren(KList<Probe> children) {
            this.children = children;
        }

        void setDecorators(KList<IrisDecorator> decorators) {
            this.decorators = decorators;
        }
    }

    @Test
    public void missingLayerBlockExcludesBiomeWithFieldPath() {
        ContentGate gate = gate("minecraft:sulfur");
        IrisBiome biome = biome("cave/sulfur-grotto");
        biome.setLayers(new KList<>(List.of(layer("minecraft:stone"), layer("minecraft:stone", "minecraft:dirt", "minecraft:sulfur"))));

        CompatStatus status = gate.evaluate(biome);

        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "biome", "cave/sulfur-grotto", "layers[1].palette[2]");
        assertEquals(1, gate.report().size());
        assertEquals(status.reasons().getFirst(), gate.report().findings().getFirst());
    }

    @Test
    public void blockPropertiesTravelWithTheKey() {
        ContentGate gate = gate("minecraft:sulfur");
        IrisBiome biome = biome("b");
        IrisBlockData entry = new IrisBlockData("sulfur");
        entry.getData().put("lit", true);
        biome.setLayers(new KList<>(List.of(new IrisBiomePaletteLayer().setPalette(new KList<>(List.of(entry))))));

        CompatStatus status = gate.evaluate(biome);

        assertTrue(status.excluded());
        assertEquals("minecraft:sulfur", only(status).key());
    }

    @Test
    public void backupRevivesEntryAsSubstitution() {
        ContentGate gate = gate("minecraft:sulfur");
        IrisBiome biome = biome("desert/dunes");
        IrisBlockData entry = new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:sand"));
        biome.setLayers(new KList<>(List.of(new IrisBiomePaletteLayer().setPalette(new KList<>(List.of(entry))))));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.SUBSTITUTED, "biome", "desert/dunes", "layers[0].palette[0] (backup minecraft:sand)");
        assertEquals(1, gate.report().size());
    }

    @Test
    public void backupChainsAndMissingBackupStillExcludes() {
        ContentGate gate = gate("minecraft:sulfur", "minecraft:brimstone");
        IrisBiome chained = biome("chained");
        IrisBlockData deep = new IrisBlockData("minecraft:sulfur")
                .setBackup(new IrisBlockData("minecraft:brimstone").setBackup(new IrisBlockData("minecraft:sand")));
        chained.setLayers(new KList<>(List.of(new IrisBiomePaletteLayer().setPalette(new KList<>(List.of(deep))))));
        CompatStatus revived = gate.evaluate(chained);
        assertFalse(revived.excluded());
        assertEquals("layers[0].palette[0] (backup minecraft:sand)", only(revived).detail());

        IrisBiome dead = biome("dead");
        IrisBlockData deadEntry = new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:brimstone"));
        dead.setLayers(new KList<>(List.of(new IrisBiomePaletteLayer().setPalette(new KList<>(List.of(deadEntry))))));
        CompatStatus excluded = gate.evaluate(dead);
        assertTrue(excluded.excluded());
        assertFinding(only(excluded), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "biome", "dead", "layers[0].palette[0]");
    }

    @Test
    public void dimensionFallbackSubstitutesEntry() {
        ContentGate gate = new ContentGate(CompatFixtures.registries("minecraft:sulfur"), Map.of("minecraft:sulfur", "minecraft:stone"), new PackCompatReport());
        IrisBiome biome = biome("desert/flats");
        biome.setLayers(new KList<>(List.of(layer("minecraft:sulfur"))));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.SUBSTITUTED, "biome", "desert/flats", "layers[0].palette[0] (fallback minecraft:stone)");
    }

    @Test
    public void legacyRenameHitIsNeitherExcludedNorReported() {
        ContentGate gate = gate("minecraft:grass");
        IrisBiome biome = biome("plains");
        biome.setLayers(new KList<>(List.of(layer("minecraft:grass"))));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertTrue(status.reasons().isEmpty());
        assertTrue(gate.report().isEmpty());
    }

    @Test
    public void missingEntityTypeExcludesEntity() {
        ContentGate gate = gate();
        IrisEntity entity = new IrisEntity().setType("minecraft:camel");
        entity.setLoadKey("camel");

        CompatStatus status = gate.evaluate(entity);

        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.ENTITY, "minecraft:camel", CompatAction.EXCLUDED, "entity", "camel", "type");
        assertEquals("minecraft:camel", entity.getType());
    }

    @Test
    public void inlinePassengerWithMissingTypeIsDroppedFromItsList() {
        ContentGate gate = gate();
        IrisEntity entity = new IrisEntity().setType("minecraft:zombie");
        entity.setLoadKey("rider");
        entity.setPassengers(new KList<>(List.of(new IrisEntity().setType("minecraft:cow"), new IrisEntity().setType("minecraft:camel"))));

        CompatStatus status = gate.evaluate(entity);

        assertFalse(status.excluded());
        assertEquals(1, entity.getPassengers().size());
        assertEquals("minecraft:cow", entity.getPassengers().getFirst().getType());
        assertFinding(only(status), CompatRegistry.ENTITY, "minecraft:camel", CompatAction.DROPPED, "entity", "rider", "passengers[1].type");
    }

    @Test
    public void customBiomeSpawnIsDroppedBeforeDatapackWrite() {
        ContentGate gate = gate();
        IrisBiome biome = biome("custom/lush");
        IrisBiomeCustom custom = new IrisBiomeCustom().setId("lush");
        custom.setSpawns(new KList<>(List.of(new IrisBiomeCustomSpawn().setType("minecraft:cow"), new IrisBiomeCustomSpawn().setType("camel"))));
        biome.setCustomDerivitives(new KList<>(List.of(custom)));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertEquals(1, custom.getSpawns().size());
        assertEquals("minecraft:cow", custom.getSpawns().getFirst().getTypeKey());
        assertFinding(only(status), CompatRegistry.ENTITY, "minecraft:camel", CompatAction.DROPPED, "biome", "custom/lush", "customDerivitives[0].spawns[1].type");
    }

    @Test
    public void lootItemIsDroppedFromTable() {
        ContentGate gate = gate();
        IrisLootTable table = new IrisLootTable();
        table.setLoadKey("chests/common");
        table.setLoot(new KList<>(List.of(loot("minecraft:diamond"), loot("minecraft:mace"))));

        CompatStatus status = gate.evaluate(table);

        assertFalse(status.excluded());
        assertEquals(1, table.getLoot().size());
        assertEquals("minecraft:diamond", table.getLoot().getFirst().getTypeKey());
        assertFinding(only(status), CompatRegistry.ITEM, "minecraft:mace", CompatAction.DROPPED, "loot", "chests/common", "loot[1].type");
    }

    @Test
    public void missingEnchantmentIsDroppedButLootEntryStays() {
        ContentGate gate = gate();
        IrisLootTable table = new IrisLootTable();
        table.setLoadKey("chests/rare");
        IrisLoot sword = loot("minecraft:diamond");
        sword.setEnchantments(new KList<>(List.of(new IrisEnchantment().setEnchantment("sharpness"), new IrisEnchantment().setEnchantment("minecraft:wind_burst"))));
        table.setLoot(new KList<>(List.of(sword)));

        CompatStatus status = gate.evaluate(table);

        assertFalse(status.excluded());
        assertEquals(1, table.getLoot().size());
        assertEquals(1, sword.getEnchantments().size());
        assertEquals("sharpness", sword.getEnchantments().getFirst().getEnchantment());
        assertFinding(only(status), CompatRegistry.ENCHANTMENT, "minecraft:wind_burst", CompatAction.DROPPED, "loot", "chests/rare", "loot[0].enchantments[1].enchantment");
    }

    @Test
    public void missingPotionEffectDropsEffectAndBlankEffectStays() {
        ContentGate gate = gate();
        IrisBiome biome = biome("swamp");
        biome.setEffects(new KList<>(List.of(new IrisEffect().setPotionEffect("SPEED"), new IrisEffect().setPotionEffect("minecraft:infested"), new IrisEffect().setPotionEffect(""))));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertEquals(2, biome.getEffects().size());
        assertEquals("SPEED", biome.getEffects().get(0).getPotionEffect());
        assertEquals("", biome.getEffects().get(1).getPotionEffect());
        assertFinding(only(status), CompatRegistry.POTION_EFFECT, "minecraft:infested", CompatAction.DROPPED, "biome", "swamp", "effects[1].potionEffect");
    }

    @Test
    public void scatterEntriesAreDroppedAndBiomeKeepsGenerating() {
        ContentGate gate = gate();
        IrisBiome biome = biome("plains");
        biome.setBiomeScatter(new KList<>(List.of("minecraft:plains", "minecraft:pale_garden")));
        biome.setBiomeSkyScatter(new KList<>(List.of("minecraft:pale_garden", "minecraft:desert")));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertEquals(List.of("minecraft:plains"), biome.getBiomeScatter());
        assertEquals(List.of("minecraft:desert"), biome.getBiomeSkyScatter());
        assertEquals(2, status.reasons().size());
        assertFinding(status.reasons().get(0), CompatRegistry.BIOME, "minecraft:pale_garden", CompatAction.DROPPED, "biome", "plains", "biomeScatter[1]");
        assertFinding(status.reasons().get(1), CompatRegistry.BIOME, "minecraft:pale_garden", CompatAction.DROPPED, "biome", "plains", "biomeSkyScatter[0]");
        assertEquals(1, gate.report().size());
    }

    @Test
    public void missingDerivativeExcludesBiome() {
        ContentGate gate = gate();
        IrisBiome biome = biome("pale");
        biome.setDerivative("minecraft:pale_garden");

        CompatStatus status = gate.evaluate(biome);

        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.BIOME, "minecraft:pale_garden", CompatAction.EXCLUDED, "biome", "pale", "derivative");
        assertEquals("minecraft:pale_garden", biome.getDerivativeKey());
    }

    @Test
    public void missingVanillaDerivativeIsNulled() throws Exception {
        ContentGate gate = gate();
        IrisBiome biome = biome("pale");
        biome.setVanillaDerivative("minecraft:pale_garden");

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertNull(rawField(biome, "vanillaDerivative"));
        assertFinding(only(status), CompatRegistry.BIOME, "minecraft:pale_garden", CompatAction.DROPPED, "biome", "pale", "vanillaDerivative");
    }

    @Test
    public void objectPlacementsAreNotDescended() {
        ContentGate gate = gate("minecraft:sulfur");
        IrisBiome biome = biome("plains");
        IrisObjectReplace replace = new IrisObjectReplace()
                .setFind(new KList<>(List.of(new IrisBlockData("minecraft:stone"))))
                .setReplace(new IrisMaterialPalette().setPalette(new KList<>(List.of(new IrisBlockData("minecraft:sulfur")))));
        IrisObjectPlacement placement = new IrisObjectPlacement().setPlace(new KList<>(List.of("trees/oak"))).setEdit(new KList<>(List.of(replace)));
        biome.setObjects(new KList<>(List.of(placement)));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertTrue(status.reasons().isEmpty());
        assertTrue(gate.report().isEmpty());
        assertEquals(1, biome.getObjects().size());
        assertEquals(1, replace.getReplace().getPalette().size());
    }

    @Test
    public void replaceFindIsMatchOnlyButReplacePaletteCounts() {
        ContentGate gate = gate("minecraft:sulfur");
        Probe matchOnly = new Probe();
        matchOnly.setLoadKey("match");
        matchOnly.setEdit(new KList<>(List.of(new IrisObjectReplace()
                .setFind(new KList<>(List.of(new IrisBlockData("minecraft:sulfur"))))
                .setReplace(new IrisMaterialPalette().setPalette(new KList<>(List.of(new IrisBlockData("minecraft:stone"))))))));
        assertFalse(gate.evaluate(matchOnly).excluded());
        assertTrue(gate.report().isEmpty());

        Probe generated = new Probe();
        generated.setLoadKey("gen");
        generated.setEdit(new KList<>(List.of(new IrisObjectReplace()
                .setFind(new KList<>(List.of(new IrisBlockData("minecraft:stone"))))
                .setReplace(new IrisMaterialPalette().setPalette(new KList<>(List.of(new IrisBlockData("minecraft:sulfur"))))))));
        CompatStatus status = gate.evaluate(generated);
        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "probe", "gen", "edit[0].replace.palette[0]");
    }

    @Test
    public void decoratorMatchListsAreIgnoredButPaletteCounts() {
        ContentGate gate = gate("minecraft:sulfur");
        Probe whitelisted = new Probe();
        whitelisted.setLoadKey("white");
        whitelisted.setDecorators(new KList<>(List.of(new IrisDecorator()
                .setWhitelist(new KList<>(List.of(new IrisBlockData("minecraft:sulfur"))))
                .setBlacklist(new KList<>(List.of(new IrisBlockData("minecraft:sulfur")))))));
        assertFalse(gate.evaluate(whitelisted).excluded());
        assertTrue(gate.report().isEmpty());

        Probe placing = new Probe();
        placing.setLoadKey("place");
        placing.setDecorators(new KList<>(List.of(new IrisDecorator().setPalette(new KList<>(List.of(new IrisBlockData("minecraft:sulfur")))))));
        CompatStatus status = gate.evaluate(placing);
        assertTrue(status.excluded());
        assertEquals("decorators[0].palette[0]", only(status).detail());
    }

    @Test
    public void cyclicGraphsTerminate() {
        ContentGate gate = gate();
        Probe probe = new Probe();
        probe.setLoadKey("loop");
        probe.setSelf(probe);
        probe.setChildren(new KList<>(List.of(probe)));

        CompatStatus status = gate.evaluate(probe);

        assertFalse(status.excluded());
        assertTrue(status.reasons().isEmpty());
    }

    @Test
    public void notReadyProducesNoFindingsNoMutationAndMarksIncomplete() {
        ContentGate gate = new ContentGate(null, Map.of(), new PackCompatReport());
        IrisBiome biome = biome("custom/lush");
        IrisBiomeCustom custom = new IrisBiomeCustom().setId("lush");
        custom.setSpawns(new KList<>(List.of(new IrisBiomeCustomSpawn().setType("minecraft:camel"))));
        biome.setCustomDerivitives(new KList<>(List.of(custom)));
        biome.setLayers(new KList<>(List.of(layer("minecraft:sulfur"))));

        CompatStatus status = gate.evaluate(biome);

        assertFalse(status.excluded());
        assertTrue(status.reasons().isEmpty());
        assertEquals(1, custom.getSpawns().size());
        assertTrue(gate.report().isEmpty());
        assertTrue(gate.report().isIncomplete());
    }

    @Test
    public void unknownSingleRegistryLeavesItsKeysAlone() {
        CompatFixtures.FakeRegistries registries = CompatFixtures.registries("minecraft:sulfur");
        registries.biomes.clear();
        ContentGate gate = gate(registries);
        IrisBiome biome = biome("plains");
        biome.setDerivative("minecraft:pale_garden");
        biome.setBiomeScatter(new KList<>(List.of("minecraft:pale_garden")));
        biome.setLayers(new KList<>(List.of(layer("minecraft:sulfur"))));

        CompatStatus status = gate.evaluate(biome);

        assertTrue(status.excluded());
        assertEquals(1, status.reasons().size());
        assertEquals(CompatRegistry.BLOCK, only(status).registry());
        assertEquals(List.of("minecraft:pale_garden"), biome.getBiomeScatter());
        assertTrue(gate.report().isIncomplete());
        assertTrue(gate.report().incompleteReason(), gate.report().incompleteReason().contains("biome"));
    }

    @Test
    public void annotatedStringFieldsOnTheRegistrantFollowTheTable() {
        ContentGate gate = gate("minecraft:sulfur");
        Probe blocked = new Probe();
        blocked.setLoadKey("blocked");
        blocked.setBlock("minecraft:sulfur");
        CompatStatus status = gate.evaluate(blocked);
        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "probe", "blocked", "block");

        Probe items = new Probe();
        items.setLoadKey("items");
        items.setItems(new KList<>(List.of("minecraft:diamond", "minecraft:mace")));
        CompatStatus dropped = gate.evaluate(items);
        assertFalse(dropped.excluded());
        assertEquals(List.of("minecraft:diamond"), items.getItems());
        assertFinding(only(dropped), CompatRegistry.ITEM, "minecraft:mace", CompatAction.DROPPED, "probe", "items", "items[1]");
    }

    @Test
    public void droppedObjectHeldByDirectFieldIsNulled() {
        ContentGate gate = gate();
        Probe probe = new Probe();
        probe.setLoadKey("gear");
        probe.setGear(loot("minecraft:mace"));

        CompatStatus status = gate.evaluate(probe);

        assertFalse(status.excluded());
        assertNull(probe.getGear());
        assertFinding(only(status), CompatRegistry.ITEM, "minecraft:mace", CompatAction.DROPPED, "probe", "gear", "gear.type");
    }

    @Test
    public void everyFindingReachesReasonsAndReportDedups() {
        ContentGate gate = gate("minecraft:sulfur", "minecraft:brimstone");
        IrisBiome biome = biome("multi");
        biome.setLayers(new KList<>(List.of(layer("minecraft:sulfur", "minecraft:brimstone"), layer("minecraft:sulfur"))));

        CompatStatus status = gate.evaluate(biome);

        assertTrue(status.excluded());
        assertEquals(3, status.reasons().size());
        assertEquals(2, gate.report().size());
        assertEquals("layers[0].palette[0]", gate.report().byKey().get("BLOCK:minecraft:sulfur").getFirst().detail());
    }

    @Test
    public void customPackBlocksResolveThroughTheLoaderStatus() {
        IrisBlockData fine = new IrisBlockData("minecraft:stone");
        fine.setCompat(CompatStatus.OK);
        IrisBlockData broken = new IrisBlockData("minecraft:sulfur");
        broken.setCompat(CompatStatus.excludedBy(List.of(new CompatFinding(CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "block", "broken", "block"))));
        Function<String, IrisBlockData> customBlocks = key -> switch (key) {
            case "fancy" -> fine;
            case "broken" -> broken;
            default -> null;
        };
        ContentGate gate = new ContentGate(CompatFixtures.registries("minecraft:sulfur"), Map::of, new PackCompatReport(), customBlocks);

        IrisBiome ok = biome("ok");
        ok.setLayers(new KList<>(List.of(layer("fancy"))));
        assertFalse(gate.evaluate(ok).excluded());
        assertTrue(gate.report().isEmpty());

        IrisBiome bad = biome("bad");
        bad.setLayers(new KList<>(List.of(layer("broken"))));
        CompatStatus status = gate.evaluate(bad);
        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "biome", "bad", "layers[0].palette[0]");
    }

    @Test
    public void blockRegistrantEvaluatesItsOwnStateWithoutTheLoader() {
        Function<String, IrisBlockData> customBlocks = key -> {
            throw new IllegalStateException("loader must not be consulted for a block registrant");
        };
        ContentGate gate = new ContentGate(CompatFixtures.registries("minecraft:sulfur"), Map::of, new PackCompatReport(), customBlocks);
        IrisBlockData registrant = new IrisBlockData("minecraft:sulfur");
        registrant.setLoadKey("sulfur-variant");

        CompatStatus status = gate.evaluate(registrant);

        assertTrue(status.excluded());
        assertFinding(only(status), CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "block", "sulfur-variant", "block");

        IrisBlockData revived = new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:sand"));
        revived.setLoadKey("sulfur-backup");
        CompatStatus substituted = gate.evaluate(revived);
        assertFalse(substituted.excluded());
        assertEquals("block (backup minecraft:sand)", only(substituted).detail());
    }

    @Test
    public void nullRegistrantIsOk() {
        assertNotNull(gate().evaluate(null));
        assertFalse(gate().evaluate(null).excluded());
    }
}
