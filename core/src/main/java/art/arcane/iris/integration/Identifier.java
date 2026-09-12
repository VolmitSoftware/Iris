package art.arcane.iris.integration;

public record Identifier(String namespace, String key) {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    public static Identifier fromString(String id) {
        String[] strings = id.split(":", 2);
        if (strings.length == 1) {
            return new Identifier(DEFAULT_NAMESPACE, strings[0]);
        }
        return new Identifier(strings[0], strings[1]);
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}
