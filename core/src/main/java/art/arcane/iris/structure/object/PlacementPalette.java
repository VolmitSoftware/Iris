package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.IdentityHashMap;
import java.util.Set;

/** Resolves decay, rotation, waterlog, air and stilt facts once per baked palette entry for one placement. */
final class PlacementPalette {
    record Facts(NativeBlockState rotated, boolean waterloggable, boolean authoredWaterlogged,
                 NativeBlockState dry, boolean air) {
        private static final Facts ROTATED_AWAY = new Facts(null, false, false, null, false);
    }

    private static final Object ROTATED_AWAY = new Object();
    private static final byte UNKNOWN = 0;
    private static final byte NO = 1;
    private static final byte YES = 2;

    private final NativeBlockState[] palette;
    private final IrisObjectRotation rotation;
    private final int spinX;
    private final int spinY;
    private final int spinZ;
    private final boolean preventingDecay;
    private final boolean debugSmartBore;
    private final Set<String> excludedStiltMaterials;
    private final NativeBlockState[] decayed;
    private final Facts[] facts;
    private final byte[] stiltLayers;
    private final byte[] stilts;
    private final NativeBlockState[] stiltDefaults;
    private IdentityHashMap<NativeBlockState, Object> rotations;
    private IdentityHashMap<NativeBlockState, Facts> editedFacts;
    private IdentityHashMap<NativeBlockState, Boolean> placedStilts;

    PlacementPalette(NativeBlockState[] palette, IrisObjectRotation rotation, int spinX, int spinY, int spinZ,
                     boolean preventingDecay, boolean debugSmartBore, Set<String> excludedStiltMaterials) {
        this.palette = palette;
        this.rotation = rotation;
        this.spinX = spinX;
        this.spinY = spinY;
        this.spinZ = spinZ;
        this.preventingDecay = preventingDecay;
        this.debugSmartBore = debugSmartBore;
        this.excludedStiltMaterials = excludedStiltMaterials;
        this.decayed = new NativeBlockState[palette.length];
        this.facts = new Facts[palette.length];
        this.stiltLayers = new byte[palette.length];
        this.stilts = new byte[palette.length];
        this.stiltDefaults = new NativeBlockState[palette.length];
    }

    private NativeBlockState source(int entry) {
        NativeBlockState state = palette[entry];
        return debugSmartBore && IrisObject.States.vair().equals(state) ? IrisObject.States.vairDebug() : state;
    }

    boolean stiltLayer(int entry) {
        byte known = stiltLayers[entry];
        if (known == UNKNOWN) {
            known = IrisObjectShaping.isStiltLayerBlock(source(entry)) ? YES : NO;
            stiltLayers[entry] = known;
        }
        return known == YES;
    }

    NativeBlockState decayed(int entry) {
        NativeBlockState resolved = decayed[entry];
        if (resolved == null) {
            NativeBlockState data = source(entry);
            resolved = preventingDecay && IrisProceduralBlocks.hasProperty(data, "distance")
                    && "false".equals(IrisProceduralBlocks.propertyValue(data, "persistent"))
                    ? data.withProperty("persistent", "true") : data;
            decayed[entry] = resolved;
        }
        return resolved;
    }

    Facts facts(int entry) {
        Facts resolved = facts[entry];
        if (resolved == null) {
            resolved = computeFacts(decayed(entry));
            facts[entry] = resolved;
        }
        return resolved;
    }

    Facts factsOf(NativeBlockState state) {
        if (editedFacts == null) {
            editedFacts = new IdentityHashMap<>();
        }
        Facts resolved = editedFacts.get(state);
        if (resolved == null) {
            resolved = computeFacts(state);
            editedFacts.put(state, resolved);
        }
        return resolved;
    }

    private Facts computeFacts(NativeBlockState state) {
        NativeBlockState rotated = rotate(state);
        if (rotated == null) {
            return Facts.ROTATED_AWAY;
        }
        boolean waterloggable = IrisProceduralBlocks.hasProperty(rotated, "waterlogged");
        boolean authored = waterloggable && "true".equals(IrisProceduralBlocks.propertyValue(rotated, "waterlogged"));
        NativeBlockState dry = waterloggable ? IrisProceduralBlocks.normalizeWaterlogging(rotated, null, false) : rotated;
        String material = IrisObjectShaping.materialKey(rotated);
        boolean air = material.equals("minecraft:air") || material.equals("minecraft:cave_air");
        return new Facts(rotated, waterloggable, authored, dry, air);
    }

    NativeBlockState rotate(NativeBlockState state) {
        if (state == null) {
            return null;
        }
        if (rotations == null) {
            rotations = new IdentityHashMap<>();
        }
        Object memo = rotations.get(state);
        if (memo == null) {
            NativeBlockState rotated = rotation.rotate(state, spinX, spinY, spinZ);
            memo = rotated == null ? ROTATED_AWAY : rotated;
            rotations.put(state, memo);
        }
        return memo == ROTATED_AWAY ? null : (NativeBlockState) memo;
    }

    boolean shouldStilt(int entry) {
        byte known = stilts[entry];
        if (known == UNKNOWN) {
            known = IrisObjectShaping.shouldStilt(palette[entry], excludedStiltMaterials) ? YES : NO;
            stilts[entry] = known;
        }
        return known == YES;
    }

    NativeBlockState stiltDefault(int entry) {
        NativeBlockState resolved = stiltDefaults[entry];
        if (resolved == null) {
            NativeBlockState state = palette[entry];
            String material = IrisObjectShaping.materialKey(state);
            resolved = material.equals("minecraft:grass_block") || material.equals("minecraft:mycelium")
                    || material.equals("minecraft:podzol") || material.equals("minecraft:dirt_path")
                    ? IrisObject.States.dirt() : state;
            stiltDefaults[entry] = resolved;
        }
        return resolved;
    }

    boolean shouldStiltPlaced(NativeBlockState state) {
        if (placedStilts == null) {
            placedStilts = new IdentityHashMap<>();
        }
        Boolean known = placedStilts.get(state);
        if (known == null) {
            known = IrisObjectShaping.shouldStilt(state, excludedStiltMaterials);
            placedStilts.put(state, known);
        }
        return known;
    }
}
