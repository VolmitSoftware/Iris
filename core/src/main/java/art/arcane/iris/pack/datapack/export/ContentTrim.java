package art.arcane.iris.pack.datapack.export;

public record ContentTrim(int filesBefore, long bytesBefore, int filesAfter, long bytesAfter) {
    public static final ContentTrim NONE = new ContentTrim(0, 0L, 0, 0L);

    public ContentTrim {
        if (filesBefore < 0 || bytesBefore < 0L || filesAfter < 0 || bytesAfter < 0L) {
            throw new IllegalArgumentException("Content trim counts cannot be negative");
        }
        if (filesAfter > filesBefore || bytesAfter > bytesBefore) {
            throw new IllegalArgumentException("A content trim cannot add files or bytes");
        }
    }

    public int filesRemoved() {
        return filesBefore - filesAfter;
    }

    public long bytesRemoved() {
        return bytesBefore - bytesAfter;
    }
}
