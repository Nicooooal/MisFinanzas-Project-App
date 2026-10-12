package com.usuario.finanzas.ui.screens.transacciones

import com.usuario.finanzas.ui.i18n.tr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.repository.CategoriaRepository
import com.usuario.finanzas.data.repository.TransaccionRepository
import com.usuario.finanzas.ui.util.Comprobantes
import dagger.hilt.android.lifecycle.HiltViewModel
import com.usuario.finanzas.domain.model.ClaseMovimiento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetalleTransaccionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transaccionRepository: TransaccionRepository,
    categoriaRepository: CategoriaRepository
) : ViewModel() {

    private val transaccionId: Int = checkNotNull(savedStateHandle["id"])

    val transaccion: StateFlow<Transaccion?> =
        transaccionRepository.observarPorId(transaccionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categorias: StateFlow<List<Categoria>> = categoriaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ocupado = MutableStateFlow(false)
    val ocupado = _ocupado.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Guarda la clasificación junto a la categoría y descripción. */
    fun guardarCambios(categoriaId: Int, descripcion: String, clase: ClaseMovimiento, etiquetas: String, onFin: () -> Unit) {
        val actual = transaccion.value ?: return
        if (_ocupado.value) return
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val editable = actual.servicioIdRelacionado == null
                transaccionRepository.actualizar(actual.copy(
                    categoriaId = categoriaId,
                    descripcion = descripcion,
                    etiquetas = etiquetas,
                    tipo = if (editable) clase.tipo else actual.tipo,
                    direccion = if (editable) clase.direccion else actual.direccion
                ))
                onFin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = tr("No se pudieron guardar los cambios. Intenta nuevamente.")
            } finally {
                _ocupado.value = false
            }
        }
    }

    fun eliminar(onFin: () -> Unit) {
        val actual = transaccion.value ?: return
        if (_ocupado.value) return
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                transaccionRepository.eliminar(actual)
                Comprobantes.eliminar(actual.comprobantePath)
                onFin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = tr("No se pudo completar la eliminación. Revisa el movimiento e intenta nuevamente.")
            } finally {
                _ocupado.value = false
            }
        }
    }
}
