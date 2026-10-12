package com.usuario.finanzas.ui.screens.resumen

import com.usuario.finanzas.ui.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.domain.finanzas.*
import com.usuario.finanzas.ui.navigation.BarraInferior
import com.usuario.finanzas.ui.navigation.Rutas
import com.usuario.finanzas.ui.theme.*
import com.usuario.finanzas.ui.util.colorDesdeHex
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoMes: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(navController: NavHostController, vm: ResumenViewModel = hiltViewModel()) {
    val d by vm.datos.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val mes = YearMonth.parse(mesTexto)
    val rango = PeriodoEstadistico.MES.rango(mes.atDay(1))
    val corte = minOf(rango.last, System.currentTimeMillis())
    val lista = remember(d.transacciones, mesTexto) { d.transacciones.filter { it.fecha in rango && it.fecha <= System.currentTimeMillis() } }
    val ingresos = CalculadoraFinanciera.total(lista, "entrada")
    val gastos = CalculadoraFinanciera.total(lista, "salida")
    val config = d.configuracion
    val saldo = if (corte >= config.fechaInicial) CalculadoraFinanciera.resumen(d.transacciones, config, corte).saldoCentavos else null
    val anterior = if (rango.first > config.fechaInicial) CalculadoraFinanciera.resumen(d.transacciones, config, rango.first - 1).saldoCentavos else config.saldoInicialCentavos
    fun dinero(m: Long) = if (prefs.ocultar) "••••" else Dinero.formatear(m, config.moneda)
    Scaffold(
        topBar = { TopAppBar(
            title = { Column {
                Text(tr("Mis Finanzas"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Tu dinero, más claro"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            actions = { IconButton(onClick = vm::privacidad) { Icon(if (prefs.ocultar) Icons.Default.VisibilityOff else Icons.Default.Visibility, tr(if (prefs.ocultar) "Mostrar importes" else "Ocultar importes")) } }
        ) },
        bottomBar = { BarraInferior(navController, Rutas.DASHBOARD, d.pendientes) },
        floatingActionButton = { FloatingActionButton(onClick = { navController.navigate(Rutas.AGREGAR_MANUAL) }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Default.Add, tr("Registro manual")) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { NavegadorPeriodo(mes.atDay(1).format(formatoMes), { mesTexto = mes.minusMonths(1).toString() }, { mesTexto = mes.plusMonths(1).toString() }, mes < YearMonth.now()) }
            if (!d.cargado) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = FondoSaldo, contentColor = TextoSaldo)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(20.dp), tint = AcentoSaldo)
                        Text(if (mes == YearMonth.now()) tr("Saldo registrado a la fecha") else tr("Saldo registrado al cierre"), style = MaterialTheme.typography.labelLarge, color = AcentoSaldo)
                    }
                    Text(saldo?.let(::dinero) ?: tr("Sin saldo para este período"), style = MaterialTheme.typography.headlineLarge)
                    if (saldo != null) Text(tr("Saldo anterior: {0}", dinero(anterior)), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = AcentoSaldo.copy(alpha = .22f))
                    Text(tr("Calculado desde el saldo inicial y los movimientos registrados. No consulta el saldo bancario."), style = MaterialTheme.typography.bodySmall, color = AcentoSaldo)
                    TextButton(onClick = { navController.navigate("ajustes") }, contentPadding = PaddingValues(horizontal = 0.dp), colors = ButtonDefaults.textButtonColors(contentColor = TextoSaldo)) { Text(tr("Configurar saldo inicial")); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp)) }
                }
            } }
            item { ParMetricas(dinero(ingresos), dinero(gastos)) }
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Registrar movimiento"), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccesoRegistro(tr("Texto"), Icons.Default.EditNote, Modifier.weight(1f)) { navController.navigate("captura?modo=texto") }
                    AccesoRegistro(tr("Voz"), Icons.Default.MicNone, Modifier.weight(1f)) { navController.navigate("captura?modo=voz") }
                    AccesoRegistro(tr("Recibo"), Icons.Default.DocumentScanner, Modifier.weight(1f)) { navController.navigate("captura?modo=recibo") }
                }
            } }
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Gasto sobre ingresos"), style = MaterialTheme.typography.titleMedium)
                    if (ingresos > 0) {
                        val proporcion = gastos.toDouble() / ingresos
                        if (!prefs.ocultar) LinearProgressIndicator(progress = { proporcion.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = if (gastos > ingresos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text(if (prefs.ocultar) "••••" else "${(proporcion * 100).toLong()}%", style = MaterialTheme.typography.titleLarge)
                    } else Text(tr("Sin ingresos registrados en este período"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            if (gastos > ingresos) item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Déficit del período"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Las salidas superan los ingresos en {0}. Puede haberse utilizado ahorro anterior.", dinero(gastos - ingresos)), style = MaterialTheme.typography.bodyMedium)
                }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Por revisar"), style = MaterialTheme.typography.titleMedium)
                AccesoFila(tr("Pendientes de revisar ({0})", d.pendientes), Icons.Default.Inbox) { navController.navigate(Rutas.PENDIENTES) }
                AccesoFila(tr("Servicios y próximos pagos"), Icons.Default.CalendarMonth) { navController.navigate(Rutas.SERVICIOS) }
            } }
            item { Text(tr("Tus traspasos propios no se incluyen en ingresos ni salidas. Clasifica los retiros de efectivo según cómo registres sus gastos posteriores."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AccesoRegistro(titulo: String, icono: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icono, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Text(titulo, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ParMetricas(ingresos: String, gastos: String) {
    val fuenteGrande = LocalDensity.current.fontScale > 1.15f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fuenteGrande) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Metrica(tr("Ingresos"), ingresos, Modifier.fillMaxWidth(), true)
            Metrica(tr("Salidas"), gastos, Modifier.fillMaxWidth(), false)
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metrica(tr("Ingresos"), ingresos, Modifier.weight(1f), true)
            Metrica(tr("Salidas"), gastos, Modifier.weight(1f), false)
        }
    }
}

@Composable
private fun Metrica(titulo: String, valor: String, modifier: Modifier = Modifier, ingreso: Boolean = true) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (ingreso) Icons.Default.SouthWest else Icons.Default.NorthEast, null, Modifier.size(18.dp), tint = if (ingreso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(valor, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun NavegadorPeriodo(titulo: String, anterior: () -> Unit, siguiente: () -> Unit, puedeSiguiente: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = anterior) { Icon(Icons.Default.ChevronLeft, tr("Anterior")) }
        Text(titulo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        IconButton(onClick = siguiente, enabled = puedeSiguiente) { Icon(Icons.Default.ChevronRight, tr("Siguiente")) }
    }
}

@Composable
private fun AccesoFila(titulo: String, icono: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icono, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBilletera(navController: NavHostController, vm: ResumenViewModel = hiltViewModel()) {
    val d by vm.datos.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(tr("Billetera")) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, bottomBar = { BarraInferior(navController, "billetera", d.pendientes) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { Text(tr("Organiza tus movimientos y planifica el dinero"), style = MaterialTheme.typography.headlineSmall) }
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Movimientos"), style = MaterialTheme.typography.titleMedium)
                AccesoFila(tr("Transacciones"), Icons.Default.SwapVert) { navController.navigate(Rutas.TRANSACCIONES) }
                AccesoFila(tr("Pendientes de revisar ({0})", d.pendientes), Icons.Default.Inbox) { navController.navigate(Rutas.PENDIENTES) }
                AccesoFila(tr("Categorías"), Icons.Default.Category) { navController.navigate("categorias") }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Tu planificación"), style = MaterialTheme.typography.titleMedium)
                val fuenteGrande = LocalDensity.current.fontScale > 1.15f
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 320.dp || fuenteGrande) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TarjetaBilletera(tr("Presupuestos y metas"), Icons.Default.Savings, Modifier.fillMaxWidth()) { navController.navigate("planes") }
                        TarjetaBilletera(tr("Movimientos recurrentes"), Icons.Default.EventRepeat, Modifier.fillMaxWidth()) { navController.navigate("recurrentes") }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TarjetaBilletera(tr("Presupuestos y metas"), Icons.Default.Savings, Modifier.weight(1f)) { navController.navigate("planes") }
                        TarjetaBilletera(tr("Movimientos recurrentes"), Icons.Default.EventRepeat, Modifier.weight(1f)) { navController.navigate("recurrentes") }
                    }
                }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Servicios"), style = MaterialTheme.typography.titleMedium)
                AccesoFila(tr("Servicios y pagos"), Icons.Default.ReceiptLong) { navController.navigate(Rutas.SERVICIOS) }
            } }
        }
    }
}

