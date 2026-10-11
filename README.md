# Silverfish Expansion

Mod de **Fabric** para Minecraft **1.21.11** que añade variantes animadas de silverfish con **GeckoLib** y un sistema de **colonia**.

Ciclo de vida:

**Huevos → Larva → Piedra infestada vanilla → Silverfish → Adulto (Araneo / Mantis / Fly / Crab)**

## Estado actual

| Contenido | Estado |
|-----------|--------|
| Adultos Araneo / Mantis / Fly / Crab | Completo |
| Trepar paredes (como araña vanilla) | Completo (adultos terrestres) |
| Deambulación errática / curiosa | Completo |
| Huevos amontonables | Completo |
| Eclosión natural de huevos | Completo |
| Larvas e infestación | Completo |
| Maduración en piedra infestada vanilla | Completo |
| Cámara de cría (worldgen) | Completo (nido amplio, sin reina) |
| Matriarca / Reina | Deshabilitada (código conservado; no spawnea) |
| Iconos de creativo | Completo |
| Spawns leves de rastro en cuevas | Completo |

## Variantes adultas

| Variante | ID de entidad | Stats / notas |
|----------|---------------|----------------|
| Araneo Silverfish | `araneo_silverfish` | 8 HP, daño 2, vel. 0.28. Terrestre; trepa. El punto medio. |
| Mantis Silverfish | `mantis_silverfish` | 7 HP, daño 3, vel. 0.30. Más daño. |
| Fly Silverfish | `fly_silverfish` | 5 HP, daño 1.5, vel. 0.36. Frágil y rápido; persigue en el aire. |
| Crab Silverfish | `crab_silverfish` | 14 HP, daño 2, vel. 0.22, armadura 3. Más aguante. |
| Queen Silverfish | `queen_silverfish` | **Deshabilitada.** No sale en worldgen ni en creativo. |

### Animaciones

| Animación | Quién | Cuándo |
|-----------|-------|--------|
| `walk` | Adultos y reina | Solo al desplazarse de verdad |
| `none` | Adultos y reina | Pose neutra al detenerse |
| `idle` / `idle2` | Adultos y reina | Idle ocasional (~35 %), una vez, luego `none` |
| `idle3` | Fly y reina | Igual que los otros idles |
| `startFlying` → `flying` | Solo Fly | Despegue y vuelo |
| `stopFlying` | Solo Fly | Aterrizaje sin daño por caída |
| `putEgg` | Reina (deshabilitada) | No se usa en el juego actual |

### Comportamiento

- **Deambulación errática**: trayectos cortos, cambios de rumbo, pausas “curiosas” y velocidad variable (terrestres; Fly solo en suelo).
- **Mirada curiosa**: giros de cabeza ocasionales.
- **Trepar**: Araneo, Mantis y Crab trepan paredes como las arañas vanilla (siguen derechos; no se pegan al techo). El Fly y la larva no trepan.
- **Agresión**: como el silverfish vanilla, atacan al jugador en cuanto lo ven. Si hieren a un adulto, los aliados cercanos se vuelven contra el atacante.
- **Loot de nido**: solo los adultos **persistentes** (los de la cámara) pueden soltar restos de cueva en poca cantidad (slime, pepitas, carbón/redstone/cobre). Los salvajes y los eclosionados no, para no farmear.
- **Fly**: vuelo local respecto al suelo/techo cercano (sin atravesar techos ni despegar en techos bajos). Si tiene objetivo, lo persigue en el aire con alcance de melee del silverfish vanilla + 10 %.

## Huevos

- Stack 1–4; se colocan en **suelo, paredes y techo** (`facing`).
- Sonido de colocación tipo tortuga (`metal.place`); rotura `TURTLE_EGG_BREAK`.
- Aplastamiento solo en suelo (como huevos de tortuga).
- ~60 % de chance de larva al romper/aplastar un huevo.
- Larva, silverfish vanilla y adultos **no aplastan** los huevos.
- Toda la colonia **atraviesa** los huevos (sin colisión ni saltos de pathfinding).

## Larvas

