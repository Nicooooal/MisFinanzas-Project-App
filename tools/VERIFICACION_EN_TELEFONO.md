# Comprobación en teléfono — 1.12.0

Estas comprobaciones complementan las pruebas automáticas; no se han ejecutado en un teléfono conectado durante esta entrega.

## Prioridad: Motorola G72 con Android 14

- **Dictado:** permitir el micrófono, decir «Almuerzo 25 mil» y comprobar preparación, aviso «Micrófono activo», nivel de voz y borrador editable. Probar silencio, cancelar, terminar y enviar la app a segundo plano: debe cerrar la sesión. Si falta un modelo local, comprobar el mensaje específico y la alternativa «Usar servicio de voz del teléfono», que requiere aceptar el aviso de procesamiento del proveedor.
- **Recibos:** probar una foto real enfocada, una girada y una imagen de galería. Comparar comercio, fecha y total con el original; subtotal, impuestos, efectivo y cambio no deben sustituir el total. Ante totales distintos, elegir el correcto. Corregir el texto reconocido y reinterpretar. Cancelar durante la lectura y probar una imagen sin texto: no debe reemplazar los datos del recibo anterior por datos de otra foto.
- **Diseño:** revisar Inicio, Billetera, Estadísticas y Captura con tema claro/oscuro y letra grande. Comprobar los accesos Texto/Voz/Recibo, desplazamiento del diálogo de dictado y ocultación de importes en modo privado.

## Comprobaciones generales

1. **Inicio y bloqueo:** crear PIN, cerrar/abrir, comprobar un PIN incorrecto y activar/desactivar biometría. Volver tras un minuto fuera debe pedir desbloqueo.
2. **Configuración:** alternar gastos/ingresos, crear categoría, desmarcar otra y continuar. La desmarcada queda archivada; el historial sigue disponible. Repetir omitiendo la configuración.
3. **Saldo:** en una instalación de prueba, saldo inicial 100; ingreso 50; salida 20; traspaso propio 10. El saldo registrado debe ser 130 y los totales 50/20. Revisar un mes anterior a la fecha inicial.
4. **Entrada:** interpretar `Almuerzo 25 mil + Uber 12k`, corregir categoría, marcar revisión y guardar. Pegar una tabla de Excel con encabezados. Probar dictado con modelo local instalado y una foto legible de recibo; comprobar los importes antes de confirmar.
5. **Detección:** con notificaciones/SMS autorizados, comprobar muestras reales anonimizadas de Nu/Nequi y promociones. Una promoción no debe convertirse en un pago confirmado. Un movimiento real debe aparecer como candidato en Pendientes.
6. **Planes:** crear presupuesto, superar su límite, registrar aporte y retiro de una meta. Los aportes no deben aumentar el gasto. Vincular un recurrente a un movimiento existente no debe duplicarlo.
7. **Reportes:** elegir un rango vacío (botón desactivado), después uno con movimientos. Abrir PDF en un lector y CSV en una hoja de cálculo; revisar acentos, importes y fechas.
8. **Respaldo:** exportar una copia, restaurarla en una instalación de prueba y revisar comprobantes, planes y preferencias. Una contraseña incorrecta debe conservar los datos actuales.
9. **Presentación:** recorrer Inicio/Billetera/Estadísticas/Ajustes en claro/oscuro, ES/EN/PT/FR y con letra grande. Probar rotación, selector de archivos, cámara y volver desde otra aplicación.

La instalación sobre una versión anterior requiere la misma firma del APK. Ante una firma diferente, conservar primero una copia de los datos; no desinstalar como paso de actualización rutinario.
