package com.usuario.finanzas.ui.screens.planes

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.domain.finanzas.CalculadoraFinanciera
import com.usuario.finanzas.domain.finanzas.Dinero
import com.usuario.finanzas.ui.util.formatearFecha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPlanes(navController: NavHostController, viewModel: PlanesViewModel = hiltViewModel()) {
    val presupuestos by viewModel.presupuestos.collectAsStateWithLifecycle()
    val metas by viewModel.metas.collectAsStateWithLifecycle()
    val aportes by viewModel.aportes.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val transacciones by viewModel.transacciones.collectAsStateWithLifecycle()
    val config by viewModel.configuracion.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ocupado by viewModel.ocupado.collectAsStateWithLifecycle()
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var editarPresupuesto by remember { mutableStateOf<Presupuesto?>(null) }
    var editarMeta by remember { mutableStateOf<Meta?>(null) }
    var aporteMeta by remember { mutableStateOf<Meta?>(null) }
    var historialMeta by remember { mutableStateOf<Meta?>(null) }
    var retirar by remember { mutableStateOf(false) }
    var mostrarArchivados by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Planes")) }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Volver")) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TabRow(selectedTabIndex = pestana) {
                    Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text(tr("Presupuestos")) })
                    Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text(tr("Metas")) })
                }
                Spacer(Modifier.height(12.dp))
                Text(if (pestana == 0) tr("Controla tus gastos de cada período. Los movimientos se cuentan automáticamente.") else tr("Reserva dinero para tus metas. Aportar y retirar no cambia tu saldo ni crea ingresos o gastos."))
                Button(onClick = {
                    viewModel.limpiarError()
                    if (pestana == 0) editarPresupuesto = Presupuesto(nombre = "", limiteCentavos = 0)
                    else editarMeta = Meta(nombre = "", objetivoCentavos = 0)
                }, enabled = !ocupado, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(if (pestana == 0) tr("Crear presupuesto") else tr("Crear meta"))
                }
                Row {
                    Checkbox(checked = mostrarArchivados, onCheckedChange = { mostrarArchivados = it })
                    Text(tr("Mostrar archivados"), modifier = Modifier.padding(top = 14.dp))
                }
                ErrorFormulario(error)
            }
            if (pestana == 0) {
                val visibles = presupuestos.filter { it.activo || mostrarArchivados }
                if (visibles.isEmpty()) item { Text(tr("Crea un presupuesto general o para una categoría de gastos.")) }
                items(visibles, key = { "presupuesto_${it.id}" }) { presupuesto ->
                    val rango = CalculadoraFinanciera.rangoPeriodo(presupuesto.ciclo)
                    val gastado = CalculadoraFinanciera.total(transacciones.filter {
                        it.fecha in rango && it.fecha <= System.currentTimeMillis() && (presupuesto.categoriaId == null || it.categoriaId == presupuesto.categoriaId)
                    }, "salida")
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(presupuesto.nombre, style = MaterialTheme.typography.titleLarge)
                            Text("${categorias.find { it.id == presupuesto.categoriaId }?.nombre ?: tr("Todos los gastos")} · ${tr(presupuesto.ciclo.replaceFirstChar { it.uppercase() })}")
                            Text("${formatearFecha(rango.first)} – ${formatearFecha(rango.last)}", style = MaterialTheme.typography.bodySmall)
                            ProgresoPlan(gastado, presupuesto.limiteCentavos, "${Dinero.formatear(gastado, config.moneda)} de ${Dinero.formatear(presupuesto.limiteCentavos, config.moneda)}")
                            Text(if (gastado > presupuesto.limiteCentavos) tr("Excedido en {0}", Dinero.formatear(gastado - presupuesto.limiteCentavos, config.moneda)) else tr("Disponible: {0}", Dinero.formatear(presupuesto.limiteCentavos - gastado, config.moneda)), color = if (gastado > presupuesto.limiteCentavos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!presupuesto.activo) Text(tr("Archivado"))
                            Row {
                                TextButton(onClick = { viewModel.limpiarError(); editarPresupuesto = presupuesto }, enabled = !ocupado) { Text(tr("Editar")) }
                                TextButton(onClick = { viewModel.guardar(presupuesto.copy(activo = !presupuesto.activo)) {} }, enabled = !ocupado) { Text(if (presupuesto.activo) tr("Archivar") else tr("Reactivar")) }
                            }
                        }
                    }
                }
            } else {
                val visibles = metas.filter { !it.archivada || mostrarArchivados }
                if (visibles.isEmpty()) item { Text(tr("Crea una meta y registra el dinero que quieres reservar para ella.")) }
                items(visibles, key = { "meta_${it.id}" }) { meta ->
                    val reservado = Dinero.sumar(aportes.filter { it.metaId == meta.id }.map { it.montoCentavos })
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(meta.nombre, style = MaterialTheme.typography.titleLarge)
                            ProgresoPlan(reservado, meta.objetivoCentavos, "${Dinero.formatear(reservado, config.moneda)} de ${Dinero.formatear(meta.objetivoCentavos, config.moneda)}")
                            Text(if (reservado >= meta.objetivoCentavos) tr("¡Objetivo alcanzado!") else tr("Faltan {0}", Dinero.formatear(meta.objetivoCentavos - reservado, config.moneda)))
                            if (meta.archivada) Text(tr("Archivada · el dinero reservado se conserva"))
                            if (!meta.archivada) Row {
                                TextButton(onClick = { viewModel.limpiarError(); retirar = false; aporteMeta = meta }, enabled = !ocupado) { Text(tr("Aportar")) }
                                TextButton(onClick = { viewModel.limpiarError(); retirar = true; aporteMeta = meta }, enabled = !ocupado && reservado > 0) { Text(tr("Retirar")) }
                            }
                            Row {
                                TextButton(onClick = { viewModel.limpiarError(); editarMeta = meta }, enabled = !ocupado) { Text(tr("Editar")) }
                                TextButton(onClick = { historialMeta = meta }) { Text(tr("Historial")) }
                                TextButton(onClick = { viewModel.guardar(meta.copy(archivada = !meta.archivada)) {} }, enabled = !ocupado) { Text(if (meta.archivada) tr("Reactivar") else tr("Archivar")) }
                            }
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { navController.navigate("recurrentes") }, modifier = Modifier.fillMaxWidth()) { Text(tr("Movimientos recurrentes")) } }
        }
    }
    editarPresupuesto?.let { inicial ->
        DialogoPresupuesto(inicial, categorias, config.moneda, ocupado, error, onCerrar = { if (!ocupado) editarPresupuesto = null }, onGuardar = { viewModel.guardar(it) { editarPresupuesto = null } })
    }
    editarMeta?.let { inicial ->
        DialogoMeta(inicial, config.moneda, ocupado, error, onCerrar = { if (!ocupado) editarMeta = null }, onGuardar = { viewModel.guardar(it) { editarMeta = null } })
    }
    aporteMeta?.let { meta ->
        DialogoAporte(meta, retirar, config.moneda, ocupado, error,
            reservado = Dinero.sumar(aportes.filter { it.metaId == meta.id }.map { it.montoCentavos }),
            onCerrar = { if (!ocupado) aporteMeta = null },
            onGuardar = { monto, nota -> viewModel.aportar(meta.id, monto, nota) { aporteMeta = null } })
    }
    historialMeta?.let { meta ->
        val historial = aportes.filter { it.metaId == meta.id }
        AlertDialog(onDismissRequest = { historialMeta = null }, title = { Text(meta.nombre) }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (historial.isEmpty()) item { Text(tr("Aún no hay aportes ni retiros.")) }
                items(historial, key = { it.id }) { aporte ->
                    Column {
                        Text("${if (aporte.montoCentavos > 0) tr("Aporte") else tr("Retiro")}: ${Dinero.formatear(kotlin.math.abs(aporte.montoCentavos), config.moneda)}")
                        Text(formatearFecha(aporte.fecha), style = MaterialTheme.typography.bodySmall)
                        if (aporte.nota.isNotBlank()) Text(aporte.nota)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { historialMeta = null }) { Text(tr("Cerrar")) } })
    }
}

