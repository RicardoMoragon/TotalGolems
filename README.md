# Total Golems

Mod de Fabric para Minecraft 26.3 que añade gólems mineros construidos con bloques de mineral.

## Cómo se usa

1. Coloca un **bloque de hierro**, de **diamante** o de **netherita** y pon encima una **calabaza tallada** (o una calabaza
   iluminada). El bloque se convierte en un cofre de ese material y aparece el gólem delante, asociado a ese cofre.
2. Dale una **flor** con clic derecho. Se la pone en la cabeza y empieza a trabajar. La flor decide el patrón (ver abajo).
3. Clic derecho con la **mano vacía** para quitarle la flor: deja de picar y lleva al cofre lo que tuviera encima.

| | Hierro | Diamante | Netherita |
|---|---|---|---|
| Túnel | 1 de ancho x 2 de alto | 3 x 3 | 3 x 3, los 9 bloques a la vez |
| Inventario | 5 huecos | 15 huecos | 27 huecos |
| Herramientas | Hierro | Diamante con Prisa II | Netherita con Prisa II |
| Movimiento | Normal | Normal | Velocidad II |
| Vida | 30 | 60 | 100 |

Cuando no le cabe un objeto más, el gólem vuelve a su cofre, lo vacía todo y regresa al punto donde lo dejó.
Si el cofre está lleno (o lo han roto) se queda esperando junto a él y no sigue picando.

### Flores

El gólem trabaja siempre a la altura a la que está cuando recibe la flor y nunca pisa una casilla sin suelo: si falta,
coloca un bloque de su inventario. Si no tiene bloques, vuelve al cofre, deja lo que lleve y coge hasta 64; si en el cofre
tampoco hay, se queda esperando allí. Para el suelo solo usa bloques completos corrientes (nada de menas, bloques de
mineral, arena o grava).

- **Amapola**: túnel en línea recta hacia donde miras al dársela, sin cambiar de altura. Si se topa con un bloque que no
  puede romper (roca madre, un cofre, un generador...) da el trabajo por terminado, vuelve al cofre y espera allí a que le
  des otra flor.
- **Diente de león**: cantera del chunk en el que está. Pica toda la capa a su altura, baja tantos bloques como alto es su
  túnel (2 el de hierro, 3 los otros) y repite, hasta llegar a la roca madre.
  - El anillo exterior del chunk queda reservado para una **escalera en espiral**: de cada columna del borde el gólem deja
    (o coloca) un bloque como peldaño, y por ella sube y baja al cofre.
  - Si el cofre está dentro del chunk y a su misma altura, deja también una pasarela desde lo alto de la escalera hasta él.
  - Los cofres y bloques irrompibles que encuentra los rodea y los deja en pie.
  - Si le quitas la flor y se la vuelves a dar dentro del mismo pozo, sigue con la misma cantera y la misma escalera.

Darle la flor que ya lleva reinicia el patrón desde donde está; darle la otra se la cambia y te devuelve la anterior.
El resto de flores no tienen patrón y no las acepta.

### Encantamientos

Clic derecho con un **libro encantado** de **Fortuna** (I, II o III) o **Toque de seda**. El libro se gasta y el gólem pica
como si sus herramientas llevaran ese encantamiento. Solo puede tener uno: al darle otro libro te devuelve el anterior.
La antena brilla en **rojo** con Fortuna y en **azul** con Toque de seda. Al morir suelta el libro.

### Detalles

- Es inmune al fuego y no se ahoga.
- Solo se mueve en chunks cargados, como cualquier otra criatura.
- Si lleva diez segundos sin poder moverse hacia donde va, se teletransporta a su destino.
- Los cofres también se fabrican: un cofre rodeado de 8 lingotes de hierro, 8 diamantes u 8 lingotes de netherita.
  Se unen en cofres dobles.
- Un gólem invocado con `/summon` adopta el cofre de gólem más cercano (8 bloques) al recibir la flor.
- La forma de gólem de hierro de siempre (la T de cuatro bloques) sigue creando el gólem de hierro normal.

## Instalación

Copia `build/libs/totalgolems-1.1.0.jar` a la carpeta `mods` junto con [Fabric API](https://modrinth.com/mod/fabric-api) para 26.3.

## Compilar

Hace falta un JDK 25.

```bash
./gradlew build
```

- `./gradlew runGameTest` ejecuta las pruebas de comportamiento (`src/gametest`).
- `./gradlew runClientGameTest` abre el juego, monta una escena con los gólems y cofres y guarda una captura en
  `build/run/clientGameTest/screenshots`.
- `python tools/generate_textures.py` regenera todas las texturas (necesita Pillow).
