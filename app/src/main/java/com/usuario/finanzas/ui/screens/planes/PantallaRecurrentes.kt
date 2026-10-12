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
import com.usuario.finanzas.domain.finanzas.CalendarioRecurrente
import com.usuario.finanzas.domain.finanzas.Dinero
import com.usuario.finanzas.ui.components.SelectorCategoria
import com.usuario.finanzas.ui.util.formatearFecha

private val frecuenciasVisibles get() = listOf("diario" to tr("Diario"), "semanal" to tr("Semanal"), "quincenal" to tr("Cada 14 días"), "mensual" to tr("Mensual"), "anual" to tr("Anual"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRecurrentes(navController: NavHostController, viewModel: PlanesViewModel = hiltViewModel()) {
    val recurrentes by viewModel.recurrentes.collectAsStateWithLifecycle()
    val ocurrencias by viewModel.ocurrencias.collectAsStateWithLifecycle()
    val transacciones by viewModel.transacciones.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val config by viewModel.configuracion.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ocupado by viewModel.ocupado.collectAsStateWithLifecycle()
    var editar by remember { mutableStateOf<Recurrente?>(null) }
    var confirmar by remember { mutableStateOf<Pair<Recurrente, Long>?>(null) }
    var mostrarHistorial by rememberSaveable { mutableStateOf(false) }
    val registrados = ocurrencias.map { it.recurrenteId to it.fechaProgramada }.toSet()
    val vencimientos = recurrentes.flatMap { recurrente ->
        CalendarioRecurrente.vencimientos(recurrente).filter { (recurrente.id to it) !in registrados }.map { recurrente to it }
    }.sortedBy { it.second }
    Scaffold(topBar = { TopAppBar(title = { Text(tr("Recurrentes")) }, navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Volver")) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(tr("Cada vencimiento requiere tu confirmación. Si el movimiento ya existe, puedes vincularlo para evitar registrarlo dos veces."))
                Button(onClick = {
                    viewModel.limpiarError()
                    editar = Recurrente(nombre = "", montoCentavos = 0, categoriaId = 0, direccion = "salida", frecuencia = "mensual", fechaInicio = inicioHoy())
                }, enabled = !ocupado, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(tr("Crear recurrente")) }
                ErrorFormulario(error)
            }
            item { Text(tr("Pendientes ({0})", vencimientos.size), style = MaterialTheme.typography.titleLarge) }
            if (vencimientos.isEmpty()) item { Text(tr("No hay vencimientos por confirmar.")) }
            items(vencimientos.take(100), key = { "${it.first.id}_${it.second}" }) { (recurrente, fecha) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(recurrente.nombre, style = MaterialTheme.typography.titleMedium)
                        Text("${formatearFecha(fecha)} · ${if (recurrente.direccion == "entrada") tr("Ingreso") else tr("Gasto")} · ${Dinero.formatear(recurrente.montoCentavos, config.moneda)}")
                        OutlinedButton(enabled = !ocupado, onClick = { viewModel.limpiarError(); confirmar = recurrente to fecha }) { Text(tr("Revisar y confirmar")) }
                    }
                }
            }
            if (vencimientos.size > 100) item { Text(tr("Se muestran los 100 vencimientos más antiguos. Al confirmarlos aparecerán los siguientes.")) }
            item { Text(tr("Programaciones"), style = MaterialTheme.typography.titleLarge) }
            if (recurrentes.isEmpty()) item { Text(tr("Agrega tus ingresos o gastos que se repiten.")) }
            items(recurrentes, key = { "programacion_${it.id}" }) { recurrente ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(recurrente.nombre, style = MaterialTheme.typography.titleMedium)
                        Text("${Dinero.formatear(recurrente.montoCentavos, config.moneda)} · ${frecuenciasVisibles.find { it.first == recurrente.frecuencia }?.second}")
                        Text("${categorias.find { it.id == recurrente.categoriaId }?.nombre ?: tr("Categoría")} · ${if (recurrente.direccion == "entrada") tr("Ingreso") else tr("Gasto")}")
                        Text(tr("Desde {0} · {1}", formatearFecha(recurrente.fechaInicio), if (recurrente.activo) tr("Activo") else tr("Pausado")))
                        Row {
                            TextButton(onClick = { viewModel.limpiarError(); editar = recurrente }, enabled = !ocupado) { Text(tr("Editar")) }
                            TextButton(onClick = { viewModel.guardar(recurrente.copy(activo = !recurrente.activo)) {} }, enabled = !ocupado) { Text(if (recurrente.activo) tr("Pausar") else tr("Reanudar")) }
                        }
                    }
                }
            }
            item { TextButton(onClick = { mostrarHistorial = !mostrarHistorial }) { Text(if (mostrarHistorial) tr("Ocultar confirmados") else tr("Ver confirmados ({0})", ocurrencias.size)) } }
            if (mostrarHistorial) items(ocurrencias, key = { "confirmado_${it.recurrenteId}_${it.fechaProgramada}" }) { ocurrencia ->
                val movimiento = transacciones.find { it.id == ocurrencia.transaccionId }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(recurrentes.find { it.id == ocurrencia.recurrenteId }?.nombre ?: tr("Recurrente"))
                        Text(tr("Vencimiento: {0}", formatearFecha(ocurrencia.fechaProgramada)))
                        if (movimiento != null) TextButton(onClick = { navController.navigate("detalle_transaccion/${movimiento.id}") }) { Text(tr("Ver movimiento registrado")) }
                        else Text(tr("El movimiento fue eliminado. El vencimiento conserva su confirmación."))
                    }
                }
            }
        }
    }
    editar?.let { inicial ->
        DialogoRecurrente(inicial, categorias, config.moneda, ocupado, error,
            onCerrar = { if (!ocupado) editar = null }, onGuardar = { viewModel.guardar(it) { editar = null } })
    }
    confirmar?.let { (recurrente, fecha) ->
        val vinculadas = ocurrencias.mapNotNull { it.transaccionId }.toSet()
        val candidatas = transacciones.filter { it.direccion == recurrente.direccion && it.tipo != "transferencia_propia" && it.id !in vinculadas && it.fecha <= System.currentTimeMillis() }
        DialogoConfirmarRecurrente(recurrente, fecha, candidatas, config.moneda, ocupado, error,
            onCerrar = { if (!ocupado) confirmar = null },
            onConfirmar = { id -> viewModel.registrar(recurrente.id, fecha, id) { confirmar = null } })
    }
}

