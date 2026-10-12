package com.usuario.finanzas.ui.screens.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.data.preferences.PreferenciasApp
import com.usuario.finanzas.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DatosResumen(val transacciones: List<Transaccion> = emptyList(), val categorias: List<Categoria> = emptyList(), val configuracion: ConfiguracionFinanciera = ConfiguracionFinanciera(), val pendientes: Int = 0, val servicios: List<Servicio> = emptyList(), val cargado: Boolean = false)

@HiltViewModel
class ResumenViewModel @Inject constructor(transacciones: TransaccionRepository, categorias: CategoriaRepository, finanzas: FinanzasRepository, pendientes: PendientesRepository, servicios: ServicioRepository, private val prefs: PreferenciasApp) : ViewModel() {
    val datos = combine(transacciones.observarTodas(), categorias.observarTodas(), finanzas.observarConfiguracion(), pendientes.conteo, servicios.observarServicios()) { t, c, f, p, s -> DatosResumen(t,c,f,p,s,true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatosResumen())
    val preferencias = prefs.preferencias
    fun privacidad() { prefs.ocultar(!preferencias.value.ocultar) }
}
