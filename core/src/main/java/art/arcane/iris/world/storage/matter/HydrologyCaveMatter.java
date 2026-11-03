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
        output.writeUTF("");
    }

    @Override
    public HydrologyCaveCell readNode(DataInputStream input) throws IOException {
        HydrologyCaveAction action = actionFromCode(input.readUnsignedByte());
        String fluidProfileKey = input.readUTF();
        input.readUTF();
        return new HydrologyCaveCell(action, fluidProfileKey);
    }

    private int actionCode(HydrologyCaveAction action) {
        return switch (action) {
            case WET_SOURCE -> 1;
            case DRY_AIR -> 3;
            case SEAL_GUARD -> 4;
            case POOL_SOURCE -> 5;
            case SPRING_SOURCE -> 6;
        };
    }

    private HydrologyCaveAction actionFromCode(int code) throws IOException {
        return switch (code) {
            case 1, 2 -> HydrologyCaveAction.WET_SOURCE;
            case 3 -> HydrologyCaveAction.DRY_AIR;
            case 4 -> HydrologyCaveAction.SEAL_GUARD;
            case 5 -> HydrologyCaveAction.POOL_SOURCE;
            case 6 -> HydrologyCaveAction.SPRING_SOURCE;
            default -> throw new IOException("Unknown hydrology cave action code " + code);
        };
    }
}