@Composable
private fun DialogoRecurrente(inicial: Recurrente, categorias: List<Categoria>, moneda: String, ocupado: Boolean, error: String?, onCerrar: () -> Unit, onGuardar: (Recurrente) -> Unit) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var monto by remember { mutableStateOf(if (inicial.montoCentavos == 0L) "" else Dinero.editable(inicial.montoCentavos)) }
    var direccion by remember { mutableStateOf(inicial.direccion) }
    var categoriaId by remember { mutableIntStateOf(inicial.categoriaId) }
    var frecuencia by remember { mutableStateOf(inicial.frecuencia) }
    var fecha by remember { mutableLongStateOf(inicial.fechaInicio) }
    var validacion by remember { mutableStateOf<String?>(null) }
    val disponibles = categorias.filter { (!it.archivada || it.id == inicial.categoriaId) && it.direccion in listOf(direccion, "ambas") }
    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (inicial.id == 0L) tr("Crear recurrente") else tr("Editar recurrente")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nombre, { nombre = it.take(80) }, label = { Text(tr("Nombre")) }, singleLine = true)
            CampoMonto(monto, moneda, { monto = it })
            SelectorOpcion(tr("Movimiento"), direccion, listOf("entrada" to tr("Ingreso"), "salida" to tr("Gasto")), { direccion = it; categoriaId = 0 })
            SelectorCategoria(disponibles, disponibles.find { it.id == categoriaId }, { categoriaId = it.id })
            SelectorOpcion(tr("Frecuencia"), frecuencia, frecuenciasVisibles, { frecuencia = it })
            CampoFecha(fecha, { fecha = it }, tr("Primera fecha"), permitirFuturo = true)
            if (inicial.id != 0L) Text(tr("Los vencimientos confirmados se conservan. Cambiar la primera fecha o la frecuencia puede generar nuevos vencimientos pendientes."), style = MaterialTheme.typography.bodySmall)
            ErrorFormulario(validacion ?: error)
        }
    }, confirmButton = { TextButton(enabled = !ocupado, onClick = {
        val valor = Dinero.parsear(monto)
        when {
            nombre.isBlank() || valor == null || valor <= 0 -> validacion = tr("Escribe un nombre y un monto mayor que cero.")
            disponibles.none { it.id == categoriaId } -> validacion = tr("Selecciona una categoría.")
            else -> { validacion = null; onGuardar(inicial.copy(nombre = nombre.trim(), montoCentavos = valor, direccion = direccion, categoriaId = categoriaId, frecuencia = frecuencia, fechaInicio = fecha)) }
        }
    }) { Text(if (ocupado) tr("Guardando…") else tr("Guardar")) } }, dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text(tr("Cancelar")) } })
}