- No atacan; huyen del jugador; sin salto; `walk` a velocidad ×2.
- Tras caminar ~1 bloque o ~1.5 s pueden infestarse.
- Animaciones `insertStone_Front` / `insertStone_Down`, alineadas a la cara del bloque.
- Una larva por piedra (reserva).
- Partículas de polvo al entrar (sin burst de rotura).

## Piedra infestada (vanilla)

El mod registra el momento de la infestación (`ColonyInfestationData`). Al romper:

| Edad | Resultado |
|------|-----------|
| 0–3 min | Larva |
| 3–10 min | Silverfish vanilla |
| 10+ min | Adulto al azar (Araneo / Mantis / Fly / Crab) |

## Cámara de cría

Estructura subterránea natural (no dungeon). Localizar con:

```
/locate structure silverfish-expansion:breeding_chamber
```

### Forma

- Cavidad irregular por metaballs (varios lóbulos + salas laterales).
- Red tipo hormiguero **más extensa**: más nodos satélite, más cruces y más pasadizos exploratorios.
- Algunos túneles conectan con cuevas vanilla; otros se pierden en la roca.
- Rareza aprox.: spacing 32 / separation 12; altura entre y=-44 y y=4.

### Decoración (filosofía vanilla)

Piedra / deepslate dominante, con manchas raras y **bordes ruidosos** de:

- grava y **grava sospechosa** (~20 % de la grava; loot vanilla `trail_ruins_archaeology_common`)
- manchas de **musgo pálido**, algunas con alfombra de musgo pálido
- toba, andesita, cobble
- vetas de carbón, hierro, cobre y algo de redstone
- liquen brillante (más denso en las salas/nodos), raíces colgantes, musgo pálido colgante
- hongos, huevos irregulares, piedras infestadas maduras

**Sin** agua (las zonas bajas son depresiones de grava/musgo), sin dripstone, sin cofres, sin spawners, sin loot artificial.

### Fauna en la cámara

Al generar la estructura aparecen **6–12 adultos** repartidos por las salas (nodos), no amontonados en un solo centro. Los huevos también se esparcen por esos nodos.

Fuera de la cámara hay **spawns leves** de adultos en cuevas oscuras (aprox. y=-54 a y=24, máximo 3 en 40 bloques). Sirven de rastro: el jugador nota que “aquí hay algo más” y, explorando, llega al nido.

El silverfish vanilla sigue saliendo al romper piedra infestada. Las piedras infestadas de la cámara están “maduras”: al romperlas sale un adulto.

Hay un tope blando de combatientes cerca de la colonia (~14) para que no se sature.

### Huevos (eclosión)

Además de aplastar/romper: los huevos hacen **tick aleatorio**, agrietan (como tortuga) y eclosionan una larva por etapa final. La eclosión se frena si la colonia ya está llena de adultos.

## Pendiente

| Pieza | Rol |
|-------|-----|
| **Balance fino** | Frecuencia/cantidad de puestas y eclosión |

## En el juego

- Spawn eggs de adultos y larva, más el ítem de huevos, en creativo.
- Dependencias: **Fabric API** y **GeckoLib**.
- Idiomas: inglés (`en_us`) y español México (`es_mx`).

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

1. **File → Export → Export GeckoLib Model** → `geckolib/models/entity/{id}.geo.json`
2. **File → Export → Export GeckoLib Animations** → `geckolib/animations/entity/{id}.animation.json`
3. Textura → `textures/entity/{id}.png`

IDs: `araneo_silverfish`, `mantis_silverfish`, `fly_silverfish`, `crab_silverfish`, `queen_silverfish`, `larva`.

### Huevos (bloque Java)

Modelos `block/silverfish_eggs_1` … `_4`, blockstate con `eggs` + `facing`, ítem en `textures/item/silverfish_eggs.png`.

### Iconos

PNG 16×16 en `textures/item/` para spawn eggs e ítem de huevos.

## Compilar

```powershell
.\gradlew.bat build
```

JAR: `build/libs/silverfish-expansion-1.0.0.jar`

## Autor y licencia

Autor: **MOISES** (Suchinecoast92). Todos los derechos reservados. Ver `LICENSE`.

## Repositorio

https://github.com/Suchinecoast92/Silverfish-Expansion
