package art.arcane.iris.util.hunk;

import art.arcane.volmlib.util.hunk.HunkLike;

final class SharedHunkAdapter<T> implements Hunk<T> {
    private final HunkLike<T> delegate;

    SharedHunkAdapter(HunkLike<T> delegate) {
        this.delegate = delegate;
    }

    HunkLike<T> delegate() {
        return delegate;
    }

    @Override
    public int getWidth() {
        return delegate.getWidth();
    }

    @Override
    public int getDepth() {
        return delegate.getDepth();
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        delegate.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return delegate.getRaw(x, y, z);
    }

    @Override
    public boolean isMapped() {
        return delegate instanceof art.arcane.volmlib.util.hunk.storage.MappedHunk<?>
                || delegate instanceof art.arcane.volmlib.util.hunk.storage.MappedSyncHunk<?>;
    }

    @Override
    public int getEntryCount() {
        if (delegate instanceof art.arcane.volmlib.util.hunk.storage.MappedHunk<?> mapped) {
            return mapped.getEntryCount();
        }

        if (delegate instanceof art.arcane.volmlib.util.hunk.storage.MappedSyncHunk<?> mapped) {
            return mapped.getEntryCount();
        }

        return Hunk.super.getEntryCount();
    }

    @Override
    public boolean isAtomic() {
        return delegate instanceof art.arcane.volmlib.util.hunk.storage.AtomicHunk<?>;
    }
}
