package com.usuario.finanzas.domain.model

/** Proyección de la query de gastos agrupados por categoría (no es una entidad). */
data class GastoPorCategoria(
    val categoriaId: Int,
    val nombre: String,
    val color: String,
    val icono: String,
    val total: Double
)
