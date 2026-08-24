# Silverfish Expansion

Mod de **Fabric** para Minecraft **1.21.11** que añade variantes animadas de silverfish con **GeckoLib** y un sistema de **colonia**.

Ciclo de vida:

**Huevos → Larva → Piedra infestada vanilla → Silverfish → Adulto (Araneo / Mantis / Fly / Crab) → Matriarca → Nuevos huevos**

## Estado actual

| Contenido | Estado |
|-----------|--------|
| Adultos Araneo / Mantis / Fly / Crab | Completo |
| Trepar paredes (como araña vanilla) | Completo (terrestres + reina) |
| Deambulación errática / curiosa | Completo |
| Huevos amontonables | Completo |
| Eclosión natural de huevos | Completo |
| Larvas e infestación | Completo |
| Maduración en piedra infestada vanilla | Completo |
| Cámara de cría (worldgen) | Completo |
| Matriarca / Reina | Completo (puesta + alerta; decadencia sin ella) |
| Iconos de creativo | Completo |
| Spawns leves de rastro en cuevas | Completo |

## Variantes adultas

| Variante | ID de entidad | Stats / notas |
|----------|---------------|----------------|
| Araneo Silverfish | `araneo_silverfish` | 8 HP, daño 2, vel. 0.28. Terrestre; trepa. El punto medio. |
| Mantis Silverfish | `mantis_silverfish` | 7 HP, daño 3, vel. 0.30. Más daño. |
| Fly Silverfish | `fly_silverfish` | 5 HP, daño 1.5, vel. 0.36. Frágil y rápido; persigue en el aire. |
| Crab Silverfish | `crab_silverfish` | 14 HP, daño 2, vel. 0.22, armadura 3. Más aguante. |
| Queen Silverfish | `queen_silverfish` | 20 HP, vel. 0.22, escala 1.5. No ataca; huye al herirla. Pone huevos. |

### Animaciones

| Animación | Quién | Cuándo |
|-----------|-------|--------|
| `walk` | Adultos y reina | Solo al desplazarse de verdad |
| `none` | Adultos y reina | Pose neutra al detenerse |
| `idle` / `idle2` | Adultos y reina | Idle ocasional (~35 %), una vez, luego `none` |
| `idle3` | Fly y reina | Igual que los otros idles |
| `startFlying` → `flying` | Solo Fly | Despegue y vuelo |
| `stopFlying` | Solo Fly | Aterrizaje sin daño por caída |
| `putEgg` | Solo reina | Puesta de huevos (~6 s) |

### Comportamiento

- **Deambulación errática**: trayectos cortos, cambios de rumbo, pausas “curiosas” y velocidad variable (terrestres y reina; Fly solo en suelo).
- **Mirada curiosa**: giros de cabeza ocasionales.
- **Trepar**: Araneo, Mantis, Crab y la reina trepan paredes como las arañas vanilla (siguen derechas; no se pegan al techo). El Fly y la larva no trepan.
- **Alerta de colonia**: si hieren a un adulto o a la reina, los aliados cercanos (adultos y silverfish vanilla) se vuelven contra el atacante. La reina no pelea: huye.
- **Puesta**: la reina se clava, reproduce `putEgg` y deja un huevo detrás. Condiciones y topes más abajo.
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

- grava y **grava sospechosa** (~20 % de la grava; loot vanilla `trail_ruins_archaeology_common`)
- tierra, tierra basta, tierra enraizada (soporte de raíces)
- toba, andesita, cobble
- vetas de carbón, hierro, cobre y algo de redstone
- liquen brillante (única luz), raíces colgantes, musgo pálido colgante
- hongos, huevos irregulares, piedras infestadas maduras

**Sin** agua (las zonas bajas son depresiones de grava/tierra), sin dripstone, sin cofres, sin spawners, sin loot artificial.

### Fauna en la cámara

Al generar la estructura aparecen **1 Matriarca** y **3–12 adultos** (no cuenta la reina), repartidos por los chunks de la cámara.

Fuera de la cámara hay **spawns leves** de adultos en cuevas oscuras (aprox. y=-54 a y=24, máximo 3 en 40 bloques). No hay reina salvaje. Sirven de rastro: el jugador nota que “aquí hay algo más” y, explorando, llega al nido.

La reina se queda cerca de su ancla y pone huevos periódicamente. Si la hieren, huye y alerta a la colonia; ella no ataca. Si muere, deja de haber puestas nuevas; los huevos ya colocados siguen eclosionando. El silverfish vanilla sigue saliendo al romper piedra infestada. Las piedras infestadas de la cámara están “maduras”: al romperlas sale un adulto.

Hay un tope blando de combatientes cerca de la colonia (~14) para que no se sature.

### Puesta de la reina

Cada ciclo coloca **un huevo** (cluster nuevo, o +1 si ya hay uno detrás con menos de 4). No suelta varios de un solo `putEgg`.

Para empezar sola hace falta todo esto a la vez:

- reina viva, no herida en ~4 s y no poniendo ya
- cooldown a 0 (~1–2 min al aparecer; ~2–4 min tras una puesta; ~30–50 s si no halló sitio)
- menos de **10 clusters** en 12 bloques del hogar
- menos de **14 combatientes** (adultos + silverfish vanilla) en 48 bloques
- tirada de ~5 % cada tick

Al arrancar se queda **quieta**: misma posición y rotación durante `putEgg` (~6 s). El huevo sale **justo detrás de la cola**, en el suelo. Si la golpean a mitad, aborta y no pone nada.

### Huevos (eclosión)

Además de aplastar/romper: los huevos hacen **tick aleatorio**, agrietan (como tortuga) y eclosionan una larva por etapa final. La eclosión se frena si la colonia ya está llena de adultos.

## Pendiente

| Pieza | Rol |
|-------|-----|
| **Balance fino** | Frecuencia/cantidad de puestas y eclosión |

## En el juego

- Spawn eggs de adultos, reina y larva, más el ítem de huevos, en creativo.
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
