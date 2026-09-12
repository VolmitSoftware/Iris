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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.collection.KList;

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
        IrisEntity entity = data.getEntityLoader().load(ENTITY_KEY);
        require(entity != null, "entity deserialized");
        require("minecraft:panda".equals(entity.getType()), "type round-trips as authored namespaced string");
        require("COMMAND".equals(entity.getReason()), "reason retained as string");
        require("BROWN".equals(entity.getPandaMainGene()), "pandaMainGene retained as string");
        require("LAZY".equals(entity.getPandaHiddenGene()), "pandaHiddenGene retained as string");
        require(entity.isBaby(), "baby flag retained");
        require(entity.isGlowing(), "glowing flag retained");

        IrisLoot helmet = entity.getHelmet();
        require(helmet != null, "helmet equipment loot deserialized");
        require(helmet.isUnbreakable(), "helmet unbreakable flag retained");
        require(helmet.getItemFlags().contains("HIDE_ATTRIBUTES"), "helmet item flags retained as strings");
        KList<?> helmetEnchants = helmet.getEnchantments();
        require(!helmetEnchants.isEmpty(), "helmet enchantments deserialized");
        require(entity.getMainHand() != null, "mainHand equipment loot deserialized");

        List<IrisEntity> passengers = entity.getPassengers();
        require(passengers.size() == 1, "single passenger entity deserialized");
        require("minecraft:zombie".equals(passengers.get(0).getType()), "nested passenger type round-trips");

        require(entity.getAttributes().size() == 1, "attribute modifier deserialized");
        require("generic.max_health".equals(entity.getAttributes().get(0).getAttribute()), "attribute key retained as string");

        require(entity.getLoot() != null, "loot reference deserialized");
        require(entity.getLoot().getTables().contains(LOOT_KEY), "loot reference table retained");
    }

    private static void assertSpawner(IrisData data) {
        IrisSpawner spawner = data.getSpawnerLoader().load(SPAWNER_KEY);
        require(spawner != null, "spawner deserialized");
        List<IrisEntitySpawn> spawns = spawner.getSpawns();
        require(spawns.size() == 1, "single entity spawn deserialized");
        require(ENTITY_KEY.equals(spawns.get(0).getEntity()), "spawn references the probe entity by key");
        require("NORMAL".equals(spawner.getGroup().name()), "spawn group enum deserialized");
        require(spawner.getMaxEntitiesPerChunk() == 4, "spawner numeric field retained");
    }

    private static void assertLoot(IrisData data) {
        IrisLootTable table = data.getLootLoader().load(LOOT_KEY);
        require(table != null, "loot table deserialized");
        require(LOOT_KEY.equals(table.getName()), "loot table name retained");
        require(table.getRarity() == 4, "loot table rarity retained");

        KList<IrisLoot> items = table.getLoot();
        require(!items.isEmpty(), "loot items deserialized");
        IrisLoot item = items.get(0);
        require(item.getMaxAmount() == 3, "loot item amount retained");
        require("STORAGE".equals(item.getSlotTypes().name()), "loot item slot type enum deserialized");
        require(item.getItemFlags().contains("HIDE_ENCHANTS"), "loot item flags retained as strings");
        require(!item.getEnchantments().isEmpty(), "loot item enchantments deserialized");
        require("unbreaking".equals(item.getEnchantments().get(0).getEnchantment()), "loot item enchantment key retained as string");
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
