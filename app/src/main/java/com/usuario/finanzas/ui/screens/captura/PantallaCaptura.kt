package com.usuario.finanzas.ui.screens.captura

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.domain.finanzas.Dinero
import com.usuario.finanzas.ui.components.SelectorCategoria
import com.usuario.finanzas.ui.components.SelectorMovimiento
import com.usuario.finanzas.ui.i18n.tr
import com.usuario.finanzas.ui.screens.transacciones.DialogoFecha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaCaptura(navController: NavHostController, vm: CapturaViewModel = hiltViewModel(), modoInicial: String = "texto") {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var modo by rememberSaveable { mutableStateOf(modoInicial.takeIf { it in listOf("texto", "voz", "recibo") } ?: "texto") }
    var mostrarVoz by remember { mutableStateOf(false) }
    var modoPendiente by remember { mutableStateOf(ModoDictado.LOCAL) }
    var confirmarServicio by remember { mutableStateOf(false) }
    var editandoFecha by remember { mutableStateOf<BorradorEntrada?>(null) }
    var fotoPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var imagenElegida by rememberSaveable { mutableStateOf<String?>(null) }
    var verTexto by rememberSaveable { mutableStateOf(false) }
    val resultado by rememberUpdatedState<(String) -> Unit> { texto -> mostrarVoz = false; vm.dictado(texto) }
    val controlador = remember(context) { ControladorDictado(context) { resultado(it) } }
    val voz by controlador.estado.collectAsStateWithLifecycle()
    DisposableEffect(controlador, lifecycle) {
        val observer = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) { controlador.cancelar(); mostrarVoz = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controlador.destruir() }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) { mostrarVoz = true; controlador.iniciar(modoPendiente) }
        else { mostrarVoz = false; vm.aviso(tr("El micrófono necesita permiso para dictar.")) }
    }
    fun iniciarVoz(modoVoz: ModoDictado) { modoPendiente = modoVoz; permiso.launch(Manifest.permission.RECORD_AUDIO) }
    fun recibirImagen(uri: Uri) { imagenElegida = uri.toString(); verTexto = false; vm.leerRecibo(uri) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            recibirImagen(it)
        }
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) fotoPendiente?.let { recibirImagen(Uri.parse(it)) }
    }
    BackHandler(vm.ocupado) { if (vm.leyendoRecibo) vm.cancelarLectura() }
    val seleccionados = vm.borradores.count { it.incluido }
    Scaffold(
        topBar = { TopAppBar(title = { Text(tr("Añadir movimiento")) }, navigationIcon = {
            IconButton(onClick = { vm.cancelarLectura(); navController.popBackStack() }, enabled = !vm.ocupado || vm.leyendoRecibo) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Volver"))
            }
        }) },
        bottomBar = {
            if (vm.borradores.isNotEmpty()) Surface(shadowElevation = 8.dp, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vm.revisado, { vm.revisado = it }, enabled = !vm.ocupado)
                        Text(tr("He revisado los movimientos incluidos"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = { vm.guardar { navController.popBackStack() } }, enabled = vm.revisado && seleccionados > 0 && !vm.ocupado,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text(tr("Guardar seleccionados ({0})", seleccionados))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text(tr("Menos esfuerzo. Más claridad."), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(tr("Elige cómo registrarlo. Tú confirmas cada movimiento."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("texto", "Texto", Icons.Default.EditNote), Triple("voz", "Voz", Icons.Default.Mic), Triple("recibo", "Recibo", Icons.Default.Receipt)).forEach { (id, titulo, icono) ->
                        FilterChip(selected = modo == id, onClick = { modo = id }, enabled = !vm.ocupado, modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(tr(titulo)) }, leadingIcon = { Icon(icono, null, Modifier.size(18.dp)) })
                    }
                }
            }
            when (modo) {
                "texto" -> item {
                    PanelCaptura(Icons.Default.EditNote, "Escríbelo a tu manera", "Puedes pegar varios movimientos desde Excel.") {
                        OutlinedTextField(vm.texto, { vm.texto = it }, placeholder = { Text(tr("Almuerzo 25 mil + Uber 12k")) }, minLines = 4, maxLines = 9,
                            enabled = !vm.ocupado, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                        Button(onClick = vm::analizar, enabled = !vm.ocupado && vm.texto.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tr("Revisar movimientos")) }
                        Text(tr("Excel: Fecha, Concepto, Monto y Tipo, separados por tabuladores. Importes en {0}.", vm.moneda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "voz" -> item {
                    PanelCaptura(Icons.Default.Mic, "Dilo y revísalo", "Por ejemplo: almuerzo veinticinco mil pesos.") {
                        Button(onClick = { iniciarVoz(ModoDictado.LOCAL) }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(10.dp)); Text(tr("Comenzar a dictar"))
                        }
                        Text(tr("Verás cuándo está activo el micrófono. Puedes detenerlo en cualquier momento."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { confirmarServicio = true }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) { Text(tr("Usar servicio de voz del teléfono")) }
                        if (vm.texto.isNotBlank()) Text(vm.texto, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> item {
                    PanelCaptura(Icons.Default.Receipt, "Tu recibo, en un movimiento", "Fotografía el recibo completo, con buena luz y el total visible.") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                runCatching {
                                    val folder = File(context.cacheDir, "capturas").apply { mkdirs() }
                                    fotoPendiente = FileProvider.getUriForFile(context, "${context.packageName}.files", File.createTempFile("recibo_", ".jpg", folder)).toString()
                                    camara.launch(Uri.parse(fotoPendiente))
                                }.onFailure { vm.aviso(tr("No hay una cámara disponible. Selecciona una imagen.")) }
                            }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Cámara")) }
                            OutlinedButton(onClick = { galeria.launch(arrayOf("image/*")) }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tr("Galería")) }
                        }
                        (if (vm.leyendoRecibo) imagenElegida else vm.uriReciboActual)?.let { VistaPreviaRecibo(Uri.parse(it)) }
                        if (vm.leyendoRecibo) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(tr("Leyendo comercio, fecha y total…"), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = vm::cancelarLectura) { Text(tr("Cancelar")) }
                        }
                        if (vm.esReciboCargado) {
                            if (vm.candidatosTotales.size > 1) {
                                Text(tr("Encontré varios importes. Elige el total del recibo."), style = MaterialTheme.typography.bodyMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    vm.candidatosTotales.take(12).forEach { candidato ->
                                        SuggestionChip(onClick = { vm.elegirTotalRecibo(candidato.centavos) }, enabled = !vm.ocupado,
                                            label = { Text(Dinero.formatear(candidato.centavos, vm.moneda)) })
                                    }
                                }
                            }
                            TextButton(onClick = { verTexto = !verTexto }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.FindInPage, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr(if (verTexto) "Ocultar texto del recibo" else "Ver y corregir texto del recibo"))
                            }
                            if (verTexto) {
                                OutlinedTextField(vm.textoRecibo, { vm.textoRecibo = it }, enabled = !vm.ocupado, minLines = 4, maxLines = 10,
                                    label = { Text(tr("Texto leído")) }, modifier = Modifier.fillMaxWidth())
                                OutlinedButton(onClick = vm::reanalizarRecibo, enabled = !vm.ocupado && vm.textoRecibo.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(tr("Volver a interpretar")) }
                            }
                        }
                    }
                }
            }
            if (vm.ocupado && !vm.leyendoRecibo) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            vm.error?.let { mensaje -> item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Info, null); Text(tr(mensaje), modifier = Modifier.weight(1f)) }
                }
            } }
            if (vm.borradores.isNotEmpty()) item {
                Column {
                    Text(tr("Revisa antes de guardar"), style = MaterialTheme.typography.titleLarge)
                    Text(tr("Puedes corregir el importe, la fecha y la categoría."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(vm.borradores, key = { _, b -> b.id }) { indice, b ->
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Movimiento {0}", indice + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Checkbox(b.incluido, { vm.cambiar(b.copy(incluido = it)) }, enabled = !vm.ocupado, modifier = Modifier.semantics { contentDescription = tr("Incluir movimiento") })
                        }
                        OutlinedTextField(b.concepto, { vm.cambiar(b.copy(concepto = it)) }, enabled = !vm.ocupado, label = { Text(tr("Comercio o persona")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(b.monto, { vm.cambiar(b.copy(monto = it)) }, enabled = !vm.ocupado, label = { Text(tr("Importe") + " · " + vm.moneda) },
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        SelectorMovimiento(b.clase) { clase -> if (!vm.ocupado) vm.cambiar(b.copy(clase = clase, categoriaId = vm.categorias.firstOrNull { !it.archivada && it.nombre == "Otros" }?.id)) }
                        SelectorCategoria(vm.categorias.filter { !it.archivada && (it.direccion == "ambas" || it.direccion == b.clase.direccion) }, vm.categorias.find { it.id == b.categoriaId }, { if (!vm.ocupado) vm.cambiar(b.copy(categoriaId = it.id)) })
                        OutlinedButton(onClick = { editandoFecha = b }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Fecha: {0}", b.fecha)) }
                        b.aviso?.let { Text(tr(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (mostrarVoz) Dialog(onDismissRequest = { controlador.cancelar(); mostrarVoz = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            PanelDictado(voz, onDetener = controlador::detener, onCancelar = { controlador.cancelar(); mostrarVoz = false },
                onReintentar = { controlador.iniciar(voz.modo) }, onServicio = { confirmarServicio = true }, onAjustes = {
                    controlador.cancelar(); mostrarVoz = false
                    val intent = if (voz.codigoError == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")) else Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                    runCatching { context.startActivity(intent) }.onFailure { vm.aviso(tr("Abre los ajustes de voz o los permisos desde Ajustes de Android.")) }
                })
        }
    }
    if (confirmarServicio) AlertDialog(onDismissRequest = { confirmarServicio = false },
        title = { Text(tr("Servicio de voz del teléfono")) },
        text = { Text(tr("El proveedor de voz de tu teléfono puede procesar el audio en línea. Se enviará únicamente lo que dictes; no tus recibos ni tu historial.")) },
        confirmButton = { TextButton(onClick = { confirmarServicio = false; controlador.cancelar(); iniciarVoz(ModoDictado.SISTEMA) }) { Text(tr("Continuar con este servicio")) } },
        dismissButton = { TextButton(onClick = { confirmarServicio = false }) { Text(tr("Cancelar")) } })
    editandoFecha?.let { b -> DialogoFecha(b.fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), {
        vm.cambiar(b.copy(fecha = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()))
    }, { editandoFecha = null }) }
}

@Composable
private fun PanelCaptura(icono: ImageVector, titulo: String, descripcion: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icono, null, Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
            Text(tr(titulo), style = MaterialTheme.typography.titleLarge)
            Text(tr(descripcion), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Separate content permits rendering each microphone state without activating a real microphone. */
@Composable
internal fun PanelDictado(estado: EstadoDictado, onDetener: () -> Unit, onCancelar: () -> Unit, onReintentar: () -> Unit, onServicio: () -> Unit, onAjustes: () -> Unit) {
    val escuchando = estado.fase == FaseDictado.ESCUCHANDO
    val titulo = when (estado.fase) {
        FaseDictado.PREPARANDO -> "Preparando micrófono…"
        FaseDictado.ESCUCHANDO -> "Micrófono activo"
        FaseDictado.PROCESANDO -> "Procesando tu dictado…"
        FaseDictado.ERROR -> "No pudimos completar el dictado"
        else -> "Dictado detenido"
    }
    val nivel by animateFloatAsState(if (escuchando) estado.nivel else 0f, label = "nivelVoz")
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Keep microphone controls reachable while long messages or transcripts scroll.
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(88.dp).background(if (escuchando) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (estado.fase == FaseDictado.ERROR) Icons.Default.MicOff else Icons.Default.Mic, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(tr(titulo), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (estado.fase == FaseDictado.PREPARANDO || estado.fase == FaseDictado.PROCESANDO) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
            if (escuchando) {
                Text(tr("Te escucho. Habla con naturalidad."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = tr("Nivel de voz") }) {
                    val separacion = size.width / 13
                    repeat(11) { i ->
                        val altura = 8.dp.toPx() + (size.height - 8.dp.toPx()) * nivel * (1f - kotlin.math.abs(i - 5) / 8f)
                        drawRoundRect(color, Offset((i + 1) * separacion, (size.height - altura) / 2), Size(separacion * .45f, altura), CornerRadius(10f))
                    }
                }
            }
            if (estado.parcial.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(estado.parcial, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
            }
            estado.error?.let { Text(tr(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (escuchando) Button(onClick = onDetener, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text(tr("Terminar dictado")) }
            if (estado.fase == FaseDictado.ERROR) {
                Button(onClick = onReintentar, modifier = Modifier.fillMaxWidth()) { Text(tr("Intentar de nuevo")) }
                if (estado.modo == ModoDictado.LOCAL) OutlinedButton(onClick = onServicio, modifier = Modifier.fillMaxWidth()) { Text(tr("Usar servicio de voz del teléfono")) }
                TextButton(onClick = onAjustes) { Text(tr("Ajustes de voz y permisos")) }
            }
            TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr(if (estado.fase == FaseDictado.ERROR) "Cerrar" else "Cancelar")) }
        }
    }
}

@Composable
private fun VistaPreviaRecibo(uri: Uri) {
    val context = LocalContext.current
    val imagen by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val escala = minOf(1f, 1000f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * escala).toInt().coerceAtLeast(1), (info.size.height * escala).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.asImageBitmap()
                else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    imagen?.let { Image(it, tr("Vista previa del recibo"), modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Fit) }
}
