package art.arcane.iris.pack.datapack.export;

public interface WorldgenExportProgress {
    WorldgenExportProgress NONE = new WorldgenExportProgress() {
        @Override
        public void stage(String stage) {
        }

        @Override
        public void resources(int done, int total) {
        }
    };

    void stage(String stage);

    void resources(int done, int total);

    default void trimmed(ContentTrim trim) {
    }
}
