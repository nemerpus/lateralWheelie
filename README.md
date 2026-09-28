# lateralWheelie 1.0.0

Revisión centrada en coherencia visual, máximos de ruta, telemetría, controles de fondo y estabilidad del sensor en reposo.

Revisión centrada en simplificar la pantalla de conducción y llevar el boceto de personalización a la APK.

## Cambios principales

- Fondo de conducción **estático**: ya no rota ni simula la inclinación. El arco y el valor numérico son la única referencia física.
- 10 fondos predeterminados incluidos, con el motorista pequeño, alejado y visualmente centrado.
- Selector **Sin fondo** para una interfaz limpia/neón.
- Selector **Mi fondo** mediante el selector seguro de documentos de Android (`ACTION_OPEN_DOCUMENT`), sin pedir acceso completo al almacenamiento.
- Ajustes de fondo: opacidad, oscurecimiento, Rellenar/Encajar/Centrar.
- Ajuste de brillo del arco y activación/desactivación de datos superpuestos.
- Tema Claro / Oscuro / Según sistema aplicado a la estructura principal de la interfaz.
- Selector de idioma persistente (la interfaz completa actual sigue siendo español; English/Galego quedan preparados pero no se consideran traducción completa todavía).
- Telemetría ampliada: GPS, temperatura ambiente cuando el teléfono dispone del sensor, altitud, velocidad, tiempo de ruta y distancia.
- Ruta: nuevo mapa OpenStreetMap/Leaflet con el trazado GPS real coloreado por inclinación. La cartografía necesita Internet; las muestras GPS y la sesión se siguen guardando localmente.
- Máximos de ruta: la pantalla principal solo acumula máximos cuando existe una ruta activa y sigue sincronizada con `RideService`/`RideState`.
- Se mantiene la corrección independiente vertical/horizontal de `SensorMath`.

## Compilar e instalar

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1 -Install
```

El script ejecuta clean, Android Lint, compilación debug, instalación ADB y smoke test de arranque.

## Pruebas manuales recomendadas

1. Vertical: izquierda/derecha deben coincidir físicamente con el arco.
2. Horizontal 90/270: repetir la prueba de sentido.
3. Iniciar Ruta y comprobar que `MÁX. RUTA` registra ambos lados.
4. Ruta: caminar/conducir con GPS y comprobar que aparece el trazado sobre mapa.
5. Ajustes > Fondo: probar los 10 predeterminados, Mi fondo y Sin fondo.
6. Cambiar tema Claro/Oscuro/Sistema.
7. Confirmar GPS, altitud, velocidad, distancia, tiempo y temperatura (si el dispositivo posee `TYPE_AMBIENT_TEMPERATURE`).