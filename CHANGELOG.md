# Changelog

Changes to the `map.yml` format and the Clash Mapmaker tools. The format version
(`proto`) only changes when old maps would be read differently, and every change to it is
listed here.

## 0.1.0 (unreleased)

First release of the `clash-map` library.

- `map.yml` format `proto: 1` for Bunkers, Destroy the Core, Capture the Wool and Base
  Defense. See [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md).
- Reading with clear messages for every mistake, including misspelled keys.
- Saving in a stable, readable order.
- Checks that every id points at something real, every team can spawn, and each mode has
  the goals it needs.
- One example map per mode in `examples/`.
