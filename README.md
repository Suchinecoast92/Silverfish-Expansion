# Silverfish Expansion

Fabric mod for Minecraft **1.21.11** that adds three animated silverfish variants using **GeckoLib**.

## Variants

| Variant | Entity ID | Source model |
|---------|-----------|--------------|
| Araneo Silverfish | `araneo_silverfish` | `silverfish2.bbmodel` |
| Mantis Silverfish | `mantis_silverfish` | `silverfish2-mantis.bbmodel` |
| Fly Silverfish | `fly_silverfish` | `silverfish2-fly.bbmodel` |

Each variant has walk / idle animations (fly also includes flying animations in the asset file).

## In-game

- Spawn eggs are in the **Spawn Eggs** creative tab.
- Requires **Fabric API** and **GeckoLib** at runtime.

## Re-exporting models

If you edit the Blockbench files in `silverfish-textures and models/bbmodel/`, export from Blockbench with the **GeckoLib** plugin:

1. **File → Export → Export GeckoLib Model** → `geckolib/models/entity/{id}.geo.json`
2. **File → Export → Export GeckoLib Animations** → `geckolib/animations/entity/{id}.animation.json`
3. Right-click the texture → **Save As** → `textures/entity/{id}.png`

Use `araneo_silverfish`, `mantis_silverfish`, or `fly_silverfish` as `{id}`.

## Build

```powershell
.\gradlew.bat build
```

Output JAR: `build/libs/silverfish-expansion-1.0.0.jar`
