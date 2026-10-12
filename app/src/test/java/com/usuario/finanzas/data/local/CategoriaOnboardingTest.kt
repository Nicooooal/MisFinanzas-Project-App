package com.usuario.finanzas.data.local

import android.app.Application
import androidx.room.Room
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Transaccion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CategoriaOnboardingTest {
    @Test fun seleccionVaciaConservaOtrosYElHistoricoPuedeReactivarCategoria() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinanzasDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val categorias = db.categoriaDao()
            categorias.insertar(Categoria(1, "Otros", "#999999", "Category"))
            categorias.insertar(Categoria(2, "Comida", "#FFAA00", "Category", direccion = "salida"))
            categorias.insertar(Categoria(3, "Nómina", "#00AA00", "Category", direccion = "entrada"))
            val original = Transaccion(7, 12.34, 2, "Almuerzo guardado", "Cafetería", 1000,
                "manual", montoCentavos = 1234, etiquetas = "trabajo")
            db.transaccionDao().insertar(original)

            categorias.aplicarSeleccionInicial(emptyList())
            assertEquals(listOf(1), categorias.obtenerActivas("salida").map { it.id })
            assertEquals(listOf(1), categorias.obtenerActivas("entrada").map { it.id })
            assertTrue(categorias.obtenerPorId(2)!!.archivada)
            assertEquals(original, db.finanzasDao().transaccion(7))

            categorias.aplicarSeleccionInicial(listOf(2))
            assertEquals(setOf(1, 2), categorias.obtenerActivas("salida").map { it.id }.toSet())
            assertEquals(listOf(1), categorias.obtenerActivas("entrada").map { it.id })
            assertFalse(categorias.obtenerPorId(1)!!.archivada)
            assertFalse(categorias.obtenerPorId(2)!!.archivada)
            assertEquals(original, db.finanzasDao().transaccion(7))
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close() }
    }
}
