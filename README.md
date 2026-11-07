# Game of Life

A dependency-free Java library for deterministic simulations of Conway's Game
of Life. It is intended to be reusable by Java applications, including
Minecraft Fabric mods, while remaining independent of any framework, filesystem,
or network API.

## Project status

The library provides an immutable board model, a synchronous simulation API,
the version 1 binary encoder and input format, GitHub Packages publishing, and
a standalone command-line example. It targets Java 21 and has no runtime
dependencies outside the Java standard library.

## Conway's Game of Life

The library simulates a finite, two-dimensional grid of cells. Each cell is
either alive or dead. For each synchronous transition, the eight surrounding
cells (the Moore neighborhood) determine the next state:

- A live cell with fewer than two live neighbors dies.
- A live cell with two or three live neighbors survives.
- A live cell with more than three live neighbors dies.
- A dead cell with exactly three live neighbors becomes alive.

Cells outside the board are always dead. Boards do not wrap at their edges.

## Requirements and build

- Java 21
- No runtime dependencies outside the Java standard library
- Gradle Wrapper (included)

On macOS or Linux:

```bash
./gradlew build
./gradlew test
```

On Windows:

```bat
gradlew.bat build
gradlew.bat test
```

`build` compiles the library, runs the JUnit 5 test suite, and creates the JAR
under `build/libs/`. The project deliberately does not use JPMS and has no
`module-info.java`.

## Architecture

The package layout separates state, computation, input, and output:

```text
com.civilonia.gameoflife
├── model   # Immutable board state and coordinates
├── core    # Deterministic Conway transitions and generation evolution
├── input   # Immutable simulation input and validation
└── output  # Binary encoding to caller-provided streams
```

The `core` package must remain free of I/O. The library will not add Spring,
Fabric, JSON libraries, logging frameworks, or other runtime dependencies.

### Board and coordinate contract

The board model is immutable: callers can inspect dimensions and cell states,
but cannot modify a constructed board or mutate its retained input. The
following contract applies to every board:

- Coordinates are zero-based.
- The origin is the top-left cell.
- `x` increases to the right and `y` increases downward.
- Invalid dimensions and invalid coordinates are rejected.
- Positions outside the finite board are dead; no toroidal wrapping is used.

## Simulation semantics

A complete description of experiment conditions, rules, and reproducible
validation examples is in the [simulation specification](docs/simulation-specification.md).

A simulation input consists of an initial immutable board and a non-negative
number of transitions, `N`. A zero transition count is valid.

`N` is the number of state transitions, not the number of encoded boards. A
simulation always includes the initial state:

```text
t=0, t=1, ..., t=N
```

Consequently, it produces exactly `N + 1` board states. For `N = 0`, the output
contains exactly the initial board at `t=0`. Each invocation is an independent,
sequential, deterministic experiment: identical inputs must produce identical
states and identical encoded bytes.

## Public API

The public simulation entry point is `com.civilonia.gameoflife.GameOfLifeSimulator`.
Create an instance with its static factory, providing the caller-owned output stream;
it evolves the board synchronously and encodes states from `t=0` through `t=N`.

Usage:

```java
Board initialBoard = /* construct an immutable board */;
SimulationInput input = new SimulationInput(initialBoard, 10);

try (OutputStream output = Files.newOutputStream(Path.of("game-of-life.bin"))) {
    GameOfLifeSimulator simulator = GameOfLifeSimulator.create(output);
    simulator.simulate(input);
}
```

The public API is `GameOfLifeSimulator.create(output).simulate(input)`. The example
application, rather than the library, owns file creation and closes its file
stream. The library does not close a caller-provided stream; `IOException`s are
propagated and a failed write may leave partial output in that stream.

## Binary output

The deterministic, versioned binary format is specified in
[Binary output format](docs/binary-output-format.md). It defines the `GOLF`
format identifier, version 1 header, byte and bit order, generation semantics,
padding, decoder error handling, and manually verifiable byte-level examples.
It is independent of Java serialization. Compression and extra metadata are
intentionally out of scope for the initial format.

## Binary input

