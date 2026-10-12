package com.usuario.finanzas.ui.screens.categorias

import com.usuario.finanzas.ui.i18n.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.repository.CategoriaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriasViewModel @Inject constructor(private val repository: CategoriaRepository) : ViewModel() {
    val categorias = repository.observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _ocupado = MutableStateFlow(false)
    val ocupado = _ocupado.asStateFlow()
    fun limpiarError() { _error.value = null }
    fun guardar(categoria: Categoria, listo: () -> Unit = {}) {
        if (_ocupado.value) return
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try { repository.guardar(categoria); listo() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message?.let { tr(it) } ?: tr("No se pudo guardar la categoría.") }
            finally { _ocupado.value = false }
        }
    }
}
