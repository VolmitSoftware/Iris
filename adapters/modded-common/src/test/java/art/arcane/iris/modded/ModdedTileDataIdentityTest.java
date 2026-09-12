package art.arcane.iris.modded;

import art.arcane.iris.generation.block.TileData;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Mantle tile sections are palette backed and resolve palette ids through equals/hashCode, so two different tiles
 * in one section must not share an identity - otherwise the first tile's NBT is written into every other tile.
 * The superclass generates equals/hashCode from fields a modded record never populates, which is why this class
 * answers identity itself.
 */
public class ModdedTileDataIdentityTest {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The generic map form the Bukkit side reads cannot express an NBT array: ByteArray, IntArray and LongArray all
     * collapse to a plain List, and pasting that back produces a ListTag where Minecraft demands an array. A player
     * head's {@code profile.id} is an IntArray of four, so a captured head pasted from the map form loses its skin.
     * Capture therefore keeps the original SNBT alongside the map, and the modded paste path reads it first.
     */
    @Test
    public void captureKeepsSnbtSoArrayTypingSurvivesThePasteRoundTrip() throws Exception {
        String snbt = "{profile:{id:[I;1,2,3,4],name:\"iris\"}}";

        ModdedTileData captured = ModdedTileData.capture("minecraft:player_head", snbt);

        assertEquals(snbt, captured.snbt());
        assertEquals(snbt, captured.getProperties().get(ModdedTileData.NBT_PROPERTY));

        // The Bukkit-readable map form is still written, and is still array-lossy - which is why the SNBT has to stay.
        assertTrue(captured.getProperties().get("profile") instanceof KMap);
        KMap<?, ?> profile = (KMap<?, ?>) captured.getProperties().get("profile");
        assertTrue(profile.get("id") instanceof List);

        CompoundTag payload = captured.payload();
        assertNotNull(payload);
        Tag profileTag = payload.get("profile");
        assertTrue(profileTag instanceof CompoundTag);
        Tag idTag = ((CompoundTag) profileTag).get("id");
        assertTrue("profile.id must paste back as an IntArrayTag, got "
                        + (idTag == null ? "null" : idTag.getClass().getSimpleName()),
                idTag instanceof IntArrayTag);
        assertArrayEquals(new int[]{1, 2, 3, 4}, ((IntArrayTag) idTag).getAsIntArray());
    }

    @Test
    public void differentTilesAreNotEqualAndDoNotShareHashCode() {
        ModdedTileData chest = tile("minecraft:chest", "Items", "diamond");
        ModdedTileData otherChest = tile("minecraft:chest", "Items", "emerald");
        ModdedTileData sign = tile("minecraft:oak_sign", "front_text", "hello");

        assertNotEquals(chest, otherChest);
        assertNotEquals(chest, sign);
        // The inherited identity was a constant hash for every modded record; unequal objects are allowed to
        // collide, so assert only that the hash actually varies with the record.
        assertTrue(Set.of(chest.hashCode(), otherChest.hashCode(), sign.hashCode()).size() > 1);
    }

    @Test
    public void identicalTilesShareOnePaletteIdentity() {
        ModdedTileData first = tile("minecraft:chest", "Items", "diamond");
        ModdedTileData second = tile("minecraft:chest", "Items", "diamond");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void materialKeyReportsTheBlockKeyInsteadOfNull() {
        assertEquals("minecraft:chest", tile("minecraft:chest", "Items", "diamond").getMaterialKey());
    }

    @Test
    public void legacyRecordHasNoBlockKey() {
        ModdedTileData legacy = new ModdedTileData(new byte[]{0, 1}, new KMap<>(), null, 0);
        assertNull(legacy.getMaterialKey());
    }

    @Test
    public void cloneIsEqualButIndependentOfTheSource() {
        ModdedTileData source = tile("minecraft:chest", "Items", "diamond");
        KMap<String, Object> nested = new KMap<>();
        nested.put("id", "minecraft:stone");
        source.getProperties().put("nested", nested);
        source.getProperties().put("list", List.of("a", "b"));

        TileData copy = source.clone();

        assertNotSame(source, copy);
        assertNotSame(source.getProperties(), copy.getProperties());
        assertEquals(source.getProperties(), copy.getProperties());
        assertNotSame(source.getProperties().get("nested"), copy.getProperties().get("nested"));
        assertTrue(copy.getProperties().get("list") instanceof List);
    }

    private static ModdedTileData tile(String blockKey, String propertyKey, String propertyValue) {
        KMap<String, Object> properties = new KMap<>();
        properties.put(propertyKey, propertyValue);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(blockKey);
                out.writeUTF(GSON.toJson(properties));
            }
            return new ModdedTileData(bytes.toByteArray(), properties, blockKey, -1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
