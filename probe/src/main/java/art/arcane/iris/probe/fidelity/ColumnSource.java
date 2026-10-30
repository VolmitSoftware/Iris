package art.arcane.iris.probe.fidelity;

public interface ColumnSource extends AutoCloseable {
    String label();

    int minY();

    int maxY();

    FidelityColumn column(int x, int z) throws Exception;
}
