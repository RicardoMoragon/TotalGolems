# Total Golems

Mod de Fabric para Minecraft 26.3 que añade gólems de trabajo construidos con bloques de mineral.

| Bloque | Gólem | Flor que lo activa | Herramienta | Qué hace |
|---|---|---|---|---|
| Hierro, diamante, netherita | Minero | Amapola o diente de león | Pico | Hace minas en tiras o canteras y lo lleva a su cofre |
| Carbón | Fundidor | Margarita (oxeye daisy) | — | Vacía los cofres de los demás: funde lo fundible y lleva el resto a un cofre de cobre |
| Amatista | Leñador | Lirio de los valles | Hacha | Tala y replanta los árboles de su chunk (3x3 chunks con un catalejo) |
| Heno | Agricultor | Aciano (cornflower) | Azada | Ara, siembra y cosecha el campo vallado en el que está |
| Hueso | Ganadero | Tulipán blanco | Espada | Alimenta, cría y sacrifica los animales de su recinto |

Todos se crean igual: el bloque con una **calabaza tallada** encima. Se activan dándoles su flor con clic derecho y se
paran quitándosela con la mano vacía. Tienen los **ojos encendidos solo mientras trabajan**.

Tienen el cuerpo del gólem de cobre, pero **sin antena** (es solo del de cobre): llevan la flor sobre la cabeza. Cada uno
tiene algo propio:

- **Hierro:** brazos largos y gruesos que casi llegan al suelo, como los del gólem de hierro.
- **Diamante:** más grande y tosco, con bultos de piedra en los hombros, la cabeza y la espalda.
- **Netherita:** forjado como una armadura, con hombreras, una cresta en la cabeza y un cinturón.
- **Amatista:** le salen cristales pequeños de la cabeza y de los brazos.
- **Heno:** lleva un sombrero de paja, con la flor en la cinta, que salta cada vez que da un golpe de azada.
- **Hueso:** es hueco: el pecho es una caja torácica por la que se ve a través, con la columna dentro.
- **Carbón:** tiene agujeros donde tenía los puntos naranjas, y por ellos se ve su interior al rojo, que brilla.

## Herramientas

El minero, el leñador, el agricultor y el ganadero trabajan con una **herramienta de verdad** que cogen de su propio
cofre y llevan en la mano: pico, hacha, azada y espada. Vale cualquiera, de cualquier material.

- La herramienta **se gasta con cada uso igual que en manos de un jugador** (un punto por bloque picado, tronco talado,
  casilla arada, cultivo cosechado o animal sacrificado) y se rompe al llegar a cero. Irrompibilidad funciona como siempre.
- La **piedra y la pizarra naturales gastan la mitad**: de cada dos bloques, solo el primero desgasta el pico. Cuentan la
  piedra, la pizarra profunda, el granito, la diorita, la andesita y la toba. Las menas y el resto gastan un punto.
- Al romperse, el gólem **vuelve a su cofre a por otra**. Si no hay, se queda **junto al cofre con los ojos apagados**
  hasta que pongas una; en cuanto la hay, la coge y sigue donde lo dejó.
- Sin herramienta no hacen nada de su trabajo: hay que dejarles una en el cofre antes de darles la flor. La excepción
  es el ganadero, que sin espada sigue alimentando y recogiendo y solo deja de sacrificar.
- Al morir sueltan la herramienta que llevan.

### Cofres que se tocan

Los cofres del mod **del mismo tipo que se tocan** (simples o dobles, también uno encima de otro) forman **un único
inventario** para su gólem: guarda en todos y busca en todos, herramientas incluidas. Un grupo llega hasta **8 cofres**
(un cofre doble cuenta como uno). Sirve para todos los tipos de cofre.

## Gólems mineros

1. Coloca un **bloque de hierro**, de **diamante** o de **netherita** y pon encima una **calabaza tallada** (o una calabaza
   iluminada). El bloque se convierte en un cofre de ese material y aparece el gólem delante, asociado a ese cofre.
2. Dale una **flor** con clic derecho. Se la pone en la cabeza y empieza a trabajar. La flor decide el patrón (ver abajo).
3. Clic derecho con la **mano vacía** para quitarle la flor: deja de picar y lleva al cofre lo que tuviera encima.

