# Silverfish Expansion

Mod de **Fabric** para Minecraft **1.21.11** que añade variantes animadas de silverfish con **GeckoLib**, trepar por **AWCAPI** y un sistema de **colonia**.

Ciclo de vida:

**Huevos → Larva → Piedra infestada vanilla → Silverfish → Adulto (Araneo / Mantis / Fly / Crab) → Matriarca → Nuevos huevos**

## Estado actual

| Contenido | Estado |
|-----------|--------|
| Adultos Araneo / Mantis / Fly / Crab | Completo |
| Trepar paredes y techos (AWCAPI) | Completo (terrestres + reina) |
| Deambulación errática / curiosa | Completo |
| Huevos amontonables | Completo |
| Eclosión natural de huevos | Completo |
| Larvas e infestación | Completo |
| Maduración en piedra infestada vanilla | Completo |
| Cámara de cría (worldgen) | Completo |
| Matriarca / Reina | Completo (puesta + alerta; decadencia sin ella) |
| Iconos de creativo | Completo |
| Spawns naturales fuera de la cámara | Pendiente |

## Variantes adultas

| Variante | ID de entidad | Notas |
|----------|---------------|--------|
| Araneo Silverfish | `araneo_silverfish` | Terrestre; trepa paredes y techos |
| Mantis Silverfish | `mantis_silverfish` | Terrestre; trepa paredes y techos |
| Fly Silverfish | `fly_silverfish` | Vuela a baja altura; persigue en el aire |
| Crab Silverfish | `crab_silverfish` | Terrestre; trepa paredes y techos |
| Queen Silverfish | `queen_silverfish` | Matriarca; trepa más lenta |

### Animaciones

| Animación | Quién | Cuándo |
|-----------|-------|--------|
| `walk` | Adultos y reina | Solo al desplazarse de verdad (también en muros/techos) |
| `none` | Adultos y reina | Pose neutra al detenerse |
| `idle` / `idle2` | Adultos y reina | Idle ocasional (~35 %), una vez, luego `none` |
| `idle3` | Fly y reina | Igual que los otros idles |
| `startFlying` → `flying` | Solo Fly | Despegue y vuelo |
| `stopFlying` | Solo Fly | Aterrizaje sin daño por caída |
| `putEgg` | Solo reina | Puesta de huevos (~6 s) |

### Comportamiento

- **Deambulación errática**: trayectos cortos, cambios de rumbo, pausas “curiosas” y velocidad variable (terrestres y reina; Fly solo en suelo).
- **Mirada curiosa**: giros de cabeza ocasionales.
- **Trepar (AWCAPI)**: Araneo, Mantis, Crab y la reina caminan por paredes y techos. El modelo se orienta a la superficie. El Fly y la larva no trepan.
- **Fly**: vuelo local respecto al suelo/techo cercano (sin atravesar techos ni despegar en techos bajos). Si tiene objetivo, lo persigue en el aire con alcance de melee del silverfish vanilla + 10 %.

## Huevos

- Stack 1–4; se colocan en **suelo, paredes y techo** (`facing`).
- Sonido de colocación tipo tortuga (`metal.place`); rotura `TURTLE_EGG_BREAK`.
- Aplastamiento solo en suelo (como huevos de tortuga).
- ~60 % de chance de larva al romper/aplastar un huevo.
- Larva, silverfish vanilla, adultos y reina **no aplastan** los huevos.
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
- Red tipo hormiguero: nodos satélite conectados por túneles, cruces entre nodos y túneles exploratorios largos.
- Algunos túneles conectan con cuevas vanilla; otros se pierden en la roca.
- Rareza aprox.: spacing 32 / separation 12; altura entre y=-44 y y=4.

### Decoración (filosofía vanilla)

Piedra / deepslate dominante, con manchas raras y **bordes ruidosos** de:

- grava y grava sospechosa (loot vanilla `trail_ruins_archaeology_common`)
- tierra, tierra basta, tierra enraizada (soporte de raíces)
- toba, andesita, cobble
- vetas de carbón, hierro, cobre y algo de redstone
- liquen brillante (única luz), raíces colgantes, musgo pálido colgante
- hongos, huevos irregulares, piedras infestadas maduras

**Sin** agua (las zonas bajas son depresiones de grava/tierra), sin dripstone, sin cofres, sin spawners, sin loot artificial.

### Fauna en la cámara

Al generar la estructura aparecen **1 Matriarca** y **3–12 adultos** (no cuenta la reina), repartidos por los chunks de la cámara. No hay spawns de colonia en biomas fuera de la estructura.

La reina se queda cerca de su ancla, pone huevos periódicamente (animación `putEgg`) y, al recibir daño, alerta a la colonia cercana. Si muere, deja de haber puestas nuevas; los huevos ya colocados siguen eclosionando. El silverfish vanilla sigue saliendo al romper piedra infestada. Las piedras infestadas de la cámara están “maduras”: al romperlas sale un adulto.

Hay un tope blando de combatientes cerca de la colonia (~14) para que no se sature.

### Huevos (eclosión)

Además de aplastar/romper: los huevos hacen **tick aleatorio**, agrietan (como tortuga) y eclosionan una larva por etapa final. La eclosión se frena si la colonia ya está llena de adultos.

## Pendiente

| Pieza | Rol |
|-------|-----|
| **Balance fino** | Frecuencia/cantidad de puestas y eclosión |
| **Spawns naturales** | Colonia / adultos en cuevas fuera de la estructura |

## En el juego

- Spawn eggs de adultos, reina y larva, más el ítem de huevos, en creativo.
- Dependencias: **Fabric API**, **GeckoLib** y **Advanced Wall Climber API (AWCAPI)**. En un launcher hay que poner también el jar de AWCAPI.
- Idiomas: inglés (`en_us`) y español México (`es_mx`).

## Requisitos

| Dependencia | Versión |
|-------------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.19.2 |
| Fabric API | 0.141.4+1.21.11 |
| GeckoLib | 5.4.4 |
| Advanced Wall Climber API | 1.0.2 (Fabric 1.21.11) |
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

## Repositorio

https://github.com/Suchinecoast92/Silverfish-Expansion
