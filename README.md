# lateralWheelie

**lateralWheelie** es una aplicación Android de telemetría para motocicleta centrada en la inclinación, el análisis de curvas y el registro de rutas. Utiliza los sensores del teléfono y el GPS para mostrar información de conducción en tiempo real y conservar el historial de cada sesión.

La aplicación funciona de forma independiente: no necesita una motocicleta Honda, RoadSync ni hardware propietario. La integración con **Honda BTU** es opcional y puede utilizarse como referencia Bluetooth para automatizar el inicio y la parada de una ruta.
<p align="center">
  <img
    src="<img width="939" height="2048" alt="lW" src="https://github.com/user-attachments/assets/fe747275-fd1d-42bd-a5cf-647fa9ad22d7" />
"
    alt="lateralWheelie"
    width="280">
</p>

## Funciones principales

### Inclinómetro en tiempo real

- Ángulo de inclinación de **-70° a +70°**, diferenciando izquierda, derecha y posición central.
- Arco dinámico con escala térmica según la magnitud de la inclinación.
- Máximos independientes a izquierda y derecha.
- Detección y retención visual del **pico de curva**.
- Historial reciente de picos separado por lado.
- Calibración de 0° para adaptar el teléfono a su posición real en la motocicleta.
- Corrección para las diferentes orientaciones físicas del dispositivo.
- Interfaz adaptada tanto a vertical como a horizontal.

### Protección frente a falsos máximos

lateralWheelie incorpora una protección específica para evitar que coger el teléfono, retirarlo del soporte o realizar movimientos bruscos genere máximos de inclinación falsos.

El sistema detecta situaciones de manipulación mediante la orientación, velocidad angular y aceleración del dispositivo. Durante ese intervalo bloquea temporalmente la actualización de máximos y vuelve a habilitarla cuando el teléfono recupera una posición estable próxima al centro.

Durante una ruta, la validación también tiene en cuenta el estado del GPS y el movimiento antes de aceptar máximos y eventos de curva.

## Telemetría

Además de la inclinación, lateralWheelie puede mostrar y registrar:

- velocidad angular de balanceo (**roll rate**);
- fuerza G lateral;
- fuerza G longitudinal;
- aceleración y frenada;
- máximos de aceleración y frenada;
- velocidad GPS;
- altitud;
- posición GPS;
- tiempo de ruta;
- distancia recorrida;
- temperatura ambiente cuando está disponible;
- medidor gráfico de fuerzas G.

## Grabación de rutas

Las rutas se almacenan como sesiones de telemetría con muestras GPS y datos asociados a la conducción.

Durante una sesión se pueden conservar posición, velocidad, altitud, inclinación, velocidad angular y fuerzas G. La grabación utiliza un **Foreground Service**, por lo que una ruta activa puede continuar registrándose cuando la interfaz de lateralWheelie deja de estar en primer plano.

Cada sesión mantiene información como duración, distancia, máximos de inclinación y estadísticas asociadas.

### Curvas destacadas

Las curvas cuyo pico supera **35°** pueden guardarse como eventos de la ruta. El evento conserva el lado, el pico alcanzado y la información disponible en ese momento, incluida la posición GPS.

Esto permite relacionar posteriormente una inclinación destacada con el punto concreto del recorrido donde se produjo.

## Mapa de ruta

El recorrido GPS puede visualizarse directamente desde lateralWheelie.

El visor ofrece:

- cartografía **satélite**;
- alternativa de mapa de **calle**;
- perspectiva inclinada;
- trazado GPS de la sesión;
- representación del recorrido según la inclinación;
- inicio y final diferenciados;
- actualización de una ruta activa sin reconstruir continuamente el mapa.

Las muestras y sesiones se conservan localmente aunque la cartografía online no esté disponible.

## Comparación de rutas

lateralWheelie puede buscar una sesión anterior cuyo recorrido GPS coincida suficientemente con la ruta actual y utilizarla como referencia.

La comparación puede mostrar:

- fecha de la ruta anterior;
- diferencia de tiempo;
- tiempo transcurrido actual y de referencia;
- inclinación media actual y anterior;
- diferencia entre ambas medias;
- máximos izquierda/derecha;
- separación GPS media utilizada para estimar la coincidencia.

