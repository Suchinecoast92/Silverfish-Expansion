# Silverfish Expansion

Mod de **Fabric** para Minecraft **1.21.11** que añade variantes animadas de silverfish con **GeckoLib** y prepara un sistema de **colonia** con ciclo de vida completo.

## Estado actual

| Contenido | Estado |
|-----------|--------|
| Adultos Araneo / Mantis / Fly | Completo (modelo, animaciones, comportamiento) |
| Assets de larva | Añadidos (modelo + animaciones GeckoLib) |
| Huevos amontonables | Pendiente (modelos en Blockbench) |
| Piedra infestada de colonia | Pendiente |
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

## Sistema de colonia (en desarrollo)

La colonia debe sentirse como un hábitat natural de cuevas, no como un dungeon ni un jefe.

Ciclo de vida previsto:

**Huevos → Larvas → Piedra infestada → Adultos → Matriarca → Nuevos huevos**

### Piezas del ecosistema

| Pieza | Rol |
|-------|-----|
| **Cámara de cría** | Estructura natural en cuevas profundas (sin cofres ni loot artificial) |
| **Huevos** | Bloques gelatinosos amontonables (1–4 por bloque, como pepinos de mar / velas), en suelo, paredes y techo |
| **Larvas** | Fase juvenil: no atacan, huyen y buscan piedra para refugiarse |
| **Piedra infestada** | Refugio de maduración; al romperla sale un adulto |
| **Matriarca** | Única que pone huevos; alarma a los adultos si recibe daño |
| **Decadencia** | Sin Matriarca no hay nuevos huevos; lo existente sigue su ciclo hasta agotarse |

Los adultos actuales no cambian su comportamiento individual; la colonia se construye encima de ellos.

### Assets de larva

Ya están en el proyecto (pendientes de cablear del todo en código si hace falta):

- `geckolib/models/entity/larva.geo.json`
- `geckolib/animations/entity/larva.animation.json`
- Fuente Blockbench: `larva.bbmodel`

### Próximo paso: huevos

Se modelarán en Blockbench como bloque Java amontonable (`eggs=1..4`), con varios modelos según cantidad, y luego se integrarán al mod.

## En el juego

- Huevos de aparición de adultos en la pestaña creativa **Huevos de generación** / **Spawn Eggs**.
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

Los huevos se exportan como **Java Block/Item** (no GeckoLib): modelos `block/silverfish_eggs_1` … `_4` y blockstate con propiedad `eggs`.

## Compilar

```powershell
.\gradlew.bat build
```

El JAR queda en: `build/libs/silverfish-expansion-1.0.0.jar`

## Repositorio

https://github.com/Suchinecoast92/Silverfish-Expansion
