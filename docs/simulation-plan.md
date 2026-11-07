# Game of Life simulation plan

This document defines the experiments, initial data, expected behaviors, and
execution/observation procedures. The [simulation specification](simulation-specification.md)
defines the simulator model and input/output contract. This plan also defines
the sample code needed to execute and verify the experiments. Both documents are
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
defined in [RLE decoding](#rle-decoding) below.

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
[Verification and failure handling](#verification-and-failure-handling) below.

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

## Existing regression examples

Coordinates below list all live cells. Compare every generated state, not just
the last one, when validating a run.

| Pattern | Board and initial live cells | Transitions | Expected result |
| --- | --- | ---: | --- |
| Empty | 3 × 3; none | 0 | One empty board at `t=0`. |
| Block | 4 × 4; `(1,1)`, `(2,1)`, `(1,2)`, `(2,2)` | 2 | The same four cells remain alive at `t=1` and `t=2`. |
| Blinker | 5 × 5; `(2,1)`, `(2,2)`, `(2,3)` | 2 | At `t=1`, `(1,2)`, `(2,2)`, `(3,2)` are alive; `t=2` equals `t=0`. |
| Glider | 5 × 5; `(1,0)`, `(2,1)`, `(0,2)`, `(1,2)`, `(2,2)` | 4 | At `t=4`, the initial pattern has moved one cell right and down: `(2,1)`, `(3,2)`, `(1,3)`, `(2,3)`, `(3,3)`. |

To reproduce the blinker with the included binary input, run from the
repository root:

```sh
./gradlew runProgram --args='src/example/resources/goli/blinker-5x5-two-transitions.goli build/blinker.golf'
```

Decode the output using the linked GOLF specification and compare all three
boards with the table. The complete expected output bytes are documented in
the [README](../README.md#standalone-example). Repeat the command to verify
byte-for-byte reproducibility. The repository also provides empty and glider
inputs in `src/example/resources/goli/`.

Run `./gradlew test` to exercise rule and boundary cases, known patterns,
generation counts, input validation, output failures, and independent
reference bytes. The [integration tests](../src/test/java/com/civilonia/gameoflife/GameOfLifeIntegrationTest.java)
include the expected output for every generation of the examples above.

## Planned sample implementation

The following helpers extend the example source set and remain to be
implemented. They use the simulator's public APIs without changing the library
rules or binary formats.

### Example code responsibilities

Place the following proposed classes in
`src/example/java/com/civilonia/gameoflife/example/experiment/`:

| Class | Responsibility |
| --- | --- |
| `ExperimentCases` | Fixed case IDs, dimensions, N, coordinates, local RLE resources, and expected checks. No simulation logic. |
| `GenerateInputs` | Parse the bundled RLE, build boards, write GOLI with `SimulationInput.toBinary()`, and compare generated bytes with committed resources. |
| `VerifyResult` | Stream-decode GOLF independently of the encoder, check the input and every generation, and write a machine-readable validation report. Exit nonzero on any failure. |
| `ExportGeneration` | Read a validated result and export a requested generation as sorted `(x,y)` CSV or a portable monochrome image. Record the case, generation, and source result checksum. |

Keep RLE decoding and all filesystem handling in the example source set.
Reuse `Program` for execution and its preflight resource limits. Use only Java
21 and the standard library. Generated results belong under
`build/experiments/<case-id>/` and must not overwrite committed input resources.
Each case directory contains `input.goli`, `result.golf`, `validation.json`,
`observations.md`, and optional exports. Preserve failed runs and logs separately;
never label them as validated results.

Input generation follows this representative pseudocode; helpers are proposed
example code, while `Board` and `SimulationInput` are existing public APIs:

```text
for case in ExperimentCases:
    cells = case.coordinates or decodeBundledRle(case.source)
    assert expected count, bounding box, and placement
    builder = Board.builder(case.width, case.height)
    for (x, y) in cells:
        builder.setAlive(x + case.offsetX, y + case.offsetY, true)
    input = new SimulationInput(builder.build(), case.transitions)
    bytes = input.toBinary()
    assert decode(bytes) matches the case's complete initial state
    assert bytes equal the committed GOLI resource
    write a new run directory's input.goli
```

For coordinate-defined cases, use the absolute cells in the simulation plan
and offset zero. Apply the RLE offset specified by the selected case after
decoding. The generator must offer a deliberate resource-update mode for
initially creating or subsequently changing committed inputs; ordinary generation only compares against them.

`GenerateInputs <run-root>` writes all case inputs into new run directories.
`VerifyResult <case-id> <input.goli> <result.golf>` writes `validation.json`
beside the result and exits nonzero if any check fails.
`ExportGeneration <input.goli> <result.golf> <generation> <output.csv>` exports
sorted live-cell coordinates. Before export, revalidate the input/output
structure and require a successful validation report with matching file
checksums. Optional monochrome image export must preserve the same coordinates.

### RLE decoding

The sample-only RLE reader skips comment lines and payload whitespace, checks
the header and B3/S23 rule, and expands decimal run lengths (default 1).
`o` advances across live cells, `b` across dead cells, `$` advances rows and
resets x to zero, and `!` terminates the pattern. Reject invalid tokens,
missing termination, trailing payload tokens, runs exceeding declared
dimensions, and overflow. Omitted cells are dead. Validate the decoded bounding
box, live-cell count, and translated placement against the selected case's requirements before constructing the board.

### Verification and failure handling

The verifier must use checked size calculations and streaming reads bounded
to one board at a time. Before decoding, require the input to match its case
definition and bound file sizes by the simulation plan. It must not
use the simulator's transition function to generate its expected small-pattern
states. Check all small-pattern generations against independently specified
fixtures, including the intermediate glider phases.

```text
input = decode and validate complete GOLI
assert input matches ExperimentCases[caseId]
header = read exactly 17 GOLF bytes
assert magic == GOLF, version == 1
assert (width, height, N) == input conditions
assert file length == 17 + (N + 1) * ceil(width * height / 8)
for t from 0 through N:
    board = read exactly bytesPerBoard bytes
    assert final-byte padding bits are zero
    cells = decode row-major, most-significant bit first
    if t == 0: assert cells == complete input live-cell set
    assert no cells on x=0, x=width-1, y=0, or y=height-1
    if small case: assert cells == independent fixture[t]
assert end of file
write successful report with case, conditions, checks, and file checksums
```

A truncated result, extra bytes, invalid header or padding, failed known-pattern
check, or initial-state mismatch invalidates the run. Record the failing check
and generation, fix the input or implementation, and restart from generation
zero. Reject stale results from previous attempts. On boundary contact, record
the first generation and coordinates, expand the board with translated initial
placement, recompute the limits, and repeat from zero. Such a run is a revised
condition and must be recorded separately rather than silently replacing the
planned experiment.
