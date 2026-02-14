package art.arcane.iris.util.board;

public class BoardEntry {
    private final art.arcane.volmlib.util.board.BoardEntry delegate;

    private BoardEntry(art.arcane.volmlib.util.board.BoardEntry delegate) {
        this.delegate = delegate;
    }

    public String getPrefix() {
        return delegate.getPrefix();
    }

    public String getSuffix() {
        return delegate.getSuffix();
    }

    public static BoardEntry translateToEntry(String input) {
        return new BoardEntry(art.arcane.volmlib.util.board.BoardEntry.translateToEntry(input));
    }
}
