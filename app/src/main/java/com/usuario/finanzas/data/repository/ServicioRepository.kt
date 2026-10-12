package com.usuario.finanzas.data.repository

import androidx.room.withTransaction
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.dao.HistorialPagoDao
import com.usuario.finanzas.data.local.dao.ServicioDao
import com.usuario.finanzas.data.local.entities.HistorialPago
import com.usuario.finanzas.data.local.entities.Servicio
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ServicioRepository @Inject constructor(
    private val servicioDao: ServicioDao,
    private val historialPagoDao: HistorialPagoDao,
    private val db: FinanzasDatabase
) {
    fun observarServicios(): Flow<List<Servicio>> = servicioDao.observarTodos()

    fun observarPorId(id: Int): Flow<Servicio?> = servicioDao.observarPorId(id)

    fun observarHistorial(servicioId: Int): Flow<List<HistorialPago>> =
        historialPagoDao.observarPorServicio(servicioId)

    suspend fun insertar(servicio: Servicio): Long = servicioDao.insertar(servicio)

    suspend fun actualizar(servicio: Servicio) = servicioDao.actualizar(servicio)

    suspend fun eliminar(servicio: Servicio) = servicioDao.eliminar(servicio)

    // ---------------- Ciclo de facturación y estado ----------------

    /**
     * Inicio del ciclo actual: el día de corte más reciente.
     *
     * Regla:
     * - Si hoy >= día de corte de este mes → el ciclo empezó este mes
     * - Si hoy < día de corte de este mes → el ciclo empezó el mes pasado
     *
     * IMPORTANTE: el día de corte es INCLUSIVO (si hoy ES el día de corte,
     * el ciclo acaba de empezar hoy, no el mes pasado).
     */
    private fun inicioCicloActual(diaCorte: Int, hoy: LocalDate = LocalDate.now()): LocalDate {
        val diaCorteEsteMes = diaCorte.coerceIn(1, hoy.lengthOfMonth())
        return if (hoy.dayOfMonth >= diaCorteEsteMes) {
            // El ciclo empezó este mes
            hoy.withDayOfMonth(diaCorteEsteMes)
        } else {
            // El ciclo empezó el mes pasado
            val mesAnterior = hoy.minusMonths(1)
            mesAnterior.withDayOfMonth(diaCorte.coerceIn(1, mesAnterior.lengthOfMonth()))
        }
    }

    /**
     * Fecha límite de pago del ciclo actual.
     *
     * El límite siempre es POSTERIOR al inicio del ciclo.
     * Si diaLimite <= diaCorte, el límite cae en el mes siguiente al inicio.
     * Si diaLimite > diaCorte, el límite cae en el mismo mes que el inicio.
     *
     * Ejemplo: corte=1, límite=15 → límite es el 15 del mismo mes que el corte.
     * Ejemplo: corte=25, límite=5  → límite es el 5 del MES SIGUIENTE al corte.
     */
    private fun fechaLimiteCiclo(servicio: Servicio, hoy: LocalDate = LocalDate.now()): LocalDate {
        val inicio = inicioCicloActual(servicio.diaCorte, hoy)
        return if (servicio.diaLimite > servicio.diaCorte) {
            // Límite en el mismo mes que el inicio
            inicio.withDayOfMonth(
                servicio.diaLimite.coerceAtMost(inicio.lengthOfMonth())
            )
        } else {
            // Límite en el mes siguiente al inicio
            val mesSiguiente = inicio.plusMonths(1)
            mesSiguiente.withDayOfMonth(
                servicio.diaLimite.coerceAtMost(mesSiguiente.lengthOfMonth())
            )
        }
    }

    /**
     * Días que faltan para la fecha límite del ciclo actual.
     * Negativo si ya venció, 0 si vence hoy, positivo si falta tiempo.
     */
    fun diasHastaLimite(servicio: Servicio, hoy: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(hoy, fechaLimiteCiclo(servicio, hoy))

    /**
     * Estado calculado — ÚNICA fuente de verdad:
     * - "pagado"   si hay un HistorialPago dentro del ciclo actual
     * - "vencido"  si hoy pasó la fecha límite y no está pagado
     * - "pendiente" en cualquier otro caso
     *
     * Esta función es la única que decide el estado. Tanto el Dashboard
     * como la pantalla de Servicios deben leer el estado desde la BD
     * (persistido por actualizarEstados()), no calcularlo de nuevo,
     * para evitar inconsistencias.
     */
    suspend fun calcularEstado(servicio: Servicio, hoy: LocalDate = LocalDate.now()): String {
        val zona = ZoneId.systemDefault()
        val inicioCiclo = inicioCicloActual(servicio.diaCorte, hoy)
            .atStartOfDay(zona).toInstant().toEpochMilli()
        val hastaExclusivo = hoy.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val pagado = historialPagoDao.contarPagosEntre(servicio.id, inicioCiclo, hastaExclusivo) > 0
        return when {
            pagado -> Servicio.ESTADO_PAGADO
            hoy.isAfter(fechaLimiteCiclo(servicio, hoy)) -> Servicio.ESTADO_VENCIDO
            else -> Servicio.ESTADO_PENDIENTE
        }
    }

    /**
     * Recalcula y persiste el estado de todos los servicios.
     * Llamar siempre que haya cambios (nueva transacción, arranque de app,
     * WorkManager diario). Garantiza que la BD sea la única fuente de verdad.
     */
    suspend fun actualizarEstados() = db.withTransaction {
        servicioDao.obtenerTodos().forEach { servicio ->
            val nuevoEstado = calcularEstado(servicio)
            if (nuevoEstado != servicio.estado) {
                servicioDao.actualizar(servicio.copy(estado = nuevoEstado))
            }
        }
    }

    /** Servicios no pagados cuya fecha límite está a [dias] días o menos. */
    suspend fun serviciosProximosAVencer(dias: Int = 3): List<Pair<Servicio, Long>> {
        actualizarEstados()
        return servicioDao.obtenerTodos()
            .filter { it.estado != Servicio.ESTADO_PAGADO }
            .map { it to diasHastaLimite(it) }
            .filter { (_, restantes) -> restantes in 0..dias.toLong() }
    }

    // ---------------- Pagos ----------------

    suspend fun registrarPago(
        servicio: Servicio,
        monto: Double,
        fechaPago: Long = System.currentTimeMillis(),
        comprobantePath: String? = null
    ) = db.withTransaction {
        val actual = requireNotNull(servicioDao.obtenerPorId(servicio.id)) { "El servicio ya no existe." }
        historialPagoDao.insertar(
            HistorialPago(
                servicioId = actual.id,
                monto = monto,
                fechaPago = fechaPago,
                comprobantePath = comprobantePath
            )
        )
        servicioDao.actualizar(actual.copy(estado = calcularEstado(actual)))
    }

    // ---------------- Coincidencia con transacciones detectadas ----------------

    suspend fun buscarCoincidencia(comercio: String, monto: Double): Servicio? {
        val palabrasComercio = palabras(comercio)
        return servicioDao.obtenerTodos().firstOrNull { servicio ->
            val nombre = servicio.nombre.lowercase()
            val palabrasServicio = palabras(servicio.nombre)
            val coincideTexto = palabrasComercio.any { nombre.contains(it) } ||
                palabrasServicio.any { comercio.lowercase().contains(it) }
            val dentroDeRango = servicio.montoEstimado > 0 &&
                abs(monto - servicio.montoEstimado) <= servicio.montoEstimado * 0.15
            coincideTexto && dentroDeRango
        }
    }

    private fun palabras(texto: String): List<String> =
        texto.lowercase()
            .split(Regex("[^a-záéíóúñ0-9]+"))
            .filter { it.length >= 4 }
}
