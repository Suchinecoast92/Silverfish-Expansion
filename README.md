# Silverfish Expansion

Mod de **Fabric** para Minecraft **1.21.11** que añade variantes animadas de silverfish con **GeckoLib** y un sistema de **colonia** en desarrollo (huevos → larva → piedra infestada → adultos).

## Estado actual

| Contenido | Estado |
|-----------|--------|
| Adultos Araneo / Mantis / Fly | Completo (modelo, animaciones, comportamiento) |
| Huevos amontonables | Completo (suelo, paredes y techo; 1–4 por bloque) |
| Larvas | Completo (huyen, caminan y se meten en piedra) |
| Maduración en piedra infestada vanilla | Completo (larva → silverfish → adulto) |
| Iconos de creativo (spawn eggs e ítem de huevos) | Completo |
| Matriarca y cámara de cría | Pendiente |

## Variantes adultas

| Variante | ID de entidad | Modelo de origen |
|----------|---------------|------------------|
| Araneo Silverfish | `araneo_silverfish` | `silverfish2.bbmodel` |
| Mantis Silverfish | `mantis_silverfish` | `silverfish2-mantis.bbmodel` |
| Fly Silverfish | `fly_silverfish` | `silverfish2-fly.bbmodel` |

### Animaciones de adultos

| Animación | Quién la usa | Cuándo se reproduce |
|-----------|--------------|---------------------|
| `walk` | Las 3 | Solo cuando se desplazan de verdad |
| `none` | Las 3 | Pose neutra al detenerse |
| `idle` / `idle2` | Las 3 | De vez en cuando, una sola vez, y vuelven a `none` |
| `idle3` | Solo Fly | Igual que los otros idles |
| `startFlying` → `flying` | Solo Fly | Despegue y vuelo libre |
| `stopFlying` | Solo Fly | Aterrizaje cerca del suelo |

Al parar de caminar suelen quedarse en `none`. Aproximadamente un 35 % de las veces ejecutan un idle breve y luego regresan a la pose neutra.

### Comportamiento del Fly Silverfish

1. **Despegue** — espera a que arranque `startFlying` y sube despacio.
2. **Vuelo libre** — movimiento errático a baja altura.
3. **Aproximación** — descenso suave.
4. **Aterrizaje** — `stopFlying` cerca del suelo, sin daño por caída.

## Sistema de colonia

La colonia debe sentirse como un hábitat natural de cuevas, no como un dungeon ni un jefe.

Ciclo de vida:

**Huevos → Larva → Piedra infestada vanilla → Silverfish juvenil → Adulto (Araneo / Mantis / Fly) → Matriarca → Nuevos huevos**

### Qué ya funciona

| Pieza | Comportamiento actual |
|-------|------------------------|
| **Huevos** | Bloque amontonable (`eggs` 1–4), colocable en suelo, paredes y techo. Al romper o aplastar (solo suelo) pueden salir larvas (~60 %). Sonido de colocación como huevos de tortuga (`metal.place`); rotura con `TURTLE_EGG_BREAK`. |
| **Larvas** | No atacan, huyen del jugador. Deben caminar ~2.5 bloques antes de meterse en piedra. Al entrar reproducen `insertStone_Front` o `insertStone_Down` y convierten la piedra en infestada vanilla. |
| **Piedra infestada** | Sigue siendo el bloque vanilla. El mod guarda el momento de la infestación: al romperla, 0–3 min sale larva, 3–10 min silverfish vanilla, 10+ min una variante adulta al azar. |
| **Iconos creativos** | Texturas 16×16 propias para spawn eggs y el ítem de huevos. |

### Piezas pendientes

| Pieza | Rol |
|-------|-----|
| **Cámara de cría** | Estructura natural en cuevas profundas (sin cofres ni loot artificial) |
| **Matriarca** | Única que pone huevos; alarma a los adultos si recibe daño |
| **Decadencia** | Sin Matriarca no hay nuevos huevos; lo existente sigue su ciclo hasta agotarse |

### Próximos ajustes conocidos

- Revisar que las larvas salidas de huevos usen bien `insertStone_Front`.
- Evitar que silverfish, larvas y las 3 variantes aplasten los huevos.

### Assets de larva

- `geckolib/models/entity/larva.geo.json`
- `geckolib/animations/entity/larva.animation.json` (`walk`, `insertStone_Front`, `insertStone_Down`)
- Fuente Blockbench: `larva.bbmodel`
- Textura: `textures/entity/larva.png`

## En el juego

- Spawn eggs de adultos y larva, más el ítem de huevos, en creativo.
- Dependencias en tiempo de ejecución: **Fabric API** y **GeckoLib**.
- Idiomas incluidos: inglés (`en_us`) y español México (`es_mx`).

## Requisitos

| Dependencia | Versión |
|-------------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.19.2 |
| Fabric API | 0.141.4+1.21.11 |
| GeckoLib | 5.4.4 |
| Java | ≥ 21 |

## Reexportar modelos

### Entidades (GeckoLib)

Si editas los `.bbmodel` de entidades, exporta con el plugin **GeckoLib**:

1. **File → Export → Export GeckoLib Model** → `geckolib/models/entity/{id}.geo.json`
2. **File → Export → Export GeckoLib Animations** → `geckolib/animations/entity/{id}.animation.json`
3. Textura → `textures/entity/{id}.png`

IDs: `araneo_silverfish`, `mantis_silverfish`, `fly_silverfish`, `larva`.

### Huevos (bloque Java)

Modelos `block/silverfish_eggs_1` … `_4`, blockstate con `eggs` + `facing`, e ítem con textura en `textures/item/silverfish_eggs.png`.

### Iconos de ítem

PNG 16×16 en `textures/item/`:

- `araneo_silverfish_spawn_egg.png`
- `mantis_silverfish_spawn_egg.png`
- `fly_silverfish_spawn_egg.png`
- `larva_spawn_egg.png`
- `silverfish_eggs.png`

## Compilar

```powershell
.\gradlew.bat build
```

El JAR queda en: `build/libs/silverfish-expansion-1.0.0.jar`

## Repositorio

https://github.com/Suchinecoast92/Silverfish-Expansion
