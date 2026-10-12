package com.usuario.finanzas.ui.screens.dashboard

import com.usuario.finanzas.ui.i18n.tr

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.column.ColumnChart
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.domain.model.GastoPorCategoria
import com.usuario.finanzas.domain.model.TotalMensual
import com.usuario.finanzas.ui.navigation.BarraInferior
import com.usuario.finanzas.ui.screens.pendientes.PendientesViewModel
import com.usuario.finanzas.ui.navigation.Rutas
import com.usuario.finanzas.ui.theme.Naranja
import com.usuario.finanzas.ui.theme.Rojo
import com.usuario.finanzas.ui.theme.Verde
import com.usuario.finanzas.ui.util.colorDesdeHex
import com.usuario.finanzas.ui.util.formatearMonto
import com.usuario.finanzas.ui.util.iconoCategoria
import kotlin.math.abs
import androidx.compose.material3.TextButton

/** Gris apagado para las barras de meses anteriores en la comparativa mensual. */
private val GrisBarra = Color(0xFF3D3D3D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDashboard(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val pendientesVM: PendientesViewModel = hiltViewModel()
    val numPendientes by pendientesVM.pendientes.collectAsStateWithLifecycle()
    val totalMes by viewModel.totalMes.collectAsStateWithLifecycle()
    val entradasMes by viewModel.entradasMes.collectAsStateWithLifecycle()
    val gastos by viewModel.gastosPorCategoria.collectAsStateWithLifecycle()
    val proximos by viewModel.proximosPagos.collectAsStateWithLifecycle()
    val pendiente by viewModel.pendiente.collectAsStateWithLifecycle()
    val historial by viewModel.historialMensual.collectAsStateWithLifecycle()
    val mesSeleccionado by viewModel.mesSeleccionado.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr("Mis Finanzas"))
                        Text(
                            text = "v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Abre los ajustes de "Acceso a notificaciones" para habilitar el listener
                    IconButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = tr("Acceso a notificaciones"))
                    }
                }
            )
        },
        bottomBar = { BarraInferior(navController, Rutas.DASHBOARD, numPendientes.size) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Rutas.AGREGAR_MANUAL) }) {
                Icon(Icons.Filled.Add, contentDescription = tr("Agregar transacción"))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Banner: transacciones detectadas pendientes de revisar
            pendiente?.let { deteccion ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Rutas.PENDIENTES) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    tr("Transacciones pendientes de revisar"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    tr("Toca para ver y confirmar"),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // 1) Tarjeta resumen: monto grande + variación vs mes anterior + sparkline
            item { TarjetaResumenMes(totalMes, historial) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Entradas del mes"), style = MaterialTheme.typography.titleMedium)
                        Text(formatearMonto(entradasMes), color = Verde, style = MaterialTheme.typography.headlineSmall)
                        Text(tr("Balance del mes: {0}", formatearMonto(entradasMes - totalMes)), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Entradas menos salidas registradas. No es el saldo bancario. Los traspasos entre tus cuentas se excluyen."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 2) Donut + lista de categorías (con transición suave vacío <-> datos)
            item { TarjetaDistribucionCategorias(gastos) }

            // 3) Próximos pagos, estilo "ticket" con borde de color por estado
            if (proximos.isNotEmpty()) {
                item {
                    Column {
                        Text(tr("Próximos pagos"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(proximos.size) { i ->
                                val item = proximos[i]
                                TarjetaTicketPago(item) {
                                    navController.navigate(Rutas.detalleServicio(item.servicio.id))
                                }
                            }
                        }
                    }
                }
            }

            // 4) Comparativa mensual: barras de los últimos 6 meses (Vico)
            if (historial.any { it.total > 0 }) {
                item {
                    TarjetaComparativaMensual(
                        historial = historial,
                        mesSeleccionado = mesSeleccionado,
                        onMesTocado = { viewModel.seleccionarMes(it) }
                    )
                }
            }

            // 5) Detalle del mes seleccionado (aparece al tocar una barra)
            mesSeleccionado?.let { mes ->
                item {
                    TarjetaDetalleMesSeleccionado(
                        mes = mes,
                        onVerDetalle = {
                            navController.navigate(Rutas.historialMes(mes.anio, mes.mes))
                        },
                        onCerrar = { viewModel.deseleccionarMes() }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1) Tarjeta resumen principal
// ---------------------------------------------------------------------------

@Composable
private fun TarjetaResumenMes(totalMes: Double, historial: List<TotalMensual>) {
    // Variación porcentual vs el mes anterior (null si no hay datos del mes pasado)
    val variacion = remember(historial, totalMes) {
        val anterior = historial.getOrNull(historial.size - 2)?.total
        if (anterior != null && anterior > 0.0) {
            ((totalMes - anterior) / anterior * 100).toInt()
        } else null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            // Sparkline decorativa de los últimos 6 meses al fondo de la card
            Sparkline(
                valores = historial.map { it.total },
                color = Verde,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .alpha(0.55f)
            )
            Column(Modifier.padding(24.dp)) {
                Text(
                    tr("Salidas este mes"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatearMonto(totalMes),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                variacion?.let { v ->
                    // Gastar MÁS que el mes pasado es malo (rojo); menos es bueno (verde)
                    val (color, icono, texto) = when {
                        v > 0 -> Triple(Rojo, Icons.Filled.ArrowUpward, tr("+{0}% que el mes pasado", v))
                        v < 0 -> Triple(Verde, Icons.Filled.ArrowDownward, tr("{0}% que el mes pasado", v))
                        else -> Triple(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            null,
                            tr("Igual que el mes pasado")
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        icono?.let {
                            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            texto,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

/** Mini gráfico de tendencia (línea + degradado) dibujado con Canvas. */
@Composable
private fun Sparkline(valores: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (valores.size < 2) return@Canvas
        val maximo = valores.max().takeIf { it > 0 } ?: return@Canvas
        val pasoX = size.width / (valores.size - 1)
        val puntos = valores.mapIndexed { i, v ->
            // 0.85f deja un margen superior para que la línea no toque el borde
            Offset(i * pasoX, size.height * (1f - (v / maximo).toFloat() * 0.85f))
        }
        val linea = Path().apply {
            moveTo(puntos.first().x, puntos.first().y)
            puntos.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val relleno = Path().apply {
            addPath(linea)
            lineTo(puntos.last().x, size.height)
            lineTo(puntos.first().x, size.height)
            close()
        }
        drawPath(
            relleno,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), Color.Transparent))
        )
        drawPath(
            linea,
            color = color.copy(alpha = 0.55f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// 2) Donut + lista de categorías
// ---------------------------------------------------------------------------

@Composable
private fun TarjetaDistribucionCategorias(gastos: List<GastoPorCategoria>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Distribución de salidas"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            // Transición suave entre el estado vacío y los datos
            Crossfade(targetState = gastos.isEmpty(), label = "distribucion") { vacio ->
                if (vacio) {
                    Text(
                        tr("Aún no hay salidas registradas este mes"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    ContenidoDistribucion(gastos)
                }
            }
        }
    }
}

@Composable
private fun ContenidoDistribucion(gastos: List<GastoPorCategoria>) {
    val total = remember(gastos) { gastos.sumOf { it.total } }
    if (total <= 0) return

    // Categoría resaltada al tocar una fila (null = ninguna, todas con opacidad plena)
    var categoriaResaltada by remember { mutableStateOf<Int?>(null) }

    // Animación de entrada: el donut se "dibuja" progresivamente
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(gastos) {
        progreso.snapTo(0f)
        progreso.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    Column {
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Donut(
                gastos = gastos,
                total = total,
                progreso = progreso.value,
                categoriaResaltada = categoriaResaltada,
                modifier = Modifier.fillMaxSize()
            )
            // Total del mes en el centro del donut
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("Total del mes"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatearMonto(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        gastos.forEach { gasto ->
            FilaCategoria(
                gasto = gasto,
                total = total,
                resaltada = categoriaResaltada == gasto.categoriaId,
                onClick = {
                    categoriaResaltada =
                        if (categoriaResaltada == gasto.categoriaId) null else gasto.categoriaId
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Donut chart en Canvas con animación de entrada y resaltado de la porción tocada. */
@Composable
private fun Donut(
    gastos: List<GastoPorCategoria>,
    total: Double,
    progreso: Float,
    categoriaResaltada: Int?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val grosorBase = 26.dp.toPx()
        val grosorResaltado = 32.dp.toPx()
        val margen = grosorResaltado / 2
        val diametro = size.minDimension - grosorResaltado
        val limite = progreso * 360f
        var inicioRelativo = 0f
        gastos.forEach { gasto ->
            val barrido = (gasto.total / total * 360.0).toFloat()
            // Dibujo progresivo: cada porción solo aparece hasta donde va la animación
            val visible = (limite - inicioRelativo).coerceIn(0f, barrido)
            if (visible > 0.5f) {
                val resaltada = categoriaResaltada == gasto.categoriaId
                val alpha = if (categoriaResaltada == null || resaltada) 1f else 0.30f
                drawArc(
                    color = colorDesdeHex(gasto.color).copy(alpha = alpha),
                    startAngle = -90f + inicioRelativo,
                    // -1.5° de separación visual entre porciones
                    sweepAngle = (visible - 1.5f).coerceAtLeast(0.5f),
                    useCenter = false,
                    style = Stroke(width = if (resaltada) grosorResaltado else grosorBase),
                    topLeft = Offset(margen, margen),
                    size = Size(diametro, diametro)
                )
            }
            inicioRelativo += barrido
        }
    }
}

/** Fila de categoría: ícono, nombre, monto, porcentaje y barra de progreso delgada. */
@Composable
private fun FilaCategoria(
    gasto: GastoPorCategoria,
    total: Double,
    resaltada: Boolean,
    onClick: () -> Unit
) {
    val color = colorDesdeHex(gasto.color)
    val fraccion = (gasto.total / total).toFloat()
    val fondoAlpha by animateFloatAsState(if (resaltada) 0.10f else 0f, label = "fondoFila")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = fondoAlpha))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconoCategoria(gasto.icono),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    gasto.nombre,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatearMonto(gasto.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            // Barra de progreso horizontal delgada del color de la categoría
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraccion)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${(fraccion * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// 3) Próximos pagos estilo "ticket"
// ---------------------------------------------------------------------------

/** Card tipo ticket: borde izquierdo grueso de color según estado del servicio. */
@Composable
private fun TarjetaTicketPago(item: ServicioConDias, onClick: () -> Unit) {
    val vencido = item.servicio.estado == Servicio.ESTADO_VENCIDO
    val (colorEstado, textoEstado) = when {
        item.servicio.estado == Servicio.ESTADO_PAGADO -> Verde to tr("Pagado")
        vencido -> {
            val dias = abs(item.diasRestantes)
            Rojo to if (dias > 0) tr("Vencido hace {0} {1}", dias, if (dias == 1L) tr("día") else tr("días")) else tr("Vencido hoy")
        }
        item.diasRestantes <= 0L -> Rojo to tr("Vence hoy")
        item.diasRestantes == 1L -> Naranja to tr("Vence mañana")
        item.diasRestantes <= 3 -> Naranja to tr("Vence en {0} días", item.diasRestantes)
        else -> Verde to tr("Vence en {0} días", item.diasRestantes)
    }

    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() }
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Borde izquierdo grueso de color según estado
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(colorEstado)
            )
            Column(Modifier.padding(14.dp)) {
                Text(
                    item.servicio.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatearMonto(item.servicio.montoEstimado),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vencido) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Rojo,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        textoEstado,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorEstado
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4) Comparativa mensual (barras Vico, últimos 6 meses)
// ---------------------------------------------------------------------------

@Composable
private fun TarjetaComparativaMensual(
    historial: List<TotalMensual>,
    mesSeleccionado: MesSeleccionado?,
    onMesTocado: (TotalMensual) -> Unit
) {
    val modelo = remember(historial) {
        entryModelOf(
            historial.mapIndexed { i, m ->
                entryOf(i.toFloat(), if (m.esMesActual) 0f else m.total.toFloat())
            },
            historial.mapIndexed { i, m ->
                entryOf(i.toFloat(), if (m.esMesActual) m.total.toFloat() else 0f)
            }
        )
    }
    val etiquetas = remember(historial) { historial.map { it.etiqueta } }
    val formateadorEjeX = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { valor, _ ->
        etiquetas.getOrNull(valor.toInt()) ?: ""
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Salidas de los últimos 6 meses"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Toca un mes para ver el detalle"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Chart(
                chart = columnChart(
                    columns = listOf(
                        lineComponent(
                            color = GrisBarra,
                            thickness = 22.dp,
                            shape = Shapes.roundedCornerShape(
                                topLeftPercent = 40, topRightPercent = 40,
                                bottomRightPercent = 0, bottomLeftPercent = 0
                            )
                        ),
                        lineComponent(
                            color = Verde,
                            thickness = 22.dp,
                            shape = Shapes.roundedCornerShape(
                                topLeftPercent = 40, topRightPercent = 40,
                                bottomRightPercent = 0, bottomLeftPercent = 0
                            )
                        )
                    ),
                    mergeMode = ColumnChart.MergeMode.Stack
                ),
                model = modelo,
                bottomAxis = rememberBottomAxis(
                    valueFormatter = formateadorEjeX,
                    guideline = null
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Spacer(Modifier.height(8.dp))
            // Botones de mes tocables debajo del gráfico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                historial.forEach { mes ->
                    val seleccionado = mesSeleccionado?.anio == mes.anio &&
                        mesSeleccionado.mes == mes.mes
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (seleccionado)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    Color.Transparent
                            )
                            .clickable { onMesTocado(mes) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = mes.etiqueta,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mes.esMesActual) Verde
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// 5) Tarjeta de detalle del mes seleccionado
// ---------------------------------------------------------------------------

@Composable
private fun TarjetaDetalleMesSeleccionado(
    mes: MesSeleccionado,
    onVerDetalle: () -> Unit,
    onCerrar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${mes.nombre} ${mes.anio}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatearMonto(mes.total),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row {
                    TextButton(onClick = onVerDetalle) {
                        Text(tr("Ver todo"))
                    }
                    IconButton(onClick = onCerrar) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = tr("Cerrar"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (mes.gastosPorCategoria.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                mes.gastosPorCategoria.take(4).forEach { gasto ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = iconoCategoria(gasto.icono),
                                contentDescription = null,
                                tint = colorDesdeHex(gasto.color),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(gasto.nombre, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            formatearMonto(gasto.total),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (mes.gastosPorCategoria.size > 4) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onVerDetalle,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(tr("Ver {0} categorías más →", mes.gastosPorCategoria.size - 4))
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Sin salidas registradas en este mes."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
