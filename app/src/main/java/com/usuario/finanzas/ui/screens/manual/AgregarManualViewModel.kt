package com.usuario.finanzas.ui.screens.manual

import com.usuario.finanzas.ui.i18n.tr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.repository.CategoriaRepository
import com.usuario.finanzas.data.repository.TransaccionRepository
import com.usuario.finanzas.ui.util.Comprobantes
import com.usuario.finanzas.ui.util.leerMonto
import com.usuario.finanzas.domain.model.ClaseMovimiento
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AgregarManualViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transaccionRepository: TransaccionRepository,
    categoriaRepository: CategoriaRepository
) : ViewModel() {

    var monto by mutableStateOf("")
    var clase by mutableStateOf(ClaseMovimiento.COMPRA)
        private set
    var guardando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var comercio by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var etiquetas by mutableStateOf("")
    var fechaMillis by mutableStateOf(System.currentTimeMillis())
    var comprobantePath by mutableStateOf<String?>(null)
        private set

    private var todasLasCategorias by mutableStateOf<List<Categoria>>(emptyList())
    val categorias: List<Categoria> get() = todasLasCategorias.filter {
        !it.archivada && (it.direccion == "ambas" || it.direccion == clase.direccion)
    }
    var categoriaSeleccionada by mutableStateOf<Categoria?>(null)

    init {
        viewModelScope.launch {
            categoriaRepository.observarTodas().collect { lista ->
                todasLasCategorias = lista
                ajustarCategoria()
            }
        }
    }

    /** Guarda la foto tomada con la cámara en almacenamiento interno privado. */
    fun guardarFotoCamara(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val ruta = Comprobantes.guardarBitmap(context, bitmap)
            withContext(Dispatchers.Main) { comprobantePath = ruta }
        }
    }

    /** Copia la imagen elegida de la galería al almacenamiento interno privado. */
    fun guardarFotoGaleria(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ruta = Comprobantes.copiarDesdeUri(context, uri)
            withContext(Dispatchers.Main) { comprobantePath = ruta }
        }
    }

    fun quitarFoto() {
        Comprobantes.eliminar(comprobantePath)
        comprobantePath = null
    }

    val esValido: Boolean
        get() = leerMonto(monto) != null && comercio.isNotBlank() &&
            categorias.any { it.id == categoriaSeleccionada?.id } && fechaMillis <= System.currentTimeMillis() && !guardando

    fun seleccionarClase(nueva: ClaseMovimiento) {
        clase = nueva
        ajustarCategoria()
    }

    private fun ajustarCategoria() {
        categoriaSeleccionada = categorias.find { it.id == categoriaSeleccionada?.id }
            ?: categorias.find { it.nombre == "Otros" } ?: categorias.firstOrNull()
    }

    fun guardar(onFin: () -> Unit) {
        val categoria = categoriaSeleccionada ?: return
        if (!esValido) return
        val montoNum = leerMonto(monto) ?: return
        val nueva = Transaccion(
            monto = montoNum, categoriaId = categoria.id, descripcion = descripcion,
            nombreComercio = comercio.trim(), fecha = fechaMillis,
            fuente = Transaccion.FUENTE_MANUAL, comprobantePath = comprobantePath,
            direccion = clase.direccion, tipo = clase.tipo, etiquetas = etiquetas
        )
        guardando = true
        error = null
        viewModelScope.launch {
            try {
                transaccionRepository.insertar(nueva)
                onFin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = tr("No se pudo guardar el movimiento. Inténtalo de nuevo.")
            } finally {
                guardando = false
            }
        }
    }
}
