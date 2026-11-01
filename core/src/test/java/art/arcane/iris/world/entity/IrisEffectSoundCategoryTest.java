package art.arcane.iris.world.entity;

import org.bukkit.SoundCategory;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IrisEffectSoundCategoryTest {
    private static final Map<String, SoundCategory> VANILLA_CATEGORIES = Map.ofEntries(
            Map.entry("master", SoundCategory.MASTER),
            Map.entry("music", SoundCategory.MUSIC),
            Map.entry("record", SoundCategory.RECORDS),
            Map.entry("weather", SoundCategory.WEATHER),
            Map.entry("block", SoundCategory.BLOCKS),
            Map.entry("hostile", SoundCategory.HOSTILE),
            Map.entry("neutral", SoundCategory.NEUTRAL),
            Map.entry("player", SoundCategory.PLAYERS),
            Map.entry("ambient", SoundCategory.AMBIENT),
            Map.entry("voice", SoundCategory.VOICE),
            Map.entry("ui", SoundCategory.UI));

    @Test
    public void soundCategoryIsStoredAsAPlainStringField() throws NoSuchFieldException {
        assertEquals(String.class, IrisEffect.class.getDeclaredField("soundCategory").getType());
    }

    @Test
    public void soundCategoryDefaultsToMaster() {
        assertEquals("master", new IrisEffect().getSoundCategoryKey());
        assertEquals("master", new IrisEffect().setSoundCategory(null).getSoundCategoryKey());
        assertEquals("master", new IrisEffect().setSoundCategory("  ").getSoundCategoryKey());
    }

    @Test
    public void soundCategoryKeyIgnoresCaseAndSurroundingSpace() {
        assertEquals("ambient", new IrisEffect().setSoundCategory(" Ambient ").getSoundCategoryKey());
        assertEquals("music", new IrisEffect().setSoundCategory("MUSIC").getSoundCategoryKey());
    }

    @Test
    public void everyVanillaCategoryNameResolvesToItsBukkitCategory() {
        for (Map.Entry<String, SoundCategory> entry : VANILLA_CATEGORIES.entrySet()) {
            assertSame(entry.getKey(), entry.getValue(), IrisEffect.bukkitSoundCategory(entry.getKey()));
        }
        assertEquals(SoundCategory.values().length, VANILLA_CATEGORIES.size());
    }

    @Test
    public void unknownCategoryNamesDoNotResolve() {
        assertNull(IrisEffect.bukkitSoundCategory("ambiant"));
        assertNull(IrisEffect.bukkitSoundCategory("records"));
        assertNull(IrisEffect.bukkitSoundCategory("players"));
    }
}
