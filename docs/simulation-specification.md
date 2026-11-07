# Game of Life simulator specification

## Purpose and scope

This document defines the finite-board Conway's Game of Life Java library: its
state model, transition rules, public API contract, and binary input/output.
The library targets Java 21 and has no runtime dependencies outside the Java
standard library.

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

## Simulation input and output

A simulation input specifies the board dimensions, the complete initial cell
state, and a non-negative number of transitions `N`. The initial state is `t=0`;
each transition produces the next generation. A run outputs exactly `N + 1`
boards, ordered from `t=0` through `t=N`. In particular, `N=0` outputs only the
initial board.

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
values representable by signed 32-bit integers. Invalid dimensions, negative
transition counts, and malformed GOLI documents are rejected.

## Limitations

The simulation uses fixed finite, non-wrapping boundaries, rather than an
unbounded universe. It implements Conway's rules only; alternate rules,
random initial-state generation, visualization, and analysis of experimental
results are outside this library's current scope. Binary format details and
decoder validity requirements are governed by the linked format documents.
