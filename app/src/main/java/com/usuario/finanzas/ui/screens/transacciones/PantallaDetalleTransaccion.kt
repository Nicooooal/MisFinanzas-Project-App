package com.usuario.finanzas.ui.screens.transacciones

import com.usuario.finanzas.ui.i18n.tr

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.ui.components.SelectorMovimiento
import com.usuario.finanzas.ui.components.SelectorCategoria
import com.usuario.finanzas.ui.util.formatearFechaHora
import com.usuario.finanzas.ui.util.formatearMonto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleTransaccion(
    navController: NavHostController,
    viewModel: DetalleTransaccionViewModel = hiltViewModel()
) {
    val transaccion by viewModel.transaccion.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val ocupado by viewModel.ocupado.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var clase by remember(transaccion?.id) { mutableStateOf(ClaseMovimiento.desde(transaccion?.tipo.orEmpty(), transaccion?.direccion.orEmpty())) }

    var categoriaSeleccionada by remember(transaccion?.id) { mutableStateOf<Categoria?>(null) }
    var descripcion by remember(transaccion?.id) { mutableStateOf(transaccion?.descripcion.orEmpty()) }
    var etiquetas by remember(transaccion?.id) { mutableStateOf(transaccion?.etiquetas.orEmpty()) }
    var confirmandoEliminar by remember { mutableStateOf(false) }
    var categoriaInicializada by remember(transaccion?.id) { mutableStateOf(false) }
    val categoriasElegibles = categorias.filter {
        (it.direccion == "ambas" || it.direccion == clase.direccion) &&
            (!it.archivada || (it.id == transaccion?.categoriaId && clase.direccion == transaccion?.direccion))
    }
    LaunchedEffect(transaccion?.id, categoriasElegibles) {
        if (!categoriaInicializada && transaccion != null && categorias.isNotEmpty()) {
            categoriaSeleccionada = categoriasElegibles.find { it.id == transaccion?.categoriaId }
            categoriaInicializada = true
        }
        categoriaSeleccionada = categoriasElegibles.find { it.id == categoriaSeleccionada?.id }
            ?: categoriasElegibles.find { it.nombre == "Otros" } ?: categoriasElegibles.firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Detalle de transacción")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver"))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmandoEliminar = true }, enabled = !ocupado) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = tr("Eliminar"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        val t = transaccion ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        (if (t.direccion == "entrada") "+" else "−") + formatearMonto(t.monto),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (t.tipo == "transferencia_propia") MaterialTheme.colorScheme.onSurfaceVariant
                            else if (t.direccion == "entrada") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(t.nombreComercio, style = MaterialTheme.typography.titleLarge)
                    Text(tr(ClaseMovimiento.desde(t.tipo, t.direccion).etiqueta))
                    if (t.banco.isNotBlank()) Text(t.banco)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatearFechaHora(t.fecha),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(tr("Fuente: "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (t.fuente == Transaccion.FUENTE_AUTOMATICO) tr("Automática") else tr("Manual"),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    t.servicioIdRelacionado?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Vinculada a un servicio (pago registrado)"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Comprobante (si existe)
            t.comprobantePath?.let { ruta ->
                val bitmap = remember(ruta) { BitmapFactory.decodeFile(ruta) }
                bitmap?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = tr("Comprobante"),
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }

            // Campos editables
            if (t.servicioIdRelacionado == null) {
                SelectorMovimiento(clase = clase, onSeleccionar = { clase = it })
            } else {
                Text(tr("La dirección y el tipo se conservan porque este movimiento está vinculado al pago de un servicio."),
                    style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            SelectorCategoria(
                categorias = categoriasElegibles,
                seleccionada = categoriaSeleccionada,
                onSeleccionar = { categoriaSeleccionada = it }
            )
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text(tr("Descripción")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = etiquetas,
                onValueChange = { etiquetas = it },
                label = { Text(tr("Etiquetas (opcional)")) },
                supportingText = { Text(tr("Separa con comas, hasta 10 etiquetas de 30 caracteres.")) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    categoriaSeleccionada?.let { cat ->
                        viewModel.guardarCambios(cat.id, descripcion, clase, etiquetas) {
                            navController.popBackStack()
                        }
                    }
                },
                enabled = categoriasElegibles.any { it.id == categoriaSeleccionada?.id } && !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Guardar cambios")) }
        }
    }

    if (confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = { confirmandoEliminar = false },
            title = { Text(tr("Eliminar transacción")) },
            text = { Text(tr("¿Seguro que quieres eliminar esta transacción? Esta acción no se puede deshacer.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoEliminar = false
                    viewModel.eliminar { navController.popBackStack() }
                }) { Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoEliminar = false }) { Text(tr("Cancelar")) }
            }
        )
    }
}
