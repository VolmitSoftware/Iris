package art.arcane.iris.util.project.hunk.storage;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.bits.Writable;
import java.io.IOException;
import java.util.function.Supplier;

public abstract class PaletteOrHunk<T> extends art.arcane.volmlib.util.hunk.storage.PaletteOrHunk<T> implements Hunk<T>, Writable<T> {
    public PaletteOrHunk(int width, int height, int depth, boolean allow, Supplier<Hunk<T>> factory) {
        super(width, height, depth, allow, factory::get);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DataContainer<T> palette() {
        return (DataContainer<T>) super.palette();
    }

    public void setPalette(DataContainer<T> c) {
        super.setPalette(c);
    }

    @Override
    public PaletteOrHunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        super.iterateSync(c);
        return this;
    }

    @Override
    public PaletteOrHunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        super.iterateSyncIO(c);
        return this;
    }
}
