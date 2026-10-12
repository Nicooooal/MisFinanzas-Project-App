package com.usuario.finanzas.ui.screens.confirmacion

import com.usuario.finanzas.ui.i18n.tr

import com.usuario.finanzas.ui.util.FormatoFinanciero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.usuario.finanzas.ui.components.SelectorCategoria
import com.usuario.finanzas.ui.components.SelectorMovimiento
import com.usuario.finanzas.ui.theme.Verde
import com.usuario.finanzas.ui.util.leerMonto
import com.usuario.finanzas.ui.util.formatearFechaHora
import com.usuario.finanzas.ui.util.formatearMonto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConfirmacionTransaccion(
    navController: NavHostController,
    viewModel: ConfirmacionViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Confirmar transacción")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver"))
                    }
                }
            )
        }
    ) { padding ->

        // Mientras carga los datos de la pendiente desde BD
        if (viewModel.cargando) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // Si la pendiente no existe (ya fue procesada o id inválido)
        if (!viewModel.disponible) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp)
            ) {
                Text(tr("Esta transacción ya fue revisada o no existe."))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) { Text(tr("Volver")) }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Resumen de lo detectado
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${tr(viewModel.clase.etiqueta)} · ${formatearFechaHora(viewModel.fecha)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatearMonto(leerMonto(viewModel.monto) ?: 0.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (viewModel.clase.direccion == "entrada") Verde else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Campos editables
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
                label = { Text(tr("Descripción")) },
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

            viewModel.servicioDisponible?.let { servicio ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.vincularServicio,
                            onCheckedChange = { viewModel.vincularServicio = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(tr("Vincular a servicio: {0}", servicio.nombre))
                            Text(
                                tr("Se registrará el pago y el servicio quedará como pagado"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(tr("Mensaje original"), style = MaterialTheme.typography.labelLarge)
            Text(viewModel.textoOriginal, style = MaterialTheme.typography.bodySmall)
            viewModel.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.guardar { navController.popBackStack() } },
                enabled = viewModel.esValido,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (viewModel.guardando) tr("Guardando…") else tr("Guardar")) }

            OutlinedButton(
                onClick = { viewModel.descartar { navController.popBackStack() } },
                enabled = !viewModel.guardando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Descartar"), color = MaterialTheme.colorScheme.error) }
        }
    }
}
