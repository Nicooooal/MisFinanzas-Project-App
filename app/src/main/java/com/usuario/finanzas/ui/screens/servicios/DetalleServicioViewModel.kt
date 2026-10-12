package com.usuario.finanzas.ui.screens.servicios

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.HistorialPago
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.data.repository.ServicioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetalleServicioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val servicioRepository: ServicioRepository
) : ViewModel() {

    private val servicioId: Int = checkNotNull(savedStateHandle["id"])

    val servicio: StateFlow<Servicio?> = servicioRepository.observarPorId(servicioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val historial: StateFlow<List<HistorialPago>> =
        servicioRepository.observarHistorial(servicioId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun actualizar(nombre: String, montoEstimado: Double, diaCorte: Int, diaLimite: Int) {
        val actual = servicio.value ?: return
        viewModelScope.launch {
            servicioRepository.actualizar(
                actual.copy(
                    nombre = nombre,
                    montoEstimado = montoEstimado,
                    diaCorte = diaCorte,
                    diaLimite = diaLimite
                )
            )
            servicioRepository.actualizarEstados()
        }
    }

    /** "Marcar como pagado manualmente": crea HistorialPago con la fecha actual. */
    fun marcarComoPagado(monto: Double) {
        val actual = servicio.value ?: return
        viewModelScope.launch {
            servicioRepository.registrarPago(actual, monto)
        }
    }

    fun eliminar(onFin: () -> Unit) {
        val actual = servicio.value ?: return
        viewModelScope.launch {
            servicioRepository.eliminar(actual)
            onFin()
        }
    }
}
