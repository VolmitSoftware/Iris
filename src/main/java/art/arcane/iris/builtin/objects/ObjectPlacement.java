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

package art.arcane.iris.builtin.objects;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.loot.ObjectVanillaLoot;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.spawn.PlacementMarker;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One object placement of a biome or region, an entry of its {@code objects}: which objects, how often, and how they sit
 * in the world. {@code iris:objects} decides from the options it owns (what to place, chance, density, carving and
 * surface support); Iris's placement stages read the rest from the placement itself, so this keeps the JSON the placement
 * was written as ({@link #json()}) for Iris to read it again the way it reads placements everywhere else.
 */
@Snippet("object-placer")
@Desc("Represents an iris object placer. It places objects.")
@JsonAdapter(ObjectPlacement.KeepingItsJson.class)
public final class ObjectPlacement implements PlacementSource {
    private static final int DEFAULT_SURFACE_SUPPORT_BUFFER = 2;
    private static final int DEFAULT_SURFACE_SUPPORT_DEPTH = 2;

    @Required
    @MinItems(1)
    @Ref(Reference.PACK_OBJECT)
    @Desc("List of objects to place")
    private List<String> place = List.of();
    @Desc("Rotate this objects placement")
    private ObjectRotation rotation = new ObjectRotation();
    @Desc("Limit the max height or min height of placement.")
    private ObjectLimit clamp = new ObjectLimit();
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The maximum layer level of a snow filter overtop of this placement. Set to 0 to disable. Max of 1.")
    private double snow = 0;
    @Desc("Whether or not this object can be targeted by a dolphin.")
    private boolean isDolphinTarget = false;
    @Desc("The slope at which this object can be placed. Range from 0 to 10 by default. Calculated from a 3-block radius from the center of the object placement.")
    private SlopeClip slopeCondition = new SlopeClip();
    @Desc("Set to true to add the rotation of the direction of the slope of the terrain (wherever the slope is going down) to the y-axis rotation of the object." +
            "Rounded to 90 degrees. Adds the *min* rotation of the y axis as well (to still allow you to rotate objects nicely). Discards *max* and *interval* on *yaxis*")
    private boolean rotateTowardsSlope = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance for this to place in a chunk. If you need multiple per chunk, set this to 1 and use density.")
    private double chance = 1;
    @MinNumber(1)
    @Desc("If the chance check passes, place this many in a single chunk")
    private int density = 1;
    @Desc("If the chance check passes, and you specify this, it picks a number in the range based on noise, and 'density' is ignored.")
    private StyledRange densityStyle = null;
    @Desc("When stilting is enabled, this object will define various properties related to it.")
    private StiltSettings stiltSettings;
    @Desc("When a VACUUM place mode is used, this defines the deformation radius and falloff.")
    private VacuumSettings vacuumSettings;
    @MaxNumber(64)
    @MinNumber(0)
    @Desc("When bore is enabled, expand max-y of the cuboid it removes")
    private int boreExtendMaxY = 0;
    @MinItems(1)
    @Desc("Add markers to blocks in this object")
    private List<PlacementMarker> markers = List.of();
    @MaxNumber(64)
    @MinNumber(-1)
    @Desc("When bore is enabled, lower min-y of the cuboid it removes")
    private int boreExtendMinY = 0;
    @Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
    private boolean underwater = false;
    @Desc("If set to true, objects will place in carvings (such as underground) or under an overhang.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;
    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Extra buffer, in blocks, around this object's base footprint that must also be solid, un-carved ground. 0 checks only the footprint itself.")
    private int surfaceSupportBuffer = DEFAULT_SURFACE_SUPPORT_BUFFER;
    @MinNumber(1)
    @MaxNumber(16)
    @Desc("Minimum thickness, in blocks, of un-carved ground required under every column of this object's base footprint.")
    private int surfaceSupportDepth = DEFAULT_SURFACE_SUPPORT_DEPTH;
    @Desc("Set to false to let this placement sit over carved surface openings such as cave entrances.")
    private boolean requireSurfaceSupport = true;
    @Desc("When carving placement is enabled, select which carved-space anchor this placement targets.")
    private CaveAnchorMode caveAnchorMode = CaveAnchorMode.PROFILE_DEFAULT;
    @Desc("If this is defined, this object wont place on the terrain heightmap, but instead on this virtual heightmap")
    private HeightmapNoise heightmap;
    @Desc("If set to true, Iris will try to fill the insides of 'rooms' and 'pockets' where air should fit based off of raytrace checks. This prevents a village house placing in an area where a tree already exists, and instead replaces the parts of the tree where the interior of the structure is. \n\nThis operation does not affect warmed-up generation speed however it does slow down loading objects.")
    private boolean smartBore = false;
    @Desc("If set to true, Blocks placed underwater that could be waterlogged are waterlogged.")
    private boolean waterloggable = false;
    @Desc("If set to true, objects will place on the fluid height level Such as boats.")
    private boolean onwater = false;
    @Desc("If set to true, this object will only place parts of itself where blocks already exist. Warning: Melding is very performance intensive!")
    private boolean meld = false;
    @Desc("If set to true, this object will get placed from the bottom of the world up")
    private boolean fromBottom;
    @Desc("If set to true, this object will place from the ground up instead of height checks when not y locked to the surface. This is not compatable with X and Z axis rotations (it may look off)")
    private boolean bottom = false;
    @Desc("If set to true, air will be placed before the schematic places.")
    private boolean bore = false;
    @Desc("Use a generator to warp the field of coordinates. Using simplex for example would make a square placement warp like a flag")
    private NoiseStyle warp = NoiseStyle.of(NoiseStyle.FLAT);
    @Desc("The placement mode")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;
    @MinItems(1)
    @Desc("Find and replace blocks")
    private List<ObjectReplace> edit = List.of();
    @Desc("Translate this object's placement")
    private ObjectTranslate translate = new ObjectTranslate();
    @Desc("Scale Objects")
    private ObjectScale scale = new ObjectScale();
    @MinItems(1)
    @Desc("The loot tables to apply to these objects")
    private List<ObjectLoot> loot = List.of();
    @MinItems(1)
    @Desc("The vanilla loot tables to apply to these objects")
    private List<ObjectVanillaLoot> vanillaLoot = List.of();
    @Desc("Whether the given loot tables override any and all other loot tables available in the dimension, region or biome.")
    private boolean overrideGlobalLoot = false;
    @MinItems(1)
    @Desc("This object / these objects override the following trees when they grow...")
    private List<TreeOverride> trees = List.of();
    @MinItems(1)
    @Ref(Reference.PACK_OBJECT)
    @Desc("List of objects to this object is allowed to collied with")
    private List<String> allowedCollisions = List.of();
    @MinItems(1)
    @Ref(Reference.PACK_OBJECT)
    @Desc("List of objects to this object is forbidden to collied with")
    private List<String> forbiddenCollisions = List.of();
    @Desc("Ignore any placement restrictions for this object")
    @SerializedName(value = "forcePlace", alternate = {"force"})
    private boolean forcePlace = false;

    private transient volatile String json;
    private transient volatile Object hostForm;

    public ObjectPlacement() {
    }

    /** The objects to pick from, by the keys of the pack's objects. */
    public List<String> place() {
        return place == null ? List.of() : Collections.unmodifiableList(place);
    }

    @Override
    public double chance() {
        return chance;
    }

    /** The count of placements when the chance check passes, unless {@link #densityStyle()} picks it. */
    public int density() {
        return density;
    }

    public Optional<StyledRange> densityStyle() {
        return Optional.ofNullable(densityStyle);
    }

    /** The fixed density, or, when a style picks the count, the middle of its range. */
    public int middleDensity() {
        if (densityStyle == null) {
            return density;
        }
        return (int) ((densityStyle.max() + densityStyle.min()) / 2);
    }

    public ObjectRotation rotation() {
        return rotation;
    }

    public ObjectTranslate translate() {
        return translate;
    }

    public ObjectScale scale() {
        return scale;
    }

    public NoiseStyle warp() {
        return warp;
    }

    @Override
    public ObjectPlaceMode mode() {
        return mode;
    }

    public Optional<VacuumSettings> vacuumSettings() {
        return Optional.ofNullable(vacuumSettings);
    }

    @Override
    public CarvingMode carvingSupport() {
        return carvingSupport;
    }

    @Override
    public CaveAnchorMode caveAnchorMode() {
        return caveAnchorMode;
    }

    @Override
    public boolean underwater() {
        return underwater;
    }

    @Override
    public boolean isDolphinTarget() {
        return isDolphinTarget;
    }

    public boolean forcePlace() {
        return forcePlace;
    }

    @Override
    public boolean requireSurfaceSupport() {
        return requireSurfaceSupport;
    }

    @Override
    public int surfaceSupportBuffer() {
        return surfaceSupportBuffer;
    }

    public List<TreeOverride> trees() {
        return trees == null ? List.of() : Collections.unmodifiableList(trees);
    }

    /** The JSON this placement was read from, which Iris reads again as its own placement; null for one made in code. */
    public String json() {
        return json;
    }

    /**
     * What Iris made of {@link #json()} the last time it asked, kept here so it asks once. Only Iris sets or reads it,
     * and it is its own type.
     */
    public Object hostForm() {
        return hostForm;
    }

    public void hostForm(Object form) {
        this.hostForm = form;
    }

    /** Reads a placement like any settings and keeps the JSON it was read from. */
    public static final class KeepingItsJson implements TypeAdapterFactory {
        @Override
        @SuppressWarnings("unchecked") // The type is checked to be ObjectPlacement.
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != ObjectPlacement.class) {
                return null;
            }
            TypeAdapter<ObjectPlacement> placements = gson.getDelegateAdapter(this, TypeToken.get(ObjectPlacement.class));
            TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
            return (TypeAdapter<T>) new TypeAdapter<ObjectPlacement>() {
                @Override
                public void write(JsonWriter out, ObjectPlacement placement) throws IOException {
                    placements.write(out, placement);
                }

                @Override
                public ObjectPlacement read(JsonReader in) throws IOException {
                    JsonElement written = elements.read(in);
                    ObjectPlacement placement = placements.fromJsonTree(written);
                    if (placement != null) {
                        placement.json = written.toString();
                    }
                    return placement;
                }
            }.nullSafe();
        }
    }
}
