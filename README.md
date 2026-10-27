# Contador

App de Android para rutinas de ejercicio, con la pantalla dividida en dos:

- **Arriba – Tiempo total:** cronómetro de la rutina completa.
- **Abajo – Intervalos:** cuenta regresiva de **15, 25 o 30 segundos** que se repite sola. En cada vuelta alterna entre **Ejercicio A** (verde) y **Ejercicio B** (amarillo) y suma una ronda.

Detalles:
- Pitidos en los últimos 3 segundos y pitido largo + vibración al cambiar de intervalo.
- Al iniciar los intervalos también arranca el tiempo total (si estaba parado).
- Tocar los números grandes también inicia/pausa.
- La pantalla no se apaga mientras la app está abierta.
- El sonido usa el volumen multimedia.

## Descargar el APK

Desde el celular: **Releases → "Contador (última versión)" → `Contador.apk`**
(https://github.com/nelavpeluffo/Contador/releases/latest)

Android va a pedir permiso para "instalar apps de origen desconocido" la primera vez. Requiere Android 8.0 o superior.

Cada push a `main` recompila el APK con GitHub Actions y actualiza ese release. Todas las versiones están firmadas con la misma clave, así que se instalan encima sin desinstalar.

## Compilar localmente

```
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.
