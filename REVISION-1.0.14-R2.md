# lateralWheelie 1.0.14 — Revision 2

## Corrección de cabecera redundante
- Se fuerza `Window.FEATURE_NO_TITLE` antes de crear la Activity.
- Se mantiene `Theme.Material.NoActionBar` y `android:windowNoTitle=true`.
- El objetivo es impedir que Android/OEM dibuje una segunda barra superior con el texto `lateralWheelie`.
- La cabecera propia de lateralWheelie permanece intacta en la vista normal.
- En HUD/pantalla completa se recupera el espacio vertical de la barra redundante, especialmente en horizontal.
- No se modifica la lógica de sensores, máximos, ruta, GPS, Bluetooth ni telemetría.
