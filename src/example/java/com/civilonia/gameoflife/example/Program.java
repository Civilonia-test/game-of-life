package com.civilonia.gameoflife.example;

import com.civilonia.gameoflife.GameOfLifeSimulator;
import com.civilonia.gameoflife.input.SimulationInput;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a Game of Life simulation from a {@code GOLI} input file.
 */
public final class Program {

    private static final long DEFAULT_MAX_INPUT_BYTES = 1L << 20;
    private static final long DEFAULT_MAX_CELLS = 1_000_000L;
    private static final long DEFAULT_MAX_TRANSITIONS = 10_000L;
    private static final long DEFAULT_MAX_CELL_GENERATIONS = 10_000_000L;
    private static final long DEFAULT_MAX_OUTPUT_BYTES = 64L << 20;

    private Program() {
    }

    /**
     * Reads a {@code GOLI} input file and writes the resulting {@code GOLF} simulation.
     *
     * @param args {@code <input.goli> [output.golf]}; without an output path, writes binary data
     *     to standard output
     * @throws IOException if an input or output file cannot be read or written
     * @throws IllegalArgumentException if the arguments or the {@code GOLI} document are invalid
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("usage: Program <input.goli> [output.golf]");
        }

        Path inputPath = Path.of(args[0]);
        long maxInputBytes = limit("gameoflife.maxInputBytes", DEFAULT_MAX_INPUT_BYTES);
        byte[] encodedInput = readBoundedInput(inputPath, maxInputBytes);
        SimulationInput input = SimulationInput.fromBinary(encodedInput);
        validateSimulationLimits(input);

        if (args.length == 1) {
            GameOfLifeSimulator.create(System.out).simulate(input);
            System.out.flush();
            if (System.out.checkError()) {
                throw new IOException("failed to write simulation output to standard output");
            }
            return;
        }

        Path outputPath = Path.of(args[1]);
        Path outputDirectory = outputPath.getParent();
        if (outputDirectory != null) {
            Files.createDirectories(outputDirectory);
        }
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
            GameOfLifeSimulator.create(output).simulate(input);
        }

        System.err.println("Wrote " + outputPath);
    }

    private static byte[] readBoundedInput(Path inputPath, long maxInputBytes) throws IOException {
        if (maxInputBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("gameoflife.maxInputBytes must be less than "
                    + Integer.MAX_VALUE);
        }

        try (InputStream source = Files.newInputStream(inputPath);
                ByteArrayOutputStream encoded = new ByteArrayOutputStream(
                        (int) Math.min(maxInputBytes, 8_192))) {
            byte[] buffer = new byte[8_192];
            long bytesRead = 0;
            while (true) {
                int maximumRead = (int) Math.min(buffer.length, maxInputBytes - bytesRead + 1);
                int count = source.read(buffer, 0, maximumRead);
                if (count == -1) {
                    return encoded.toByteArray();
                }
                if (count > maxInputBytes - bytesRead) {
                    throw new IllegalArgumentException("input file exceeds the configured byte limit");
                }
                encoded.write(buffer, 0, count);
                bytesRead += count;
            }
        }
    }

    private static void validateSimulationLimits(SimulationInput input) {
        long cells = Math.multiplyExact((long) input.initialBoard().width(), input.initialBoard().height());
        long transitions = input.generations();
        if (cells > limit("gameoflife.maxCells", DEFAULT_MAX_CELLS)) {
            throw new IllegalArgumentException("board exceeds the configured cell limit");
        }
        if (transitions > limit("gameoflife.maxTransitions", DEFAULT_MAX_TRANSITIONS)) {
            throw new IllegalArgumentException("simulation exceeds the configured transition limit");
        }

        long cellGenerations = Math.multiplyExact(cells, transitions);
        if (cellGenerations > limit("gameoflife.maxCellGenerations", DEFAULT_MAX_CELL_GENERATIONS)) {
            throw new IllegalArgumentException("simulation exceeds the configured computation limit");
        }

        long bytesPerBoard = (cells + Byte.SIZE - 1) / Byte.SIZE;
        long outputBytes = Math.addExact(17L, Math.multiplyExact(transitions + 1, bytesPerBoard));
        if (outputBytes > limit("gameoflife.maxOutputBytes", DEFAULT_MAX_OUTPUT_BYTES)) {
            throw new IllegalArgumentException("simulation exceeds the configured output byte limit");
        }
    }

    private static long limit(String propertyName, long defaultValue) {
        String configuredValue = System.getProperty(propertyName);
        if (configuredValue == null) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(configuredValue);
            if (value <= 0) {
                throw new IllegalArgumentException(propertyName + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(propertyName + " must be a positive integer", exception);
        }
    }
}
