# ☠ Minecraft Extremo

Mod para **Minecraft 26.3 (Fabric)** inspirado en la serie *Minecraft Extremo* de Pereira7, Agustin51 y Auronplay:

- **Si un jugador muere, el mundo entero se reinicia.** Nadie es expulsado: todos aparecen directamente en un mundo nuevo, con el inventario, los logros, el Nether y el End también reiniciados.
- **La seed nunca se repite.** Cada mundo usa una seed aleatoria nueva que no se ha usado antes. Las seeds usadas se guardan.
- **Panel a la derecha** con título en degradado, el número de mundo, el tiempo que lleva vivo el mundo (en directo), el récord, las muertes de cada participante (con un punto verde si está conectado) y el total. Se mantiene entre reinicios.
- **El servidor no se apaga.** El mundo se regenera "en caliente", así que funciona en hostings donde no puedes reiniciar el servidor a mano.
- **Tus amigos no tienen que instalar nada.** El mod solo va en el servidor y el panel usa el marcador de Minecraft normal. Pueden entrar con Minecraft 26.3 sin mods.

## Qué pasa cuando alguien muere

1. **Suena el audio de muerte** a todos los jugadores, sale en pantalla **"☠ NOMBRE ☠ — ha muerto"** y se suma 1 a sus muertes en el panel.
2. A los 3 segundos (configurable) el servidor borra el mundo y crea uno nuevo con otra seed. Tarda un par de segundos.
3. Todos (vivos y muertos) **aparecen directamente en el spawn del mundo nuevo**, sin desconectarse, con el inventario, el ender chest, la XP, la cama y los logros a cero. Sale en pantalla **"MUNDO #N"**.
4. El audio sigue sonando durante el cambio de mundo y el panel se actualiza.

## Instalación en el servidor (hosting o tu PC)

1. Crea un servidor **Fabric** para Minecraft **26.3** (en casi todos los hostings basta con elegir "Fabric" y la versión 26.3).
2. Descarga estos dos archivos y ponlos en la carpeta `mods/` del servidor:
   - **Minecraft Extremo**: [`descargas/minecraft-extremo-1.0.0+26.3.jar`](descargas/minecraft-extremo-1.0.0+26.3.jar)
   - **Fabric API** para 26.3: <https://modrinth.com/mod/fabric-api/versions?g=26.3>
3. Arranca el servidor. Listo.

> **Whitelist:** en Minecraft 26.3 la whitelist viene **activada por defecto**. Añade a tus amigos escribiendo en la consola `whitelist add NombreDelAmigo`, o desactívala con `white-list=false` en `server.properties`.

> **Ojo con Aternos:** Aternos solo permite instalar mods de su propio catálogo y no deja subir archivos `.jar` propios. Para este mod necesitas un hosting que te deje subir archivos a la carpeta `mods/` (por FTP o desde su gestor de archivos), o bien un servidor en tu PC.

Para un servidor en tu propio PC, descarga el instalador de servidor desde <https://fabricmc.net/use/server/> (Minecraft 26.3), arráncalo una vez, acepta el `eula.txt` y pon los dos `.jar` en `mods/`.

El mod también funciona en un **mundo de un jugador** (o abierto a LAN), así que puedes probarlo en CurseForge sin servidor: pon el mod y Fabric API en la carpeta `mods` de tu perfil.

## Audio de muerte (paquete de recursos)

El audio que suena al morir va en un **paquete de recursos que el servidor envía solo**: al entrar por primera vez, Minecraft pregunta si quieres descargarlo y basta con darle a **"Sí"**. No hay que instalar nada a mano.

- El paquete está en [`descargas/extremo-recursos.zip`](descargas/extremo-recursos.zip) y el mod usa ese enlace de GitHub por defecto.
- Si alguien rechaza el paquete, juega igual pero no oirá el audio.
- Para cambiar el audio: sustituye `paquete-recursos/assets/extremo/sounds/muerte.ogg` (formato `.ogg`), ejecuta `./gradlew build copiarDescargas` y sube los cambios.
- Si alojas el paquete en otro sitio, cambia `urlPaqueteRecursos` en la configuración y pon su SHA-1 en `sha1PaqueteRecursos`.

## Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `/extremo` | todos | Muestra el mundo actual, el ranking de muertes y la configuración |
| `/extremo reiniciar` | OP | Fuerza un reinicio del mundo (con cuenta atrás) |
| `/extremo cancelar` | OP | Cancela la cuenta atrás de un reinicio |
| `/extremo desactivar` / `activar` | OP | Desactiva o activa el reinicio al morir (las muertes se siguen contando) |
| `/extremo cuentaatras <segundos>` | OP | Segundos entre la muerte y el cambio de mundo (de 0 a 300; 0 = instantáneo) |
| `/extremo muertes <jugador> <n>` | OP | Corrige las muertes de un jugador |
| `/extremo borrarestadisticas` | OP | Pone todas las muertes a 0 y el contador de mundos a #1 |

Para hacerte OP, escribe `op TuNombre` en la consola del servidor.

## Configuración

Se crea sola en `config/extremo/config.json`. En `sonidoMuerte` también puedes poner un sonido de Minecraft, por ejemplo `minecraft:entity.wither.spawn`.

```json
{
  "reinicioActivado": true,
  "segundosCuentaAtras": 3,
  "sonidoMuerte": "extremo:muerte",
  "volumenSonidoMuerte": 1.0,
  "enviarPaqueteRecursos": true,
  "urlPaqueteRecursos": "https://raw.githubusercontent.com/Rafaas18/modminecraft/main/descargas/extremo-recursos.zip",
  "sha1PaqueteRecursos": "",
  "paqueteRecursosObligatorio": false,
  "tituloPanel": "☠ MINECRAFT EXTREMO ☠",
  "mostrarNumeroMundo": true,
  "mostrarTotalMuertes": true,
  "mostrarTiempo": true,
  "mostrarRecord": true,
  "colorTitulo1": "#FF3B3B",
  "colorTitulo2": "#FFB020"
}
```

Las muertes, el número de mundo y las seeds usadas se guardan en `config/extremo/datos.json`, fuera de la carpeta del mundo, para que no se borren con los reinicios.

## Qué se reinicia y qué no

| Se reinicia | Se mantiene |
|---|---|
| Overworld, Nether y End (seed nueva) | Muertes de cada jugador y número de mundo |
| Inventario, vida, XP, efectos, ender chest, cama/spawn, recetas y logros de cada jugador | Reglas del juego (`/gamerule`), whitelist, OPs, baneos |
| Dragón del End, hora, clima y modo de juego (vuelve al del servidor) | `server.properties`, la configuración del mod y las estadísticas |

## Compilar el mod (solo si quieres modificarlo)

Necesitas Java 25.

```bash
./gradlew build
```

El `.jar` queda en `build/libs/`. Cada vez que se sube un cambio a GitHub, una GitHub Action también compila el mod (pestaña *Actions* → artefacto `minecraft-extremo`).
