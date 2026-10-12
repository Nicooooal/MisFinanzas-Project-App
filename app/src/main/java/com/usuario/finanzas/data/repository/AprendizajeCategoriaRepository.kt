package com.usuario.finanzas.data.repository

import com.usuario.finanzas.data.local.dao.AprendizajeCategoriaDao
import com.usuario.finanzas.data.local.entities.AprendizajeCategoria
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AprendizajeCategoriaRepository @Inject constructor(
    private val aprendizajeCategoriaDao: AprendizajeCategoriaDao
) {
    /** Busca una categoría aprendida para el comercio (lo normaliza internamente). */
    suspend fun obtenerPorComercio(nombreComercio: String): AprendizajeCategoria? {
        val clave = normalizar(nombreComercio)
        if (clave.isBlank()) return null
        return aprendizajeCategoriaDao.obtenerPorComercio(clave)
    }

    /** Registra (o refuerza) la asociación comercio → categoría elegida por el usuario. */
    suspend fun aprender(nombreComercio: String, categoriaId: Int) {
        val clave = normalizar(nombreComercio)
        if (clave.isBlank()) return
        aprendizajeCategoriaDao.guardarOActualizar(
            AprendizajeCategoria(
                nombreComercio = clave,
                categoriaId = categoriaId,
                vecesUsada = 1,
                ultimaActualizacion = System.currentTimeMillis()
            )
        )
    }

    companion object {
        /** Normaliza el nombre del comercio: minúsculas, sin espacios al borde ni repetidos. */
        fun normalizar(nombre: String): String =
            nombre.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
