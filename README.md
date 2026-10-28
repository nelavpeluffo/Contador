# Contador

App de Android para rutinas de ejercicio, con la pantalla dividida en dos contadores sincronizados:

- **Arriba – Tiempo total** de la rutina.
- **Abajo – Bloques** que se repiten solos, alternando dos duraciones: **Tiempo 1** (bloques 1, 3, 5…) y **Tiempo 2** (bloques 2, 4, 6…). Cada una se elige con − y +, que recorren 15 seg → 25 seg → 30 seg → 40 seg → 1 min → 2 min → … → 60 min.

## Botones

| Símbolo | Acción |
|---|---|
| ▶ / ❚❚ | Iniciar / pausar. Inicia o pausa **los dos** contadores. |
| ■ | Finalizar. Termina **los dos** y muestra el resumen. |
| ↺ | (solo abajo) Reinicia el bloque actual; el tiempo total sigue. |

Tocar los números grandes también inicia o pausa.

## Ejercicios (círculos 1 a 5)

Antes de empezar se elige la duración del bloque y cuántos ejercicios distintos tiene la rutina:

- **1 ejercicio:** cada bloque terminado cambia el fondo entre **turquesa** y **turquesa 15 % más oscuro**, para ver fácil cuándo cambiar la intensidad. Cada bloque cuenta como una ronda.
- **2 a 5 ejercicios:** cada bloque es el ejercicio siguiente, con su propio color (turquesa, turquesa oscuro, amarillo, rosa, violeta). La ronda se completa al terminar el último ejercicio.

Los textos, números y botones cambian de color para leerse bien sobre cada fondo.

## Al finalizar

La mitad de arriba muestra los dos cronómetros uno al lado del otro y debajo el detalle: tiempo total y rondas. Con ▶ arranca una rutina nueva.

## Historial (menú ☰)

El botón ☰ de arriba a la derecha abre un panel con todas las rutinas finalizadas, agrupadas por día (Hoy, Ayer, fecha) con horario de inicio y fin, tiempo total y rondas. Cada una se puede eliminar con el ícono de papelera.

## Avisos

Pitidos en los últimos 3 segundos de cada bloque; pitido largo y vibración al cambiar de bloque (doble vibración al completar una ronda). Usa el volumen multimedia. La pantalla no se apaga mientras la app está abierta.

## Descargar el APK

Desde el celular: **Releases → "Contador (última versión)" → `Contador.apk`**
(https://github.com/nelavpeluffo/Contador/releases/latest)

Requiere Android 8.0 o superior. Cada push a `main` recompila el APK con GitHub Actions. Todas las versiones usan la misma firma, así que se instalan encima sin desinstalar.