@Composable
private fun DialogoPresupuesto(inicial: Presupuesto, categorias: List<Categoria>, moneda: String, ocupado: Boolean, error: String?, onCerrar: () -> Unit, onGuardar: (Presupuesto) -> Unit) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var monto by remember { mutableStateOf(if (inicial.limiteCentavos == 0L) "" else Dinero.editable(inicial.limiteCentavos)) }
    var ciclo by remember { mutableStateOf(inicial.ciclo) }
    var categoria by remember { mutableStateOf(inicial.categoriaId?.toString() ?: "general") }
    var validacion by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (inicial.id == 0L) tr("Crear presupuesto") else tr("Editar presupuesto")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nombre, { nombre = it.take(80) }, label = { Text(tr("Nombre")) }, singleLine = true)
            CampoMonto(monto, moneda, { monto = it }, tr("Límite"))
            SelectorOpcion(tr("Período"), ciclo, listOf("semanal" to tr("Semanal (lunes a domingo)"), "mensual" to tr("Mensual"), "anual" to tr("Anual")), { ciclo = it })
            val disponibles = categorias.filter { (!it.archivada && it.direccion in listOf("salida", "ambas")) || it.id == inicial.categoriaId }
            SelectorOpcion(tr("Categoría de gasto"), categoria, listOf("general" to tr("Todos los gastos")) + disponibles.map { it.id.toString() to it.nombre }, { categoria = it })
            ErrorFormulario(validacion ?: error)
        }
    }, confirmButton = { TextButton(enabled = !ocupado, onClick = {
        val valor = Dinero.parsear(monto)
        if (nombre.isBlank() || valor == null || valor <= 0) validacion = tr("Escribe un nombre y un límite mayor que cero.")
        else { validacion = null; onGuardar(inicial.copy(nombre = nombre.trim(), limiteCentavos = valor, ciclo = ciclo, categoriaId = categoria.toIntOrNull())) }
    }) { Text(if (ocupado) tr("Guardando…") else tr("Guardar")) } }, dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text(tr("Cancelar")) } })
}

