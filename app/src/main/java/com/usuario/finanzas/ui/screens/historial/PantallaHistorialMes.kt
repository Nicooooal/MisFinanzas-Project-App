package com.usuario.finanzas.ui.screens.historial

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.domain.model.GastoPorCategoria
import com.usuario.finanzas.ui.util.colorDesdeHex
import com.usuario.finanzas.ui.util.formatearFecha
import com.usuario.finanzas.ui.util.formatearMonto
import com.usuario.finanzas.ui.util.iconoCategoria

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorialMes(
    navController: NavHostController,
    viewModel: HistorialMesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${state.nombreMes} ${state.anio}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!state.cargando) {
                            Text(
                                text = tr("Salidas: {0}", formatearMonto(state.total)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver"))
                    }
                }
            )
        }
    ) { padding ->
        if (state.cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.transacciones.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Sin movimientos en {0}", state.nombreMes), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("No hay transacciones registradas en este mes."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Entradas: {0}", formatearMonto(state.entradas)), color = MaterialTheme.colorScheme.primary)
                        Text(tr("Salidas: {0}", formatearMonto(state.total)), color = MaterialTheme.colorScheme.error)
                        Text(tr("Balance registrado: {0}", formatearMonto(state.entradas - state.total)), fontWeight = FontWeight.Bold)
                        Text(tr("Las salidas incluyen envíos y retiros. Se excluyen las transferencias entre tus cuentas."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Resumen por categoría
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("Salidas por categoría"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.gastosPorCategoria) { gasto ->
                FilaCategoria(gasto = gasto, totalMes = state.total)
            }

            // Separador
            item {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("Todas las transacciones ({0})", state.transacciones.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            // Lista de transacciones agrupadas por día
            val porDia = state.transacciones.groupBy { formatearFecha(it.fecha) }
            porDia.forEach { (fecha, transacciones) ->
                item {
                    Text(
                        text = fecha,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(transacciones) { transaccion ->
                    CardTransaccionMes(transaccion = transaccion)
                    Spacer(Modifier.height(6.dp))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FilaCategoria(gasto: GastoPorCategoria, totalMes: Double) {
    val porcentaje = if (totalMes > 0) (gasto.total / totalMes * 100).toInt() else 0
    val color = colorDesdeHex(gasto.color)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconoCategoria(gasto.icono),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(gasto.nombre, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$porcentaje%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Barra de progreso
            Canvas(
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp)
            ) {
                val ancho = size.width * (porcentaje / 100f).coerceIn(0f, 1f)
                drawRect(color = color.copy(alpha = 0.2f), size = size)
                drawRect(color = color, size = size.copy(width = ancho))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatearMonto(gasto.total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CardTransaccionMes(transaccion: Transaccion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = transaccion.nombreComercio.ifBlank { transaccion.descripcion },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(tr(ClaseMovimiento.desde(transaccion.tipo, transaccion.direccion).etiqueta), style = MaterialTheme.typography.labelMedium)
                if (transaccion.banco.isNotBlank()) Text(transaccion.banco, style = MaterialTheme.typography.bodySmall)
                if (transaccion.descripcion.isNotBlank() &&
                    transaccion.descripcion != transaccion.nombreComercio) {
                    Text(
                        text = transaccion.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (transaccion.fuente == Transaccion.FUENTE_AUTOMATICO) {
                    Text(
                        text = tr("Detectado automáticamente"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = (if (transaccion.direccion == "entrada") "+" else "−") + formatearMonto(transaccion.monto),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (transaccion.tipo == "transferencia_propia") MaterialTheme.colorScheme.onSurfaceVariant
                    else if (transaccion.direccion == "entrada") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
