package com.usuario.finanzas.ui.screens.onboarding

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.domain.finanzas.Dinero
import com.usuario.finanzas.ui.screens.planes.*
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.ui.screens.categorias.DialogoCategoria
import com.usuario.finanzas.ui.util.iconoCategoria
import com.usuario.finanzas.ui.util.colorDesdeHex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaOnboarding(navController: NavHostController, viewModel: OnboardingViewModel = hiltViewModel()) {
    val configuracion by viewModel.configuracion.collectAsStateWithLifecycle()
    val ocupado by viewModel.ocupado.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val preferencias by viewModel.preferencias.preferencias.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var seleccionadas by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var conocidas by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var filtro by rememberSaveable { mutableStateOf("salida") }
    var creando by remember { mutableStateOf(false) }
    var idiomas by remember { mutableStateOf(false) }
    LaunchedEffect(categorias) {
        seleccionadas = (seleccionadas + categorias.filter { it.id !in conocidas && !it.archivada }.map { it.id }).distinct()
        conocidas = categorias.map { it.id }
    }
    val terminar: () -> Unit = {
        navController.navigate("dashboard") {
            popUpTo("onboarding") { inclusive = true }
            launchSingleTop = true
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(tr("Configura tus finanzas")) }, actions = {
        Box {
            TextButton(onClick = { idiomas = true }) { Text(preferencias.idioma.uppercase()) }
            DropdownMenu(idiomas, { idiomas = false }) {
                listOf("es" to "Español", "en" to "English", "pt" to "Português", "fr" to "Français").forEach { (codigo, nombre) ->
                    DropdownMenuItem(text = { Text(nombre) }, onClick = { viewModel.preferencias.idioma(codigo); idiomas = false; (context as? Activity)?.recreate() })
                }
            }
        }
    }) }) { padding ->
        val config = configuracion
        if (config == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        } else {
            var moneda by rememberSaveable { mutableStateOf(config.moneda) }
            var saldo by rememberSaveable { mutableStateOf(Dinero.editable(config.saldoInicialCentavos)) }
            var fecha by rememberSaveable { mutableLongStateOf(if (config.fechaInicial == 0L && !config.onboardingCompleto) inicioHoy() else config.fechaInicial) }
            var validacion by remember { mutableStateOf<String?>(null) }
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(tr("Empieza a tu manera"), style = MaterialTheme.typography.headlineMedium)
                Text(tr("Esta configuración es opcional. Puedes ajustarla después desde Ajustes y personalizar tus categorías cuando quieras."))
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("Moneda y saldo inicial"), style = MaterialTheme.typography.titleLarge)
                        SelectorOpcion(tr("Moneda"), moneda, listOf("COP" to tr("COP · Peso colombiano"), "USD" to tr("USD · Dólar estadounidense"), "EUR" to tr("EUR · Euro"), "BRL" to tr("BRL · Real brasileño")), { moneda = it })
                        Text(tr("Todos los importes usan esta moneda. Si ya hay importes registrados, se conserva la moneda actual."), style = MaterialTheme.typography.bodySmall)
                        CampoMonto(saldo, moneda, { saldo = it }, tr("Saldo al comenzar la fecha"))
                        CampoFecha(fecha, { fecha = it }, tr("Fecha inicial"))
                        Text(tr("El saldo se calcula desde el comienzo del día elegido: saldo inicial + ingresos − gastos. Los movimientos anteriores siguen en tu historial y no cambian este saldo. Puedes escribir un saldo negativo."), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("Categorías de ingresos y gastos"), style = MaterialTheme.typography.titleLarge)
                        Text(tr("Ya tienes categorías para empezar. Puedes agregar, editar o archivar las tuyas. Otros siempre estará disponible."))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(filtro == "salida", { filtro = "salida" }, label = { Text(tr("Gastos")) })
                            FilterChip(filtro == "entrada", { filtro = "entrada" }, label = { Text(tr("Ingresos")) })
                        }
                        categorias.filter { it.direccion == filtro || it.direccion == "ambas" }.chunked(2).forEach { fila ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                fila.forEach { categoria ->
                                    val seleccionada = categoria.id in seleccionadas || categoria.nombre == "Otros"
                                    Card(onClick = {
                                        if (categoria.nombre != "Otros") seleccionadas = if (seleccionada) seleccionadas - categoria.id else seleccionadas + categoria.id
                                    }, enabled = !ocupado, modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (seleccionada) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(iconoCategoria(categoria.icono), null, tint = colorDesdeHex(categoria.color))
                                                Checkbox(seleccionada, onCheckedChange = null)
                                            }
                                            Text(categoria.nombre)
                                        }
                                    }
                                }
                                if (fila.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        OutlinedButton(onClick = { creando = true }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) { Text(tr("+ Crear nueva")) }
                    }
                }
                ErrorFormulario(validacion ?: error)
              }
                Button(enabled = !ocupado && categorias.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                    val monto = Dinero.parsear(saldo, permitirNegativo = true)
                    if (monto == null) validacion = tr("Escribe un saldo válido, con hasta dos decimales.")
                    else {
                        validacion = null
                        viewModel.guardar(config.copy(moneda = moneda, saldoInicialCentavos = monto, fechaInicial = fecha), seleccionadas, terminar)
                    }
                }) { Text(if (ocupado) tr("Guardando…") else tr("Continuar con {0}", seleccionadas.size)) }
                TextButton(enabled = !ocupado, onClick = { viewModel.guardar(config, listo = terminar) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Omitir por ahora")) }
            }
        }
    }
    if (creando) DialogoCategoria(Categoria(nombre = "", color = "#26A69A", icono = "Category", direccion = filtro), ocupado, error,
        onCerrar = { if (!ocupado) creando = false }, onGuardar = { viewModel.crearCategoria(it) { creando = false } })
}
