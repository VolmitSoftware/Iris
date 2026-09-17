package art.arcane.iris.modded;

import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.entity.IrisEntitySpawn;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedSpawnerRarityParityTest {
    private static IrisEntitySpawn spawn(int rarity) {
        IrisEntitySpawn spawn = new IrisEntitySpawn();
        spawn.setRarity(rarity);
        return spawn;
    }

    @Test
    public void rarityIsAppliedOnceAsPoolWeightOnly() {
        IrisEntitySpawn common = spawn(1);
        IrisEntitySpawn rare = spawn(4);

        KList<IrisEntitySpawn> expanded = IRare.expandWeighted(List.of(common, rare));

        // totalRarity 5 -> common appears 5/1 = 5 times, rare 5/4 = 1 time.
        assertEquals(6, expanded.size());
        assertEquals(5, expanded.stream().filter(entry -> entry == common).count());
        assertEquals(1, expanded.stream().filter(entry -> entry == rare).count());
    }

    @Test
    public void rarityZeroAndNegativeAreClampedToOne() {
        assertEquals(1, IRare.get(spawn(0)));
        assertEquals(1, IRare.get(spawn(-5)));
        assertEquals(3, IRare.get(spawn(3)));
    }

    @Test
    public void perPositionSpawnLoopsDoNotRerollEntryRarity() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.moddedCommonSources"),
                "art/arcane/iris/modded/ModdedWorldManager.java"));

        for (String method : List.of("private int spawnEntry(", "private int spawnEntryAt(")) {
            String body = methodBody(source, method);
            assertFalse(method + " must not re-roll rarity per position; rarityPick already weighted the pool",
                    body.contains("getRarity()"));
            assertTrue(method + " must keep the min/max spawn-count roll",
                    body.contains("LootResolver.inclusive("));
        }
    }

    private static String methodBody(String source, String declaration) {
        int start = source.indexOf(declaration);
        assertTrue("ModdedWorldManager must declare " + declaration, start >= 0);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, index);
                }
            }
        }
        throw new AssertionError(declaration + " is not brace balanced");
    }
}
