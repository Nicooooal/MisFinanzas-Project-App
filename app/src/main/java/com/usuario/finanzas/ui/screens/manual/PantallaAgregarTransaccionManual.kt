package com.usuario.finanzas.ui.screens.manual

import com.usuario.finanzas.ui.i18n.tr

import com.usuario.finanzas.ui.util.FormatoFinanciero

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.usuario.finanzas.ui.components.SelectorCategoria
import com.usuario.finanzas.ui.components.SelectorMovimiento
import com.usuario.finanzas.ui.screens.transacciones.DialogoFecha
import com.usuario.finanzas.ui.util.formatearFecha

/**
 * Formulario de transacción manual: monto, comercio, categoría, fecha y
 * comprobante opcional (cámara o galería; la imagen queda en almacenamiento
 * interno privado de la app, no en la galería pública).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAgregarTransaccionManual(
    navController: NavHostController,
    viewModel: AgregarManualViewModel = hiltViewModel()
) {
    var mostrarPickerFecha by remember { mutableStateOf(false) }

    // Foto con la cámara (TakePicturePreview no requiere permiso de cámara
    // porque delega en la app de cámara del sistema)
    val lanzadorCamara = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let(viewModel::guardarFotoCamara) }

    val lanzadorGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::guardarFotoGaleria) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Nueva transacción")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SelectorMovimiento(viewModel.clase, viewModel::seleccionarClase)
            OutlinedTextField(
                value = viewModel.monto,
                onValueChange = { viewModel.monto = it },
                label = { Text(tr("Monto ({0})", FormatoFinanciero.moneda)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.comercio,
                onValueChange = { viewModel.comercio = it },
                label = { Text(tr("Comercio o persona")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.descripcion,
                onValueChange = { viewModel.descripcion = it },
                label = { Text(tr("Descripción (opcional)")) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.etiquetas,
                onValueChange = { viewModel.etiquetas = it },
                label = { Text(tr("Etiquetas (opcional)")) },
                supportingText = { Text(tr("Separa con comas, hasta 10 etiquetas de 30 caracteres.")) },
                modifier = Modifier.fillMaxWidth()
            )

            SelectorCategoria(
                categorias = viewModel.categorias,
                seleccionada = viewModel.categoriaSeleccionada,
                onSeleccionar = { viewModel.categoriaSeleccionada = it }
            )

            // Fecha con date picker
            OutlinedButton(
                onClick = { mostrarPickerFecha = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Text(tr("  Fecha: {0}", formatearFecha(viewModel.fechaMillis)))
            }

            // Comprobante opcional
            Text(tr("Comprobante (opcional)"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { lanzadorCamara.launch(null) }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text(tr("  Cámara"))
                }
                OutlinedButton(onClick = { lanzadorGaleria.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(tr("  Galería"))
                }
            }

            viewModel.comprobantePath?.let { ruta ->
                val bitmap = remember(ruta) { BitmapFactory.decodeFile(ruta) }
                bitmap?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = tr("Comprobante"),
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                            OutlinedButton(
                                onClick = { viewModel.quitarFoto() },
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(tr("  Quitar foto"))
                            }
                        }
                    }
                }
            }

            viewModel.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.guardar { navController.popBackStack() } },
                enabled = viewModel.esValido,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (viewModel.guardando) tr("Guardando…") else tr("Guardar transacción")) }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (mostrarPickerFecha) {
        DialogoFecha(
            inicial = viewModel.fechaMillis,
            onConfirmar = { viewModel.fechaMillis = it },
            onCerrar = { mostrarPickerFecha = false }
        )
    }
}
