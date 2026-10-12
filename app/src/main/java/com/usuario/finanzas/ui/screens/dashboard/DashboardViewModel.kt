package com.usuario.finanzas.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import com.usuario.finanzas.data.repository.PendientesRepository
import com.usuario.finanzas.data.repository.ServicioRepository
import com.usuario.finanzas.data.repository.TransaccionRepository
import com.usuario.finanzas.domain.model.GastoPorCategoria
import com.usuario.finanzas.domain.model.TotalMensual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicioConDias(val servicio: Servicio, val diasRestantes: Long)

/** Datos del mes seleccionado en el gráfico de barras del Dashboard. */
data class MesSeleccionado(
    val anio: Int,
    val mes: Int,
    val nombre: String,
    val total: Double,
    val gastosPorCategoria: List<GastoPorCategoria>
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val servicioRepository: ServicioRepository,
    pendientesRepository: PendientesRepository
) : ViewModel() {

    val totalMes: StateFlow<Double> = transaccionRepository.totalMesActual()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val entradasMes: StateFlow<Double> = transaccionRepository.entradasMesActual()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val gastosPorCategoria: StateFlow<List<GastoPorCategoria>> =
        transaccionRepository.gastosPorCategoriaMesActual()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendiente: StateFlow<TransaccionPendiente?> = pendientesRepository.pendientes
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _proximosPagos = MutableStateFlow<List<ServicioConDias>>(emptyList())
    val proximosPagos: StateFlow<List<ServicioConDias>> = _proximosPagos

    private val _historialMensual = MutableStateFlow<List<TotalMensual>>(emptyList())
    val historialMensual: StateFlow<List<TotalMensual>> = _historialMensual

    /** Mes seleccionado en el gráfico — null = ninguno seleccionado. */
    private val _mesSeleccionado = MutableStateFlow<MesSeleccionado?>(null)
    val mesSeleccionado: StateFlow<MesSeleccionado?> = _mesSeleccionado

    init {
        viewModelScope.launch {
            servicioRepository.actualizarEstados()
            servicioRepository.observarServicios().collect { servicios ->
                _proximosPagos.value = servicios
                    .map { ServicioConDias(it, servicioRepository.diasHastaLimite(it)) }
                    .sortedBy { it.diasRestantes }
            }
        }
        viewModelScope.launch {
            combine(transaccionRepository.observarCambios(), transaccionRepository.observarRangoActual()) { _, rango -> rango }.collect {
                _historialMensual.value = transaccionRepository.totalesUltimosMeses(6)
                _mesSeleccionado.value?.let { mes ->
                    _mesSeleccionado.value = mes.copy(
                        total = transaccionRepository.totalDelMes(mes.anio, mes.mes),
                        gastosPorCategoria = transaccionRepository.gastosPorCategoriaDelMes(mes.anio, mes.mes)
                    )
                }
            }
        }
    }

    /** Carga y muestra los detalles del mes tocado en el gráfico. */
    fun seleccionarMes(totalMensual: TotalMensual) {
        // Si se toca el mismo mes, lo deselecciona (toggle)
        if (_mesSeleccionado.value?.anio == totalMensual.anio &&
            _mesSeleccionado.value?.mes == totalMensual.mes) {
            _mesSeleccionado.value = null
            return
        }
        viewModelScope.launch {
            val gastos = transaccionRepository.gastosPorCategoriaDelMes(
                totalMensual.anio, totalMensual.mes
            )
            _mesSeleccionado.value = MesSeleccionado(
                anio = totalMensual.anio,
                mes = totalMensual.mes,
                nombre = totalMensual.nombreMes(),
                total = totalMensual.total,
                gastosPorCategoria = gastos
            )
        }
    }

    fun deseleccionarMes() {
        _mesSeleccionado.value = null
    }
}
