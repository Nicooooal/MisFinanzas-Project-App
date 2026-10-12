package com.usuario.finanzas.ui.screens.confirmacion

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import com.usuario.finanzas.data.repository.AprendizajeCategoriaRepository
import com.usuario.finanzas.data.repository.CategoriaRepository
import com.usuario.finanzas.data.repository.DeteccionRepository
import com.usuario.finanzas.data.repository.PendientesRepository
import com.usuario.finanzas.data.repository.ServicioRepository
import com.usuario.finanzas.data.repository.TransaccionRepository
import com.usuario.finanzas.domain.SugeridorCategoria
import androidx.room.withTransaction
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.ui.util.leerMonto
import com.usuario.finanzas.ui.util.montoEditable
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@HiltViewModel
class ConfirmacionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deteccionRepository: DeteccionRepository,
    private val pendientesRepository: PendientesRepository,
    private val transaccionRepository: TransaccionRepository,
    private val servicioRepository: ServicioRepository,
    private val categoriaRepository: CategoriaRepository,
    private val aprendizajeRepository: AprendizajeCategoriaRepository,
    private val db: FinanzasDatabase
) : ViewModel() {

    // El id de la pendiente llega como argumento de navegación
    private val pendienteId: Int = checkNotNull(savedStateHandle["pendienteId"])

    private var pendiente: TransaccionPendiente? = null

    var monto by mutableStateOf("")
    var clase by mutableStateOf(ClaseMovimiento.COMPRA)
        private set
    var guardando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val fecha: Long get() = pendiente?.fecha ?: 0L
    val textoOriginal: String get() = pendiente?.textoOriginal.orEmpty()
    val disponible: Boolean get() = pendiente != null
    val esValido: Boolean get() = disponible && leerMonto(monto) != null &&
        comercio.isNotBlank() && categorias.any { it.id == categoriaSeleccionada?.id } && !guardando
    var comercio by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var etiquetas by mutableStateOf("")
    private var todasLasCategorias by mutableStateOf<List<Categoria>>(emptyList())
    val categorias: List<Categoria> get() = todasLasCategorias.filter {
        !it.archivada && (it.direccion == "ambas" || it.direccion == clase.direccion)
    }
    var categoriaSeleccionada by mutableStateOf<Categoria?>(null)
    var servicioCoincidente by mutableStateOf<Servicio?>(null)
        private set
    val servicioDisponible: Servicio? get() = servicioCoincidente.takeIf {
        clase.permiteServicio && comercio.trim() == pendiente?.comercio?.trim() &&
            leerMonto(monto) == pendiente?.monto
    }
    var vincularServicio by mutableStateOf(true)
    var cargando by mutableStateOf(true)
        private set

    private var categoriaAprendidaId: Int? = null
    private var nombreSugeridoDiccionario: String = "Otros"

    init {
        viewModelScope.launch {
            pendiente = pendientesRepository.obtenerPorId(pendienteId)
            val p = pendiente ?: run { cargando = false; return@launch }

            monto = montoEditable(p.monto)
            clase = ClaseMovimiento.desde(p.tipo, p.direccion)
            comercio = p.comercio
            descripcion = "${p.tipo} (${p.banco})"

            todasLasCategorias = categoriaRepository.obtenerTodas()

            val aprendida = aprendizajeRepository.obtenerPorComercio(p.comercio)
            categoriaAprendidaId = aprendida?.categoriaId
            nombreSugeridoDiccionario = SugeridorCategoria.sugerir(p.comercio)
            categoriaSeleccionada = aprendida?.let { a -> categorias.find { it.id == a.categoriaId } }
                ?: categorias.find { it.nombre == nombreSugeridoDiccionario }
                ?: categorias.find { it.nombre == "Otros" }
                ?: categorias.firstOrNull()

            servicioCoincidente = servicioRepository.buscarCoincidencia(p.comercio, p.monto)
            cargando = false
            categoriaRepository.observarTodas().collect { lista ->
                todasLasCategorias = lista
                ajustarCategoria()
            }
        }
    }

    fun seleccionarClase(nueva: ClaseMovimiento) {
        clase = nueva
        ajustarCategoria()
    }

    private fun ajustarCategoria() {
        categoriaSeleccionada = categorias.find { it.id == categoriaSeleccionada?.id }
            ?: categorias.find { it.nombre == "Otros" } ?: categorias.firstOrNull()
    }

    fun guardar(onFin: () -> Unit) {
        val p = pendiente ?: return
        val categoria = categoriaSeleccionada ?: return
        if (!esValido) return
        val montoFinal = leerMonto(monto) ?: return
        val claseFinal = clase
        val comercioFinal = comercio.trim()
        val descripcionFinal = descripcion
        val etiquetasFinal = etiquetas
        val servicio = servicioDisponible.takeIf { vincularServicio }
        guardando = true
        error = null
        viewModelScope.launch {
          try {
            db.withTransaction {
              check(pendientesRepository.obtenerPorId(pendienteId) != null) { tr("Movimiento ya revisado") }
              transaccionRepository.insertar(
                Transaccion(
                    monto = montoFinal,
                    categoriaId = categoria.id,
                    descripcion = descripcionFinal,
                    nombreComercio = comercioFinal,
                    fecha = p.fecha,
                    fuente = Transaccion.FUENTE_AUTOMATICO,
                    servicioIdRelacionado = servicio?.id,
                    direccion = claseFinal.direccion,
                    tipo = claseFinal.tipo,
                    banco = p.banco,
                    etiquetas = etiquetasFinal
                )
            )
            servicio?.let { servicioRepository.registrarPago(it, montoFinal, p.fecha) }
            if (claseFinal.permiteServicio) aprenderCategoria(comercioFinal, categoria)
            deteccionRepository.consumir(pendienteId)
            }
            pendiente = null
            onFin()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            error = tr("No se pudo guardar. Revisa que el movimiento siga pendiente e inténtalo de nuevo.")
          } finally {
            guardando = false
          }
        }
    }

    private suspend fun aprenderCategoria(comercioDetectado: String, elegida: Categoria) {
        if (comercioDetectado.isBlank()) return
        val huboSugerenciaDiccionario = nombreSugeridoDiccionario != "Otros"
        val debeAprender = when {
            categoriaAprendidaId == elegida.id -> true
            huboSugerenciaDiccionario && elegida.nombre != nombreSugeridoDiccionario -> true
            !huboSugerenciaDiccionario && elegida.nombre != "Otros" -> true
            else -> false
        }
        if (debeAprender) aprendizajeRepository.aprender(comercioDetectado, elegida.id)
    }

    fun descartar(onFin: () -> Unit) {
        if (guardando) return
        guardando = true
        viewModelScope.launch {
            try {
                deteccionRepository.consumir(pendienteId)
                pendiente = null
                onFin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = tr("No se pudo descartar. Inténtalo de nuevo.")
            } finally {
                guardando = false
            }
        }
    }
}
