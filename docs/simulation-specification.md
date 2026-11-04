# Game of Life simulation specification

## Purpose and scope

This document defines the conditions and expected behavior of a deterministic
Conway's Game of Life experiment. It describes the current finite-board Java
library, its inputs and outputs, and reproducible checks. The initial patterns
below are project examples; finite boundaries can change their behavior once a
pattern reaches an edge.

## Grid and rules

The universe is a finite rectangular grid with positive width and height. Each
cell is alive or dead. Coordinates start at `(0, 0)` in the upper-left corner;
`x` increases to the right and `y` increases downward. Positions outside the
grid are dead, and opposite edges do not wrap.

Each cell has up to eight neighbors: the horizontal, vertical, and diagonal
adjacent positions (the Moore neighborhood). Every cell in a generation is
updated simultaneously from the preceding generation:

| Current state | Live neighbors | Next state |
| --- | --- | --- |
| Alive | 2 or 3 | Alive |
| Alive | Any other count | Dead |
| Dead | 3 | Alive |
| Dead | Any other count | Dead |

Board dimensions do not change during a run. Boards are immutable, so a
transition does not modify its input state.

## Experiment input and output

An experiment specifies the board dimensions, the complete initial cell state,
and a non-negative number of transitions `N`. Unspecified cells in the example
patterns below are dead. The initial state is `t=0`; each transition produces
the next generation. A run outputs exactly `N + 1` boards, ordered from `t=0`
through `t=N`. In particular, `N=0` outputs only the initial board.

The Java API accepts a `SimulationInput` and writes results to a caller-owned
stream through `GameOfLifeSimulator`. The caller closes the stream; an I/O
failure is propagated and may leave partial output. Identical inputs produce
identical states and encoded bytes. No randomness or external state is used.

The binary encodings are specified separately:

- [Binary input format (GOLI v1)](binary-input-format.md): one initial board
  and the requested transition count.
- [Binary output format (GOLF v1)](binary-output-format.md): the conditions
  and all boards from `t=0` through `t=N`.

The Java input API limits dimensions, their product, and transition counts to
values representable by signed 32-bit integers. The standalone example applies
additional configurable resource limits; see the [README](../README.md).

## Reproducible examples and validation

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

## Limitations

The simulation uses fixed finite, non-wrapping boundaries, rather than an
unbounded universe. It implements Conway's rules only; alternate rules,
random initial-state generation, visualization, and analysis of experimental
results are outside this library's current scope. Binary format details and
decoder validity requirements are governed by the linked format documents.
