package com.civilonia.gameoflife.example.experiment;

import static org.junit.jupiter.api.Assertions.*;

import com.civilonia.gameoflife.example.Program;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExperimentTest {
    @TempDir
    Path temporary;

    @Test
    void bundledResourcesAndManifestRegenerateExactlyWithoutOverwritingRuns() throws Exception {
        Path resources = temporary.resolve("resources");
        GenerateInputs.main(new String[] {"--update-resources", resources.toString()});
        for (var experiment : ExperimentCases.all()) {
            assertArrayEquals(ExperimentFiles.resource("goli/" + experiment.id() + ".goli"),
                    Files.readAllBytes(resources.resolve("goli/" + experiment.id() + ".goli")));
        }
        assertArrayEquals(ExperimentFiles.resource("manifest.md"), Files.readAllBytes(resources.resolve("manifest.md")));
        Path root = temporary.resolve("run");
        GenerateInputs.main(new String[] {root.toString()});
        assertThrows(IOException.class, () -> GenerateInputs.main(new String[] {root.toString()}));
        assertArrayEquals(ExperimentFiles.resource("sources/gosperglidergun.rle"),
                Files.readAllBytes(root.resolve("sources/gosperglidergun.rle")));
    }

    @Test
    void resourcesHavePlannedDimensionsSizesCoordinatesAndStableHashes() throws Exception {
        long[] sizes = {23, 29, 52, 36_582_161, 100_316};
        String[] hashes = {
            "5ed09e9838e9c58855d5efb86aa60ede283749466fd92293fdd12c922dc7f746",
            "1b03017a86193efeae82d8d6d7470d7e2913100dbf4667b4e1f3ccfb176e1831",
            "fa1d4e579231374bf1020b444f321d1b17f09d6281ba6cb3a7fa38ecd6516c7e",
            "a4800d1c68063a610c1d1257ba215ea549d40a6cddff51fe36f562651a54565a",
            "048a8e7881ecd71d679ea8f89b4eeb73da4521ad1434b332e9558e0935247eca"};
        var cases = ExperimentCases.all();
        for (int i = 0; i < cases.size(); i++) {
            assertEquals(sizes[i], cases.get(i).outputBytes());
            assertEquals(hashes[i], ExperimentFiles.sha256(cases.get(i).input().toBinary()));
        }
        var r = ExperimentCases.find("r-pentomino");
        assertEquals(503, r.width());
        assertEquals(527, r.height());
        assertEquals(1103, r.transitions());
        assertTrue(r.input().initialBoard().isAlive(242, 259));
        assertFalse(r.input().initialBoard().isAlive(241, 259));
        var gun = ExperimentCases.find("gosper-glider-gun");
        assertEquals(36, gun.cells().size());
        assertTrue(gun.input().initialBoard().isAlive(25, 1));
        assertTrue(gun.input().initialBoard().isAlive(36, 4));
    }

    @Test
    void rleSupportsCommentsWhitespaceCountsAndOmittedDeadCells() {
        var pattern = RlePattern.decode("# comment\nx = 4, y = 3, rule = B3/S23\n2o$ b o $ 3bo !\n");
        assertEquals(List.of(new ExperimentCases.Cell(0, 0), new ExperimentCases.Cell(1, 0),
                new ExperimentCases.Cell(1, 1), new ExperimentCases.Cell(3, 2)), pattern.cells());
    }

    @Test
    void rleRejectsMalformedUnsupportedAndOversizedPatterns() {
        for (String payload : List.of("o", "o!o", "0o!", "5o!", "3$!", "o?!", "2!", "999999999999o!")) {
            assertThrows(IllegalArgumentException.class,
                    () -> RlePattern.decode("x = 4, y = 3, rule = B3/S23\n" + payload), payload);
        }
        for (String header : List.of("x = 0, y = 3, rule = B3/S23", "x = 4, y = 3, rule = B36/S23",
                "x = 999999999999, y = 3, rule = B3/S23", "x = 1000000, y = 2, rule = B3/S23")) {
            assertThrows(IllegalArgumentException.class, () -> RlePattern.decode(header + "\no!"));
        }
        assertThrows(IllegalArgumentException.class, () -> RlePattern.decode(" ".repeat(65_537)));
    }

    @Test
    void allSmallCasesExecuteAndExportWithIndependentGliderFrames() throws Exception {
        for (String id : List.of("block", "blinker", "glider")) {
            Path directory = prepare(id);
            RunExperiment.main(new String[] {id, directory.toString()});
            String report = Files.readString(directory.resolve("validation.json"));
            assertTrue(report.contains("\"smallPatternStatesChecked\": true"));
            assertTrue(Files.readString(directory.resolve("observations.md")).contains("Status: success"));
            assertThrows(IOException.class, () -> RunExperiment.main(new String[] {id, directory.toString()}));
            Path csv = directory.resolve("final.csv");
            ExportGeneration.main(new String[] {directory.resolve("input.goli").toString(),
                    directory.resolve("result.golf").toString(), "2", csv.toString()});
            assertTrue(Files.readString(csv).contains("generation=2"));
        }
        byte[] glider = Files.readAllBytes(temporary.resolve("glider/result.golf"));
        assertArrayEquals(HexFormat.of().parseHex("004043800000000001418200000000004283000000"
                + "000080c300000000004043800000"), Arrays.copyOfRange(glider, 17, glider.length));
        Path csv = temporary.resolve("glider/four.csv");
        ExportGeneration.main(new String[] {temporary.resolve("glider/input.goli").toString(),
                temporary.resolve("glider/result.golf").toString(), "4", csv.toString()});
        assertEquals(List.of("x,y", "2,4", "3,2", "3,4", "4,3", "4,4"),
                Files.readAllLines(csv).stream().filter(line -> !line.startsWith("#")).toList());
    }

    @Test
    void gunExecutesWithinDefaultsAndVerifierChecksAll201Boards() throws Exception {
        Path directory = prepare("gosper-glider-gun");
        RunExperiment.main(new String[] {"gosper-glider-gun", directory.toString()});
        assertEquals(100_316, Files.size(directory.resolve("result.golf")));
        assertTrue(Files.readString(directory.resolve("validation.json")).contains("\"boardsChecked\": 201"));
    }

    @Test
    void rPentominoOverridePassesComputationGateAndFailuresAreRecorded() throws Exception {
        Path directory = prepare("r-pentomino");
        var failure = assertThrows(IllegalArgumentException.class,
                () -> RunExperiment.main(new String[] {"r-pentomino", directory.toString()}));
        assertTrue(failure.getMessage().contains("computation limit"));
        assertTrue(Files.readString(directory.resolve("observations.md")).contains("Status: failed"));
        assertFalse(Files.exists(directory.resolve("validation.json")));
        String oldCells = System.getProperty("gameoflife.maxCellGenerations");
        String oldOutput = System.getProperty("gameoflife.maxOutputBytes");
        try {
            System.setProperty("gameoflife.maxCellGenerations", "292384343");
            System.setProperty("gameoflife.maxOutputBytes", "1");
            var gated = assertThrows(IllegalArgumentException.class, () -> Program.main(new String[] {
                    directory.resolve("input.goli").toString(), directory.resolve("other.golf").toString()}));
            assertTrue(gated.getMessage().contains("output byte limit"));
        } finally {
            restore("gameoflife.maxCellGenerations", oldCells);
            restore("gameoflife.maxOutputBytes", oldOutput);
        }
    }

    @Test
    void verifierRejectsHeaderMutationsAndDeletesStaleSuccess() throws Exception {
        Path directory = run("blinker");
        Path result = directory.resolve("result.golf");
        byte[] original = Files.readAllBytes(result);
        for (int offset : new int[] {0, 4, 5, 9, 13}) {
            byte[] corrupt = original.clone();
            corrupt[offset] ^= 1;
            Files.write(result, corrupt);
            assertThrows(IOException.class, () -> verify("blinker", directory));
            assertFalse(Files.exists(directory.resolve("validation.json")));
        }
    }

    @Test
    void verifierRejectsTruncationExtraBytesPaddingAndInitialMismatch() throws Exception {
        Path directory = run("blinker");
        Path result = directory.resolve("result.golf");
        byte[] original = Files.readAllBytes(result);
        byte[] padding = original.clone();
        padding[20] |= 1;
        byte[] wrongInitial = original.clone();
        wrongInitial[18] ^= 4;
        for (byte[] corrupt : List.of(Arrays.copyOf(original, original.length - 1),
                Arrays.copyOf(original, original.length + 1), new byte[0], padding, wrongInitial)) {
            Files.write(result, corrupt);
            assertThrows(IOException.class, () -> verify("blinker", directory));
        }
        Files.write(result, original);
        Files.write(directory.resolve("input.goli"), new byte[33_154]);
        assertThrows(IOException.class, () -> verify("blinker", directory));
    }

    @Test
    void verifierRejectsWrongIntermediateGliderPhaseAndBoundaryContact() throws Exception {
        Path directory = run("glider");
        Path result = directory.resolve("result.golf");
        byte[] original = Files.readAllBytes(result);
        byte[] phase = original.clone();
        System.arraycopy(original, 17, phase, 24, 7); // Correct initial/final states cannot hide a wrong t=1.
        Files.write(result, phase);
        var mismatch = assertThrows(IOException.class, () -> verify("glider", directory));
        assertTrue(mismatch.getMessage().contains("generation 1"));
        byte[] boundary = original.clone();
        boundary[24] |= (byte) 0x80;
        Files.write(result, boundary);
        var edge = assertThrows(IOException.class, () -> verify("glider", directory));
        assertTrue(edge.getMessage().contains("boundary contact at generation 1 (0,0)"));
    }

    @Test
    void exportRejectsMissingStaleReportsInvalidGenerationsAndExistingDestinations() throws Exception {
        Path directory = run("block");
        Path input = directory.resolve("input.goli");
        Path result = directory.resolve("result.golf");
        Path csv = directory.resolve("cells.csv");
        String[] args = {input.toString(), result.toString(), "1", csv.toString()};
        Path report = directory.resolve("validation.json");
        byte[] valid = Files.readAllBytes(report);
        Files.delete(report);
        assertThrows(IOException.class, () -> ExportGeneration.main(args));
        Files.writeString(report, new String(valid, java.nio.charset.StandardCharsets.UTF_8).replace("true", "false"));
        assertThrows(IOException.class, () -> ExportGeneration.main(args));
        Files.write(report, valid);
        args[2] = "3";
        assertThrows(IllegalArgumentException.class, () -> ExportGeneration.main(args));
        args[2] = "1";
        ExportGeneration.main(args);
        assertThrows(IOException.class, () -> ExportGeneration.main(args));
        args[3] = input.toString();
        assertThrows(IOException.class, () -> ExportGeneration.main(args));
        assertEquals(19, Files.size(input));
    }

    @Test
    void exportRechecksResultAfterSuccessfulReport() throws Exception {
        Path directory = run("block");
        Path result = directory.resolve("result.golf");
        byte[] corrupt = Files.readAllBytes(result);
        corrupt[21] ^= 2;
        Files.write(result, corrupt);
        assertThrows(IOException.class, () -> ExportGeneration.main(new String[] {
                directory.resolve("input.goli").toString(), result.toString(), "0",
                directory.resolve("cells.csv").toString()}));
    }

    @Test
    void exportRejectsChecksumChangesEvenWhenMainResultStillPassesStructuralChecks() throws Exception {
        Path directory = run("gosper-glider-gun");
        Path result = directory.resolve("result.golf");
        byte[] changed = Files.readAllBytes(result);
        // Interior cell at t=200: main experiments have no reference-state assertion.
        int index = 20 * 70 + 20;
        changed[17 + 200 * 499 + index / 8] ^= (byte) (1 << (7 - index % 8));
        Files.write(result, changed);
        assertDoesNotThrow(() -> VerifyResult.validate(ExperimentCases.find("gosper-glider-gun"),
                directory.resolve("input.goli"), result));
        var failure = assertThrows(IOException.class, () -> ExportGeneration.main(new String[] {
                directory.resolve("input.goli").toString(), result.toString(), "200",
                directory.resolve("cells.csv").toString()}));
        assertTrue(failure.getMessage().contains("checksums"));
    }

    private Path prepare(String id) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(id));
        Files.write(directory.resolve("input.goli"), ExperimentCases.find(id).input().toBinary());
        return directory;
    }

    private Path run(String id) throws Exception {
        Path directory = prepare(id);
        Program.main(new String[] {directory.resolve("input.goli").toString(), directory.resolve("result.golf").toString()});
        verify(id, directory);
        return directory;
    }

    private void verify(String id, Path directory) throws IOException {
        VerifyResult.main(new String[] {id, directory.resolve("input.goli").toString(),
                directory.resolve("result.golf").toString()});
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
