package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public final class HydrologyCaveMatter extends RawMatter<HydrologyCaveCell> {
    public HydrologyCaveMatter() {
        this(1, 1, 1);
    }

    public HydrologyCaveMatter(int width, int height, int depth) {
        super(width, height, depth, HydrologyCaveCell.class);
    }

    @Override
    public Palette<HydrologyCaveCell> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(HydrologyCaveCell hydrology, DataOutputStream output) throws IOException {
        output.writeByte(actionCode(hydrology.action()));
        output.writeUTF(hydrology.fluidProfileKey());
        output.writeUTF(hydrology.floodedBiomeKey());
    }

    @Override
    public HydrologyCaveCell readNode(DataInputStream input) throws IOException {
        HydrologyCaveAction action = actionFromCode(input.readUnsignedByte());
        return new HydrologyCaveCell(action, input.readUTF(), input.readUTF());
    }

    private int actionCode(HydrologyCaveAction action) {
        return switch (action) {
            case WET_SOURCE -> 1;
            case FALLING_FLUID -> 2;
            case DRY_AIR -> 3;
            case SEAL_GUARD -> 4;
        };
    }

    private HydrologyCaveAction actionFromCode(int code) throws IOException {
        return switch (code) {
            case 1 -> HydrologyCaveAction.WET_SOURCE;
            case 2 -> HydrologyCaveAction.FALLING_FLUID;
            case 3 -> HydrologyCaveAction.DRY_AIR;
            case 4 -> HydrologyCaveAction.SEAL_GUARD;
            default -> throw new IOException("Unknown hydrology cave action code " + code);
        };
    }
}
