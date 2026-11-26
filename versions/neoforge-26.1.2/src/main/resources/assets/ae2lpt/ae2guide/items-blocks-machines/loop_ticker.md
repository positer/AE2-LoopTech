---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Loop Tick Accelerator
  icon: ae2lpt:loop_ticker
  position: 935
categories:
- devices
item_ids:
- ae2lpt:loop_ticker
- ae2lpt:loop_ticker_part
---

# Loop Tick Accelerator

<BlockImage id="ae2lpt:loop_ticker" scale="5" />
<RecipeFor id="ae2lpt:loop_ticker" />

Aim the green top at an adjacent machine or a block that responds to random ticks. The block supports all six directions. Craft either form alone to switch between the full block and the cable part. The part accelerates the neighbor directly in front of its mounted face.

Connect the accelerator to a powered ME network and right-click it with an empty hand. Set the rate using the number field or slider. The default maximum is four extra calls per server tick. Each Acceleration Card doubles the maximum, up to 64 with four cards. Several accelerators aimed at one target add their rates to its normal ticking. The target still needs its own inputs and power.

An accelerator can also target another accelerator. Each extra activation applies the downstream rate. A chain that comes back to an accelerator it already visited stops there.

With Applied Flux, install one Induction Card to send FE from the ME network to an adjacent machine once per server tick. The machine must accept FE on the face touching the green face or the part’s mounting face.
