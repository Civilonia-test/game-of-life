# Game of Life simulation plan

This document defines the experiments, initial data, expected behaviors, and
execution/observation procedures. The [simulation specification](simulation-specification.md)
defines the shared model, formats, and sample code design. Both documents are
local to this repository; no external research documents or pattern downloads
are required.

The experiment resources and helper classes described here are planned work;
they are not implemented yet. Commands using those helpers become executable
only after implementation. Existing library regression examples remain separate.

## Objectives and common conditions

The objective is to reproduce the emergence and evolution of structures in
R-pentomino and sustained glider production in Gosper glider gun. First validate
the pipeline with isolated blocks, blinkers, and gliders. All experiments use
B3/S23, synchronous updates, fixed dead boundaries, and generation zero as the
initial state. Every generation is saved.

## Sample resources and initial conditions

Commit the five generated GOLI files under
`src/example/resources/experiments/goli/`, using the case IDs below as file
stems. Store the RLE data specified below under
`src/example/resources/experiments/sources/`. Include a local input manifest
recording each case's resource filename, checksum, initial coordinates, and
conversion procedure. Generate all resources deterministically from the data
in this document, without downloading patterns. Never silently rotate, reflect,
or recenter a pattern.

All unspecified cells are dead. Coordinates are absolute unless an offset is
explicitly stated. The small boards ensure no live cell reaches an edge during
their observation periods.

| Case ID | Board | Initial live cells | Transitions N | Expected behavior |
| --- | --- | --- | ---: | --- |
| `block` | 4 × 4 | `(1,1)`, `(2,1)`, `(1,2)`, `(2,2)` | 2 | Unchanged at generations 1 and 2. |
| `blinker` | 5 × 5 | `(1,2)`, `(2,2)`, `(3,2)` | 2 | Vertical at generation 1: `(2,1)`, `(2,2)`, `(2,3)`; generation 2 equals generation 0. |
| `glider` | 7 × 7 | `(2,1)`, `(3,2)`, `(1,3)`, `(2,3)`, `(3,3)` | 4 | At generation 4, exactly the initial live-cell set translated by `(1,1)`. |
| `r-pentomino` | 503 × 527 | `(242,259)`, `(243,259)`, `(241,260)`, `(242,260)`, `(242,261)` | 1103 | Observe the long transient and compare reported structures, including six emitted gliders. |
| `gosper-glider-gun` | 70 × 57 | Decode the RLE below, then offset every live cell by `(1,1)`; 36 live cells | 200 | Compare repeated emissions with the reported 30-generation interval. |

The experimental blinker starts horizontally, unlike the existing vertical
blinker fixture. The experimental glider translates the existing orientation
into a larger board: the existing 5 × 5 fixture has live cells on its initial
boundary and cannot satisfy the experiment's boundary check. Keep both sets of
resources distinct.

R-pentomino is the following pattern with its top-left at `(241,259)`:

```text
.OO
OO.
.O.
```

Create `sources/gosperglidergun.rle` from the following complete pattern data.
The RLE header and payload define the required orientation and phase; no
external pattern file is needed.

```text
x = 36, y = 9, rule = B3/S23
24bo11b$22bobo11b$12b2o6b2o12b2o$11bo3bo4b2o12b2o$2o8bo5bo3b2o14b$2o8b
o3bob2o4bobo11b$10bo5bo7bo11b$11bo3bo20b$12b2o!
```

