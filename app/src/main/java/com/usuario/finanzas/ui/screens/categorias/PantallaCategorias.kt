package com.usuario.finanzas.ui.screens.categorias

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.ui.screens.planes.ErrorFormulario
import com.usuario.finanzas.ui.screens.planes.SelectorOpcion
import com.usuario.finanzas.ui.util.colorDesdeHex
import com.usuario.finanzas.ui.util.iconoCategoria

private val tiposCategoria get() = listOf("salida" to tr("Gastos"), "entrada" to tr("Ingresos"), "ambas" to tr("Ingresos y gastos"))
private val coloresCategoria get() = listOf("#4CAF50" to tr("Verde"), "#2196F3" to tr("Azul"), "#FF9800" to tr("Naranja"), "#9C27B0" to tr("Morado"), "#E91E63" to tr("Rosa"), "#26A69A" to tr("Turquesa"), "#607D8B" to tr("Gris"))
private val iconosCategoria get() = listOf("Category" to tr("General"), "Restaurant" to tr("Comida"), "DirectionsCar" to tr("Transporte"), "Movie" to tr("Entretenimiento"), "LocalHospital" to tr("Salud"), "Home" to tr("Hogar"), "ReceiptLong" to tr("Recibo"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCategorias(navController: NavHostController, viewModel: CategoriasViewModel = hiltViewModel()) {
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ocupado by viewModel.ocupado.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf("todas") }
    var archivadas by rememberSaveable { mutableStateOf(false) }
    var editar by remember { mutableStateOf<Categoria?>(null) }
    val visibles = categorias.filter { (!it.archivada || archivadas) && (filtro == "todas" || it.direccion in listOf(filtro, "ambas")) }
    Scaffold(topBar = { TopAppBar(title = { Text(tr("Categorías")) }, navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Volver")) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(tr("Personaliza las categorías para tus ingresos y gastos. Archivar conserva todos tus movimientos anteriores."))
                Button(onClick = {
                    viewModel.limpiarError()
                    editar = Categoria(nombre = "", color = "#4CAF50", icono = "Category", direccion = if (filtro == "entrada") "entrada" else "salida")
                }, enabled = !ocupado, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text(tr("Crear categoría")) }
                SelectorOpcion(tr("Mostrar"), filtro, listOf("todas" to tr("Todas"), "entrada" to tr("Ingresos"), "salida" to tr("Gastos")), { filtro = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(archivadas, { archivadas = it })
                    Text(tr("Mostrar archivadas"))
                }
                ErrorFormulario(error)
            }
            if (visibles.isEmpty()) item { Text(tr("No hay categorías para este filtro.")) }
            items(visibles, key = { it.id }) { categoria ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(iconoCategoria(categoria.icono), null, tint = colorDesdeHex(categoria.color))
                            Column(Modifier.weight(1f)) {
                                Text(categoria.nombre, style = MaterialTheme.typography.titleMedium)
                                Text("${tiposCategoria.find { it.first == categoria.direccion }?.second ?: categoria.direccion}${if (categoria.archivada) tr(" · Archivada") else ""}")
                            }
                        }
                        Row {
                            TextButton(onClick = { viewModel.limpiarError(); editar = categoria }, enabled = !ocupado) { Text(tr("Editar")) }
                            if (categoria.nombre != "Otros") TextButton(onClick = { viewModel.guardar(categoria.copy(archivada = !categoria.archivada)) }, enabled = !ocupado) { Text(if (categoria.archivada) tr("Reactivar") else tr("Archivar")) }
                        }
                        if (categoria.nombre == "Otros") Text(tr("Siempre disponible para ingresos y gastos."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    editar?.let { inicial ->
        DialogoCategoria(inicial, ocupado, error, onCerrar = { if (!ocupado) editar = null }, onGuardar = { viewModel.guardar(it) { editar = null } })
    }
}

@Composable
fun DialogoCategoria(inicial: Categoria, ocupado: Boolean, error: String?, onCerrar: () -> Unit, onGuardar: (Categoria) -> Unit) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var direccion by remember { mutableStateOf(inicial.direccion) }
    var color by remember { mutableStateOf(inicial.color) }
    var icono by remember { mutableStateOf(inicial.icono) }
    var validacion by remember { mutableStateOf<String?>(null) }
    val protegida = inicial.id != 0 && inicial.nombre == "Otros"
    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (inicial.id == 0) tr("Crear categoría") else tr("Editar categoría")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nombre, { nombre = it.take(50) }, label = { Text(tr("Nombre")) }, singleLine = true, enabled = !protegida)
            if (protegida) Text(tr("Otros se conserva para ingresos y gastos."))
            else SelectorOpcion(tr("Usar en"), direccion, tiposCategoria, { direccion = it })
            SelectorOpcion(tr("Color"), color, coloresCategoria.let { if (it.any { par -> par.first == color }) it else it + (color to tr("Color actual")) }, { color = it })
            SelectorOpcion(tr("Ícono"), icono, iconosCategoria.let { if (it.any { par -> par.first == icono }) it else it + (icono to tr("Ícono actual")) }, { icono = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(iconoCategoria(icono), null, tint = colorDesdeHex(color))
                Text(nombre.ifBlank { tr("Vista previa") })
            }
            ErrorFormulario(validacion ?: error)
        }
    }, confirmButton = { TextButton(enabled = !ocupado, onClick = {
        if (nombre.isBlank()) validacion = tr("Escribe un nombre.")
        else { validacion = null; onGuardar(inicial.copy(nombre = nombre.trim(), direccion = if (protegida) "ambas" else direccion, color = color, icono = icono)) }
    }) { Text(if (ocupado) tr("Guardando…") else tr("Guardar")) } }, dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text(tr("Cancelar")) } })
}
