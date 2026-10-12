# Changelog

Todas las modificaciones notables de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y este proyecto se adhiere a [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.12.0] - 2026-10

### Añadido
- **Diseño visual renovado:** paleta con acento verde de alto contraste, saldo destacado y tarjetas adaptativas al tamaño de fuente.
- **Ventana modal de dictado por voz:** interfaz con visualización de nivel de audio, captura parcial en tiempo real, temporizador y cancelación limpia.
- **Motor OCR mejorado:** soporte para orientación automática de fotos, separación de subtotal, impuestos y cambio, y selección explícita de importes ambiguos.
- **Documentación de arquitectura C4:** diagramas de Contexto, Contenedores y Componentes en `docs/ARCHITECTURE.md`.
- **Integración de CI:** workflow automatizado en GitHub Actions para pruebas unitarias e instrumentadas.
- **Soporte para Android Keystore:** helper criptográfico `KeystoreHelper` para generación y custodia de llaves AES-256 en hardware.

### Cambiado
- Migración oficial de SQLCipher a `net.zetetic:sqlcipher-android:4.6.1` para compatibilidad nativa con arquitecturas de páginas de memoria de 16 KB.
- Esquema de base de datos Room migrado de versión v4 a v5 con importes monetarios representados en centavos enteros para evitar errores de coma flotante.
- Fortalecimiento de seguridad del PIN usando PBKDF2 con sal aleatoria dinámica.

### Corregido
- Filtrado y deduplicación de notificaciones SMS y push bancarias (Nequi, Nu).
- Prevención de fugas de memoria y liberación del micrófono al abandonar la vista de dictado.

---

## [1.11.0] - 2026-09

### Añadido
- Billetera y planes: presupuestos por categoría, metas con aportes/retiros y movimientos recurrentes.
- Exportación de reportes a formatos PDF paginado y CSV codificado en UTF-8 con sanitización de inyecciones de fórmulas.
- Soporte para 4 idiomas en interfaz (Español, Inglés, Portugués, Francés).

---

## [1.10.0] - 2026-09

### Añadido
- Base de datos Room cifrada de extremo a extremo con SQLCipher.
- Detección automática en segundo plano de SMS financieros en Colombia (COP).
- Pantalla de bienvenida / onboarding para configuración de moneda base y categorías iniciales.
- Respaldo portátil en formato cifrado `.mfin`.
