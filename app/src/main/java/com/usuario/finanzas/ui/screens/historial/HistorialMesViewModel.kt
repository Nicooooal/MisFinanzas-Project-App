package com.usuario.finanzas.ui.screens.historial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.repository.TransaccionRepository
import com.usuario.finanzas.domain.model.GastoPorCategoria
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class HistorialMesUiState(
    val cargando: Boolean = true,
    val anio: Int = 0,
    val mes: Int = 0,
    val nombreMes: String = "",
    val total: Double = 0.0,
    val entradas: Double = 0.0,
    val transacciones: List<Transaccion> = emptyList(),
    val gastosPorCategoria: List<GastoPorCategoria> = emptyList()
)

@HiltViewModel
class HistorialMesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transaccionRepository: TransaccionRepository
) : ViewModel() {

    private val anio: Int = checkNotNull(savedStateHandle["anio"])
    private val mes: Int = checkNotNull(savedStateHandle["mes"])

    private val _uiState = MutableStateFlow(HistorialMesUiState())
    val uiState: StateFlow<HistorialMesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val nombreMes = Month.of(mes)
                .getDisplayName(TextStyle.FULL, Locale("es", "CO"))
                .replaceFirstChar { it.uppercase() }

            transaccionRepository.observarTodas().collectLatest {
                val transacciones = transaccionRepository.transaccionesDelMes(anio, mes)
                val gastos = transaccionRepository.gastosPorCategoriaDelMes(anio, mes)
                val total = transaccionRepository.totalDelMes(anio, mes)

                _uiState.value = HistorialMesUiState(
                    cargando = false,
                    anio = anio,
                    mes = mes,
                    nombreMes = nombreMes,
                    total = total,
                    entradas = transaccionRepository.entradasDelMes(anio, mes),
                    transacciones = transacciones,
                    gastosPorCategoria = gastos
                )
            }
        }
    }
}
