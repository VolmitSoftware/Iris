package art.arcane.iris.util.common.board;

public enum ScoreDirection {
    UP,
    DOWN;

    public art.arcane.volmlib.util.board.ScoreDirection toShared() {
        return this == UP
                ? art.arcane.volmlib.util.board.ScoreDirection.UP
                : art.arcane.volmlib.util.board.ScoreDirection.DOWN;
    }

    public static ScoreDirection fromShared(art.arcane.volmlib.util.board.ScoreDirection direction) {
        return direction == art.arcane.volmlib.util.board.ScoreDirection.UP ? UP : DOWN;
    }
}
