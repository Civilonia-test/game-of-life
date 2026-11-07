package com.civilonia.gameoflife.example.experiment;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.util.Arrays;
import java.util.HexFormat;

/** Independent streaming GOLF verifier for the five fixed experiment conditions. */
public final class VerifyResult {
    private VerifyResult() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: VerifyResult <case-id> <input.goli> <result.golf>");
        }
        Path input = Path.of(args[1]);
        Path result = Path.of(args[2]);
        Path report = reportPath(result);
        ExperimentFiles.distinct(input, result);
        ExperimentFiles.distinct(input, report);
        ExperimentFiles.distinct(result, report);
        Files.deleteIfExists(report); // A failing attempt must not leave a previous success report.
        Verified verified = validate(ExperimentCases.find(args[0]), input, result);
        ExperimentFiles.create(report, verified.json().getBytes(StandardCharsets.UTF_8));
        System.out.println("Verified " + args[0] + ": " + result);
    }

    static Path reportPath(Path result) {
        return result.toAbsolutePath().resolveSibling("validation.json");
    }

    /** Hash-bound report. It certifies structure and small fixtures, not scientific conclusions. */
    record Verified(ExperimentCases.Case experiment, String inputSha256, String resultSha256,
            byte[] selectedBoard) {
        String json() {
            return """
                    {
                      "schema": 1,
                      "valid": true,
                      "case": "%s",
                      "width": %d,
                      "height": %d,
                      "transitions": %d,
                      "boardsChecked": %d,
                      "boundaryClear": true,
                      "smallPatternStatesChecked": %s,
                      "inputSha256": "%s",
                      "resultSha256": "%s"
                    }
                    """.formatted(experiment.id(), experiment.width(), experiment.height(),
                    experiment.transitions(), experiment.transitions() + 1,
                    experiment.expected(0) != null, inputSha256, resultSha256);
        }
    }

    static Verified validate(ExperimentCases.Case experiment, Path inputPath, Path resultPath)
            throws IOException {
        return validate(experiment, inputPath, resultPath, -1);
    }

    static Verified validate(ExperimentCases.Case experiment, Path inputPath, Path resultPath,
            int selectedGeneration) throws IOException {
        ExperimentFiles.distinct(inputPath, resultPath);
        byte[] input = ExperimentFiles.readBounded(inputPath, 17 + experiment.bytesPerBoard());
        if (!Arrays.equals(input, experiment.input().toBinary())) {
            throw new IOException("input does not match planned case: " + experiment.id());
        }
        if (Files.size(resultPath) != experiment.outputBytes()) {
            throw new IOException("GOLF length must be exactly " + experiment.outputBytes() + " bytes");
        }
        var digest = ExperimentFiles.digest();
        byte[] selectedBoard = null;
        try (var stream = new DataInputStream(new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(resultPath)), digest))) {
            if (stream.readInt() != 0x474f4c46 || stream.readUnsignedByte() != 1) {
                throw new IOException("expected GOLF v1 header");
            }
            if (Integer.toUnsignedLong(stream.readInt()) != experiment.width()
                    || Integer.toUnsignedLong(stream.readInt()) != experiment.height()
                    || Integer.toUnsignedLong(stream.readInt()) != experiment.transitions()) {
                throw new IOException("GOLF conditions do not match input");
            }
            byte[] board = new byte[experiment.bytesPerBoard()];
            for (int generation = 0; generation <= experiment.transitions(); generation++) {
                stream.readFully(board);
                checkBoard(experiment, input, board, generation);
                if (generation == selectedGeneration) {
                    selectedBoard = board.clone();
                }
            }
            if (stream.read() != -1) {
                throw new IOException("unexpected trailing GOLF bytes");
            }
        }
        return new Verified(experiment, ExperimentFiles.sha256(input),
                HexFormat.of().formatHex(digest.digest()), selectedBoard);
    }

    private static void checkBoard(ExperimentCases.Case experiment, byte[] input, byte[] board,
            int generation) throws IOException {
        int cells = experiment.width() * experiment.height();
        int remainder = cells % 8;
        if (remainder != 0 && (board[board.length - 1] & ((1 << (8 - remainder)) - 1)) != 0) {
            throw new IOException("nonzero padding at generation " + generation);
        }
        if (generation == 0 && !Arrays.equals(board, Arrays.copyOfRange(input, 17, input.length))) {
            throw new IOException("initial output differs from input at generation 0");
        }
        var expectedCells = experiment.expected(generation);
        boolean[] expected = null;
        if (expectedCells != null) {
            expected = new boolean[cells];
            for (var cell : expectedCells) {
                expected[cell.y() * experiment.width() + cell.x()] = true;
            }
        }
        for (int y = 0; y < experiment.height(); y++) {
            for (int x = 0; x < experiment.width(); x++) {
                int index = y * experiment.width() + x;
                boolean alive = alive(board, index);
                if (alive && (x == 0 || y == 0 || x == experiment.width() - 1
                        || y == experiment.height() - 1)) {
                    throw new IOException("boundary contact at generation " + generation + " (" + x + "," + y + ")");
                }
                if (expected != null && alive != expected[index]) {
                    throw new IOException("known-pattern mismatch at generation " + generation + " (" + x + "," + y + ")");
                }
            }
        }
    }

    static boolean alive(byte[] board, int index) {
        return (board[index / 8] & (1 << (7 - index % 8))) != 0;
    }
}
