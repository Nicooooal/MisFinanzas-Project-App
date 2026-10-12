package com.usuario.finanzas.ui.screens.onboarding

import com.usuario.finanzas.ui.i18n.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.repository.CategoriaRepository
import com.usuario.finanzas.data.preferences.PreferenciasApp
import com.usuario.finanzas.data.local.entities.ConfiguracionFinanciera
import com.usuario.finanzas.data.repository.FinanzasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repository: FinanzasRepository, private val categoriasRepository: CategoriaRepository, private val db: FinanzasDatabase, val preferencias: PreferenciasApp) : ViewModel() {
    val categorias = categoriasRepository.observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val configuracion: StateFlow<ConfiguracionFinanciera?> = repository.observarConfiguracion()
        .map<ConfiguracionFinanciera, ConfiguracionFinanciera?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _ocupado = MutableStateFlow(false)
    val ocupado = _ocupado.asStateFlow()
    fun crearCategoria(categoria: Categoria, listo: () -> Unit) {
        if (_ocupado.value) return
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try { categoriasRepository.guardar(categoria); listo() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message?.let { tr(it) } ?: tr("No se pudo guardar la categoría.") }
            finally { _ocupado.value = false }
        }
    }
    fun guardar(configuracion: ConfiguracionFinanciera, seleccionadas: List<Int>? = null, listo: () -> Unit) {
        if (_ocupado.value) return
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                db.withTransaction {
                    repository.guardarConfiguracion(configuracion.copy(onboardingCompleto = true))
                    seleccionadas?.let { db.categoriaDao().aplicarSeleccionInicial(it) }
                }
                listo()
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message?.let { tr(it) } ?: tr("No se pudo guardar la configuración.") }
            finally { _ocupado.value = false }
        }
    }
}
