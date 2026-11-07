# Game of Life simulation plan

This document defines the experiments, initial data, expected behaviors, and
execution/observation procedures. The [simulation specification](simulation-specification.md)
defines the simulator model and input/output contract. This plan also defines
the sample code needed to execute and verify the experiments. Both documents are
local to this repository; no external research documents or pattern downloads
are required.

The experiment resources and helper classes are included in the example source
set. Build them with Java 21 before running the commands below. Existing library
regression examples remain separate.

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

Run from this repository with Java 21 (the included devcontainer provides it).
Choose a new run root for every attempt; generation refuses any existing root.
The generator compares all five inputs and the manifest against bundled resources
before creating the run. It also copies the gun RLE for offline preservation.

```sh
set -e
./gradlew exampleClasses
EXPERIMENT_CP=build/classes/java/main:build/classes/java/example:build/resources/example
EXPERIMENT_PACKAGE=com.civilonia.gameoflife.example.experiment
RUN_ROOT=build/experiments/run-001
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.GenerateInputs" "$RUN_ROOT"
for case_id in block blinker glider; do
  java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.RunExperiment" \
    "$case_id" "$RUN_ROOT/$case_id"
done
java -Dgameoflife.maxCellGenerations=292384343 \
  -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.RunExperiment" \
  r-pentomino "$RUN_ROOT/r-pentomino"
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.RunExperiment" \
  gosper-glider-gun "$RUN_ROOT/gosper-glider-gun"
java -cp "$EXPERIMENT_CP" "$EXPERIMENT_PACKAGE.ExportGeneration" \
  "$RUN_ROOT/glider/input.goli" "$RUN_ROOT/glider/result.golf" \
  4 "$RUN_ROOT/glider/generation-4.csv"
```

`RunExperiment` invokes the existing `Program` and then `VerifyResult` in the
same JVM, so system properties apply directly to the simulation. It refuses
existing results, observations, or reports. Failed output remains in the run
directory for diagnosis, without a successful validation report. Every helper
returns a nonzero process exit status on failure and prints diagnostics; stop
and resolve failures before proceeding. Capture standard error in a run log
when retaining diagnostics.

`VerifyResult` may also be invoked separately with the case ID, input path, and
result path. It replaces any earlier `validation.json` with a successful report
only after checking the complete result; a failed attempt removes the stale
report. Reports record both file checksums, conditions, generation count, and
which checks were performed. They certify structural/fixture checks, not
scientific observations. Export revalidates the entire result and requires an
exactly matching successful report; existing export destinations are rejected.
CSV comments identify the case, generation, input/result checksums, origin and
axes. Rows are sorted by x, then y. Read the CSV while ignoring `#` comment lines.

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
generation; use the recorded elapsed time to assess performance. A changed
board or N requires recomputing sizes and limits.

## Run acceptance

Run verification after every execution. Require exact headers, file length,
generation count, padding, agreement with the initial input, and the expected
states of all small-pattern generations. Require no boundary live cells in any
saved generation. The verifier algorithm and failure handling are defined in
[Verification and failure handling](#verification-and-failure-handling) below.

The planned main boards were estimated using an independent unbounded-grid
calculation and one dead-cell margin. Validate every boundary at every generation
before accepting the observation period as free of boundary effects.

## Observation and preservation

`RunExperiment` creates `observations.md` with the process command, environment,
effective limits, compiled-library fingerprint, input/result checksums, status,
and elapsed execution-plus-verification time. The compiled fingerprint hashes
the ordered names and bytes of all library classes (including `Board.Builder`).
Set `-Dgameoflife.simulatorVersion=<version>` to record a known release version;
the default is `development`. Add scientific observations manually.

Preserve input and output checksums, RLE conversion details and decoded initial
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

The example tests cover deterministic input/manifest regeneration, RLE decoding,
intermediate known-pattern states, truncation, extra bytes, malformed headers,
invalid padding, initial mismatches, boundary contact, stale reports, export
validation, and refusal to overwrite prior artifacts. Run `./gradlew build`
to execute these checks alongside the library tests.

Generated files under `build/` are temporary. Before marking execution complete,
copy the entire run root, including its manifest, source RLE, and each case's
inputs, validated outputs, reports, observations, and exports, to a new
`data/game-of-life/<run-id>/` in this repository's working directory. Preserve
them outside the disposable build directory and record their paths and
checksums in `observations.md`; repeat runs must use separate directories so
earlier results are not overwritten. Publishing or moving this archive
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

## Sample implementation

The following helpers extend the example source set. They use the simulator's
public APIs without changing the library rules or binary formats.

### Example code responsibilities

The classes are in
`src/example/java/com/civilonia/gameoflife/example/experiment/`:

| Class | Responsibility |
| --- | --- |
| `ExperimentCases` | Fixed case IDs, dimensions, N, coordinates, local RLE resources, and expected checks. No simulation logic. |
| `GenerateInputs` | Parse the bundled RLE, build boards, write GOLI with `SimulationInput.toBinary()`, and compare generated bytes with committed resources. |
| `RunExperiment` | Invoke `Program`, verify output, refuse prior artifacts, and record timing/environment/limits/checksums. |
| `VerifyResult` | Stream-decode GOLF independently of the encoder, check the input and every generation, and write a machine-readable validation report. Exit nonzero on any failure. |
| `ExportGeneration` | Revalidate a result and export a requested generation as sorted `(x,y)` CSV. Record the case, generation, and input/result checksums. |

Keep RLE decoding and all filesystem handling in the example source set.
Reuse `Program` for execution and its preflight resource limits. Use only Java
21 and the standard library. Generated results belong under
`build/experiments/<run-id>/<case-id>/` and must not overwrite committed input resources.
Each case directory contains `input.goli`, `result.golf`, `validation.json`,
`observations.md`, and optional exports. Preserve failed runs and logs separately;
never label them as validated results.

Input generation follows this sequence; `Board` and `SimulationInput` are the
library's public APIs:

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
decoding. To deliberately update committed inputs and their manifest, run
`GenerateInputs --update-resources src/example/resources/experiments` from the
repository root after building. This mode overwrites those resources and copies
the bundled RLE. Change the authoritative RLE file before building when updating
the pattern. Ordinary generation only compares against bundled resources.

`GenerateInputs <run-root>` writes all case inputs into new run directories.
`VerifyResult <case-id> <input.goli> <result.golf>` writes `validation.json`
beside the result and exits nonzero if any check fails.
`ExportGeneration <input.goli> <result.golf> <generation> <output.csv>` exports
sorted live-cell coordinates. Before export, revalidate the input/output
structure and require a successful validation report with matching file
checksums. Monochrome image export is not implemented.

### RLE decoding

The sample-only RLE reader skips comment lines and payload whitespace, checks
the header and B3/S23 rule, and expands decimal run lengths (default 1).
`o` advances across live cells, `b` across dead cells, `$` advances rows and
resets x to zero, and `!` terminates the pattern. Reject invalid tokens,
missing termination, trailing payload tokens, runs exceeding declared
dimensions, and overflow. Omitted cells are dead. Validate the decoded bounding
box, live-cell count, and translated placement against the selected case's
requirements before constructing the board. The RLE reader is bounded to 64 KiB
of text, one million declared cells, and runs up to one million cells; this is
a decoder for the bundled experiment data rather than a general RLE library.

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
