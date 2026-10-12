# Arquitectura de MisFinanzas

Este documento describe la arquitectura técnica de la aplicación móvil **MisFinanzas** utilizando el estándar **C4 Model** (Contexto, Contenedores y Componentes).

---

## 1. Nivel 1: Contexto de Sistema (System Context)

Muestra a qué usuarios sirve la aplicación y cómo interactúa con el ecosistema y hardware del dispositivo Android.

```mermaid
flowchart TD
    User["👤 Usuario\n(Gestión de finanzas personales)"]
    App["📱 MisFinanzas App\n(Kotlin / Jetpack Compose)"]
    AndroidOS["🤖 Sistema Android\n(Cámara, Micrófono, SMS / Notificaciones)"]
    Keystore["🔐 Android Keystore\n(Clave maestra de preferencias locales)"]

    User -->|"Registra movimientos, escanea recibos,\nconfigura presupuestos y genera reportes"| App
    App -->|"Captura audio para dictado,\nprocesa comprobantes y lee SMS bancarios opcionales"| AndroidOS
    App -->|"Resguarda claves criptográficas"| Keystore
```

---

## 2. Nivel 2: Contenedor (Container Diagram)

Describe los subsistemas y capas que componen el artefacto de la aplicación y su almacenamiento local seguro (*Offline-First*).

```mermaid
flowchart TB
    subgraph AppContainer["📱 Aplicación MisFinanzas (APK)"]
        UI["🎨 Capa de Presentación\n(Compose, ViewModels y StateFlow)"]
        Domain["⚙️ Capa de Dominio / Lógica de Negocio\n(Parsers, cálculos financieros y modelos)"]
        Data["💾 Capa de Datos\n(Room Repositories, Migraciones, Workers en Background)"]
    end

    subgraph LocalStorage["🔒 Almacenamiento Local (Dispositivo)"]
        SQLCipher[("🗄️ Base de Datos Room\n(Cifrada con SQLCipher)")]
        EncryptedPrefs[("🔑 EncryptedSharedPreferences\n(Ajustes, PIN con PBKDF2)")]
        FileStorage[("📄 Archivos Locales Seguros\n(Comprobantes de fotos, Respaldos .mfin)")]
    end

    UI -->|"Eventos de usuario / Observa StateFlow"| Domain
    Domain -->|"Casos de uso / Invocación de repositorios"| Data
    Data -->|"Lectura y persistencia cifrada"| SQLCipher
    Data -->|"Gestión de configuración y claves"| EncryptedPrefs
    Data -->|"Almacenamiento privado"| FileStorage
```

---

## 3. Nivel 3: Componentes (Component Diagram)

Detalla la interacción entre los módulos de captura (Voz, OCR, Manual), lógica financiera y seguridad.

```mermaid
flowchart LR
    subgraph UI_Layer["Presentación"]
        CapturaScreen["PantallaCaptura"]
        CapturaVM["CapturaViewModel"]
        VoiceEngine["ControladorDictado"]
        BackupVM["AjustesViewModel"]
    end

    subgraph Domain_Services["Interpretación y captura"]
        OCRService["LectorRecibo\n(ML Kit local)"]
        Parser["ParserEntrada / ParserRecibo"]
    end

    subgraph Security_Data["Datos y seguridad"]
        Repo["TransaccionRepository"]
        BackupRepo["RespaldoRepository"]
        Archivo["ArchivoRespaldo\n(Datos, preferencias y adjuntos)"]
        BackupCodec["CodecRespaldo\n(PBKDF2 + AES-GCM con contraseña)"]
        Almacen["AlmacenSeguro\n(MasterKey + EncryptedSharedPreferences)"]
        RoomDB[("Room / SQLCipher")]
        Keystore["Android Keystore"]
    end

    CapturaScreen --> CapturaVM
    CapturaScreen --> VoiceEngine
    VoiceEngine -->|"Texto reconocido"| CapturaVM
    CapturaVM --> OCRService
    CapturaVM --> Parser
    CapturaVM --> Repo
    Repo --> RoomDB
    BackupVM --> BackupRepo
    BackupRepo --> Archivo
    BackupRepo --> BackupCodec
    BackupRepo --> RoomDB
    Almacen --> Keystore
```

---

## Principios Clave de Diseño

1. **Privacidad y Cero Rastreo (Offline-First):**
   * La aplicación no requiere cuenta en la nube ni conexión a internet para su lógica central.
   * La base de datos SQLite está totalmente cifrada mediante **SQLCipher**.
2. **Entrada Asistida Multimodal:**
   * Soporta registro manual rápido, dictado por voz local en el dispositivo y escaneo óptico (OCR) de facturas y comprobantes.
   * El servicio de voz del teléfono es una alternativa elegida explícitamente por el usuario tras un aviso de posible procesamiento del audio en línea.
3. **Manejo Seguro de Identidad y Respaldo:**
   * Bloqueo por PIN con derivación de llave PBKDF2 y soporte biométrico.
   * Los respaldos portátiles `.mfin` se cifran con contraseña: `CodecRespaldo` deriva una llave con PBKDF2 y usa AES-GCM.
   * `AlmacenSeguro` protege las preferencias de seguridad y la passphrase local con `MasterKey` y `EncryptedSharedPreferences`. La disponibilidad de protección por hardware depende del dispositivo.
