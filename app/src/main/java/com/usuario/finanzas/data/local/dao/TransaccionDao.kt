package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.domain.model.GastoPorCategoria
import kotlinx.coroutines.flow.Flow

@Dao
interface TransaccionDao {

    @Query("SELECT * FROM transacciones ORDER BY fecha DESC")
    fun observarTodas(): Flow<List<Transaccion>>

    @Query("SELECT * FROM transacciones WHERE id = :id")
    fun observarPorId(id: Int): Flow<Transaccion?>

    @Insert
    suspend fun insertar(transaccion: Transaccion): Long

    @Update
    suspend fun actualizar(transaccion: Transaccion)

    @Delete
    suspend fun eliminar(transaccion: Transaccion)

    /** Total gastado entre dos timestamps (se usa con el rango del mes actual). */
    @Query("SELECT COALESCE(SUM(monto), 0) FROM transacciones WHERE fecha BETWEEN :inicio AND :fin AND direccion = 'salida' AND tipo != 'transferencia_propia'")
    fun totalEntre(inicio: Long, fin: Long): Flow<Double>

    /** Versión puntual (no reactiva) del total entre dos timestamps; para el historial mensual. */
    @Query("SELECT COALESCE(SUM(monto), 0) FROM transacciones WHERE fecha BETWEEN :inicio AND :fin AND direccion = 'salida' AND tipo != 'transferencia_propia'")
    suspend fun totalEntreUnaVez(inicio: Long, fin: Long): Double

    @Query("SELECT COALESCE(SUM(monto), 0) FROM transacciones WHERE fecha BETWEEN :inicio AND :fin AND direccion = 'entrada' AND tipo != 'transferencia_propia'")
    fun entradasEntre(inicio: Long, fin: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(monto), 0) FROM transacciones WHERE fecha BETWEEN :inicio AND :fin AND direccion = 'entrada' AND tipo != 'transferencia_propia'")
    suspend fun entradasEntreUnaVez(inicio: Long, fin: Long): Double

    /** Invalida informes incluso si cambia un importe de un mes anterior. */
    @Query("SELECT COUNT(*) FROM transacciones")
    fun observarCambios(): Flow<Int>

    /** Lista de transacciones entre dos timestamps, más recientes primero. */
    @Query("SELECT * FROM transacciones WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun listarEntre(inicio: Long, fin: Long): List<Transaccion>

    /** Gastos por categoría entre dos timestamps (versión puntual, para historial de mes). */
    @Query(
        """
        SELECT c.id AS categoriaId, c.nombre AS nombre, c.color AS color, c.icono AS icono,
               SUM(t.monto) AS total
        FROM transacciones t
        INNER JOIN categorias c ON c.id = t.categoriaId
        WHERE t.fecha BETWEEN :inicio AND :fin AND t.direccion = 'salida' AND t.tipo != 'transferencia_propia'
        GROUP BY c.id
        ORDER BY total DESC
        """
    )
    suspend fun gastosPorCategoriaEntreUnaVez(inicio: Long, fin: Long): List<GastoPorCategoria>

    /** Gastos agrupados por categoría entre dos timestamps. */
    @Query(
        """
        SELECT c.id AS categoriaId, c.nombre AS nombre, c.color AS color, c.icono AS icono,
               SUM(t.monto) AS total
        FROM transacciones t
        INNER JOIN categorias c ON c.id = t.categoriaId
        WHERE t.fecha BETWEEN :inicio AND :fin AND t.direccion = 'salida' AND t.tipo != 'transferencia_propia'
        GROUP BY c.id
        ORDER BY total DESC
        """
    )
    fun gastosPorCategoriaEntre(inicio: Long, fin: Long): Flow<List<GastoPorCategoria>>
}
