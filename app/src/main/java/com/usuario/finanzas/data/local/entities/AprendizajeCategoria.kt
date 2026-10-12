package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Asociación comercio → categoría aprendida de las correcciones manuales del usuario.
 * 100% local: cuando el usuario corrige la categoría sugerida para un comercio,
 * se guarda aquí y se reutiliza la próxima vez que aparezca el mismo comercio.
 */
@Entity(
    tableName = "aprendizaje_categorias",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoriaId")]
)
data class AprendizajeCategoria(
    /** Nombre del comercio normalizado: minúsculas y sin espacios extra. */
    @PrimaryKey val nombreComercio: String,
    val categoriaId: Int,
    /** Cuántas veces se ha confirmado esta asociación. */
    val vecesUsada: Int = 1,
    /** Timestamp epoch ms de la última vez que se usó/corrigió. */
    val ultimaActualizacion: Long = System.currentTimeMillis()
)