| | Hierro | Diamante | Netherita |
|---|---|---|---|
| Túnel | 1 de ancho x 2 de alto | 3 x 3 | 3 x 3 |
| Bloques que pica a la vez | 1 | 2 | 3 |
| Inventario | 9 huecos | 15 huecos | 27 huecos |
| Movimiento | Normal | Normal | Velocidad II |

Los tres tienen **60 de vida** y pican cada bloque a la velocidad del pico, sin Prisa: lo que cambia con el material es
cuántos bloques trabajan a la vez. Más bloques a la vez es más rápido, y gasta el pico igual de rápido.

El material del gólem ya no decide con qué pica: **pica con el pico que lleva**, a la velocidad de ese pico y con sus
encantamientos (**Eficiencia**, **Fortuna**, **Toque de seda**, Irrompibilidad...). Un pico demasiado flojo para un
mineral lo rompe despacio y sin soltar nada. Los libros encantados ya no se le dan al gólem: encanta el pico.

**Pala (opcional):** si hay una **pala** en su cofre, la coge al pasar por él y la lleva en la otra mano. Con ella pica
**la tierra, la grava, la arena** y todo lo que una pala rompe más rápido que su pico, a la velocidad de esa pala y con
sus encantamientos, y es la pala la que se gasta. Sin pala lo pica todo con el pico, como antes. Nunca espera por una
pala: si se le rompe, sigue con el pico hasta que vuelva al cofre y haya otra.

El gólem de **netherita** recién creado **no acepta amapola ni diente de león**. Dale primero una **rosa de Wither**: se
gasta y lo desbloquea para siempre. Después elige patrón con amapola o diente de león como los demás.

Cuando no le cabe un objeto más, el gólem vuelve a su cofre, lo vacía todo y regresa al punto donde lo dejó.
Si el cofre está lleno (o lo han roto) se queda esperando junto a él y no sigue picando.

### Flores

El gólem trabaja siempre a la altura a la que está cuando recibe la flor y nunca pisa una casilla sin suelo: si falta,
coloca un bloque de su inventario. Solo usa bloques completos corrientes que se pican con pico y no cuestan mucho de
picar (piedra, adoquín, pizarra, ladrillos...; nada de tierra, madera, obsidiana, menas, bloques de mineral, arena o
grava), así siempre puede volver a quitarlos.

- **Siempre lleva 2 huecos de bloques** (los más llenos, hasta 128 bloques) que no deja en el cofre mientras lleva su
  flor: son los que usa para tapar el agua y poner suelo. Picando piedra se le llenan solos.
- **Cada vez que pasa por el cofre** (a descargar, a por un pico...) completa esos 2 huecos con los bloques que haya en
  él, aunque no se haya quedado sin ninguno.
- Si se queda sin bloques, vuelve al cofre a por más; si en el cofre tampoco hay, se queda esperando allí.

- **Amapola**: **mina en tiras** (*strip mining*) en la capa en la que está, y solo dentro de los **3x3 chunks** que hay
  alrededor del suyo.
  1. **Túnel principal:** en línea recta desde donde está hasta el borde de esa zona, hacia donde miras al darle la flor.
     Al llegar se da la vuelta y lo sigue hasta el borde contrario, así que el túnel cruza la zona entera.
  2. **Ramales:** luego hace túneles perpendiculares al principal, de borde a borde, dejando **3 bloques sin picar entre
     uno y otro**, aunque alguna fila no llegue a verse nunca. Hace todos los que caben.
  - El de **hierro** pica túneles de 1x2, así que hace un ramal cada 4 bloques. Los de **diamante y netherita** pican de
    3x3, y hacen un ramal cada 6.
  - **Vetas:** si ve mineral en las paredes, el techo o el suelo (a menos de 5 bloques), se para y pica **la veta entera,
    hasta el último bloque**: todo el mineral que toca a ese, aunque siga lejos del túnel o detrás de la piedra. Una veta
    que cruza el borde de su zona la sigue hasta **8 bloques más allá**, para no dejar menas a medias. Luego sigue con su ruta. Un mineral suelto que no ve, tapado por piedra, se queda donde está.
  - **Agua y lava:** si ve agua o lava, la tapa con bloques para que no le corte el camino (ver *Agua*).
  - **Obstáculos:** si algo que no puede romper le corta un túnel (roca madre, un cofre, un generador...), ese túnel acaba
    ahí y pasa al siguiente. Si es el principal el que está cortado, se salta los ramales que hay más allá.
  - **Grava y arena:** si le cae encima o se le mete en el túnel, la pica. Si se queda encima de un bloque que no debería
    estar en su túnel, lo pica y vuelve a bajar. Los mineros no se asfixian enterrados: siguen picando hasta salir.
  - **Final:** cuando termina toda la mina vuelve al cofre y espera allí a que le des otra flor. Si se queda sin camino de
    vuelta (lava, una cueva) dentro de su zona, se teletransporta al cofre o al túnel.
