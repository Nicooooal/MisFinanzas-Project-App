package com.usuario.finanzas.domain.model

/** Gasto total de un mes calendario (para sparkline y comparativa del Dashboard). */
data class TotalMensual(
    val anio: Int,
    /** Mes 1-12. */
    val mes: Int,
    val total: Double,
    val esMesActual: Boolean
) {
    /** Nombre abreviado del mes: "Ene", "Feb", ... */
    val etiqueta: String get() = MESES_ABREVIADOS[mes - 1]

    /** Nombre completo del mes en español. */
    fun nombreMes(): String = MESES_COMPLETOS[mes - 1]

    companion object {
        val MESES_ABREVIADOS = listOf(
            "Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
        )
        val MESES_COMPLETOS = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
    }
}
