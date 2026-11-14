# 🪙 MisFinanzas (Android)

[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.20-blue.svg)](https://kotlinlang.org)
[![Compose Version](https://img.shields.io/badge/Jetpack%20Compose-2024.09.03-green.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-brightgreen.svg)](https://developer.android.com)
[![Security](https://img.shields.io/badge/Security-SQLCipher%20%7C%20PBKDF2-red.svg)](https://www.zetetic.net/sqlcipher/)

**MisFinanzas** es una aplicación móvil nativa para Android diseñada bajo un paradigma **Offline-First**, orientada a la privacidad total del usuario y el control exhaustivo de finanzas personales, presupuestos, metas de ahorro y conciliación de movimientos.

No requiere creación de cuentas en la nube ni contiene anuncios. La base de datos y las preferencias de seguridad están cifradas; los comprobantes se guardan en el almacenamiento privado de la app.

---

## ✨ Características Principales

* 🔒 **Privacidad Total & Cifrado:**
  * Base de datos local cifrada con **SQLCipher** (compatible con páginas de memoria de 16 KB).
  * Autenticación biométrica y PIN protegido mediante derivación de claves **PBKDF2**.
  * Respaldos cifrados portátiles (`.mfin`) protegidos con contraseña, con PBKDF2 y AES-GCM. La protección de las preferencias locales usa **Android Keystore** mediante `MasterKey` y `EncryptedSharedPreferences`.
* 🎙️ **Registro Multimodal Inteligente:**
  * **Dictado por voz local:** Transcripción en el dispositivo cuando hay un modelo compatible instalado. Si el usuario elige el servicio de voz del teléfono y acepta el aviso, ese proveedor puede procesar el audio en línea.
  * **Escaneo de recibos (OCR):** Detección automática de importes, fechas y comercios con **Google ML Kit Text Recognition** incluido en el APK.
  * **Importación por lotes:** Reconocimiento de entradas rápidas de texto y tablas tabuladas copiadas de Excel.
* 🏦 **Detección Financiera Asistida:**
  * Detección automática en segundo plano de movimientos bancarios vía SMS o notificaciones push (Nequi, Nu, etc.) en pesos colombianos (COP), con filtro anti-spam y deduplicación.
* 📊 **Presupuestos y Planificación:**
  * Metas de ahorro con aportes y retiros independientes.
  * Presupuestos por categorías con alertas de déficit.
  * Estadísticas visuales interactivas (semanal, mensual, trimestral, semestral).
* 📄 **Exportación y Reportes:**
  * Generación de reportes en PDF formal paginado y hojas de cálculo CSV (sanitizadas contra ataques de inyección de fórmulas).

---

## 🏛️ Arquitectura del Sistema

El proyecto sigue los principios de **Clean Architecture** y patrón **MVVM (Model-View-ViewModel)**.

Para una descripción detallada con diagramas del **C4 Model** (Contexto, Contenedor y Componentes), consulta la documentación técnica:
👉 **[Ver Documentación de Arquitectura C4](docs/ARCHITECTURE.md)**

```
app/
 ├── src/main/java/com/usuario/finanzas/
 │    ├── data/         # Room DB, SQLCipher, SharedPreferences cifradas, Repositorios
 │    ├── domain/       # Modelos de dominio, reglas financieras, parsers (texto, OCR, voz)
 │    ├── ui/           # Compose, ViewModels, temas, formateadores y comprobantes
 │    ├── services/     # OCR, recepción de SMS y notificaciones
 │    └── workers/      # WorkManager para recordatorios y tareas en background
 └── src/test/          # Pruebas unitarias de lógica y compatibilidad con Robolectric
```

---

## 🛠️ Tecnologías Utilizadas

* **Lenguaje:** Kotlin 2.0.20
* **UI:** Jetpack Compose con Material Design 3
* **Persistencia:** Room 2.6.1 + SQLCipher 4.6.1 (Zetetic)
* **Inyección de Dependencias:** Dagger Hilt 2.52
* **Procesamiento de Imágenes / OCR:** Google ML Kit Text Recognition 16.0.1
* **Asincronía & Flujos:** Kotlin Coroutines & StateFlow
* **Tareas en Segundo Plano:** AndroidX WorkManager
* **Gráficos:** Vico Charts + Canvas nativo de Compose
* **Testing:** JUnit 4 y Robolectric, incluida la interfaz Compose

---

## 🚀 Compilación y Ejecución

### Requisitos Previos
* **Android Studio compatible con Android Gradle Plugin 8.13.2**.
* **JDK 17** como mínimo para ejecutar Gradle; la app usa bytecode Java 17.
* Android SDK 34 y Build Tools 35.0.0 instalados.
* Gradle 8.13 se obtiene mediante el wrapper incluido. Consulta la [compatibilidad de AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes#compatibility).

### Clonar y Compilar

1. Clona el repositorio:
   ```bash
   git clone https://github.com/Nicooooal/MisFinanzas-Project-App.git
   cd MisFinanzas-Project-App
   ```

2. Compila el APK de depuración y ejecuta las pruebas unitarias desde terminal:
   * **En Windows (PowerShell):**
     ```powershell
     .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
     ```
   * **En macOS / Linux:**
     ```bash
     ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
     ```

3. El APK generado se ubicará en:
   `app/build/outputs/apk/debug/app-debug.apk`

## Verificación automatizada

GitHub Actions compila el APK de depuración, ejecuta las pruebas unitarias con Robolectric y Android Lint en Windows. Una ejecución correcta publica el artefacto `app-debug`; los resultados de pruebas y Lint están en `validation-reports`.

La suite actual contiene 101 pruebas en `app/src/test`. Las comprobaciones en dispositivo se describen en [VERIFICACION_EN_TELEFONO.md](tools/VERIFICACION_EN_TELEFONO.md). Para automatizar pruebas de cámara, micrófono o navegación en un emulador será necesario añadir pruebas en `app/src/androidTest` y preparar un AVD.

---

## 📄 Licencia y Contribución

Consulta el historial de cambios en [CHANGELOG.md](CHANGELOG.md).
Este proyecto es de código abierto y se distribuye bajo la **licencia Apache 2.0**. Consulta [LICENSE](LICENSE) para conocer los términos de uso, modificación y distribución.

Las contribuciones son bienvenidas. Antes de enviar cambios, consulta [CONTRIBUTING.md](CONTRIBUTING.md).
