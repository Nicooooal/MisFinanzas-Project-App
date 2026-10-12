package com.usuario.finanzas.ui.screens.pendientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import com.usuario.finanzas.data.repository.PendientesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendientesViewModel @Inject constructor(
    private val pendientesRepository: PendientesRepository
) : ViewModel() {

    val pendientes: StateFlow<List<TransaccionPendiente>> = pendientesRepository.pendientes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun descartar(id: Int) {
        viewModelScope.launch { pendientesRepository.eliminar(id) }
    }

    fun descartarTodas() {
        viewModelScope.launch {
            pendientes.value.forEach { pendientesRepository.eliminar(it.id) }
        }
    }
}
