# Loop Tick Accelerator stacking

- Both generations share the same iterative, path-local cascade scheduler. Native adapters explicitly dispatch powered ticker blocks and mounted ticker parts rather than depending on an ordinary block ticker.
- Direct inputs add; additional activations along a chain use the target ticker's selected rate. Back-edges are skipped without suppressing other inputs from independent paths.
- A single originating activation can execute at most 8,192 target steps, including intermediate activations. Two chained 64× stages fit in that budget. This is a work guard, not an FE limit.
- Existing once-per-server-tick induction output is unchanged by extra activations.
- Regression matrix: both game generations; blocks and cable parts; two-source stacking; chained multipliers; path cycles; no additional FE dispatch; existing native induction suite.
