package com.usuario.finanzas.data.repository

import com.usuario.finanzas.data.local.dao.CategoriaDao
import com.usuario.finanzas.data.local.entities.Categoria
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoriaRepository @Inject constructor(
    private val categoriaDao: CategoriaDao
) {
    fun observarTodas(): Flow<List<Categoria>> = categoriaDao.observarTodas()

    suspend fun obtenerTodas(): List<Categoria> = categoriaDao.obtenerTodas()

    suspend fun obtenerPorId(id: Int): Categoria? = categoriaDao.obtenerPorId(id)

    fun observarActivas(direccion: String): Flow<List<Categoria>> = categoriaDao.observarActivas(direccion)
    suspend fun obtenerActivas(direccion: String): List<Categoria> = categoriaDao.obtenerActivas(direccion)

    suspend fun guardar(categoria: Categoria) {
        val nombre = categoria.nombre.trim()
        require(nombre.isNotBlank() && nombre.length <= 50) { "Escribe un nombre de hasta 50 caracteres." }
        require(categoria.direccion in listOf("entrada", "salida", "ambas")) { "Selecciona una dirección válida." }
        require(categoria.color.matches(Regex("#[0-9a-fA-F]{6}"))) { "Selecciona un color válido." }
        val todas = categoriaDao.obtenerTodas()
        require(todas.none { it.id != categoria.id && it.nombre.equals(nombre, true) }) { "Ya existe una categoría con ese nombre." }
        val anterior = todas.find { it.id == categoria.id }
        if (anterior?.nombre == "Otros") {
            require(nombre == "Otros" && categoria.direccion == "ambas" && !categoria.archivada) { "Otros debe permanecer disponible para ambos movimientos." }
        }
        if (categoria.id == 0) categoriaDao.insertar(categoria.copy(nombre = nombre))
        else categoriaDao.actualizar(categoria.copy(nombre = nombre))
    }

    suspend fun archivar(categoria: Categoria, archivada: Boolean = true) = guardar(categoria.copy(archivada = archivada))
}
