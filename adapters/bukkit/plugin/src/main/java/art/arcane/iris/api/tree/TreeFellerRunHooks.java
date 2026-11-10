package art.arcane.iris.api.tree;

public interface TreeFellerRunHooks {
    default boolean requiresSneaking() {
        return true;
    }

    TreeFellerRunHooks NONE = new TreeFellerRunHooks() {
        @Override
        public void onActivationAccepted() {
        }

        @Override
        public boolean reserveLogCost() {
            return true;
        }

        @Override
        public void commitLogCost() {
        }

        @Override
        public void refundLogCost() {
        }
    };

    void onActivationAccepted();

    boolean reserveLogCost();

    void commitLogCost();

    void refundLogCost();
}
