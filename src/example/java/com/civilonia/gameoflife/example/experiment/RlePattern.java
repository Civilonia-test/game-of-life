package com.civilonia.gameoflife.example.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded, offline B3/S23 pattern decoder used only by the experiments. */
record RlePattern(int width, int height, List<ExperimentCases.Cell> cells) {
    private static final Pattern HEADER = Pattern.compile(
            "x\\s*=\\s*(\\d+)\\s*,\\s*y\\s*=\\s*(\\d+)\\s*,\\s*rule\\s*=\\s*B3/S23");

    static RlePattern decode(String text) {
        if (text.length() > 65_536) {
            throw new IllegalArgumentException("RLE exceeds 64 KiB");
        }
        List<String> lines = text.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("missing RLE header");
        }
        var header = HEADER.matcher(lines.getFirst());
        if (!header.matches()) {
            throw new IllegalArgumentException("expected RLE dimensions and B3/S23 rule");
        }
        int width = Integer.parseInt(header.group(1));
        int height = Integer.parseInt(header.group(2));
        if (width <= 0 || height <= 0 || (long) width * height > 1_000_000) {
            throw new IllegalArgumentException("RLE dimensions exceed sample limits");
        }
        String payload = String.join("", lines.subList(1, lines.size())).replaceAll("\\s", "");
        List<ExperimentCases.Cell> cells = new ArrayList<>();
        int x = 0;
        int y = 0;
        long count = 0;
        boolean digits = false;
        for (int i = 0; i < payload.length(); i++) {
            char token = payload.charAt(i);
            if (token >= '0' && token <= '9') {
                count = count * 10 + token - '0';
                if (count > 1_000_000) {
                    throw new IllegalArgumentException("RLE run exceeds sample limits");
                }
                digits = true;
                continue;
            }
            if (token == '!') {
                if (digits || i != payload.length() - 1) {
                    throw new IllegalArgumentException("invalid RLE terminator or trailing tokens");
                }
                return new RlePattern(width, height, List.copyOf(cells));
            }
            int run = digits ? (int) count : 1;
            if (run == 0) {
                throw new IllegalArgumentException("RLE run must be positive");
            }
            if (token == '$') {
                if ((long) y + run >= height) {
                    throw new IllegalArgumentException("RLE rows exceed declared height");
                }
                y += run;
                x = 0;
            } else if (token == 'b' || token == 'o') {
                if ((long) x + run > width) {
                    throw new IllegalArgumentException("RLE run exceeds declared width");
                }
                if (token == 'o') {
                    for (int j = 0; j < run; j++) {
                        cells.add(new ExperimentCases.Cell(x + j, y));
                    }
                }
                x += run;
            } else {
                throw new IllegalArgumentException("invalid RLE token: " + token);
            }
            count = 0;
            digits = false;
        }
        throw new IllegalArgumentException("missing RLE terminator");
    }
}
