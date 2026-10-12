package com.usuario.finanzas.ui.screens.planes

import com.usuario.finanzas.ui.i18n.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.dao.TransaccionDao
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.data.repository.CategoriaRepository
import com.usuario.finanzas.data.repository.FinanzasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanesViewModel @Inject constructor(
    private val repository: FinanzasRepository,
    categoriasRepository: CategoriaRepository,
    transaccionDao: TransaccionDao
) : ViewModel() {
    val configuracion = repository.observarConfiguracion().stateIn(viewModelScope, SharingStarted.Eagerly, ConfiguracionFinanciera())
    val presupuestos = repository.observarPresupuestos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val metas = repository.observarMetas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val aportes = repository.observarAportes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurrentes = repository.observarRecurrentes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ocurrencias = repository.observarOcurrencias().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categorias = categoriasRepository.observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transacciones = transaccionDao.observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _ocupado = MutableStateFlow(false)
    val ocupado = _ocupado.asStateFlow()
    fun limpiarError() { _error.value = null }
    fun guardar(presupuesto: Presupuesto, listo: () -> Unit) = ejecutar(listo) { repository.guardarPresupuesto(presupuesto) }
    fun guardar(meta: Meta, listo: () -> Unit) = ejecutar(listo) { repository.guardarMeta(meta) }
    fun guardar(recurrente: Recurrente, listo: () -> Unit) = ejecutar(listo) { repository.guardarRecurrente(recurrente) }
    fun aportar(id: Long, monto: Long, nota: String, listo: () -> Unit) = ejecutar(listo) { repository.registrarAporte(id, monto, nota) }
    fun registrar(id: Long, fecha: Long, transaccion: Int?, listo: () -> Unit) = ejecutar(listo) { repository.registrarOcurrencia(id, fecha, transaccion) }
    private fun ejecutar(listo: () -> Unit, accion: suspend () -> Unit) {
        if (_ocupado.value) return
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try { accion(); listo() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message?.let { tr(it) } ?: tr("No se pudo guardar. Inténtalo de nuevo.") }
            finally { _ocupado.value = false }
        }
    }
}
