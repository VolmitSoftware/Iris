package art.arcane.iris;

// The constants are replaced before compilation
public interface BuildConstants {
    String ENVIRONMENT = "${environment}";
    String COMMIT = "${commit}";
}