Durante una ruta activa la comparación puede actualizarse a medida que aumenta el recorrido disponible.

## Conductores y motocicletas

La aplicación separa los perfiles de **conductor** y **motocicleta** para poder asociar las sesiones a la combinación utilizada.

El garaje permite gestionar estos perfiles y conservar los ajustes relacionados con el montaje del teléfono y la calibración.

La calibración puede mantenerse de forma independiente según la orientación física utilizada por el dispositivo.

## Estadísticas e historial

lateralWheelie conserva las sesiones en una base de datos local y permite consultar información histórica como:

- rutas registradas;
- máximos izquierda/derecha;
- fecha y hora de los máximos;
- duración y distancia;
- estadísticas por sesión;
- inclinación media;
- máximos y datos de telemetría asociados.

También mantiene un registro de **inclinación media acumulada** que puede reiniciarse de forma independiente sin eliminar las rutas guardadas.

Las sesiones pueden eliminarse individualmente y el borrado de telemetría se mantiene separado de los perfiles de conductor y motocicleta.

## Honda BTU opcional

La aplicación no depende de Honda BTU para funcionar.

Si se habilita **AUTO-RUTA · HONDA BTU**, lateralWheelie puede utilizar la conexión Bluetooth de un dispositivo Honda BTU emparejado como referencia para automatizar una sesión:

- al conectarse el BTU puede iniciarse una ruta;
- al desconectarse se programa la detención;
- una reconexión dentro del margen previsto cancela esa detención.

En Android 12 o posterior esta función necesita el permiso de conexión Bluetooth correspondiente.

## Personalización

La pantalla de conducción dispone de diferentes opciones para adaptarla al teléfono y al estilo visual preferido:

- 10 fondos integrados;
- opción **Sin fondo**;
- **Mi fondo** mediante el selector de documentos de Android;
- opacidad del fondo;
- oscurecimiento;
- Rellenar, Encajar o Centrar;
- ajuste de brillo del arco;
- datos superpuestos configurables;
- tema Claro, Oscuro o Según sistema;
- interfaz adaptativa vertical/horizontal;
- controles de pantalla completa/HUD.

Los fondos son únicamente visuales: no alteran la medición de los sensores ni los datos registrados.

## Privacidad y almacenamiento

Los perfiles, sesiones, muestras GPS, eventos y estadísticas se almacenan **localmente en el dispositivo**.

La aplicación utiliza permisos Android únicamente para las funciones que los requieren, como ubicación para registrar rutas, notificaciones para mantener visible el servicio de grabación y Bluetooth cuando se utiliza la integración opcional con Honda BTU.

La cartografía y algunos datos externos pueden requerir conexión a Internet, pero el historial de telemetría y las rutas se mantienen localmente.

## Requisitos

- Android **8.0 (API 26)** o posterior.
- Sensores de movimiento compatibles para el inclinómetro y la telemetría.
- GPS para el registro completo de rutas.
- Conexión a Internet para la cartografía online.
- Permiso Bluetooth en las versiones de Android que lo requieran si se utiliza Honda BTU.

## Compilar e instalar

El proyecto incluye un script de compilación para Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1 -Install
```

El flujo de compilación del proyecto utiliza Java 17 y el entorno Android/Gradle incluido o configurado para el proyecto. El script puede ejecutar las comprobaciones de compilación, Android Lint, generar la APK debug e instalarla mediante ADB cuando existe un dispositivo conectado.

## Estado del proyecto

La rama `main` contiene el desarrollo actual. Las versiones publicadas se conservan mediante tags y Releases.

Este README describe **lateralWheelie y sus funciones actuales**. El historial detallado de cada revisión se mantiene fuera de esta presentación principal para evitar que la documentación del proyecto quede ligada a una versión concreta.

## Uso durante la conducción

lateralWheelie registra y representa mediciones obtenidas del teléfono. Un determinado valor de inclinación no implica que ese ángulo sea seguro o apropiado para una situación concreta.

Configura y calibra la aplicación antes de iniciar la marcha y evita manipular el teléfono mientras conduces.
