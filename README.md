# Total Golems

Mod de Fabric para Minecraft 26.3 que añade gólems mineros construidos con bloques de mineral.

## Cómo se usa

1. Coloca un **bloque de hierro** o un **bloque de diamante** y pon encima una **calabaza tallada** (o una calabaza iluminada).
   El bloque se convierte en un cofre de hierro o de diamante y aparece el gólem, asociado a ese cofre.
2. Dale una **flor pequeña** (amapola, diente de león, orquídea...) con clic derecho. Se la pone en la cabeza y empieza a picar
   en línea recta hacia donde estés mirando.
3. Clic derecho con la **mano vacía** para quitarle la flor: deja de picar y lleva al cofre lo que tuviera encima.

Darle otra flor a un gólem que ya tiene una solo cambia la dirección del túnel.

| | Gólem de hierro | Gólem de diamante |
|---|---|---|
| Túnel | 1 de ancho x 2 de alto | 3 x 3 |
| Inventario | 5 huecos | 15 huecos |
| Velocidad | Herramientas de hierro | Herramientas de diamante con Prisa II |
| Vida | 30 | 60 |

Cuando no le cabe un objeto más, el gólem vuelve a su cofre, lo vacía todo y regresa al punto donde lo dejó.
Si el cofre está lleno (o lo han roto) se queda esperando junto a él y no sigue picando.

### Detalles

- No rompe bloques irrompibles ni bloques con datos (cofres, generadores...): se detiene delante hasta que el camino quede libre.
- El túnel sigue la altura del gólem: si cae a una cueva, continúa picando desde ahí en la misma dirección.
- Es inmune al fuego y no se ahoga.
- Solo se mueve en chunks cargados, como cualquier otra criatura.
- Si lleva diez segundos sin poder acercarse al cofre o al túnel, se teletransporta a su destino.
- Los cofres también se fabrican: un cofre rodeado de 8 lingotes de hierro o de 8 diamantes. Se unen en cofres dobles.
- Un gólem invocado con `/summon` adopta el cofre de hierro o diamante más cercano (8 bloques) al recibir la flor.
- La forma de gólem de hierro de siempre (la T de cuatro bloques) sigue creando el gólem de hierro normal.

## Instalación

Copia `build/libs/totalgolems-1.0.0.jar` a la carpeta `mods` junto con [Fabric API](https://modrinth.com/mod/fabric-api) para 26.3.

## Compilar

Hace falta un JDK 25.

```bash
./gradlew build
```

- `./gradlew runGameTest` ejecuta las pruebas de comportamiento (`src/gametest`).
- `./gradlew runClientGameTest` abre el juego, monta una escena con los gólems y cofres y guarda una captura en
  `build/run/clientGameTest/screenshots`.
- `python tools/generate_textures.py` regenera todas las texturas (necesita Pillow).
