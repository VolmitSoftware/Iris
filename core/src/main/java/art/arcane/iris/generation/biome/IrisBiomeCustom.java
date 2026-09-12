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

package art.arcane.iris.generation.biome;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.KeyStatus;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.DependsOn;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.Color;
import java.util.Locale;

@Snippet("custom-biome")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A custom biome, generated through a datapack")
@Data
public class IrisBiomeCustom {
    @Required
    @Description("The resource key of this biome. Just a simple id such as 'plains' or something.")
    private String id = "";

    @MinNumber(-3)
    @MaxNumber(3)
    @Description("The biome's temperature")
    private double temperature = 0.8;

    @MinNumber(-3)
    @MaxNumber(3)
    @Description("The biome's downfall amount (snow / rain), see preci")
    private double humidity = 0.4;

    @DependsOn("spawnRarity")
    @ArrayType(min = 1, type = IrisBiomeCustomSpawn.class)
    @Description("The biome's mob spawns")
    private KList<IrisBiomeCustomSpawn> spawns = new KList<>();

    @ArrayType(min = 1, type = String.class)
    @Description("Biome tags to opt this custom biome into, such as minecraft:allows_surface_slime_spawns")
    private KList<String> tags = new KList<>();

    @Description("The biome's downfall type")
    private IrisBiomeCustomPrecipType downfallType = IrisBiomeCustomPrecipType.rain;

    @Description("Define an ambient particle to be rendered clientside (no server cost!)")
    private IrisBiomeCustomParticle ambientParticle = null;

    @Required
    @Description("The biome's category type")
    private IrisBiomeCustomCategory category = IrisBiomeCustomCategory.plains;

    @MinNumber(0)
    @MaxNumber(20)
    @Description("The spawn rarity of any defined spawners")
    private int spawnRarity = 0;

    @Description("The color of the sky, top half of sky. (hex format)")
    private String skyColor = "#79a8e1";

    @Description("The color of the fog, bottom half of sky. (hex format)")
    private String fogColor = "#c0d8e1";

    @Description("The color of the water (hex format). Leave blank / don't define to not change")
    private String waterColor = "#3f76e4";

    @Description("The color of water fog (hex format). Leave blank / don't define to not change")
    private String waterFogColor = "#050533";

    @Description("The color of the grass (hex format). Leave blank / don't define to not change")
    private String grassColor = "";

    @Description("The color of foliage (hex format). Leave blank / don't define to not change")
    private String foliageColor = "";

    /**
     * The tags this custom biome is installed into: the pack author's own {@code tags} plus the direct tag
     * membership of the Iris biome's vanilla derivative. Inheriting the derivative's tags is what makes
     * {@code #minecraft:is_overworld} and every mod-authored tag selector resolve against Iris custom biomes;
     * without it a custom biome sits in no tag at all and mod content that gates on tags silently never runs.
     *
     * <p>Author tags win order but duplicates collapse: the tag files are written through a sorted set.
     * Structure tags ({@code has_structure/*}) are deliberately not inherited - native structure placement
     * resolves through the biome's structure derivative, so injecting them would place a structure twice.
     *
     * @param vanillaDerivativeKey the owning Iris biome's vanilla derivative key, may be null
     */
    public KList<String> getEffectiveTags(String vanillaDerivativeKey) {
        KList<String> resolved = new KList<>();
        KList<String> authored = getTags();

        if (authored != null) {
            for (String tag : authored) {
                resolved.addIfMissing(tag);
            }
        }

        for (String inherited : IrisVanillaBiomeTags.tagsFor(vanillaDerivativeKey)) {
            resolved.addIfMissing(inherited);
        }

        return resolved;
    }

    public String generateJson(IDataFixer fixer) {
        return generateJson(fixer, null);
    }

