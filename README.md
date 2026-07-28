# Silverfish Expansion

Mod de **Fabric** para Minecraft **1.21.11** que añade tres variantes animadas de silverfish con **GeckoLib**.

## Variantes

| Variante | ID de entidad | Modelo de origen |
|----------|---------------|------------------|
| Araneo Silverfish | `araneo_silverfish` | `silverfish2.bbmodel` |
| Mantis Silverfish | `mantis_silverfish` | `silverfish2-mantis.bbmodel` |
| Fly Silverfish | `fly_silverfish` | `silverfish2-fly.bbmodel` |

## Animaciones

Todas las variantes usan GeckoLib y comparten un comportamiento natural de movimiento:

| Animación | Quién la usa | Cuándo se reproduce |
|-----------|--------------|---------------------|
| `walk` | Las 3 | Solo cuando se desplazan de verdad |
| `none` | Las 3 | Pose neutra al detenerse |
| `idle` / `idle2` | Las 3 | De vez en cuando, una sola vez, y vuelven a `none` |
| `idle3` | Solo Fly | Igual que los otros idles |
| `startFlying` → `flying` | Solo Fly | Despegue y vuelo libre |
| `stopFlying` | Solo Fly | Aterrizaje cerca del suelo |

Al parar de caminar suelen quedarse en `none`. Aproximadamente un 35 % de las veces ejecutan un idle breve y luego regresan a la pose neutra.

## Comportamiento del Fly Silverfish

El tipo volador tiene un ciclo completo de vuelo:

1. **Despegue** — espera un momento a que arranque `startFlying` y luego sube despacio.
2. **Vuelo libre** — se mueve de forma errática a baja altura (estilo abeja).
3. **Aproximación** — desciende suave hacia el suelo.
4. **Aterrizaje** — reproduce `stopFlying` cerca del suelo, sin daño por caída.

Prefiere volar antes que caminar mucho por el suelo.

## En el juego

- Huevos de aparición en la pestaña creativa **Huevos de generación** / **Spawn Eggs**.
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

Si editas los archivos de Blockbench en `silverfish-textures and models/bbmodel/`, exporta con el plugin **GeckoLib**:

1. **File → Export → Export GeckoLib Model** → `src/main/resources/assets/silverfish-expansion/geckolib/models/entity/{id}.geo.json`
2. **File → Export → Export GeckoLib Animations** → `src/main/resources/assets/silverfish-expansion/geckolib/animations/entity/{id}.animation.json`
3. Clic derecho en la textura → **Save As** → `src/main/resources/assets/silverfish-expansion/textures/entity/{id}.png`

Usa como `{id}`: `araneo_silverfish`, `mantis_silverfish` o `fly_silverfish`.

## Compilar

```powershell
.\gradlew.bat build
```

El JAR queda en: `build/libs/silverfish-expansion-1.0.0.jar`

## Repositorio

https://github.com/Suchinecoast92/Silverfish-Expansion