Before placement, verify a 36 × 9 bounding box and 36 live cells. After
applying offset `(1,1)`, verify that every cell fits the 70 × 57 board. Record
the decoded coordinates in the local input manifest. RLE decoding behavior is
defined in the [sample code design](simulation-specification.md#rle-decoding).

## Execution commands and resource limits

After implementation, run from this repository with Java 21. Build the existing
example source set, generate the inputs, and run and verify all three small
cases before either main experiment. The proposed generator's single argument
is the run root. Its normal mode writes all five case inputs and checks them
against the committed resources.

```sh
./gradlew exampleClasses
EXPERIMENT_CP=build/classes/java/main:build/classes/java/example:build/resources/example
EXPERIMENT_PACKAGE=com.civilonia.gameoflife.example
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.experiment.GenerateInputs" build/experiments
for case_id in block blinker glider; do
  java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.Program" \
    "build/experiments/$case_id/input.goli" "build/experiments/$case_id/result.golf" || exit 1
  java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.experiment.VerifyResult" \
    "$case_id" "build/experiments/$case_id/input.goli" \
    "build/experiments/$case_id/result.golf" || exit 1
done
java -Dgameoflife.maxCellGenerations=292384343 \
  -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.Program" \
  build/experiments/r-pentomino/input.goli build/experiments/r-pentomino/result.golf
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.experiment.VerifyResult" \
  r-pentomino build/experiments/r-pentomino/input.goli build/experiments/r-pentomino/result.golf
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.Program" \
  build/experiments/gosper-glider-gun/input.goli build/experiments/gosper-glider-gun/result.golf
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.experiment.VerifyResult" \
  gosper-glider-gun build/experiments/gosper-glider-gun/input.goli build/experiments/gosper-glider-gun/result.golf
```

For both main runs, stop immediately on a nonzero execution or verification
exit status; the commands are individual steps, not an unattended batch. The
verifier writes `validation.json` beside the result. Export boards or coordinates
only after successful verification, using the interfaces in the sample code design.

Leave `Program` defaults unchanged except for the R-pentomino computation
limit: input 1 MiB, 1,000,000 cells, 10,000 transitions, 10,000,000
cell-generations, and output 64 MiB. The property above must reach the actual
Java process; setting it on a Gradle process alone does not establish this.

| Case | Cell-generations (width × height × N) | GOLI bytes | GOLF bytes |
| --- | ---: | ---: | ---: |
| Block | 32 | 19 | 23 |
| Blinker | 50 | 21 | 29 |
| Glider | 196 | 24 | 52 |
| R-pentomino | 292,384,343 | 33,153 | 36,582,161 |
| Gosper glider gun | 798,000 | 516 | 100,316 |

Sizes use `bytesPerBoard = ceil(width * height / 8)`, GOLI length
`17 + bytesPerBoard`, and GOLF length `17 + (N + 1) * bytesPerBoard`.
Confirm CPU, available memory, free space, and a short timing measurement before
the main experiments. The current algorithm scans the entire board each
generation; elapsed time is not yet measured. A changed board or N requires
recomputing sizes and limits.

## Run acceptance

Run verification after every execution. Require exact headers, file length,
generation count, padding, agreement with the initial input, and the expected
states of all small-pattern generations. Require no boundary live cells in any
saved generation. The verifier algorithm and failure handling are defined in
the [sample code design](simulation-specification.md#verification-and-failure-handling).

The planned main boards were estimated using an independent unbounded-grid
calculation and one dead-cell margin; they have not yet been verified by this
simulator. Validate every boundary at every generation before accepting the
observation period as free of boundary effects.

## Observation, preservation, and implementation gaps

Store input and output checksums, RLE conversion details and decoded initial
coordinates, case conditions, simulator version and compiled artifact checksum,
Java/OS versions, CPU and available resources, exact commands and effective
limits, exit status, elapsed time, and artifact checksums/paths in `observations.md`. Validation
reports must distinguish structural checks from scientific observations.
Exports use the same origin and axes as the simulation and identify their
generation; they must not silently crop or transform coordinates.

Use extracted boards/coordinates to examine R-pentomino's static, oscillating,
and moving structures through generation 1103. Compare the literature report
of six gliders using an isolated five-cell pattern that repeats its phase after
four generations translated diagonally by one cell; record the matching
generations and coordinates. For the gun, distinguish emitted gliders from the
gun body using the same four-generation movement check and compare successive
emissions at a fixed phase/observation region with a 30-generation interval.
Document that region and phase when analyzing the data. Do not infer a static
whole board or prove infinite growth from a finite run. Record unclassified
structures and inconclusive comparisons explicitly.

Existing capabilities are the public board/input/simulation APIs, GOLI encoding,
GOLF output, `Program`, and the earlier small fixtures. The five experimental
resources, bundled RLE and manifest, generator, independent GOLF verifier,
exporter, and their documented commands remain to be implemented. Implementation
checks must cover incorrect conversion, intermediate known-pattern states,
truncation, extra bytes, invalid padding, initial mismatch, and boundary contact.

Generated files under `build/` are temporary. Before marking execution complete,
copy each case's inputs, validated outputs, reports, observations, and exports
to `data/game-of-life/<case-id>/` in this repository's working directory.
Preserve them outside the disposable build directory and record their paths
and checksums in `observations.md`; repeat runs must use separate directories
so earlier results are not overwritten. Publishing or moving this archive
elsewhere is optional and is not required to run or interpret the samples.
Exact runtime and the detailed classification of R-pentomino structures remain
to be established during execution/analysis. These analysis choices do not
change the fixed sample inputs above.

