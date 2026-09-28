# Validación técnica v0.9.2

Validación realizada en este entorno:
- `versionName 0.9.2` / `versionCode 17` verificados.
- 10 recursos WebP de fondos presentes.
- AndroidManifest incluye INTERNET + permisos de ubicación/FGS existentes.
- XML parseable.
- Java: balance léxico de llaves/paréntesis/corchetes verificado en todos los `.java`.
- UTF-8 sin BOM verificado para fuentes/Gradle/XML/Markdown.
- ZIP final comprobado con `unzip -t`.

No se marca como validado aquí:
- `lintDebug`
- `assembleDebug`
- smoke test real en Android

El entorno no dispone del Android SDK/teléfono del usuario. `build-windows.ps1 -Install` realiza esas pruebas en Windows.
