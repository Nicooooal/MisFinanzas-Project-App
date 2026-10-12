# Verificación 1.12.0 — 2026-10-07

## Resultado final

Comando: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain --no-daemon`, con el JDK de Android Studio.

**BUILD SUCCESSFUL**. 101 pruebas en 20 suites: 0 fallos, 0 errores y 0 omitidas. APK de depuración compilado.

Android Lint: **0 errores**, 46 advertencias y 2 sugerencias. Permanecen avisos de versiones de dependencias/SDK, estilo KTX, icono monocromático, recursos redundantes y declaración adicional de respaldo para Android anterior a 12 (`allowBackup=false` ya está establecido). Los nuevos avisos incluyen el uso de ExifInterface del sistema y constantes numéricas de errores de voz incorporadas en API posteriores; el acceso al servicio local y la consulta de idiomas se comprueban por versión. No se ocultaron errores mediante una baseline.

## Cobertura

- 14 pruebas nuevas de recibos: totales en otra línea, columnas OCR, formatos numéricos, ambigüedad, descarte de subtotal/impuestos/cambio, fechas y datos fiscales.
- 12 pruebas nuevas de dictado con servicio simulado: idioma, permisos, errores, esperas, cancelación, resultados tardíos y liberación del motor.
- 3 pruebas nuevas de interfaz del dictado, cada una en tema claro/oscuro: preparación, escucha y error, textos y acciones. Se verifica que los controles sean accesibles y visibles en una ventana de 320 dp con fuente 1.3. Las capturas de Compose en Robolectric se guardan en `tools/previews`; no son fotografías del Motorola.
- Migración real Room v4 → v5: esquema e historial preservados.
- Importes exactos, límites, saldos, períodos, recurrencias y exclusión de traspasos propios.
- Presupuestos, metas, retiros y restricción del cambio de moneda con importes registrados.
- Captura bancaria, falsos positivos, entradas/envíos y deduplicación; aislamiento COP/otras monedas.
- Respaldo de 13 tablas, preferencias y adjuntos; contraseñas erróneas, corrupción, rutas/referencias inválidas, importes incoherentes y rollback.
- Protección frente a restauración concurrente, lectura actualizada del servicio y ciclo de pagos.
- PIN, migración del hash y bloqueo temporal; compatibilidad básica AppCompat/biometría y Compose claro/oscuro.
- Selección inicial de categorías, protección de Otros y conservación del historial.
- CSV: UTF-8, comillas, fechas, moneda, centavos exactos, archivos vacíos y neutralización de fórmulas.
- Traducciones ES/EN/PT/FR, cambio de idioma, fallback e interpolación sin modificar valores del usuario.

## Correcciones finales de compatibilidad

- Telefonía y micrófono declarados opcionales.
- SQLCipher actualizado del paquete antiguo al oficial `sqlcipher-android:4.6.1`, con soporte de alineación de 16 KB; se conserva la passphrase existente y se usa SupportOpenHelperFactory.
- Reglas explícitas de extracción Android 12+ y todos los idiomas incluidos en posibles bundles.
- Firma del APK verificada con apksigner (v2); alineación de empaquetado a 16 KB verificada con zipalign.
- APK: `com.usuario.finanzas`, versión 1.12.0, versionCode 14, mínimo API 26, target API 34. Incluye arm64-v8a, armeabi-v7a, x86 y x86_64. No incluye permiso INTERNET. Conserva el mismo certificado de firma que el APK 1.11.0 entregado.

## Límites reales

Robolectric utiliza API 28 y una base Room temporal sin SQLCipher. No se verificó en un teléfono/emulador: apertura de una base cifrada histórica con la nueva biblioteca nativa, recepción SMS/push real, cámara/OCR, servicio de voz local, biometría física, navegación completa y presentación visual de los cuatro idiomas. No se dispone de la foto del recibo que falló ni de acceso al Motorola G72 del usuario. Las pruebas del parser usan texto y posiciones sintéticos, y las de dictado un motor simulado; no certifican precisión OCR ni disponibilidad del modelo de voz en el dispositivo.

Los reportes, notificaciones y algunos errores técnicos conservan el español. El parser de lenguaje natural está orientado al español.

Ver `tools/VERIFICACION_EN_TELEFONO.md` para la comprobación pendiente en dispositivo.

Informes locales generados: `app/build/reports/tests/testDebugUnitTest/index.html` y `app/build/reports/lint-results-debug.html` (se excluyen del ZIP junto con las compilaciones y cachés).
