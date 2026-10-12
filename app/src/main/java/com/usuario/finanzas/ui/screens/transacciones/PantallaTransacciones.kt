package com.usuario.finanzas.ui.screens.transacciones

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.ui.navigation.BarraInferior
import com.usuario.finanzas.ui.screens.pendientes.PendientesViewModel
import com.usuario.finanzas.ui.navigation.Rutas
import com.usuario.finanzas.ui.util.colorDesdeHex
import com.usuario.finanzas.ui.util.formatearFecha
import com.usuario.finanzas.ui.util.formatearMonto
import com.usuario.finanzas.ui.util.iconoCategoria
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTransacciones(
    navController: NavHostController,
    viewModel: TransaccionesViewModel = hiltViewModel()
) {
    val transacciones by viewModel.transacciones.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val filtros by viewModel.filtros.collectAsStateWithLifecycle()
    val pendientesVM: PendientesViewModel = hiltViewModel()
    val numPendientes by pendientesVM.pendientes.collectAsStateWithLifecycle()

    var mostrarPickerDesde by remember { mutableStateOf(false) }
    var mostrarPickerHasta by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tr("Transacciones")) }) },
        bottomBar = { BarraInferior(navController, Rutas.TRANSACCIONES, numPendientes.size) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = filtros.consulta,
                onValueChange = viewModel::buscar,
                label = { Text(tr("Buscar comercio, monto o etiqueta")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // ---------- Filtros ----------
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Por categoría
                categorias.forEach { cat ->
                    FilterChip(
                        selected = filtros.categoriaId == cat.id,
                        onClick = {
                            viewModel.filtrarCategoria(if (filtros.categoriaId == cat.id) null else cat.id)
                        },
                        label = { Text(cat.nombre) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("entrada" to tr("Entradas"), "salida" to tr("Salidas")).forEach { (direccion, etiqueta) ->
                    FilterChip(
                        selected = filtros.direccion == direccion,
                        onClick = { viewModel.filtrarDireccion(if (filtros.direccion == direccion) null else direccion) },
                        label = { Text(etiqueta) }
                    )
                }
                FilterChip(
                    selected = filtros.soloTransferencias,
                    onClick = { viewModel.filtrarTransferencias() },
                    label = { Text(tr("Transferencias")) }
                )
                // Por fuente
                FilterChip(
                    selected = filtros.fuente == Transaccion.FUENTE_AUTOMATICO,
                    onClick = {
                        viewModel.filtrarFuente(
                            if (filtros.fuente == Transaccion.FUENTE_AUTOMATICO) null
                            else Transaccion.FUENTE_AUTOMATICO
                        )
                    },
                    label = { Text(tr("Automáticas")) }
                )
                FilterChip(
                    selected = filtros.fuente == Transaccion.FUENTE_MANUAL,
                    onClick = {
                        viewModel.filtrarFuente(
                            if (filtros.fuente == Transaccion.FUENTE_MANUAL) null
                            else Transaccion.FUENTE_MANUAL
                        )
                    },
                    label = { Text(tr("Manuales")) }
                )
                // Por rango de fechas
                AssistChip(
                    onClick = { mostrarPickerDesde = true },
                    label = { Text(filtros.desde?.let { tr("Desde {0}", formatearFecha(it)) } ?: tr("Desde")) }
                )
                AssistChip(
                    onClick = { mostrarPickerHasta = true },
                    label = { Text(filtros.hasta?.let { tr("Hasta {0}", formatearFecha(it)) } ?: tr("Hasta")) }
                )
                if (filtros.hayFiltros) {
                    AssistChip(
                        onClick = { viewModel.limpiarFiltros() },
                        label = { Text(tr("Limpiar")) },
                        leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) }
                    )
                }
            }

            // ---------- Lista ----------
            if (transacciones.isEmpty()) {
                Text(
                    tr("No hay transacciones"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transacciones, key = { it.transaccion.id }) { item ->
                    TarjetaTransaccion(item) {
                        navController.navigate(Rutas.detalleTransaccion(item.transaccion.id))
                    }
                }
            }
        }
    }

    if (mostrarPickerDesde) {
        DialogoFecha(
            inicial = filtros.desde,
            onConfirmar = { viewModel.filtrarDesde(it) },
            onCerrar = { mostrarPickerDesde = false }
        )
    }
    if (mostrarPickerHasta) {
        DialogoFecha(
            inicial = filtros.hasta,
            onConfirmar = { viewModel.filtrarHasta(it) },
            onCerrar = { mostrarPickerHasta = false }
        )
    }
}

@Composable
fun TarjetaTransaccion(item: TransaccionConCategoria, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconoCategoria(item.categoria?.icono ?: ""),
                contentDescription = item.categoria?.nombre,
                tint = item.categoria?.let { colorDesdeHex(it.color) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.transaccion.nombreComercio, style = MaterialTheme.typography.titleMedium)
                Text(
                    tr(ClaseMovimiento.desde(item.transaccion.tipo, item.transaccion.direccion).etiqueta) +
                        item.transaccion.banco.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.transaccion.etiquetas.isNotBlank()) {
                    Text(item.transaccion.etiquetas.split(",").joinToString(" · ") { "#${it.trim()}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatearFecha(item.transaccion.fecha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.transaccion.fuente == Transaccion.FUENTE_AUTOMATICO) {
                        Spacer(Modifier.width(8.dp))
                        // Badge pequeño para transacciones detectadas automáticamente
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                tr("auto"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Text(
                (if (item.transaccion.direccion == "entrada") "+" else "−") + formatearMonto(item.transaccion.monto),
                style = MaterialTheme.typography.titleMedium,
                color = if (item.transaccion.tipo == "transferencia_propia") MaterialTheme.colorScheme.onSurfaceVariant
                    else if (item.transaccion.direccion == "entrada") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoFecha(inicial: Long?, onConfirmar: (Long) -> Unit, onCerrar: () -> Unit) {
    // Material DatePicker representa días en UTC; la aplicación almacena fechas locales.
    val inicialUtc = remember(inicial) {
        inicial?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    }
    val estado = rememberDatePickerState(initialSelectedDateMillis = inicialUtc)
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { elegido ->
                    val local = Instant.ofEpochMilli(elegido).atZone(ZoneOffset.UTC).toLocalDate()
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onConfirmar(local)
                }
                onCerrar()
            }) { Text(tr("Aceptar")) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(tr("Cancelar")) } }
    ) {
        DatePicker(state = estado)
    }
}
