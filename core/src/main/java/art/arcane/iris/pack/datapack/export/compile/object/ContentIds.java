package art.arcane.iris.pack.datapack.export.compile.object;

import com.google.gson.JsonElement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ContentIds {
    private static final int HASH_CHARACTERS = 12;

    private ContentIds() {
    }

    static String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_CHARACTERS);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for export resource ids", exception);
        }
    }

    static String hash(JsonElement json) {
        return hash(json.toString());
    }

    static long seed(String content) {
        return Long.parseUnsignedLong(hash(content), 16);
    }
}
