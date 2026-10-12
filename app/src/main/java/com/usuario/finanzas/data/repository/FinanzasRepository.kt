package com.usuario.finanzas.data.repository

import androidx.room.withTransaction
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.dao.FinanzasDao
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.domain.finanzas.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanzasRepository @Inject constructor(private val db: FinanzasDatabase, private val dao: FinanzasDao) {
    fun observarConfiguracion(): Flow<ConfiguracionFinanciera> = dao.observarConfiguracion().map { it ?: ConfiguracionFinanciera() }
    fun observarPresupuestos(): Flow<List<Presupuesto>> = dao.observarPresupuestos()
    fun observarMetas(): Flow<List<Meta>> = dao.observarMetas()
    fun observarAportes(): Flow<List<AporteMeta>> = dao.observarAportes()
    fun observarRecurrentes(): Flow<List<Recurrente>> = dao.observarRecurrentes()
    fun observarOcurrencias(): Flow<List<OcurrenciaRecurrente>> = dao.observarOcurrencias()

    suspend fun guardarConfiguracion(configuracion: ConfiguracionFinanciera) = db.withTransaction {
        require(configuracion.id == 1 && configuracion.moneda in listOf("COP", "USD", "EUR", "BRL")) { "Moneda no válida." }
        require(configuracion.saldoInicialCentavos in -Dinero.MAX_CENTAVOS..Dinero.MAX_CENTAVOS) { "Saldo fuera del rango permitido." }
        require(configuracion.fechaInicial >= 0 && configuracion.fechaInicial <= System.currentTimeMillis()) { "La fecha inicial no puede estar en el futuro." }
        val anterior = dao.configuracion() ?: ConfiguracionFinanciera()
        if (anterior.moneda != configuracion.moneda) {
            require(dao.contarDatosFinancieros() == 0 && anterior.saldoInicialCentavos == 0L) {
                "Ya hay importes registrados. La moneda base no puede cambiarse sin convertirlos."
            }
        }
        dao.guardarConfiguracion(configuracion)
    }

    suspend fun guardarPresupuesto(presupuesto: Presupuesto) {
        validarNombreMonto(presupuesto.nombre, presupuesto.limiteCentavos)
        require(presupuesto.ciclo in listOf("semanal", "mensual", "anual")) { "Período no válido." }
        // Archivar sigue siendo posible si la categoría cambió de dirección después de crear el plan.
        if (presupuesto.activo) presupuesto.categoriaId?.let { validarCategoria(it, "salida", permitirArchivada = presupuesto.id != 0L) }
        val limpio = presupuesto.copy(nombre = presupuesto.nombre.trim())
        if (limpio.id == 0L) dao.insertarPresupuesto(limpio) else dao.actualizarPresupuesto(limpio)
    }

    suspend fun guardarMeta(meta: Meta) {
        validarNombreMonto(meta.nombre, meta.objetivoCentavos)
        val limpia = meta.copy(nombre = meta.nombre.trim())
        if (limpia.id == 0L) dao.insertarMeta(limpia) else dao.actualizarMeta(limpia)
    }

    /** Solo reserva o libera dinero ya existente, nunca inserta una transacción. */
    suspend fun registrarAporte(metaId: Long, montoCentavos: Long, nota: String = "", fecha: Long = System.currentTimeMillis()) = db.withTransaction {
        val meta = requireNotNull(dao.meta(metaId)) { "La meta ya no existe." }
        require(!meta.archivada) { "Reactiva la meta antes de registrar aportes." }
        require(montoCentavos != 0L && montoCentavos in -Dinero.MAX_CENTAVOS..Dinero.MAX_CENTAVOS) { "Escribe un monto válido." }
        require(fecha in 0..System.currentTimeMillis()) { "La fecha no puede estar en el futuro." }
        val saldo = Dinero.sumar(dao.aportes(metaId).map { it.montoCentavos })
        val nuevoSaldo = Math.addExact(saldo, montoCentavos)
        require(nuevoSaldo >= 0) { "El retiro supera lo reservado en esta meta." }
        require(nuevoSaldo <= Dinero.MAX_CENTAVOS) { "El aporte supera el límite permitido." }
        dao.insertarAporte(AporteMeta(metaId = metaId, montoCentavos = montoCentavos, fecha = fecha, nota = nota.trim().take(200)))
    }

    suspend fun guardarRecurrente(recurrente: Recurrente) {
        validarNombreMonto(recurrente.nombre, recurrente.montoCentavos)
        require(recurrente.direccion in listOf("entrada", "salida")) { "Dirección no válida." }
        require(recurrente.frecuencia in CalendarioRecurrente.frecuencias) { "Frecuencia no válida." }
        require(recurrente.fechaInicio >= 0) { "Fecha no válida." }
        // Pausar no depende de que la categoría todavía sea apta para registrar movimientos nuevos.
        if (recurrente.activo) validarCategoria(recurrente.categoriaId, recurrente.direccion, permitirArchivada = recurrente.id != 0L)
        val limpio = recurrente.copy(nombre = recurrente.nombre.trim())
        if (limpio.id == 0L) dao.insertarRecurrente(limpio) else dao.actualizarRecurrente(limpio)
    }

    /** Confirmación explícita. Una ocurrencia puede vincular un movimiento real ya detectado. */
    suspend fun registrarOcurrencia(recurrenteId: Long, fechaProgramada: Long, transaccionExistenteId: Int? = null): Int = db.withTransaction {
        val recurrente = requireNotNull(dao.recurrente(recurrenteId)) { "El movimiento recurrente ya no existe." }
        require(recurrente.activo) { "El movimiento recurrente está pausado." }
        require(dao.ocurrencia(recurrenteId, fechaProgramada) == null) { "Este vencimiento ya se registró." }
        require(fechaProgramada in CalendarioRecurrente.vencimientos(recurrente)) { "La fecha todavía no vence o no pertenece a esta programación." }
        val id = if (transaccionExistenteId != null) {
            val transaccion = requireNotNull(dao.transaccion(transaccionExistenteId)) { "El movimiento seleccionado ya no existe." }
            require(transaccion.direccion == recurrente.direccion && transaccion.tipo != "transferencia_propia") { "La dirección del movimiento no coincide." }
            require(transaccion.fecha <= System.currentTimeMillis()) { "No se puede vincular un movimiento futuro." }
            require(dao.contarVinculos(transaccion.id) == 0) { "Este movimiento ya está vinculado a otro vencimiento." }
            transaccion.id
        } else {
            validarCategoria(recurrente.categoriaId, recurrente.direccion)
            db.transaccionDao().insertar(Transaccion(
                monto = Dinero.aUnidades(recurrente.montoCentavos), montoCentavos = recurrente.montoCentavos,
                categoriaId = recurrente.categoriaId, descripcion = "Recurrente: ${recurrente.nombre}",
                nombreComercio = recurrente.nombre, fecha = fechaProgramada, fuente = Transaccion.FUENTE_MANUAL,
                direccion = recurrente.direccion, tipo = if (recurrente.direccion == "entrada") "ingreso" else "compra"
            )).toInt()
        }
        dao.insertarOcurrencia(OcurrenciaRecurrente(recurrenteId, fechaProgramada, id, System.currentTimeMillis()))
        id
    }

    private fun validarNombreMonto(nombre: String, monto: Long) {
        require(nombre.isNotBlank() && nombre.trim().length <= 80) { "Escribe un nombre de hasta 80 caracteres." }
        require(monto in 1..Dinero.MAX_CENTAVOS) { "Escribe un monto mayor que cero." }
    }

    private suspend fun validarCategoria(id: Int, direccion: String, permitirArchivada: Boolean = false) {
        val categoria = requireNotNull(db.categoriaDao().obtenerPorId(id)) { "La categoría ya no existe." }
        require(permitirArchivada || !categoria.archivada) { "La categoría está archivada; selecciona otra." }
        require(categoria.direccion == "ambas" || categoria.direccion == direccion) { "La categoría no corresponde a la dirección seleccionada." }
    }
}
