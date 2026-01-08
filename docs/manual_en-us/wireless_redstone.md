# Wireless Redstone System

The mod provides a concise and efficient global wireless Redstone transmission solution, greatly simplifying wiring for large-scale builds or cross-region music systems.

## Core Blocks

![WirelessRedstone](/docs/assets/wireless.png)

### 1. Redstone Signal Transmitter
- **Function**: When the transmitter receives a Redstone signal (powered), it activates the wireless network for the entire world.
- **Mechanism**: As long as there is **at least one** transmitter in the world that is powered, the wireless network is in an "On" state.

### 2. Redstone Signal Receiver
- **Function**: Listens to the state of the wireless network. When the wireless network is "On", the receiver outputs a Redstone signal with a strength of 15 to its surroundings.
- **Mechanism**: The receiver can be used as a normal Redstone source to trigger Note Blocks, doors, pistons, etc.

## Usage Scenarios
- **Remote Control**: Place a transmitter at a central control console to control Note Block arrays in distant areas.
- **Synchronized Playback**: Connect receivers in multiple areas to their corresponding Note Blocks to achieve cross-region synchronized sound generation via a single master signal.
