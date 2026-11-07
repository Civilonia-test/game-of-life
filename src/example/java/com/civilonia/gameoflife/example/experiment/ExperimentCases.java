package com.civilonia.gameoflife.example.experiment;

import com.civilonia.gameoflife.input.SimulationInput;
import com.civilonia.gameoflife.model.Board;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Fixed conditions and independent small-pattern states from the simulation plan. */
public final class ExperimentCases {
    private ExperimentCases() { }

    /** A live-cell coordinate; natural ordering is x then y. */
    public record Cell(int x, int y) implements Comparable<Cell> {
        @Override
        public int compareTo(Cell other) {
            return Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).compare(this, other);
        }
    }

    /** A complete, immutable experiment definition. */
    public record Case(String id, int width, int height, int transitions, List<Cell> cells) {
        public Case {
            cells = List.copyOf(cells);
        }

        public SimulationInput input() {
            Board.Builder board = Board.builder(width, height);
            for (Cell cell : cells) {
                board.setAlive(cell.x(), cell.y(), true);
            }
            return new SimulationInput(board.build(), transitions);
        }

        public int bytesPerBoard() {
            return Math.toIntExact(((long) width * height + 7) / 8);
        }

        public long outputBytes() {
            return 17L + ((long) transitions + 1) * bytesPerBoard();
        }

        /** Returns a literal expected state, or null for a main experiment. */
        public List<Cell> expected(int generation) {
            return switch (id) {
                case "block" -> cells;
                case "blinker" -> generation % 2 == 0 ? cells : coordinates(2, 1, 2, 2, 2, 3);
                case "glider" -> switch (generation) {
                    case 0 -> cells;
                    case 1 -> coordinates(1, 2, 2, 3, 2, 4, 3, 2, 3, 3);
                    case 2 -> coordinates(1, 3, 2, 4, 3, 2, 3, 3, 3, 4);
                    case 3 -> coordinates(2, 2, 2, 4, 3, 3, 3, 4, 4, 3);
                    case 4 -> coordinates(2, 4, 3, 2, 3, 4, 4, 3, 4, 4);
                    default -> throw new IllegalArgumentException("invalid glider generation");
                };
                default -> null;
            };
        }
    }

    public static List<Case> all() throws IOException {
        RlePattern gun = RlePattern.decode(new String(
                ExperimentFiles.resource("sources/gosperglidergun.rle"), StandardCharsets.UTF_8));
        if (gun.width() != 36 || gun.height() != 9 || gun.cells().size() != 36
                || gun.cells().stream().mapToInt(Cell::x).min().orElse(-1) != 0
                || gun.cells().stream().mapToInt(Cell::x).max().orElse(-1) != 35
                || gun.cells().stream().mapToInt(Cell::y).min().orElse(-1) != 0
                || gun.cells().stream().mapToInt(Cell::y).max().orElse(-1) != 8) {
            throw new IllegalArgumentException("gun must have 36 cells with a 36 x 9 bounding box");
        }
        return List.of(
                new Case("block", 4, 4, 2, coordinates(1, 1, 2, 1, 1, 2, 2, 2)),
                new Case("blinker", 5, 5, 2, coordinates(1, 2, 2, 2, 3, 2)),
                new Case("glider", 7, 7, 4, coordinates(2, 1, 3, 2, 1, 3, 2, 3, 3, 3)),
                new Case("r-pentomino", 503, 527, 1103,
                        coordinates(242, 259, 243, 259, 241, 260, 242, 260, 242, 261)),
                new Case("gosper-glider-gun", 70, 57, 200,
                        gun.cells().stream().map(c -> new Cell(c.x() + 1, c.y() + 1)).sorted().toList()));
    }

    public static Case find(String id) throws IOException {
        return all().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown experiment: " + id));
    }

    static Case identify(byte[] input) throws IOException {
        for (Case experiment : all()) {
            if (Arrays.equals(input, experiment.input().toBinary())) {
                return experiment;
            }
        }
        throw new IllegalArgumentException("input does not match a planned experiment");
    }

    private static List<Cell> coordinates(int... values) {
        var cells = new java.util.ArrayList<Cell>();
        for (int i = 0; i < values.length; i += 2) {
            cells.add(new Cell(values[i], values[i + 1]));
        }
        return cells.stream().sorted().toList();
    }
}
