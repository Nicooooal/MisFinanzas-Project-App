package com.usuario.finanzas.ui.screens.bloqueo

import androidx.lifecycle.ViewModel
import com.usuario.finanzas.data.local.security.AlmacenSeguro
import com.usuario.finanzas.data.preferences.PreferenciasApp
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BloqueoViewModel @Inject constructor(
    private val almacenSeguro: AlmacenSeguro,
    preferenciasApp: PreferenciasApp
) : ViewModel() {
    val preferencias = preferenciasApp.preferencias

    /** false la primera vez que se abre la app: hay que configurar el PIN. */
    fun tienePin(): Boolean = almacenSeguro.tienePin()

    fun guardarPin(pin: String) = almacenSeguro.guardarPin(pin)

    fun validarPin(pin: String): Boolean = almacenSeguro.validarPin(pin)

    fun segundosBloqueoPin(): Long = almacenSeguro.segundosBloqueoPin()
}
