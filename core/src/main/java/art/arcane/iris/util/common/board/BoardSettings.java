package art.arcane.iris.util.board;

import art.arcane.volmlib.util.board.BoardProvider;

public class BoardSettings {
    private final BoardProvider boardProvider;
    private final ScoreDirection scoreDirection;

    private BoardSettings(BoardProvider boardProvider, ScoreDirection scoreDirection) {
        this.boardProvider = boardProvider;
        this.scoreDirection = scoreDirection;
    }

    public BoardProvider getBoardProvider() {
        return boardProvider;
    }

    public ScoreDirection getScoreDirection() {
        return scoreDirection;
    }

    public art.arcane.volmlib.util.board.BoardSettings toShared() {
        return art.arcane.volmlib.util.board.BoardSettings.builder()
                .boardProvider(boardProvider)
                .scoreDirection(scoreDirection.toShared())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BoardProvider boardProvider;
        private ScoreDirection scoreDirection;

        public Builder boardProvider(BoardProvider boardProvider) {
            this.boardProvider = boardProvider;
            return this;
        }

        public Builder scoreDirection(ScoreDirection scoreDirection) {
            this.scoreDirection = scoreDirection;
            return this;
        }

        public BoardSettings build() {
            return new BoardSettings(boardProvider, scoreDirection);
        }
    }
}