- **Diente de león**: cantera del chunk en el que está, **capa a capa**. Pica toda la capa a su altura, baja tantos bloques
  como alto es su túnel (2 el de hierro, 3 los otros) y repite, hasta llegar a la roca madre. No empieza la capa siguiente
  mientras quede agua en la que está.
  - Si se queda sin bloques, antes de ir al cofre los saca de la capa que está picando (ver *Agua*).
  - El anillo exterior del chunk queda reservado para una **escalera en espiral**: de cada columna del borde el gólem deja
    (o coloca) un bloque como peldaño y el de debajo, y por ella sube y baja al cofre. Si bajo un peldaño había un hueco
    (una cueva, un desnivel del terreno), lo rellena: sin él podría meterse debajo y quedarse sin poder subir.
  - Si el cofre está dentro del chunk y a su misma altura, deja también una pasarela desde lo alto de la escalera hasta él,
    y el bloque sobre el que está el cofre.
  - Si a la escalera o a la pasarela les falta algún bloque (porque el terreno tenía un hueco o algo lo rompió), lo repone
    al pasar cerca, para no caerse al pozo cada vez que va al cofre.
  - Los cofres y bloques irrompibles que encuentra los rodea y los deja en pie.
  - Si le quitas la flor y se la vuelves a dar dentro del mismo pozo, sigue con la misma cantera y la misma escalera.
  - **Un gólem por chunk.** Si el chunk ya lo está picando otro gólem, el nuevo se queda con el chunk libre más cercano
    (hasta dos chunks de distancia) y va hasta él abriéndose paso. Así no se destrozan las escaleras ni el suelo entre ellos.
    Cuando pasa, te lo dice un mensaje sobre la barra de acción. Solo cuenta como ocupado el chunk de un gólem que lleva
    puesto el diente de león y no se ha parado; si no lo pica nadie, el gólem pica el chunk en el que está, aunque su cofre
    esté en otro.
  - Deja una pasarela hasta todos los cofres de gólem que haya dentro del chunk, no solo el suyo.
  - No recorre las zonas de una capa donde no hay nada que picar: una capa de aire se da por hecha al momento.
  - **Cada vez que vuelve a bajar** (después de dejar cosas en el cofre o de ir a por un pico o bloques), repasa la capa
    entera: si han aparecido bloques en la parte ya picada (grava que ha caído, bloques puestos) los quita, y si ha
    entrado agua la tapa con bloques y luego los pica. Si el agua que corre dentro del chunk no tiene fuente dentro, es que
    entra desde fuera, y la tapia por fuera del chunk (ver *Agua*).

Darle la flor que ya lleva reinicia el patrón desde donde está; darle la otra se la cambia y te devuelve la anterior.
El resto de flores no tienen patrón y no las acepta.

### Agua

Las corrientes no arrastran a los mineros: el agua solo los ralentiza. Además, **el minero sella el agua y la lava** que se
encuentra, como haría un jugador:

