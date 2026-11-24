# Diagnóstico Móvil

App Android ligera que analiza el estado del teléfono, muestra estadísticas en
tiempo real con gráficas explicadas y permite explorar los archivos del
dispositivo. Sin anuncios, sin internet, sin envío de datos.

![icono](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

## Funciones

### Panel de estado
- **Salud general 0–100** calculada con batería, temperatura, almacenamiento,
  memoria y tiempo encendido. Se actualiza sola cada 5 s mientras la app está abierta.
- **Observaciones** concretas (batería baja, poca memoria, etc.).
- **Batería**: nivel, estado, salud, temperatura, voltaje, corriente y tecnología.
- **Almacenamiento** y **RAM**: barras con usado/libre.
- **Red**: conexión, IP local, operador y tipo de red.
- **Pantalla**: resolución, densidad, refresco y orientación.
- **Sistema**: Android, parche, tiempo encendido, CPU, kernel, sensores, root...
- **Recomendaciones** y botón para compartir el informe por texto.

### Estadísticas en vivo
Pantalla con muestreo **cada segundo** (últimos 60 s) y explicación bajo cada
gráfica:

- **Batería** y **temperatura**: líneas históricas.
- **Memoria RAM en uso**: evolución del porcento ocupado.
- **Tráfico de red**: KB/s de entrada y salida (dos series).
- **Almacenamiento**: donut de espacio ocupado y libre.

### Explorador de archivos
- Carpetas del almacenamiento interno y tarjeta SD, con tamaño y fecha.
- Navegación por ruta, atajos (Descargas, Cámara, Imágenes, Música) y
  opción de mostrar archivos ocultos.
- Apertura de archivos con otras apps (solo lectura).

## Requisitos e instalación

- Android 6.0 (API 23) o superior.
- Instala el APK desde el gestor de archivos o el navegador; permite
  "Instalar apps desconocidas" si Android lo pide.
- Si Play Protect avisa (app firmada manualmente), elige "Instalar de todos modos".
- Para el explorador, activa "Permitir acceso a todos los archivos" cuando la
  app lo solicite en los ajustes del sistema.

## Compilar

```bash
./build.sh
```

Genera `EstadoDelTelefono.apk` en la raíz. Usa `aapt`, `javac` (JDK 17),
`d8`, `zipalign` y `apksigner`. Variables opcionales: `ANDROID_SDK`,
`BUILD_TOOLS`, `ANDROID_JAR`.

En este entorno las herramientas están en `/tmp/opencode/sdk`; si se reinicia,
vuelve a descargar `build-tools_r34-linux.zip` y un `android.jar` compatible
con `aapt` de Debian (API 31 funciona), o instala `aapt apksigner zipalign` con apt.

## Estructura

```
app/src/main/AndroidManifest.xml
app/src/main/java/com/phonestatus/app/MainActivity.java          panel principal
app/src/main/java/com/phonestatus/app/StatsActivity.java         gráficas en vivo
app/src/main/java/com/phonestatus/app/FilesActivity.java         explorador
app/src/main/java/com/phonestatus/app/FileContentProvider.java   apertura de archivos
app/src/main/java/com/phonestatus/app/StatusReport.java          datos y puntuación
app/src/main/java/com/phonestatus/app/ChartView.java             gráfica de líneas
app/src/main/java/com/phonestatus/app/DonutView.java             gráfica donut
app/src/main/java/com/phonestatus/app/RingView.java              anillo de salud
app/src/main/java/com/phonestatus/app/BarView.java               barras
app/src/main/res/...                                             tema, textos, iconos
tools/make_icon.py                                              generador del icono
build.sh                                                        compilación y firma
```

## Privacidad

Solo se leen datos locales. Permisos:

- `ACCESS_NETWORK_STATE`: tipo de conexión.
- `READ_EXTERNAL_STORAGE` (Android ≤ 9): listar archivos.
- `MANAGE_EXTERNAL_STORAGE` (Android 11+): explorar carpetas. Se activa
  manualmente en ajustes y el explorador es de solo lectura.
