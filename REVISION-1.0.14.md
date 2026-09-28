# lateralWheelie 1.0.14 — Revision 1

## Registro de ruta en segundo plano
- RideService sigue siendo un foreground service de ubicación y ahora declara `stopWithTask=false`.
- La grabación iniciada continúa aunque la Activity deje de estar en primer plano o se quite de recientes.
- START_STICKY conserva la recuperación de una ruta activa.

## Honda BTU como referencia opcional
- Nuevo receptor de conexión/desconexión Bluetooth ACL.
- Ajustes > AUTO-RUTA HONDA BTU permite seleccionar un dispositivo ya emparejado.
- Android 12+ solicita BLUETOOTH_CONNECT.
- Al conectar el BTU seleccionado intenta iniciar RideService si no existe una ruta activa.
- Una desconexión de una auto-ruta espera 15 s antes de detenerla para tolerar cortes breves.
- Una ruta iniciada manualmente no se detiene por una desconexión BTU.
- No se añade ACCESS_BACKGROUND_LOCATION: en versiones Android con restricciones severas,
  el sistema puede bloquear un arranque automático desde segundo plano; el error queda visible
  en Ajustes. Una ruta ya iniciada sí continúa con el foreground service.

## Inclinación media acumulada
- Nuevo registro persistente de media absoluta de inclinación.
- Solo usa conducción válida (mount guard + GPS + >= 8 km/h).
- Se muestrea a 1 Hz para no sesgar por frecuencia de sensor.
- Visible en Estadísticas y Ajustes.
- RESET INCLINACIÓN MEDIA reinicia solo ese acumulado; no borra rutas.
- Cada sesión nueva guarda también su `avg_lean` y `distance_m` (DB v6).

## Comparativa automática de rutas
- Se busca una ruta anterior del mismo conductor y moto con:
  inicio cercano, trazado GPS semejante y, al finalizar, longitud/final compatibles.
- Durante una ruta puede reconocerse una coincidencia parcial y comparar el tiempo
  hasta la posición actual con el tiempo de la ruta anterior en ese mismo tramo.
- Al finalizar usa comparación de trazado en ambos sentidos.
- Se muestra:
  * diferencia de tiempo,
  * inclinación media actual vs anterior y delta,
  * máximos izquierda/derecha actuales vs anteriores.
- La coincidencia muestra además la separación GPS media estimada para hacer visible
  la tolerancia del algoritmo.
