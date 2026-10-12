package com.usuario.finanzas.data.repository

import com.usuario.finanzas.data.local.dao.TransaccionDao
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.domain.finanzas.Dinero
import androidx.room.withTransaction
import com.usuario.finanzas.domain.model.GastoPorCategoria
import com.usuario.finanzas.domain.model.TotalMensual
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class TransaccionRepository @Inject constructor(
    private val transaccionDao: TransaccionDao,
    private val db: FinanzasDatabase
) {
    fun observarTodas(): Flow<List<Transaccion>> = transaccionDao.observarTodas()

    fun observarCambios(): Flow<Int> = transaccionDao.observarCambios()

    fun entradasMesActual(): Flow<Double> = observarRangoActual().flatMapLatest { (inicio, fin) ->
        transaccionDao.entradasEntre(inicio, fin)
    }

    suspend fun entradasDelMes(anio: Int, mes: Int): Double {
        val (inicio, fin) = rangoMes(anio, mes)
        return transaccionDao.entradasEntreUnaVez(inicio, fin)
    }

    fun observarPorId(id: Int): Flow<Transaccion?> = transaccionDao.observarPorId(id)

    suspend fun insertar(transaccion: Transaccion): Long = db.withTransaction {
        transaccionDao.insertar(normalizar(transaccion))
    }

    suspend fun insertarLote(transacciones: List<Transaccion>) = db.withTransaction {
        require(transacciones.isNotEmpty() && transacciones.size <= 200) { "Revisa la cantidad de movimientos." }
        transacciones.map { transaccionDao.insertar(normalizar(it)) }
    }

    private suspend fun normalizar(t: Transaccion): Transaccion {
        val monto = t.montoCentavos ?: Dinero.aCentavos(t.monto)
        require(monto in 1..Dinero.MAX_CENTAVOS) { "El monto debe ser mayor que cero." }
        require(t.direccion in listOf("entrada", "salida")) { "Dirección no válida." }
        require(t.fecha in 0..System.currentTimeMillis()) { "No se pueden registrar movimientos futuros; usa Recurrentes." }
        val cat = requireNotNull(db.categoriaDao().obtenerPorId(t.categoriaId)) { "La categoría ya no existe." }
        require(cat.direccion == "ambas" || cat.direccion == t.direccion) { "Selecciona una categoría compatible con el movimiento." }
        require(t.id != 0 || !cat.archivada) { "La categoría está archivada." }
        return t.copy(montoCentavos = monto, monto = Dinero.aUnidades(monto), etiquetas = t.etiquetas.split(',').map { it.trim().take(30) }.filter { it.isNotBlank() }.distinct().take(10).joinToString(","))
    }

    suspend fun actualizar(transaccion: Transaccion) = db.withTransaction { transaccionDao.actualizar(normalizar(transaccion)) }

    suspend fun eliminar(transaccion: Transaccion) = transaccionDao.eliminar(transaccion)

    /** Total gastado en el mes calendario actual. */
    fun totalMesActual(): Flow<Double> = observarRangoActual().flatMapLatest { (inicio, fin) ->
        transaccionDao.totalEntre(inicio, fin)
    }

    /** Gastos del mes actual agrupados por categoría. */
    fun gastosPorCategoriaMesActual(): Flow<List<GastoPorCategoria>> =
        observarRangoActual().flatMapLatest { (inicio, fin) ->
            transaccionDao.gastosPorCategoriaEntre(inicio, fin)
        }

    fun observarRangoActual(): Flow<Pair<Long, Long>> = flow {
        while (true) {
            emit(rangoMesActual())
            delay(60_000)
        }
    }.distinctUntilChanged()

    /**
     * Totales de los últimos [cantidad] meses calendario, incluido el actual,
     * en orden cronológico (el último elemento es el mes en curso).
     */
    suspend fun totalesUltimosMeses(cantidad: Int = 6): List<TotalMensual> {
        val zona = ZoneId.systemDefault()
        val hoy = LocalDate.now()
        return (cantidad - 1 downTo 0).map { mesesAtras ->
            val mes = hoy.minusMonths(mesesAtras.toLong())
            val inicio = mes.withDayOfMonth(1).atStartOfDay(zona).toInstant().toEpochMilli()
            val fin = mes.withDayOfMonth(mes.lengthOfMonth())
                .plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
            TotalMensual(
                anio = mes.year,
                mes = mes.monthValue,
                total = transaccionDao.totalEntreUnaVez(inicio, fin),
                esMesActual = mesesAtras == 0
            )
        }
    }

    /** Lista de transacciones de un mes/año específico. */
    suspend fun transaccionesDelMes(anio: Int, mes: Int): List<Transaccion> {
        val (inicio, fin) = rangoMes(anio, mes)
        return transaccionDao.listarEntre(inicio, fin)
    }

    /** Gastos por categoría de un mes/año específico. */
    suspend fun gastosPorCategoriaDelMes(anio: Int, mes: Int): List<GastoPorCategoria> {
        val (inicio, fin) = rangoMes(anio, mes)
        return transaccionDao.gastosPorCategoriaEntreUnaVez(inicio, fin)
    }

    /** Total gastado en un mes/año específico. */
    suspend fun totalDelMes(anio: Int, mes: Int): Double {
        val (inicio, fin) = rangoMes(anio, mes)
        return transaccionDao.totalEntreUnaVez(inicio, fin)
    }

    private fun rangoMes(anio: Int, mes: Int): Pair<Long, Long> {
        val zona = ZoneId.systemDefault()
        val fecha = LocalDate.of(anio, mes, 1)
        val inicio = fecha.atStartOfDay(zona).toInstant().toEpochMilli()
        val fin = fecha.withDayOfMonth(fecha.lengthOfMonth())
            .plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
        return inicio to fin
    }

    private fun rangoMesActual(): Pair<Long, Long> {
        val zona = ZoneId.systemDefault()
        val hoy = LocalDate.now()
        val inicio = hoy.withDayOfMonth(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val fin = hoy.withDayOfMonth(hoy.lengthOfMonth())
            .plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
        return inicio to fin
    }
}
