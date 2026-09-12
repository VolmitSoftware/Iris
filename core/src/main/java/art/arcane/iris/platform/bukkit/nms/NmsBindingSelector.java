package art.arcane.iris.platform.bukkit.nms;

final class NmsBindingSelector {
    private static final String SUPPORTED_VERSIONS = "26.1.2 or 26.2";
    private static final String SUPPORTED_TAG = "v26_2_R1";

    private NmsBindingSelector() {
    }

    static String select(MinecraftVersion version) {
        if (version == null) {
            throw new IllegalStateException("Iris requires an exact Minecraft version before selecting NMS");
        }
        if (!version.isSameRelease(26, 2, 0) && !version.isSameRelease(26, 1, 2)) {
            throw new IllegalStateException("Iris requires Minecraft " + SUPPORTED_VERSIONS
                    + ". Detected server version: " + version.value());
        }
        return SUPPORTED_TAG;
    }
}
