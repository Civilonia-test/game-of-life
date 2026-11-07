package com.civilonia.gameoflife.example.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;

/** Exports a generation only after revalidation and a matching successful report. */
public final class ExportGeneration {
    private ExportGeneration() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: ExportGeneration <input.goli> <result.golf> <generation> <output.csv>");
        }
        Path input = Path.of(args[0]);
        Path result = Path.of(args[1]);
        int generation = Integer.parseInt(args[2]);
        Path output = Path.of(args[3]);
        Path report = VerifyResult.reportPath(result);
        ExperimentFiles.distinct(output, input);
        ExperimentFiles.distinct(output, result);
        ExperimentFiles.distinct(output, report);
        var experiment = ExperimentCases.identify(ExperimentFiles.readBounded(input, 33_153));
        if (generation < 0 || generation > experiment.transitions()) {
            throw new IllegalArgumentException("generation outside 0.." + experiment.transitions());
        }
        String recorded = new String(ExperimentFiles.readBounded(report, 4_096), StandardCharsets.UTF_8);
        var verified = VerifyResult.validate(experiment, input, result, generation);
        if (!recorded.equals(verified.json())) {
            throw new IOException("validation report does not match input/result checksums and conditions");
        }
        byte[] board = verified.selectedBoard();
        var cells = new ArrayList<ExperimentCases.Cell>();
        for (int y = 0; y < experiment.height(); y++) {
            for (int x = 0; x < experiment.width(); x++) {
                if (VerifyResult.alive(board, y * experiment.width() + x)) {
                    cells.add(new ExperimentCases.Cell(x, y));
                }
            }
        }
        StringBuilder csv = new StringBuilder("# case=" + experiment.id() + "; generation=" + generation
                + "; origin=top-left; x=right; y=down; sorted=x,y\n# inputSha256="
                + verified.inputSha256() + "\n# resultSha256=" + verified.resultSha256() + "\nx,y\n");
        cells.stream().sorted().forEach(cell -> csv.append(cell.x()).append(',').append(cell.y()).append('\n'));
        ExperimentFiles.create(output, csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