    /**
     * @param gate version-content gate used to keep spawns whose entity type is missing on this server out of the
     *             generated datapack (the game rejects a datapack that names an unknown entity). Null consults the
     *             live platform registries; an unreadable registry keeps every spawn.
     */
    public String generateJson(IDataFixer fixer, ContentGate gate) {
        ContentGate contentGate = gate == null ? ContentGate.forData(null) : gate;
        JSONObject effects = new JSONObject();
        effects.put("sky_color", parseColor(getSkyColor()));
        effects.put("fog_color", parseColor(getFogColor()));
        effects.put("water_color", parseColor(getWaterColor()));
        effects.put("water_fog_color", parseColor(getWaterFogColor()));

        if (ambientParticle != null) {
            // Never touch IrisBiomeCustomParticle.getParticle() here: it returns a Bukkit Particle
            // and this method also runs during modded datapack staging.
            String particleKey = ambientParticle.getParticleKey();
            if (particleKey == null) {
                IrisLogging.warn("Custom biome " + getId() + " declares an ambientParticle with no particle key, skipping it");
            } else {
                JSONObject particle = new JSONObject();
                JSONObject po = new JSONObject();
                po.put("type", particleKey);
                particle.put("options", po);
                particle.put("probability", 1f / ambientParticle.getRarity());
                effects.put("particle", particle);
            }
        }

        if (!getGrassColor().isEmpty()) {
            effects.put("grass_color", parseColor(getGrassColor()));
        }

        if (!getFoliageColor().isEmpty()) {
            effects.put("foliage_color", parseColor(getFoliageColor()));
        }

        JSONObject j = new JSONObject();
        j.put("surface_builder", "minecraft:grass");
        j.put("depth", 0.125);
        j.put("scale", 0.05);
        j.put("temperature", getTemperature());
        j.put("downfall", getHumidity());
        j.put("creature_spawn_probability", getSpawnRarity());
        j.put("has_precipitation", getDownfallType() != IrisBiomeCustomPrecipType.none);
        j.put("precipitation", getDownfallType().toString().toLowerCase());
        j.put("category", getCategory().toString().toLowerCase());
        j.put("effects", effects);
        j.put("starts", new JSONArray());
        j.put("spawners", new JSONObject());
        j.put("spawn_costs", new JSONObject());
        j.put("carvers", new JSONObject());
        j.put("features", new JSONArray());

        if (spawnRarity > 0) {
            j.put("creature_spawn_probability", spawnRarity);
        }

        if (getSpawns() != null && getSpawns().isNotEmpty()) {
            JSONObject spawners = new JSONObject();
            KMap<IrisBiomeCustomSpawnType, JSONArray> groups = new KMap<>();

            for (IrisBiomeCustomSpawn i : getSpawns()) {
                if (i == null) {
                    continue;
                }
                String key = i.getTypeKey();
                if (key == null) {
                    IrisLogging.warn("Skipping custom biome spawn with null entity type in biome " + getId());
                    continue;
                }
                if (contentGate.entity(key) == KeyStatus.MISSING) {
                    IrisLogging.debug("Dropping custom biome spawn " + key + " in biome " + getId() + ": entity missing on this server");
                    continue;
                }
                IrisBiomeCustomSpawnType group = i.getGroup() == null ? IrisBiomeCustomSpawnType.MISC : i.getGroup();
                JSONArray g = groups.computeIfAbsent(group, (k) -> new JSONArray());
                JSONObject o = new JSONObject();
                o.put("type", key);
                o.put("weight", i.getWeight());
                o.put("minCount", i.getMinCount());
                o.put("maxCount", i.getMaxCount());
                g.put(o);
            }

            for (IrisBiomeCustomSpawnType i : groups.k()) {
                spawners.put(i.name().toLowerCase(Locale.ROOT), groups.get(i));
            }

            j.put("spawners", spawners);
        }

        return fixer.fixCustomBiome(this, j).toString(4);
    }

    private int parseColor(String c) {
        String v = (c.startsWith("#") ? c : "#" + c).trim();
        try {
            return Color.decode(v).getRGB() & 0x00FFFFFF;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("Error Parsing '''color''', (" + c + ")");
        }

        return 0;
    }

    public String getId() {
        return id.toLowerCase();
    }
}