@Composable
private fun DialogoConfirmarRecurrente(recurrente: Recurrente, fecha: Long, candidatas: List<Transaccion>, moneda: String, ocupado: Boolean, error: String?, onCerrar: () -> Unit, onConfirmar: (Int?) -> Unit) {
    var modo by remember { mutableStateOf("nuevo") }
    var seleccionada by remember { mutableStateOf<Int?>(null) }
    var busqueda by remember { mutableStateOf("") }
    val elegida = candidatas.find { it.id == seleccionada }
    val filtradas = candidatas.filter { busqueda.isBlank() || "${it.nombreComercio} ${it.descripcion} ${formatearFecha(it.fecha)} ${Dinero.formatear(CalculadoraFinanciera.monto(it), moneda)}".contains(busqueda, ignoreCase = true) }
    AlertDialog(onDismissRequest = onCerrar, title = { Text(recurrente.nombre) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Vencimiento: {0}", formatearFecha(fecha)))
            Text("${if (recurrente.direccion == "entrada") tr("Ingreso") else tr("Gasto")}: ${Dinero.formatear(recurrente.montoCentavos, moneda)}")
            SelectorOpcion(tr("Confirmar mediante"), modo, listOf("nuevo" to tr("Registrar movimiento nuevo"), "vinculo" to tr("Vincular movimiento existente")), { modo = it })
            if (modo == "nuevo") {
                Text(tr("Se guardará un movimiento por este monto y fecha. Confirma que ocurrió y que todavía no está registrado."))
            } else {
                Text(tr("Selecciona el movimiento real que corresponde a este vencimiento. Su monto y fecha se conservan."))
                OutlinedTextField(busqueda, { busqueda = it }, label = { Text(tr("Buscar comercio, monto o fecha")) }, singleLine = true)
                if (filtradas.isEmpty()) Text(tr("No hay movimientos disponibles que coincidan."))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(filtradas, key = { it.id }) { transaccion ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(selected = seleccionada == transaccion.id, onClick = { seleccionada = transaccion.id })
                            TextButton(onClick = { seleccionada = transaccion.id }) {
                                Column {
                                    Text(transaccion.nombreComercio.ifBlank { transaccion.descripcion.ifBlank { tr("Movimiento") } })
                                    Text("${formatearFecha(transaccion.fecha)} · ${Dinero.formatear(CalculadoraFinanciera.monto(transaccion), moneda)}")
                                }
                            }
                        }
                    }
                }
                elegida?.let { Text(tr("Se vinculará: {0} del {1}. No se crea otro movimiento.", Dinero.formatear(CalculadoraFinanciera.monto(it), moneda), formatearFecha(it.fecha))) }
            }
            ErrorFormulario(error)
        }
    }, confirmButton = { TextButton(enabled = !ocupado && (modo == "nuevo" || elegida != null), onClick = { onConfirmar(if (modo == "nuevo") null else elegida!!.id) }) { Text(if (ocupado) tr("Guardando…") else if (modo == "nuevo") tr("Registrar movimiento") else tr("Vincular seleccionada")) } }, dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text(tr("Cancelar")) } })
}
