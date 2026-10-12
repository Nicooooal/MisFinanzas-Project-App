package com.usuario.finanzas.ui.screens.pendientes

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import com.usuario.finanzas.ui.navigation.Rutas
import com.usuario.finanzas.ui.util.formatearFechaHora
import com.usuario.finanzas.ui.util.formatearMonto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPendientes(
    navController: NavHostController,
    viewModel: PendientesViewModel = hiltViewModel()
) {
    val pendientes by viewModel.pendientes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Pendientes de revisar"))
                        if (pendientes.isNotEmpty()) {
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Badge { Text("${pendientes.size}") }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver"))
                    }
                },
                actions = {
                    if (pendientes.isNotEmpty()) {
                        TextButton(onClick = { viewModel.descartarTodas() }) {
                            Text(tr("Descartar todas"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (pendientes.isEmpty()) {
            // Bandeja vacía
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        tr("Sin transacciones pendientes"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Aquí aparecerán las transacciones detectadas\nautomáticamente para que las revises."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(pendientes, key = { it.id }) { pendiente ->
                    CardPendiente(
                        pendiente = pendiente,
                        onGuardar = {
                            navController.navigate(Rutas.confirmacionDesde(pendiente.id))
                        },
                        onDescartar = { viewModel.descartar(pendiente.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CardPendiente(
    pendiente: TransaccionPendiente,
    onGuardar: () -> Unit,
    onDescartar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Banco + fecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pendiente.banco,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatearFechaHora(pendiente.fecha),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            // Monto
            Text(
                text = (if (pendiente.direccion == "entrada") "+" else "−") + formatearMonto(pendiente.monto),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (pendiente.direccion == "entrada") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                tr(ClaseMovimiento.desde(pendiente.tipo, pendiente.direccion).etiqueta),
                style = MaterialTheme.typography.labelMedium
            )
            // Comercio
            Text(
                text = pendiente.comercio,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(4.dp))
            // Texto original (para que el usuario pueda validar)
            Text(
                text = pendiente.textoOriginal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(Modifier.height(12.dp))
            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDescartar,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(tr("Descartar"))
                }
                Button(
                    onClick = onGuardar,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(tr("Revisar"))
                }
            }
        }
    }
}
