# Auditoría funcional — v0.9.2

## Implementado en esta revisión
- Fondos estáticos sin inclinación visual.
- 10 wallpapers predeterminados reales (`lw_wallpaper_01..10.webp`).
- Fondo personalizado persistente mediante URI de Android.
- Sin fondo.
- Opacidad, oscurecimiento, ajuste de imagen y brillo de arco.
- Tema claro/oscuro/sistema.
- Overlay configurable de GPS, km/h, altitud, distancia, tiempo de ruta y temperatura ambiente.
- Temperatura ambiente también se recoge desde `LiveSensorController` cuando el sensor existe.
- Mapa OSM real para rutas registradas, con línea coloreada por inclinación.
- Persistencia local de sesiones y muestras sin cambios de esquema.

## Parcial / pendiente
- English y Galego tienen selector persistente pero todavía no traducen toda la UI Java heredada.
- Temperatura de motor/escape no existe: Android solo puede mostrar temperatura ambiente si el teléfono ofrece ese sensor. No se inventan datos.
- El mapa base necesita conexión a Internet; el registro de GPS funciona sin mapa base.
- Siguen pendientes métricas avanzadas del roadmap (histograma, tiempo sobre umbrales, replay sincronizado, etc.).
