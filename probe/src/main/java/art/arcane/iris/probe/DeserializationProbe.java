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

package art.arcane.iris.probe;

import art.arcane.iris.engine.loot.LootResources;
import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.EntitySpawn;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DeserializationProbe {
    private static final String FIXTURE_RESOURCE = "deserialization-pack";
    private static final String ENTITY_KEY = "probe_entity";
    private static final String SPAWNER_KEY = "probe_spawner";
    private static final String LOOT_KEY = "probe_loot";
    private static final List<Throwable> REPORTED = Collections.synchronizedList(new ArrayList<>());

    @FunctionalInterface
    private interface ProbeStep {
        void run() throws Exception;
    }

    private static final class InertPreservation implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }

    public static void main(String[] args) throws Exception {
        IrisPlatforms.bind(new StubPlatform());
        StubPlatform.verbose(false);
        StubPlatform.errorSink(REPORTED::add);
        IrisServices.register(PreservationRegistry.class, new InertPreservation());

        boolean bukkitPresent = bukkitPresent();
        System.out.println("[deserprobe] org.bukkit on classpath: " + bukkitPresent);
        if (bukkitPresent) {
            System.out.println("[deserprobe] FAIL: org.bukkit is on the probe runtime classpath; this gate only proves the Bukkit-free deserialization contract when Bukkit is absent.");
            System.exit(1);
            return;
        }

        File fixtureSource = resolveFixture(args);
        if (fixtureSource == null || !fixtureSource.isDirectory()) {
            System.out.println("[deserprobe] FAIL: fixture pack folder not found"
                    + (fixtureSource == null ? "" : ": " + fixtureSource.getAbsolutePath()));
            System.exit(1);
            return;
        }

        File workRoot = Files.createTempDirectory("iris-deserprobe").toFile();
        File pack = clonePack(fixtureSource, workRoot);
        System.out.println("[deserprobe] fixture: " + fixtureSource.getAbsolutePath());
        System.out.println("[deserprobe] work copy: " + pack.getAbsolutePath());

        IrisData data;
        try {
            data = IrisData.get(pack);
        } catch (Throwable e) {
            System.out.println("[deserprobe] FAIL: IrisData loader construction threw before any deserialization");
            e.printStackTrace(System.out);
            printReported(drain());
            System.exit(1);
            return;
        }

        int failures = 0;
        failures += run("entity<" + ENTITY_KEY + ">", () -> assertEntity(data));
        failures += run("spawner<" + SPAWNER_KEY + ">", () -> assertSpawner(data));
        failures += run("loot<" + LOOT_KEY + ">", () -> assertLoot(data));

        List<Throwable> residual = drain();
        if (!residual.isEmpty()) {
            System.out.println("[deserprobe] FAIL: " + residual.size() + " residual error(s) reported after deserialization:");
            printReported(residual);
            failures += residual.size();
        }

        System.out.println("[deserprobe] RESULT: " + (failures == 0 ? "PASS" : "FAIL"));
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void assertEntity(IrisData data) {
        EntityDefinition entity = SpawnResources.entity(data, ENTITY_KEY);
        require(entity != null, "entity deserialized");
        require("minecraft:panda".equals(entity.type()), "type round-trips as authored namespaced string");
        require("COMMAND".equals(entity.reason()), "reason retained as string");
        require("BROWN".equals(entity.pandaMainGene()), "pandaMainGene retained as string");
        require("LAZY".equals(entity.pandaHiddenGene()), "pandaHiddenGene retained as string");
        require(entity.baby(), "baby flag retained");
        require(entity.glowing(), "glowing flag retained");

        LootEntry helmet = entity.helmet();
        require(helmet != null, "helmet equipment loot deserialized");
        require(helmet.unbreakable(), "helmet unbreakable flag retained");
        require(helmet.itemFlags().contains("HIDE_ATTRIBUTES"), "helmet item flags retained as strings");
        List<?> helmetEnchants = helmet.enchantments();
        require(!helmetEnchants.isEmpty(), "helmet enchantments deserialized");
        require(entity.mainHand() != null, "mainHand equipment loot deserialized");

        List<EntityDefinition> passengers = entity.passengers();
        require(passengers.size() == 1, "single passenger entity deserialized");
        require("minecraft:zombie".equals(passengers.get(0).type()), "nested passenger type round-trips");

        require(entity.attributes().size() == 1, "attribute modifier deserialized");
        require("generic.max_health".equals(entity.attributes().get(0).attribute()), "attribute key retained as string");

        require(entity.loot() != null, "loot reference deserialized");
        require(entity.loot().tables().contains(LOOT_KEY), "loot reference table retained");
    }

    private static void assertSpawner(IrisData data) {
        Spawner spawner = SpawnResources.spawner(data, SPAWNER_KEY);
        require(spawner != null, "spawner deserialized");
        List<EntitySpawn> spawns = spawner.spawns();
        require(spawns.size() == 1, "single entity spawn deserialized");
        require(ENTITY_KEY.equals(spawns.get(0).entity()), "spawn references the probe entity by key");
        require("NORMAL".equals(spawner.group().name()), "spawn group enum deserialized");
        require(spawner.maxEntitiesPerChunk() == 4, "spawner numeric field retained");
    }

    private static void assertLoot(IrisData data) {
        LootTable table = LootResources.table(data, LOOT_KEY);
        require(table != null, "loot table deserialized");
        require(LOOT_KEY.equals(table.name()), "loot table name retained");
        require(table.rarity() == 4, "loot table rarity retained");

        List<LootEntry> items = table.loot();
        require(!items.isEmpty(), "loot items deserialized");
        LootEntry item = items.get(0);
        require(item.maxAmount() == 3, "loot item amount retained");
        require("STORAGE".equals(item.slotTypes().name()), "loot item slot type enum deserialized");
        require(item.itemFlags().contains("HIDE_ENCHANTS"), "loot item flags retained as strings");
        require(!item.enchantments().isEmpty(), "loot item enchantments deserialized");
        require("unbreaking".equals(item.enchantments().get(0).enchantment()), "loot item enchantment key retained as string");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new IllegalStateException("assertion failed: " + description);
        }
    }

    private static int run(String label, ProbeStep step) {
        drain();
        Throwable thrown = null;
        try {
            step.run();
        } catch (Throwable e) {
            thrown = e;
        }
        List<Throwable> reported = drain();
        int failures = 0;
        if (thrown != null) {
            System.out.println("[deserprobe] " + label + " FAILED: " + thrown.getClass().getName() + ": " + thrown.getMessage());
            thrown.printStackTrace(System.out);
            failures++;
        }
        if (!reported.isEmpty()) {
            System.out.println("[deserprobe] " + label + " FAILED: " + reported.size() + " error(s) reported (swallowed) during deserialization:");
            printReported(reported);
            failures += reported.size();
        }
        if (failures == 0) {
            System.out.println("[deserprobe] " + label + " OK");
        }
        return failures;
    }

    private static boolean bukkitPresent() {
        try {
            Class.forName("org.bukkit.Bukkit", false, DeserializationProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static File resolveFixture(String[] args) throws Exception {
        if (args.length > 0 && !args[0].isBlank()) {
            return new File(args[0]);
        }
        URL resource = DeserializationProbe.class.getClassLoader().getResource(FIXTURE_RESOURCE);
        if (resource == null) {
            return null;
        }
        return new File(resource.toURI());
    }

    private static File clonePack(File source, File workRoot) throws IOException {
        Path sourcePath = source.toPath();
        Path destinationPath = workRoot.toPath().resolve(source.getName());
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(sourcePath)) {
            paths = walk.collect(Collectors.toList());
        }
        for (Path path : paths) {
            Path target = destinationPath.resolve(sourcePath.relativize(path).toString());
            if (Files.isDirectory(path)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return destinationPath.toFile();
    }

    private static List<Throwable> drain() {
        synchronized (REPORTED) {
            List<Throwable> drained = new ArrayList<>(REPORTED);
            REPORTED.clear();
            return drained;
        }
    }

    private static void printReported(List<Throwable> errors) {
        for (Throwable error : errors) {
            error.printStackTrace(System.out);
        }
    }
}
