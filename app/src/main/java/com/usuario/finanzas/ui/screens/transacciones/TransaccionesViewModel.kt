package com.usuario.finanzas.ui.screens.transacciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.repository.CategoriaRepository
import com.usuario.finanzas.data.repository.TransaccionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Filtros activos de la lista de transacciones. */
data class FiltrosTransacciones(
    val consulta: String = "",
    val categoriaId: Int? = null,
    val direccion: String? = null,
    val soloTransferencias: Boolean = false,
    val fuente: String? = null,   // "manual" / "automatico" / null = todas
    val desde: Long? = null,
    val hasta: Long? = null
) {
    val hayFiltros: Boolean
        get() = consulta.isNotBlank() || categoriaId != null || direccion != null || soloTransferencias || fuente != null || desde != null || hasta != null
}

/** Transacción junto con su categoría (para mostrar ícono y color). */
data class TransaccionConCategoria(val transaccion: Transaccion, val categoria: Categoria?)

@HiltViewModel
class TransaccionesViewModel @Inject constructor(
    transaccionRepository: TransaccionRepository,
    categoriaRepository: CategoriaRepository
) : ViewModel() {

    val filtros = MutableStateFlow(FiltrosTransacciones())

    val categorias: StateFlow<List<Categoria>> = categoriaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transacciones: StateFlow<List<TransaccionConCategoria>> = combine(
        transaccionRepository.observarTodas(),
        categorias,
        filtros
    ) { lista, cats, f ->
        val palabras = normalizarBusqueda(f.consulta).split(Regex("\\s+")).filter { it.isNotBlank() }
        val categoriasPorId = cats.associateBy { it.id }
        val finExclusivo = f.hasta?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        lista.asSequence()
            .filter { f.categoriaId == null || it.categoriaId == f.categoriaId }
            .filter { f.fuente == null || it.fuente == f.fuente }
            .filter { f.direccion == null || it.direccion == f.direccion }
            .filter { !f.soloTransferencias || it.tipo in setOf("transferencia", "transferencia_propia") }
            .filter { f.desde == null || it.fecha >= f.desde }
            .filter { finExclusivo == null || it.fecha < finExclusivo }
            .filter { t ->
                if (palabras.isEmpty()) true else {
                    val texto = normalizarBusqueda(listOf(
                        t.nombreComercio, t.descripcion, t.etiquetas, t.banco,
                        categoriasPorId[t.categoriaId]?.nombre.orEmpty(), t.monto.toString()
                    ).joinToString(" "))
                    palabras.all { texto.contains(it) }
                }
            }
            .map { t -> TransaccionConCategoria(t, categoriasPorId[t.categoriaId]) }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun buscar(texto: String) { filtros.value = filtros.value.copy(consulta = texto) }
    fun filtrarDireccion(direccion: String?) { filtros.value = filtros.value.copy(direccion = direccion) }
    fun filtrarTransferencias() { filtros.value = filtros.value.copy(soloTransferencias = !filtros.value.soloTransferencias) }
    fun filtrarCategoria(id: Int?) { filtros.value = filtros.value.copy(categoriaId = id) }
    fun filtrarFuente(fuente: String?) { filtros.value = filtros.value.copy(fuente = fuente) }
    fun filtrarDesde(millis: Long?) { filtros.value = filtros.value.copy(desde = millis) }
    fun filtrarHasta(millis: Long?) { filtros.value = filtros.value.copy(hasta = millis) }
    fun limpiarFiltros() { filtros.value = FiltrosTransacciones() }

    private fun normalizarBusqueda(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

}