- En una **cantera** quita el agua **capa a capa**, igual que pica: solo se ocupa del agua de la capa en la que está, y
  dentro de ella empieza por arriba.
  - Tapa con un bloque las fuentes de esa capa dentro del chunk y luego pica esos bloques como cualquier otro. Así el agua
    no vuelve a formar fuentes nuevas mientras pica (lo que hacía que un acuífero fuera imposible de vaciar).
  - Por donde entra agua o lava desde fuera del chunk, a la altura de la capa, pone un bloque que se queda ahí como
    pared. En la primera capa también tapa la que cae desde arriba (bajo el mar o junto a un río).
  - **No pone bloques lejos:** solo a unos 4 bloques de donde está, así que va hasta el agua para taparla.
  - **Si se queda sin bloques mientras quita agua**, pica uno de su capa: primero los que ya puso en el agua (donde ya no
    queda agua), y si no, el más cercano que no tenga una fuente al lado. Si está lejos, va hasta él. Solo si en la capa
    no queda ninguno vuelve al cofre a por más. Solo coge bloques a los que puede llegar: al alcance, o con una casilla al
    lado en la que pueda estar.
  - La **lava** la tapa siempre antes de picar el bloque de al lado.
- En una **mina en tiras**, antes de romper cada bloque mira lo que hay alrededor del hueco que va a abrir: el techo, las
  paredes y el suelo de la casilla a la que va a entrar. Si hay agua o lava (fuente o corriente), la tapa primero con un
  bloque y luego rompe. Lo mismo al sacar mineral de una veta. También tapa las fuentes que hay en el bloque que va a
  picar. El agua o la lava que ya está dentro de sus túneles no la toca.
- Sin fuentes, el agua que quedaba se va sola.
- Para esto usa los bloques de construcción que lleva encima. En una mina en tiras, si no le quedan, **no rompe el
  bloque**: vuelve al cofre a por más y, si en el cofre tampoco hay, espera allí.

Si aun así se queda dentro del agua, sigue picando. Los gólems flotan: en agua profunda el minero sigue trabajando
aunque flote por encima de la altura de su capa, en vez de intentar volver una y otra vez al sitio en el que ya está.

## Gólem de carbón (fundidor)

Calabaza sobre un **bloque de carbón**. No genera cofre: el bloque se consume. Se activa con una **margarita** y trabaja en
un radio de 16 bloques alrededor del punto donde se la des.

- Coge **cualquier objeto** de los cofres de **hierro, diamante, netherita y hueso**. Lleva 9 huecos para los
  hornos, 9 de carbón y 9 para lo que va al cofre de cobre.
- Lo **fundible** lo lleva a un horno: **minerales** (menas y metal en bruto), **carne y pescado crudos** y **patatas**.
  El carbón y el carbón vegetal los usa de combustible.
- **Todo lo demás** lo lleva directamente al **cofre de cobre** más cercano (tienes que colocarlo tú).
- **Nunca coge lo que necesita el dueño del cofre:**
  - los **picos** y las **palas** de los cofres de minero y las **espadas** de los cofres de hueso;
  - en cofres de minero, **128 bloques** de los que el minero usa para construir, lo que llena sus 2 huecos de reserva (se
    lleva solo lo que pase de ahí);
  - en cofres de hueso, la **comida que come algún animal del recinto** de ese gólem de hueso, salvo las patatas.
- Atiende hasta **5 hornos**, los más cercanos, contando juntos hornos normales, altos hornos y ahumadores.
  - Los minerales van a **altos hornos** y la comida a **ahumadores** si tiene alguno asignado; si no, a hornos normales.
- Pone la carga arriba y el carbón abajo. Reparte la carga a partes iguales entre los hornos que la admiten, y el carbón
  también cuando no hay suficiente para todos.
- Lleva lo que sale de los hornos al cofre de cobre.
- Solo recoge algo si tiene dónde dejarlo: un horno del tipo adecuado libre, o sitio en un cofre de cobre.

## Gólem de cobre (el de siempre): marcos, vagonetas y Eficiencia

El gólem de cobre de Minecraft reparte lo que hay en los cofres de cobre. Con este mod puedes decirle dónde va cada cosa:

- Si un cofre tiene **uno o más marcos de objeto pegados con un objeto dentro**, el gólem de cobre solo deposita en él los
  objetos que coinciden con alguno de los marcos. Si lo que lleva no coincide, **ni se acerca ni lo abre**.
