package com.civilonia.gameoflife.example.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Generates checked experiment inputs or explicitly updates their committed resources. */
public final class GenerateInputs {
    private GenerateInputs() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("--update-resources")) {
            updateResources(Path.of(args[1]));
        } else if (args.length == 1) {
            generate(Path.of(args[0]));
        } else {
            throw new IllegalArgumentException(
                    "usage: GenerateInputs <new-run-root> | --update-resources <experiments-resource-root>");
        }
    }

    static void generate(Path root) throws IOException {
        List<ExperimentCases.Case> cases = ExperimentCases.all();
        for (var experiment : cases) {
            if (!Arrays.equals(experiment.input().toBinary(),
                    ExperimentFiles.resource("goli/" + experiment.id() + ".goli"))) {
                throw new IOException("committed input differs from definition: " + experiment.id());
            }
        }
        byte[] manifest = manifest(cases).getBytes(StandardCharsets.UTF_8);
        if (!Arrays.equals(manifest, ExperimentFiles.resource("manifest.md"))) {
            throw new IOException("committed experiment manifest differs from definitions");
        }
        Path absolute = root.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Files.createDirectory(absolute); // An existing run, even a partial one, is never overwritten.
        ExperimentFiles.create(root.resolve("manifest.md"), manifest);
        Files.createDirectory(root.resolve("sources"));
        ExperimentFiles.create(root.resolve("sources/gosperglidergun.rle"),
                ExperimentFiles.resource("sources/gosperglidergun.rle"));
        for (var experiment : cases) {
            Path directory = Files.createDirectory(root.resolve(experiment.id()));
            ExperimentFiles.create(directory.resolve("input.goli"), experiment.input().toBinary());
        }
    }

    private static void updateResources(Path root) throws IOException {
        List<ExperimentCases.Case> cases = ExperimentCases.all();
        Files.createDirectories(root.resolve("goli"));
        Files.createDirectories(root.resolve("sources"));
        Files.write(root.resolve("sources/gosperglidergun.rle"),
                ExperimentFiles.resource("sources/gosperglidergun.rle"));
        for (var experiment : cases) {
            Files.write(root.resolve("goli/" + experiment.id() + ".goli"), experiment.input().toBinary());
        }
        Files.writeString(root.resolve("manifest.md"), manifest(cases), StandardCharsets.UTF_8);
    }

    private static String manifest(List<ExperimentCases.Case> cases) throws IOException {
        StringBuilder text = new StringBuilder("# Experiment input manifest\n\n"
                + "Coordinates are zero-based: x right, y down; all other cells are dead.\n"
                + "Coordinate lists are sorted by x, then y. All cases use B3/S23 and fixed dead boundaries.\n\n"
                + "## RLE conversion\n\n"
                + "Resource: `sources/gosperglidergun.rle`\n\nSHA-256: `"
                + ExperimentFiles.sha256(ExperimentFiles.resource("sources/gosperglidergun.rle"))
                + "`\n\nDecode o/b runs and $ rows, preserving orientation and phase. Validate 36 live\n"
                + "cells in a 36 x 9 bounding box, then translate each coordinate by (1,1).\n"
                + "Other inputs are generated directly from the absolute coordinates below.\n"
                + "Encode with SimulationInput.toBinary() as GOLI v1.\n");
        for (var experiment : cases) {
            text.append("\n## ").append(experiment.id()).append("\n\nResource: `goli/")
                    .append(experiment.id()).append(".goli`\n\nBoard: ")
                    .append(experiment.width()).append(" x ").append(experiment.height())
                    .append("; transitions: ").append(experiment.transitions())
                    .append("; live cells: ").append(experiment.cells().size())
                    .append("\n\nSHA-256: `").append(ExperimentFiles.sha256(experiment.input().toBinary()))
                    .append("`\n\nInitial coordinates:");
            for (var cell : experiment.cells()) {
                text.append(" (").append(cell.x()).append(',').append(cell.y()).append(')');
            }
            text.append('\n');
        }
        return text.toString();
    }
}