@Composable
private fun TarjetaBilletera(titulo: String, icono: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icono, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEstadisticas(navController:NavHostController,vm:ResumenViewModel=hiltViewModel()) {
    val d by vm.datos.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    var periodoNombre by rememberSaveable { mutableStateOf(PeriodoEstadistico.MES.name) }
    var fechaTexto by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val periodo = PeriodoEstadistico.valueOf(periodoNombre)
    val fecha = LocalDate.parse(fechaTexto)
    val inicio = periodo.inicio(fecha)
    val fin = periodo.siguiente(inicio).minusDays(1)
    val rango = periodo.rango(fecha)
    val lista = remember(d.transacciones,fechaTexto,periodoNombre) { d.transacciones.filter {it.fecha in rango && it.fecha<=System.currentTimeMillis()} }
    val ingresos = CalculadoraFinanciera.total(lista,"entrada")
    val gastos = CalculadoraFinanciera.total(lista,"salida")
    val grupos = lista.filter {it.direccion=="salida" && it.tipo!="transferencia_propia"}.groupBy {it.categoriaId}.mapValues { (_,t)->Dinero.sumar(t.map(CalculadoraFinanciera::monto)) }.toList().sortedByDescending {it.second}
    val tendencia = (5 downTo 0).map { meses ->
        val mes = YearMonth.from(fecha).minusMonths(meses.toLong())
        val r = PeriodoEstadistico.MES.rango(mes.atDay(1))
        mes to CalculadoraFinanciera.total(d.transacciones.filter {it.fecha in r && it.fecha<=System.currentTimeMillis()},"salida")
    }
    var barraSeleccionada by remember { mutableStateOf("entrada") }
    fun dinero(m:Long)=if(prefs.ocultar) "••••" else Dinero.formatear(m,d.configuracion.moneda)
    Scaffold(topBar={TopAppBar(title={Text(tr("Estadísticas"))},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),actions={TextButton(onClick={navController.navigate("reportes")}){Text(tr("Exportar"))}})},bottomBar={BarraInferior(navController,"estadisticas",d.pendientes)}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { PeriodoEstadistico.entries.forEach { p -> FilterChip(periodo==p,{periodoNombre=p.name},label={Text(tr(p.etiqueta))}) } } }
            item { NavegadorPeriodo("$inicio — $fin",{fechaTexto=periodo.mover(fecha,-1).toString()},{fechaTexto=periodo.mover(fecha,1).toString()},periodo.siguiente(inicio)<=LocalDate.now()) }
            item { ParMetricas(dinero(ingresos), dinero(gastos)) }
            item { Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(tr("Ingresos vs salidas"),style=MaterialTheme.typography.titleMedium)
                val maximo = maxOf(ingresos,gastos,1L).toFloat()
                if (!prefs.ocultar) Row(Modifier.fillMaxWidth().height(150.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceEvenly) {
                    listOf("entrada" to ingresos,"salida" to gastos).forEach { (tipo,monto) ->
                        val color=if(tipo=="entrada") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        val descripcion = "${if(tipo=="entrada") tr("Ingresos") else tr("Salidas")}: ${dinero(monto)}"
                        Box(Modifier.width(68.dp).fillMaxHeight().clickable{barraSeleccionada=tipo}.semantics { contentDescription = descripcion }, contentAlignment=Alignment.BottomCenter) {
                            Canvas(Modifier.width(40.dp).height((monto/maximo*140).coerceAtLeast(3f).dp)) { drawRoundRect(color, cornerRadius=androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(),6.dp.toPx())) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { TextButton(onClick={barraSeleccionada="entrada"}){Text(tr("Ingresos"))};TextButton(onClick={barraSeleccionada="salida"}){Text(tr("Salidas"))} }
                Text("${if(barraSeleccionada=="entrada") tr("Ingresos") else tr("Salidas")}: ${dinero(if(barraSeleccionada=="entrada") ingresos else gastos)}")
            } } }
            item { Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(tr("Salidas por categoría"),style=MaterialTheme.typography.titleMedium)
                if(grupos.isEmpty()) Text(tr("No hay salidas en este período"))
                else {
                    val colores=grupos.map { (id,_) -> colorDesdeHex(d.categorias.find{it.id==id}?.color ?: "#888888") }
                    if (!prefs.ocultar) Canvas(Modifier.size(170.dp).padding(10.dp).align(Alignment.CenterHorizontally)) {
                        var angulo=-90f
                        grupos.forEachIndexed { i,(_,m) -> val arco=(m.toDouble()/gastos*360).toFloat();drawArc(colores[i],angulo,arco,false,style=Stroke(26.dp.toPx()));angulo+=arco }
                    }
                    grupos.forEachIndexed { indice, (id,monto) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Surface(Modifier.size(10.dp), shape=CircleShape, color=colores[indice]) { }
                            Column(Modifier.weight(1f)) {
                                Text(d.categorias.find{it.id==id}?.nombre ?: tr("Otros"), style=MaterialTheme.typography.bodyMedium)
                                Text(dinero(monto), style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } } }
            item { Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(tr("Tendencia de salidas · 6 meses"),style=MaterialTheme.typography.titleMedium)
                val color=MaterialTheme.colorScheme.primary
                if (!prefs.ocultar) Canvas(Modifier.fillMaxWidth().height(140.dp).padding(horizontal=6.dp)) {
                    val maximo=tendencia.maxOf {it.second}.coerceAtLeast(1L).toDouble()
                    val path=Path()
                    tendencia.forEachIndexed {i,(_,m)->val x=size.width*i/5;val y=size.height-(m/maximo*size.height*.9).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y);drawCircle(color,4.dp.toPx(),Offset(x,y))}
                    drawPath(path,color,style=Stroke(2.dp.toPx()))
                }
                tendencia.forEach { (mes,monto)->Text("${mes.atDay(1).format(formatoMes)}: ${dinero(monto)}",style=MaterialTheme.typography.bodySmall) }
            } } }
            item { Text(tr("Los períodos son de calendario. El período en curso contiene solo movimientos registrados hasta hoy; los traspasos propios se excluyen."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