- En un cofre con marcos mandan los marcos, no la regla de siempre: lo que coincide entra **aunque el cofre ya tenga
  otras cosas dentro**, mientras quepa.
- Un cofre con un **marco vacío** recibe **cualquier objeto**, aunque ya tenga otras cosas dentro, pero solo lo que
  ningún otro cofre o vagoneta al alcance del gólem muestre en un marco: eso va al que lo muestra. Sirve de cofre para
  "todo lo demás". (Un cofre al que el gólem ya sabe que no puede llegar no cuenta.)
- Los cofres **sin marcos** se comportan como siempre: solo reciben un objeto si están vacíos o ya tienen ese objeto.
- En un cofre doble vale un marco en cualquiera de las dos mitades. Coincide el tipo de objeto (un pico gastado vale para
  un marco con un pico nuevo).

### Vagonetas

El gólem de cobre también **carga vagonetas** (con cofre o con tolva) y las **envía** cuando están llenas:

- Una vagoneta cuenta como **estación** si está parada sobre una vía con un **botón justo encima de esa vía** (por ejemplo,
  en la pared de al lado o en el techo). Las vagonetas sin botón encima no las toca.
- Si lleva algo y hay una estación que lo acepta **más cerca que el cofre** al que lo llevaría (o no hay cofre), va a la
  vagoneta y lo mete como en un cofre.
- Cuando la vagoneta **se queda sin huecos libres**, o lo que lleva no cabe, **pulsa el botón** y la vagoneta sale. Monta la
  estación para que el botón la ponga en marcha, por ejemplo con un raíl propulsor bajo el botón y un bloque detrás.
- Un **marco al lado del botón** (a un lado o encima) hace de filtro, igual que en un cofre: la vagoneta solo recibe lo que
  coincide con él, y con un **marco vacío** recibe lo que no muestre ningún otro cofre o vagoneta. Sin marco sigue la
  regla de siempre: solo recibe un objeto si está vacía o ya lo tiene.

### Eficiencia

Dale al gólem de cobre un **libro encantado de Eficiencia** con clic derecho. El libro se gasta y el gólem sube de nivel.
Los niveles van **en orden estricto**: sin nivel solo acepta Eficiencia I, con I solo II, y así hasta V. Cualquier otro
libro de Eficiencia lo rechaza sin gastarlo y te dice en la barra de acción qué nivel necesita.

| Nivel | Efecto (acumulativo) |
|---|---|
| I | Lleva hasta **32** objetos por viaje (16 sin encantar) |
| II | Hasta **48** |
| III | Hasta **64**, una pila entera. Nunca más de lo que admite la pila del objeto (16 en perlas de ender, huevos, carteles...) |
| IV | Las esperas al abrir un cofre, coger y dejar duran **la mitad** |
| V | Esas esperas se reducen **al mínimo** y anda un **20 % más rápido** |

- Con cualquier nivel, la **antena brilla** como un objeto encantado, y en el centro de la **espalda** tiene **4 puntos de
  cobre** que se van encendiendo, con poco brillo, uno por nivel: de arriba a la izquierda hasta abajo a la derecha. Con
  Eficiencia IV están los 4 encendidos con brillo bajo, y con V los 4 brillan más.
- El nivel se guarda con el gólem. Si se oxida del todo y se convierte en **estatua**, la estatua lo conserva y el gólem lo
  recupera al revivirlo con un hacha. (Si picas la estatua y la recoges como objeto, el nivel se pierde.)
- Al **morir** suelta un libro de Eficiencia de su nivel.

## Gólem de amatista (leñador)

Calabaza sobre un **bloque de amatista**; deja un cofre de amatista. Se activa con un **lirio de los valles**.

- Necesita un **hacha** en su cofre (ver Herramientas). Se gasta con cada tronco.
- De base tala solo **el chunk donde se activa**. Dale un **catalejo** con clic derecho para subirlo de nivel: el catalejo
  se gasta, su zona pasa a una malla de **3x3 chunks** que recorre en espiral desde el centro, y **sus ojos cambian de
  color** (de lila a dorado). Al morir suelta el catalejo.
