package com.usuario.finanzas.ui.screens.servicios

import com.usuario.finanzas.ui.i18n.tr

import com.usuario.finanzas.ui.util.FormatoFinanciero

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.ui.navigation.BarraInferior
import com.usuario.finanzas.ui.screens.pendientes.PendientesViewModel
import com.usuario.finanzas.ui.navigation.Rutas
import com.usuario.finanzas.ui.theme.Naranja
import com.usuario.finanzas.ui.theme.Rojo
import com.usuario.finanzas.ui.theme.Verde
import com.usuario.finanzas.ui.util.formatearMonto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaServicios(
    navController: NavHostController,
    viewModel: ServiciosViewModel = hiltViewModel()
) {
    val servicios by viewModel.servicios.collectAsStateWithLifecycle()
    val pendientesVM: PendientesViewModel = hiltViewModel()
    val numPendientes by pendientesVM.pendientes.collectAsStateWithLifecycle()
    var mostrarDialogoCrear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tr("Servicios")) }) },
        bottomBar = { BarraInferior(navController, Rutas.SERVICIOS, numPendientes.size) },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarDialogoCrear = true }) {
                Icon(Icons.Filled.Add, contentDescription = tr("Agregar servicio"))
            }
        }
    ) { padding ->
        if (servicios.isEmpty()) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(
                    tr("No tienes servicios registrados.\nToca + para agregar el primero (luz, agua, internet…)."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(servicios, key = { it.id }) { servicio ->
                TarjetaServicio(servicio) {
                    navController.navigate(Rutas.detalleServicio(servicio.id))
                }
            }
        }
    }

    if (mostrarDialogoCrear) {
        DialogoServicio(
            titulo = tr("Nuevo servicio"),
            onGuardar = { nombre, monto, corte, limite ->
                viewModel.crear(nombre, monto, corte, limite)
                mostrarDialogoCrear = false
            },
            onCerrar = { mostrarDialogoCrear = false }
        )
    }
}

@Composable
fun TarjetaServicio(servicio: Servicio, onClick: () -> Unit) {
    val colorEstado = when (servicio.estado) {
        Servicio.ESTADO_PAGADO -> Verde
        Servicio.ESTADO_VENCIDO -> Rojo
        else -> Naranja
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(servicio.nombre, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatearMonto(servicio.montoEstimado),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Corte: día {0} · Límite: día {1}", servicio.diaCorte, servicio.diaLimite),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Badge de estado
            Surface(color = colorEstado, shape = MaterialTheme.shapes.small) {
                Text(
                    tr(servicio.estado.replaceFirstChar { it.uppercase() }),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Diálogo reutilizable para crear/editar un servicio. */
@Composable
fun DialogoServicio(
    titulo: String,
    inicial: Servicio? = null,
    onGuardar: (nombre: String, monto: Double, diaCorte: Int, diaLimite: Int) -> Unit,
    onCerrar: () -> Unit
) {
    var nombre by remember { mutableStateOf(inicial?.nombre ?: "") }
    var monto by remember { mutableStateOf(inicial?.montoEstimado?.toLong()?.toString() ?: "") }
    var diaCorte by remember { mutableStateOf(inicial?.diaCorte?.toString() ?: "") }
    var diaLimite by remember { mutableStateOf(inicial?.diaLimite?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(tr("Nombre (ej. Luz, Internet)")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it.filter(Char::isDigit) },
                    label = { Text(tr("Monto estimado ({0})", FormatoFinanciero.moneda)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = diaCorte,
                    onValueChange = { diaCorte = it.filter(Char::isDigit) },
                    label = { Text(tr("Día de corte (1-31)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = diaLimite,
                    onValueChange = { diaLimite = it.filter(Char::isDigit) },
                    label = { Text(tr("Día límite de pago (1-31)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val montoNum = monto.toDoubleOrNull()
                val corteNum = diaCorte.toIntOrNull()
                val limiteNum = diaLimite.toIntOrNull()
                when {
                    nombre.isBlank() -> error = tr("Escribe un nombre")
                    montoNum == null || montoNum <= 0 -> error = tr("Monto inválido")
                    corteNum == null || corteNum !in 1..31 -> error = tr("Día de corte inválido (1-31)")
                    limiteNum == null || limiteNum !in 1..31 -> error = tr("Día límite inválido (1-31)")
                    else -> onGuardar(nombre.trim(), montoNum, corteNum, limiteNum)
                }
            }) { Text(tr("Guardar")) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(tr("Cancelar")) } }
    )
}
