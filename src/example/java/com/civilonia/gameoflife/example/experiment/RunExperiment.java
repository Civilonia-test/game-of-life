package com.civilonia.gameoflife.example.experiment;

import com.civilonia.gameoflife.example.Program;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

/** Runs Program and verification, preserving failures and recording execution metadata. */
public final class RunExperiment {
    private RunExperiment() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: RunExperiment <case-id> <case-directory>");
        }
        var experiment = ExperimentCases.find(args[0]);
        Path directory = Path.of(args[1]);
        Path input = directory.resolve("input.goli");
        Path result = directory.resolve("result.golf");
        Path observations = directory.resolve("observations.md");
        Path report = VerifyResult.reportPath(result);
        byte[] bytes = ExperimentFiles.readBounded(input, 17 + experiment.bytesPerBoard());
        if (!Arrays.equals(bytes, experiment.input().toBinary())) {
            throw new IOException("input does not match planned case: " + experiment.id());
        }
        if (Files.exists(result) || Files.exists(observations) || Files.exists(report)) {
            throw new IOException("run artifacts already exist; use a new run directory");
        }
        String metadata = metadata(directory, bytes);
        ExperimentFiles.create(result, new byte[0]);
        ExperimentFiles.create(observations, (metadata + "\nStatus: running\n").getBytes(StandardCharsets.UTF_8));
        long started = System.nanoTime();
        String status = "failed (partial output is not a validated result)";
        try {
            Program.main(new String[] {input.toString(), result.toString()});
            VerifyResult.main(new String[] {experiment.id(), input.toString(), result.toString()});
            status = "success (execution and structural/fixture verification only)";
        } catch (IOException | RuntimeException exception) {
            status = "failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
            throw exception;
        } finally {
            String completion = "\nStatus: " + status + "\n\nElapsed seconds (execution + verification): "
                    + (System.nanoTime() - started) / 1_000_000_000.0
                    + "\n\nArtifacts: input.goli, result.golf, validation.json (only on success).\n"
                    + "Result SHA-256: " + hashFile(result)
                    + "\n\nScientific observations: not evaluated by this runner.\n"
                    + "Preservation: copy this directory and the run-root manifest/sources outside build/.\n";
            Files.writeString(observations, metadata + completion, StandardCharsets.UTF_8);
        }
    }

    private static String metadata(Path directory, byte[] input) throws IOException {
        var compiled = ExperimentFiles.digest();
        for (String name : new String[] {"GameOfLifeSimulator", "model/Board", "model/Board$Builder",
                "input/SimulationInput", "core/ConwayTransition", "core/SimulationConditions",
                "core/SimulationOutput", "output/BinaryStreamEncoder"}) {
            try (var stream = RunExperiment.class.getResourceAsStream("/com/civilonia/gameoflife/" + name + ".class")) {
                if (stream == null) {
                    throw new IOException("missing compiled library class: " + name);
                }
                compiled.update(name.getBytes(StandardCharsets.UTF_8));
                compiled.update(stream.readAllBytes());
            }
        }
        return "# Execution record\n\nCommand: "
                + ProcessHandle.current().info().commandLine().orElse("unavailable")
                + "\n\nSimulator version: " + System.getProperty("gameoflife.simulatorVersion", "development")
                + "\nCompiled library SHA-256 (ordered class names and bytes): "
                + HexFormat.of().formatHex(compiled.digest())
                + "\nInput SHA-256: " + ExperimentFiles.sha256(input)
                + "\nJava: " + System.getProperty("java.runtime.version")
                + "\nOS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch")
                + "\nAvailable processors: " + Runtime.getRuntime().availableProcessors()
                + "\nJVM max memory bytes: " + Runtime.getRuntime().maxMemory()
                + "\nJVM free memory bytes at start: " + Runtime.getRuntime().freeMemory()
                + "\nUsable filesystem bytes at start: " + Files.getFileStore(directory).getUsableSpace()
                + "\n\nEffective Program limits:\n"
                + limit("maxInputBytes", "1048576") + limit("maxCells", "1000000")
                + limit("maxTransitions", "10000") + limit("maxCellGenerations", "10000000")
                + limit("maxOutputBytes", "67108864");
    }

    private static String limit(String name, String fallback) {
        return "- gameoflife." + name + "=" + System.getProperty("gameoflife." + name, fallback) + "\n";
    }

    private static String hashFile(Path path) throws IOException {
        var digest = ExperimentFiles.digest();
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