- Tira **todos los troncos** del árbol, ramas incluidas. Las hojas las deja caer solas.
- Llega tan lejos como un jugador. Para los troncos más altos **se hace un pilar** al lado del tronco con **bloques de su
  cofre** (de los mismos que usan los mineros para construir: bloques normales que se pican con pico, como la piedra o
  el pedrusco): salta, pone un bloque debajo y repite. Si hay hojas encima, las rompe. Al terminar baja **quitando el
  pilar** y se queda los bloques. Lleva hasta 16, que coge de su cofre cada vez que pasa por él; si al llegar a un árbol
  no lleva ninguno, va una vez a por ellos. Sin bloques, o con algo sólido encima, deja en el árbol los troncos a los que
  no llega.
- Al terminar un árbol **planta un brote** de su especie donde estaba (cuatro en los árboles de tronco 2x2), si tiene.
- Lleva 10 huecos de madera y una reserva de hasta 64 brotes de cada tipo. Del suelo **solo recoge brotes**, y se acerca
  a por los que ve a menos de 10 bloques.
- Lleva la madera a su cofre cuando se llena o cuando se queda sin árboles.
- Solo considera árbol un tronco sobre tierra que tenga hojas naturales, así que no toca construcciones de troncos.
- **Se queda parado junto al cofre, con los ojos apagados**, si el cofre está lleno o si ya no quedan árboles en la zona.
  Cada 30 segundos vuelve a mirar si ha crecido alguno.
- No se teletransporta (ver *Detalles*): un árbol o un brote al que no llega andando lo deja estar.

## Gólems de granja: el recinto vallado

Los gólems de heno y de hueso trabajan en el **recinto vallado en el que están** cuando reciben la flor: todo lo que se
puede recorrer a pie sin cruzar una valla, un muro, una puerta de valla o un bloque alto. Si el recinto no está cerrado
(o mide más de unos 48 bloques hacia algún lado) no hacen nada. Nunca salen de él: usan los cofres desde dentro, así que
los cofres pueden estar en la propia línea de la valla o pegados a ella.

## Gólem de heno (agricultor)

Calabaza sobre un **bloque de heno**; deja un cofre de heno. Se activa con un **aciano**. En este orden:

1. Si no tiene **azada**, coge una de su cofre de heno. Sin azada no hace nada (ver Herramientas).
2. **Ara** toda la tierra y la hierba del recinto.
3. **Siembra** la tierra arada vacía. Si no lleva semillas las coge de su cofre, y si tampoco hay, las saca cosechando.
4. **Cosecha** los cultivos maduros y **replanta** el mismo cultivo en el mismo sitio.
5. Lleva la cosecha al **cofre de hueso** más cercano. Se queda hasta 64 semillas de cada tipo para replantar.

Sirve cualquier cultivo que se plante en tierra arada: trigo, zanahorias, patatas, remolacha... La azada **se gasta al
arar y al cosechar**, un punto por casilla arada y otro por cada cultivo recogido, así que hay que reponerla.

## Gólem de hueso (ganadero)

Calabaza sobre un **bloque de hueso**; deja un cofre de hueso. Se activa con un **tulipán blanco**.

- **Sin espada no sacrifica**, pero sigue alimentando y recogiendo. En cuanto hay una espada en su cofre de hueso la coge
  (ver Herramientas), y la gasta un punto por cada animal que sacrifica. Saqueo en la espada da más botín.
- **Aspecto ígneo no hace nada**: la carne sale siempre cruda, y la cocina el gólem de carbón.
- **Alimenta** a los animales adultos del recinto con la comida de su especie, que coge de su cofre, para que críen.
  Solo coge comida que coma algún animal del recinto que tenga pareja.
- **Solo da de comer a un animal si tiene con quién criar**: otro de su especie que ya esté enamorado, o que también esté
  listo para criar y para el que lleve comida (le hacen falta 2 de comida para una pareja). Así no gasta comida en un
  animal que se queda sin cría.
- Con **20 animales o menos nunca mata**. Con más, mata adultos hasta volver a 20: primero el que **más le falta para
  poder volver a criar**, y nunca uno enamorado si hay otro. Las crías cuentan para el total pero
  nunca se matan, y tampoco las mascotas ni los animales con nombre. Entre un sacrificio y el siguiente espera 10
  segundos, para dar tiempo a que el rebaño críe. Los animales que acaban encima de la valla también cuentan.
