package art.arcane.iris.world.storage.matter;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.util.matter.MatterCavern;

public record PreObjectMatterCell(
        boolean blockCaptured,
        PlatformBlockState block,
        boolean stringCaptured,
        String string,
        boolean cavernCaptured,
        MatterCavern cavern,
        boolean hydrologyCaptured,
        HydrologyCaveCell hydrology
) {
    public PreObjectMatterCell {
        if (!blockCaptured && block != null) {
            throw new IllegalArgumentException("An uncaptured block cannot have a value");
        }
        if (!stringCaptured && string != null) {
            throw new IllegalArgumentException("An uncaptured string cannot have a value");
        }
        if (!cavernCaptured && cavern != null) {
            throw new IllegalArgumentException("An uncaptured cavern cannot have a value");
        }
        if (!hydrologyCaptured && hydrology != null) {
            throw new IllegalArgumentException("An uncaptured hydrology cell cannot have a value");
        }
        if (!blockCaptured && !stringCaptured && !cavernCaptured && !hydrologyCaptured) {
            throw new IllegalArgumentException("A pre-object cell must capture at least one value");
        }
    }

    public static PreObjectMatterCell block(PlatformBlockState value) {
        return new PreObjectMatterCell(true, value, false, null, false, null, false, null);
    }

    public static PreObjectMatterCell string(String value) {
        return new PreObjectMatterCell(false, null, true, value, false, null, false, null);
    }

    public static PreObjectMatterCell cavern(MatterCavern value) {
        return new PreObjectMatterCell(false, null, false, null, true, value, false, null);
    }

    public PreObjectMatterCell captureBlock(PlatformBlockState value) {
        if (blockCaptured) {
            return this;
        }
        return new PreObjectMatterCell(true, value, stringCaptured, string, cavernCaptured, cavern, hydrologyCaptured, hydrology);
    }

    public PreObjectMatterCell captureString(String value) {
        if (stringCaptured) {
            return this;
        }
        return new PreObjectMatterCell(blockCaptured, block, true, value, cavernCaptured, cavern, hydrologyCaptured, hydrology);
    }

    public PreObjectMatterCell captureCavern(MatterCavern value) {
        if (cavernCaptured) {
            return this;
        }
        return new PreObjectMatterCell(blockCaptured, block, stringCaptured, string, true, value, hydrologyCaptured, hydrology);
    }

    public static PreObjectMatterCell hydrology(HydrologyCaveCell value) {
        return new PreObjectMatterCell(false, null, false, null, false, null, true, value);
    }

    public PreObjectMatterCell captureHydrology(HydrologyCaveCell value) {
        return hydrologyCaptured ? this : new PreObjectMatterCell(blockCaptured, block,
                stringCaptured, string, cavernCaptured, cavern, true, value);
    }

    public boolean captures(Class<?> type) {
        if (type == HydrologyCaveCell.class) {
            return hydrologyCaptured;
        }
        if (type == PlatformBlockState.class) {
            return blockCaptured;
        }
        if (type == String.class) {
            return stringCaptured;
        }
        if (type == MatterCavern.class) {
            return cavernCaptured;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public <T> T original(Class<T> type) {
        if (!captures(type)) {
            throw new IllegalArgumentException("Unsupported or uncaptured pre-object type " + type.getCanonicalName());
        }
        if (type == PlatformBlockState.class) {
            return (T) block;
        }
        if (type == String.class) {
            return (T) string;
        }
        return type == HydrologyCaveCell.class ? (T) hydrology : (T) cavern;
    }
}
