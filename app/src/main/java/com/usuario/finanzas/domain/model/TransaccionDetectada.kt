package com.usuario.finanzas.domain.model

/**
 * Resultado del parseo de una notificación o SMS bancario.
 * Aún NO está guardada: el usuario debe confirmarla en PantallaConfirmacionTransaccion.
 */
data class TransaccionDetectada(
    val monto: Double,
    val comercio: String,
    /** Timestamp epoch ms (normalmente el momento de la detección) */
    val fecha: Long = System.currentTimeMillis(),
    /** "compra", "pago_servicio", "transferencia", "retiro" o "ingreso" */
    val tipo: String,
    /** Banco que originó la notificación/SMS */
    val banco: String,
    /** Texto original, útil para depurar y ajustar los regex */
    val textoOriginal: String,
    val direccion: String = if (tipo == TIPO_INGRESO) DIRECCION_ENTRADA else DIRECCION_SALIDA,
    val claveOrigen: String? = null,
    val canal: String = ""
) {
    companion object {
        const val TIPO_INGRESO = "ingreso"
        const val DIRECCION_ENTRADA = "entrada"
        const val DIRECCION_SALIDA = "salida"
        const val TIPO_COMPRA = "compra"
        const val TIPO_PAGO_SERVICIO = "pago_servicio"
        const val TIPO_TRANSFERENCIA = "transferencia"
        const val TIPO_RETIRO = "retiro"
    }
}