- **Recoge lo que hay en el suelo** del recinto y lo deja en su cofre.
- El cofre de hueso es el punto de encuentro de la granja: el gólem de heno deja en él el trigo, el de hueso lo usa para
  alimentar, y el de carbón se lleva de él la carne cruda para cocinarla y todo lo que no sea comida de tus animales.

### Detalles

- Es inmune al fuego y no se ahoga.
- Solo se mueve en chunks cargados, como cualquier otra criatura.
- **Solo los mineros se teletransportan.** Si un minero lleva diez segundos sin poder moverse hacia donde va, se
  teletransporta a su destino, siempre que esté a **16 bloques o menos**. Más lejos se queda donde está y lo sigue
  intentando a pie.
- Los gólems de **carbón, amatista, heno y hueso**, igual que el de cobre, **nunca se teletransportan**. Si no encuentran
  camino a pie hasta un cofre, un horno, un cultivo, un animal, un árbol o un brote (o llevan diez segundos atascados
  sin acercarse), **lo dejan estar durante un minuto** y pasan a otra cosa. Si lo que no alcanzan es su propio cofre, esperan
  donde están con los ojos apagados y lo vuelven a intentar pasado ese minuto. Al darles otra vez su flor lo intentan
  todo de nuevo enseguida.
- Un minero nunca se teletransporta por debajo de la altura a la que trabaja (su capa, sus túneles o, bajando la
  escalera, el peldaño al que va), ni a una casilla con agua o lava, ni a una sin suelo firme. Si no hay ningún sitio
  así, no se teletransporta.
- Si un minero pasa **20 segundos trabajando sin romper ningún bloque ni llegar a una casilla nueva** (dar vueltas por
  las mismas casillas, o subir y bajar un peldaño, no cuenta), deja de insistir: en una cantera se salta el sitio al que
  iba y sigue con la capa; si estaba volviendo a un sitio al que no consigue llegar, va a la casilla de su capa más
  cercana en la que se pueda estar; si no, vuelve al cofre y empieza de nuevo desde ahí. Si le pasa 3 veces seguidas sin
  romper un solo bloque entre medias, lo deja y espera en el cofre a que le des otra flor.
- Los cofres también se fabrican: un cofre rodeado de 8 lingotes de hierro, diamantes, lingotes de netherita, fragmentos
  de amatista, trigo o huesos. Se unen en cofres dobles.
- Un gólem invocado con `/summon` adopta el cofre de gólem más cercano (8 bloques) al recibir la flor.
- La forma de gólem de hierro de siempre (la T de cuatro bloques) sigue creando el gólem de hierro normal.

## Instalación

Copia `build/libs/totalgolems-1.4.1.jar` a la carpeta `mods` junto con [Fabric API](https://modrinth.com/mod/fabric-api) para 26.3.

## Compilar

Hace falta un JDK 25.

```bash
./gradlew build
```

- `./gradlew runGameTest` ejecuta las pruebas de comportamiento (`src/gametest`).
- `./gradlew runClientGameTest` abre el juego, monta una escena con los gólems y cofres y guarda una captura en
  `build/run/clientGameTest/screenshots`.
- `python tools/generate_textures.py` genera las texturas que falten (necesita Pillow). No toca las que ya existen,
  así que las editadas a mano están a salvo. No uses `--force`: pinta los gólems y los cofres con sus formas antiguas.
- `python art/make_bbmodels.py` crea los proyectos de Blockbench de `art/` para pintar las texturas sobre el modelo:
  uno por gólem (`golem_<material>.bbmodel`, con su forma), el del gólem de cobre y los de los cofres. La forma de cada
  gólem está en `art/golem_geometry.py` y, para el juego, en `GolemModelLayers.java`.
- `python art/make_effects.py` rehace las texturas animadas (brillos, grietas, el núcleo del gólem de carbón) a partir
  de las texturas de los gólems y de `art/textures/chest`. Hay que ejecutarlo después de editar cualquiera de ellas.
