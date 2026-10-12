package com.usuario.finanzas.domain.captura

data class LineaRecibo(val texto: String, val izquierda: Int, val arriba: Int, val derecha: Int, val abajo: Int)

/** ML Kit puede entregar primero la columna de etiquetas y después la de importes. */
object TextoGeometricoRecibo {
    fun ordenar(lineas: List<LineaRecibo>): String {
        val filas = mutableListOf<MutableList<LineaRecibo>>()
        lineas.filter { it.texto.isNotBlank() }.sortedBy { it.arriba }.forEach { linea ->
            val fila = filas.lastOrNull()
            val referencia = fila?.first()
            val altura = (linea.abajo - linea.arriba).coerceAtLeast(1)
            val mismaFila = referencia != null &&
                kotlin.math.abs((linea.arriba + linea.abajo) / 2.0 - (referencia.arriba + referencia.abajo) / 2.0) <=
                minOf(altura, (referencia.abajo - referencia.arriba).coerceAtLeast(1)) * 0.45 &&
                fila.orEmpty().none { linea.izquierda < it.derecha && linea.derecha > it.izquierda }
            if (mismaFila) fila!!.add(linea) else filas.add(mutableListOf(linea))
        }
        return filas.joinToString("\n") { fila -> fila.sortedBy { it.izquierda }.joinToString(" ") { it.texto } }
    }
}