Initial simulation data can be represented as a compact `GOLI` v1 document:
magic and version, width, height, requested transitions, and one bit-packed
initial board. `SimulationInput.fromBinary(bytes)` decodes and validates this
document, and the resulting DTO can be passed directly to
`GameOfLifeSimulator.simulate(input)`. `SimulationInput.toBinary()` writes the
same input format. See the complete [binary input format](docs/binary-input-format.md)
specification.

## Standalone example

The repository includes a small application, separate from the library source
set, which needs neither Minecraft nor a server. `Program` takes a binary
`GOLI` input document, restores it to a `SimulationInput`, and writes the
resulting `GOLF` simulation.

Run it from the repository root, supplying an input and an optional output path:

```bash
./gradlew runProgram --args='input.goli build/standalone-example/result.golf'
```

On Windows:

```bat
gradlew.bat runProgram --args="input.goli build/standalone-example/result.golf"
```

The first argument must be a complete `GOLI` v1 file as specified in the
[binary input format](docs/binary-input-format.md). The optional second argument
is the output file; if omitted, the binary `GOLF` result is written to standard
output. Parent directories for an output file are created as needed. For the
documented 5 × 5, two-transition blinker input, the output is 29 bytes: the
17-byte `GOLF` v1 header followed by three 4-byte boards. The initial vertical
blinker is present at `t=0`, becomes horizontal at `t=1`, and is vertical again
at `t=2`. Its complete expected hexadecimal content is:

```text
47 4F 4C 46 01 00 00 00 05 00 00 00 05 00 00 00 02
01 08 40 00 00 1C 00 00 01 08 40 00
```

The program owns the input/output files, while the library receives the decoded
`SimulationInput` DTO through its public API.

To prevent an input file from exhausting local CPU or storage, `Program` checks
the following limits before simulation. Each may be overridden with a positive
Java system property (for example, `-Dgameoflife.maxOutputBytes=134217728`).

| Property | Default limit |
| --- | ---: |
| `gameoflife.maxInputBytes` | 1 MiB |
| `gameoflife.maxCells` | 1,000,000 cells |
| `gameoflife.maxTransitions` | 10,000 transitions |
| `gameoflife.maxCellGenerations` | 10,000,000 cell-generations |
| `gameoflife.maxOutputBytes` | 64 MiB |

Input is read incrementally and rejected as soon as the first byte beyond
`gameoflife.maxInputBytes` is encountered; the program does not rely on file
metadata to enforce that limit.

The program also checks failures reported by standard output after writing, so
a broken pipe or failed redirection does not appear to complete successfully.

### Included experimental inputs

The five reproduction experiments (block, horizontal blinker, glider,
R-pentomino, and Gosper glider gun) have separate offline inputs and tools in
the example source set. See the [simulation plan](docs/simulation-plan.md) for
generation, execution with metadata recording, independent verification, and
validated CSV export. These tools require no additional runtime dependencies.

The example source set contains reproducible, version-controlled `GOLI`
resources under `src/example/resources/goli/`.

Available inputs are:

- `empty-3x3-zero.goli`: an empty 3 × 3 board with zero transitions
- `blinker-5x5-two-transitions.goli`: a 5 × 5 blinker with two transitions
- `glider-5x5-four-transitions.goli`: a 5 × 5 glider with four transitions

For example:

```bash
./gradlew runProgram --args='src/example/resources/goli/blinker-5x5-two-transitions.goli build/blinker.golf'
```

## Publishing and consumption

The intended Maven coordinates are:

```text
com.civilonia:game-of-life
```

Published releases are available from GitHub Packages. Configure the repository
and dependency in a consuming Gradle project (with a GitHub token that can read
packages when required):

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/civilonia/game-of-life')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.civilonia:game-of-life:1.0.0'
}
```

Packages are published only when a GitHub Release with a valid SemVer tag is
published. Tags must start with `v` and use
`vMAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]`; numeric components and numeric
prerelease identifiers cannot have leading zeroes. The leading `v` is removed
for the Maven version, so `v1.0.0` produces `1.0.0` and
`v0.1.0-alpha.1` produces `0.1.0-alpha.1`.

To publish a prerelease, create a GitHub Release from a new prerelease tag
(for example, `v0.1.0-alpha.2`) and select GitHub's **Set as a pre-release**
option. That GitHub flag controls how the release is presented; the SemVer
suffix in the tag controls the published Maven version. Release tags must
still point to a commit in `main` history.
