# Experiment input manifest

Coordinates are zero-based: x right, y down; all other cells are dead.
Coordinate lists are sorted by x, then y. All cases use B3/S23 and fixed dead boundaries.

## RLE conversion

Resource: `sources/gosperglidergun.rle`

SHA-256: `c4a03a9e17afdcebb829fcfaeb667f67c9f804e490f762d6901312f4d4537d31`

Decode o/b runs and $ rows, preserving orientation and phase. Validate 36 live
cells in a 36 x 9 bounding box, then translate each coordinate by (1,1).
Other inputs are generated directly from the absolute coordinates below.
Encode with SimulationInput.toBinary() as GOLI v1.

## block

Resource: `goli/block.goli`

Board: 4 x 4; transitions: 2; live cells: 4

SHA-256: `5ed09e9838e9c58855d5efb86aa60ede283749466fd92293fdd12c922dc7f746`

Initial coordinates: (1,1) (1,2) (2,1) (2,2)

## blinker

Resource: `goli/blinker.goli`

Board: 5 x 5; transitions: 2; live cells: 3

SHA-256: `1b03017a86193efeae82d8d6d7470d7e2913100dbf4667b4e1f3ccfb176e1831`

Initial coordinates: (1,2) (2,2) (3,2)

## glider

Resource: `goli/glider.goli`

Board: 7 x 7; transitions: 4; live cells: 5

SHA-256: `fa1d4e579231374bf1020b444f321d1b17f09d6281ba6cb3a7fa38ecd6516c7e`

Initial coordinates: (1,3) (2,1) (2,3) (3,2) (3,3)

## r-pentomino

Resource: `goli/r-pentomino.goli`

Board: 503 x 527; transitions: 1103; live cells: 5

SHA-256: `a4800d1c68063a610c1d1257ba215ea549d40a6cddff51fe36f562651a54565a`

Initial coordinates: (241,260) (242,259) (242,260) (242,261) (243,259)

## gosper-glider-gun

Resource: `goli/gosper-glider-gun.goli`

Board: 70 x 57; transitions: 200; live cells: 36

SHA-256: `048a8e7881ecd71d679ea8f89b4eeb73da4521ad1434b332e9558e0935247eca`

Initial coordinates: (1,5) (1,6) (2,5) (2,6) (11,5) (11,6) (11,7) (12,4) (12,8) (13,3) (13,9) (14,3) (14,9) (15,6) (16,4) (16,8) (17,5) (17,6) (17,7) (18,6) (21,3) (21,4) (21,5) (22,3) (22,4) (22,5) (23,2) (23,6) (25,1) (25,2) (25,6) (25,7) (35,3) (35,4) (36,3) (36,4)
