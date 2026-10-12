package com.usuario.finanzas.ui.screens.captura

import com.usuario.finanzas.ui.i18n.tr

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.preferences.CoordinadorDatos
import com.usuario.finanzas.data.repository.*
import com.usuario.finanzas.domain.SugeridorCategoria
import com.usuario.finanzas.domain.captura.*
import com.usuario.finanzas.services.LectorRecibo
import com.usuario.finanzas.domain.finanzas.*
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.ui.util.Comprobantes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class BorradorEntrada(val id: Int, val concepto: String, val monto: String, val fecha: LocalDate, val clase: ClaseMovimiento, val categoriaId: Int?, val incluido: Boolean = true, val aviso: String? = null)

@HiltViewModel
class CapturaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoriasRepo: CategoriaRepository,
    private val transaccionesRepo: TransaccionRepository,
    private val finanzas: FinanzasRepository,
    private val coordinador: CoordinadorDatos
) : ViewModel() {
    var texto by mutableStateOf("")
    var borradores by mutableStateOf<List<BorradorEntrada>>(emptyList()); private set
    var categorias by mutableStateOf<List<Categoria>>(emptyList()); private set
    var ocupado by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var revisado by mutableStateOf(false)
    var moneda by mutableStateOf("COP"); private set
    var textoRecibo by mutableStateOf("")
    var leyendoRecibo by mutableStateOf(false); private set
    var candidatosTotales by mutableStateOf<List<CandidatoTotalRecibo>>(emptyList()); private set
    private var reciboUri by mutableStateOf<Uri?>(null)
    val esReciboCargado: Boolean get() = reciboUri != null
    val uriReciboActual: String? get() = reciboUri?.toString()
    private var trabajo: Job? = null
    private var generacion = 0L

    init { viewModelScope.launch { categorias = categoriasRepo.obtenerTodas(); moneda = finanzas.observarConfiguracion().first().moneda } }
    fun aviso(mensaje: String) { error = mensaje }
    fun cambiar(b: BorradorEntrada) { if (ocupado) return; borradores = borradores.map { if (it.id == b.id) b else it }; revisado = false }
    fun analizar() {
        if (ocupado) return
        val entrada = texto
        ejecutar { token ->
            prepararBorradores(withContext(Dispatchers.Default) { ParserEntrada.interpretar(entrada) }, token)
            if (token == generacion) { reciboUri = null; textoRecibo = ""; candidatosTotales = emptyList() }
        }
    }
    fun dictado(resultado: String) { if (ocupado) return; texto = resultado; analizar() }

    /** Relee las correcciones del usuario sin perder la fotografía adjunta. */
    fun reanalizarRecibo() {
        if (ocupado || !esReciboCargado) return
        val entrada = textoRecibo
        ejecutar { token -> interpretarRecibo(entrada, token) }
    }

    fun elegirTotalRecibo(centavos: Long) {
        if (ocupado || candidatosTotales.none { it.centavos == centavos } || borradores.size != 1) return
        val borrador = borradores.single()
        ejecutar { token ->
            val actuales = transaccionesRepo.observarTodas().first()
            val repetido = withContext(Dispatchers.Default) { actuales.any {
                it.nombreComercio.equals(borrador.concepto, true) && CalculadoraFinanciera.monto(it) == centavos &&
                    it.direccion == borrador.clase.direccion && CalculadoraFinanciera.fechaLocal(it.fecha) == borrador.fecha
            } }
            if (token == generacion) borradores = listOf(borrador.copy(
                monto = Dinero.editable(centavos),
                incluido = borrador.incluido && !repetido,
                aviso = tr(if (repetido) "Posible duplicado: excluido, puedes incluirlo si es otro movimiento"
                    else "Revisa comercio, fecha y total del recibo")
            ))
        }
    }

    fun cancelarLectura() {
        if (!leyendoRecibo) return
        generacion++; trabajo?.cancel(); trabajo = null
        ocupado = false; leyendoRecibo = false
    }

    private fun ejecutar(lectura: Boolean = false, bloque: suspend (Long) -> Unit) {
        val token = ++generacion
        trabajo?.cancel()
        ocupado = true; leyendoRecibo = lectura; error = null; revisado = false
        trabajo = viewModelScope.launch {
            try { bloque(token) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generacion) error = tr(e.message?.takeIf { it.isNotBlank() } ?: if (lectura)
                    "No se pudo leer la imagen. Prueba una foto enfocada y completa." else "No se pudo interpretar el texto.")
            } finally {
                if (token == generacion) { ocupado = false; leyendoRecibo = false }
            }
        }
    }

    private suspend fun interpretarRecibo(entrada: String, token: Long) {
        val resultado = withContext(Dispatchers.Default) { ParserRecibo.analizar(entrada) }
        prepararBorradores(listOf(resultado.entrada), token)
        if (token == generacion) { candidatosTotales = resultado.candidatosTotales; texto = entrada }
    }

    private suspend fun prepararBorradores(extraidos: List<EntradaInterpretada>, token: Long) {
        val actuales = transaccionesRepo.observarTodas().first()
        val disponibles = categoriasRepo.obtenerTodas()
        val nuevos = withContext(Dispatchers.Default) { extraidos.mapIndexed { i, e ->
            val compatibles = disponibles.filter { !it.archivada && (it.direccion == "ambas" || it.direccion == e.clase.direccion) }
            val sugerencia = SugeridorCategoria.sugerir(e.concepto)
            val cat = compatibles.find { it.nombre == sugerencia } ?: compatibles.find { it.nombre == "Otros" } ?: compatibles.firstOrNull()
            val repetido = actuales.any { it.nombreComercio.equals(e.concepto, true) && CalculadoraFinanciera.monto(it) == e.centavos && it.direccion == e.clase.direccion && CalculadoraFinanciera.fechaLocal(it.fecha) == e.fecha }
            val repetidoLote = extraidos.take(i).any { it.concepto.equals(e.concepto, true) && it.centavos == e.centavos && it.clase.direccion == e.clase.direccion && it.fecha == e.fecha }
            BorradorEntrada(i, e.concepto, e.centavos?.let(Dinero::editable).orEmpty(), e.fecha, e.clase, cat?.id, !repetido && !repetidoLote,
                if (repetido || repetidoLote) tr("Posible duplicado: excluido, puedes incluirlo si es otro movimiento") else e.advertencia)
        } }
        if (token != generacion) return
        categorias = disponibles; borradores = nuevos
        if (nuevos.isEmpty()) error = tr("Escribe uno o más movimientos para revisar.")
    }

    fun leerRecibo(uri: Uri) {
        if (ocupado) return
        ejecutar(lectura = true) { token ->
            val resultado = LectorRecibo.leer(context, uri)
            if (resultado.isBlank()) throw IllegalArgumentException("No se encontró texto legible. Prueba una foto enfocada, con buena luz y el recibo completo.")
            if (token != generacion) return@ejecutar
            interpretarRecibo(resultado, token)
            if (token == generacion) { reciboUri = uri; textoRecibo = resultado }
        }
    }

    fun guardar(onFin: () -> Unit) {
        if (ocupado || !revisado) return
        val incluidos = borradores.filter { it.incluido }
        if (incluidos.isEmpty() || incluidos.any { (Dinero.parsear(it.monto) ?: 0L) <= 0 || it.categoriaId == null || it.concepto.isBlank() || it.fecha.isAfter(LocalDate.now()) }) {
            error = tr("Revisa importes, fechas y categorías de los movimientos incluidos."); return
        }
        ocupado = true; error = null
        val revision = coordinador.revisionActual()
        val recibo = reciboUri
        viewModelScope.launch {
            try {
                check(coordinador.capturar(revision) {
                    // Copia e inserción comparten el bloqueo; restaurar nunca borra un adjunto a medio guardar.
                    // Tras confirmar la BD, una cancelación de la pantalla tampoco debe borrar el archivo.
                    withContext(NonCancellable) {
                        var adjunto: String? = null
                        try {
                            if (recibo != null && incluidos.size == 1) adjunto = withContext(Dispatchers.IO) {
                                requireNotNull(Comprobantes.copiarDesdeUri(context, recibo)) {
                                    tr("No se pudo guardar el comprobante. Selecciona la imagen nuevamente.")
                                }
                            }
                            val lote = incluidos.map { b ->
                                val centavos = requireNotNull(Dinero.parsear(b.monto))
                                Transaccion(monto = Dinero.aUnidades(centavos), montoCentavos = centavos, categoriaId = b.categoriaId!!,
                                    descripcion = tr("Registro asistido revisado"), nombreComercio = b.concepto.trim(), fecha = b.fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    fuente = Transaccion.FUENTE_MANUAL, direccion = b.clase.direccion, tipo = b.clase.tipo, comprobantePath = adjunto)
                            }
                            transaccionesRepo.insertarLote(lote).also { adjunto = null }
                        } finally { adjunto?.let(Comprobantes::eliminar) }
                    }
                } != null) { tr("Los datos cambiaron. Vuelve a revisar los movimientos.") }
                borradores = emptyList(); onFin()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message?.let { tr(it) } ?: tr("No se pudo guardar el lote.") }
            finally { ocupado = false }
        }
    }
}
