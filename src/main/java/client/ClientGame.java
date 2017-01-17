package client;

import game.Board;
import game.GridMark;
import game.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Rogier on 17-01-17.
 */
public class ClientGame {

    private Board board;

    private GridMark myMark = GridMark.RED;
    private GridMark theirMark = GridMark.YELLOW;

    private Random random;

    public ClientGame() {
        board = new Board();
        random = new Random();
    }

    public Move getRandomMove() {
        List<Move> movePool = new ArrayList<>();
        for (int x = 0; x < Board.GRID_SIZE_X; x++) {
            for (int y = 0; y < Board.GRID_SIZE_Y; y++) {
                Move move = new Move(x, y, myMark);
                if (board.isValidMove(move)) {
                    movePool.add(move);
                }
            }
        }

        int index = random.nextInt(movePool.size());

        return movePool.get(index);
    }

    public void enterMove(String moveString) {
        Move move = new Move(moveString, theirMark);
        board.makeMove(move);
    }

}