package com.usuario.finanzas.ui.screens.servicios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.data.repository.ServicioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiciosViewModel @Inject constructor(
    private val servicioRepository: ServicioRepository
) : ViewModel() {

    private val _servicios = MutableStateFlow<List<Servicio>>(emptyList())
    val servicios: StateFlow<List<Servicio>> = _servicios.asStateFlow()

    init {
        viewModelScope.launch {
            // Actualizar estados primero, luego observar — igual que el Dashboard,
            // para que ambas pantallas lean exactamente el mismo estado de la BD.
            servicioRepository.actualizarEstados()
            servicioRepository.observarServicios().collect { lista ->
                _servicios.value = lista
            }
        }
    }

    fun crear(nombre: String, montoEstimado: Double, diaCorte: Int, diaLimite: Int) {
        viewModelScope.launch {
            servicioRepository.insertar(
                Servicio(
                    nombre = nombre,
                    montoEstimado = montoEstimado,
                    diaCorte = diaCorte,
                    diaLimite = diaLimite
                )
            )
            // Recalcular estados tras insertar para que el estado inicial sea correcto
            servicioRepository.actualizarEstados()
        }
    }

    fun actualizar(servicio: Servicio) {
        viewModelScope.launch {
            servicioRepository.actualizar(servicio)
            servicioRepository.actualizarEstados()
        }
    }

    fun eliminar(servicio: Servicio) {
        viewModelScope.launch { servicioRepository.eliminar(servicio) }
    }
}
