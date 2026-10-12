package com.usuario.finanzas.ui.screens.servicios

import com.usuario.finanzas.ui.i18n.tr

import com.usuario.finanzas.ui.util.FormatoFinanciero

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.ui.theme.Naranja
import com.usuario.finanzas.ui.theme.Rojo
import com.usuario.finanzas.ui.theme.Verde
import com.usuario.finanzas.ui.util.formatearFechaHora
import com.usuario.finanzas.ui.util.formatearMonto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleServicio(
    navController: NavHostController,
    viewModel: DetalleServicioViewModel = hiltViewModel()
) {
    val servicio by viewModel.servicio.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()

    var editando by remember { mutableStateOf(false) }
    var confirmandoEliminar by remember { mutableStateOf(false) }
    var marcandoPagado by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(servicio?.nombre ?: tr("Servicio")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver"))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmandoEliminar = true }) {
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
        val s = servicio ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val colorEstado = when (s.estado) {
                Servicio.ESTADO_PAGADO -> Verde
                Servicio.ESTADO_VENCIDO -> Rojo
                else -> Naranja
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        formatearMonto(s.montoEstimado),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Estado: {0}", tr(s.estado.replaceFirstChar { it.uppercase() })),
                        color = colorEstado,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Corte: día {0} · Límite de pago: día {1}", s.diaCorte, s.diaLimite),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { marcandoPagado = true },
                enabled = s.estado != Servicio.ESTADO_PAGADO,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Text(tr("  Marcar como pagado manualmente"))
            }

            Button(onClick = { editando = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Editar servicio"))
            }

            // ---------- Historial de pagos ----------
            Text(tr("Historial de pagos"), style = MaterialTheme.typography.titleMedium)
            if (historial.isEmpty()) {
                Text(
                    tr("Sin pagos registrados todavía"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        historial.forEachIndexed { indice, pago ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        formatearMonto(pago.monto),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Verde
                                    )
                                    Text(
                                        formatearFechaHora(pago.fechaPago),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (pago.comprobantePath != null) {
                                    Text(
                                        tr("Con comprobante"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (indice < historial.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    val s = servicio
    if (editando && s != null) {
        DialogoServicio(
            titulo = tr("Editar servicio"),
            inicial = s,
            onGuardar = { nombre, monto, corte, limite ->
                viewModel.actualizar(nombre, monto, corte, limite)
                editando = false
            },
            onCerrar = { editando = false }
        )
    }

    if (marcandoPagado && s != null) {
        DialogoMarcarPagado(
            montoSugerido = s.montoEstimado,
            onConfirmar = { monto ->
                viewModel.marcarComoPagado(monto)
                marcandoPagado = false
            },
            onCerrar = { marcandoPagado = false }
        )
    }

    if (confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = { confirmandoEliminar = false },
            title = { Text(tr("Eliminar servicio")) },
            text = { Text(tr("Se eliminará el servicio y todo su historial de pagos. ¿Continuar?")) },
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

@Composable
private fun DialogoMarcarPagado(
    montoSugerido: Double,
    onConfirmar: (Double) -> Unit,
    onCerrar: () -> Unit
) {
    var monto by remember { mutableStateOf(montoSugerido.toLong().toString()) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(tr("Registrar pago")) },
        text = {
            OutlinedTextField(
                value = monto,
                onValueChange = { monto = it.filter(Char::isDigit) },
                label = { Text(tr("Monto pagado ({0})", FormatoFinanciero.moneda)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                monto.toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirmar)
            }) { Text(tr("Registrar")) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(tr("Cancelar")) } }
    )
}