@Composable
private fun DialogoMeta(inicial: Meta, moneda: String, ocupado: Boolean, error: String?, onCerrar: () -> Unit, onGuardar: (Meta) -> Unit) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var monto by remember { mutableStateOf(if (inicial.objetivoCentavos == 0L) "" else Dinero.editable(inicial.objetivoCentavos)) }
    var validacion by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (inicial.id == 0L) tr("Crear meta") else tr("Editar meta")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nombre, { nombre = it.take(80) }, label = { Text(tr("Nombre")) }, singleLine = true)
            CampoMonto(monto, moneda, { monto = it }, tr("Objetivo"))
            ErrorFormulario(validacion ?: error)
        }
    }, confirmButton = { TextButton(enabled = !ocupado, onClick = {
        val valor = Dinero.parsear(monto)
        if (nombre.isBlank() || valor == null || valor <= 0) validacion = tr("Escribe un nombre y un objetivo mayor que cero.")
        else { validacion = null; onGuardar(inicial.copy(nombre = nombre.trim(), objetivoCentavos = valor)) }
    }) { Text(if (ocupado) tr("Guardando…") else tr("Guardar")) } }, dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text(tr("Cancelar")) } })
}

@Composable
private fun DialogoAporte(meta: Meta, retirar: Boolean, moneda: String, ocupado: Boolean, error: String?, reservado: Long, onCerrar: () -> Unit, onGuardar: (Long, String) -> Unit) {
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var validacion by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (retirar) tr("Retirar de {0}", meta.nombre) else tr("Aportar a {0}", meta.nombre)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Reservado: {0}", Dinero.formatear(reservado, moneda)))
            Text(if (retirar) tr("Este retiro libera dinero reservado. Tu saldo no cambia.") else tr("Este aporte reserva dinero que ya tienes. Tu saldo no cambia."))
            CampoMonto(monto, moneda, { monto = it })
            OutlinedTextField(nota, { nota = it.take(200) }, label = { Text(tr("Nota opcional")) })
            ErrorFormulario(validacion ?: error)
        }
    }, confirmButton = { TextButton(enabled = !ocupado, onClick = {
        val valor = Dinero.parsear(monto)
        when {
            valor == null || valor <= 0 -> validacion = tr("Escribe un monto mayor que cero.")
            retirar && valor > reservado -> validacion = tr("El retiro supera lo reservado en esta meta.")
            else -> { validacion = null; onGuardar(if (retirar) -valor else valor, nota) }
        }
    }) { Text(if (ocupado) tr("Guardando…") else if (retirar) tr("Confirmar retiro") else tr("Confirmar aporte")) } }, dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text(tr("Cancelar")) } })
}
