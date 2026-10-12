package com.usuario.finanzas.domain.model

enum class ClaseMovimiento(val tipo: String, val direccion: String, val etiqueta: String) {
    COMPRA("compra", "salida", "Compra"),
    PAGO("pago_servicio", "salida", "Pago de servicio"),
    ENVIO("transferencia", "salida", "Dinero enviado"),
    RETIRO("retiro", "salida", "Retiro de efectivo"),
    INGRESO("ingreso", "entrada", "Dinero recibido"),
    TRANSFERENCIA_RECIBIDA("transferencia", "entrada", "Transferencia recibida"),
    PROPIA_SALIDA("transferencia_propia", "salida", "A otra cuenta mía"),
    PROPIA_ENTRADA("transferencia_propia", "entrada", "Desde otra cuenta mía");

    val permiteServicio: Boolean get() = this == COMPRA || this == PAGO
    val esPropia: Boolean get() = tipo == "transferencia_propia"

    companion object {
        fun desde(tipo: String, direccion: String): ClaseMovimiento =
            entries.firstOrNull { it.tipo == tipo && it.direccion == direccion }
                ?: if (direccion == "entrada") INGRESO else COMPRA
    }
}
