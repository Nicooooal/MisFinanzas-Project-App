package com.usuario.finanzas.ui.screens.ajustes

import com.usuario.finanzas.ui.i18n.tr

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.usuario.finanzas.domain.finanzas.*
import com.usuario.finanzas.ui.navigation.BarraInferior
import com.usuario.finanzas.ui.screens.transacciones.DialogoFecha
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(navController:NavHostController,vm:AjustesViewModel=hiltViewModel()) {
    val context=LocalContext.current
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    val config by vm.configuracion.collectAsStateWithLifecycle()
    var saldo by remember { mutableStateOf("") }
    var moneda by remember { mutableStateOf("COP") }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var picker by remember { mutableStateOf(false) }
    var accion by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var clavePendiente by remember { mutableStateOf("") }
    LaunchedEffect(config) {saldo=Dinero.editable(config.saldoInicialCentavos);moneda=config.moneda;fecha=if(config.fechaInicial==0L)LocalDate.now()else CalculadoraFinanciera.fechaLocal(config.fechaInicial)}
    val crear=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null)vm.exportar(uri,clavePendiente)
        clavePendiente=""
    }
    val abrir=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)vm.restaurar(uri,clavePendiente){(context as? Activity)?.recreate()}
        clavePendiente=""
    }
    Scaffold(topBar={TopAppBar(title={Text(tr("Ajustes"))})},bottomBar={BarraInferior(navController,"ajustes")}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Text(tr("Tus datos se guardan en este dispositivo"),style=MaterialTheme.typography.titleMedium)}
            item{Text(tr("Idioma"),style=MaterialTheme.typography.titleMedium)}
            item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("es" to "ES","en" to "EN","pt" to "PT","fr" to "FR").forEach{(codigo,nombre)->FilterChip(prefs.idioma==codigo,{vm.idioma(codigo);(context as? Activity)?.recreate()},label={Text(nombre)})}}}
            item{Text(tr("Apariencia"),style=MaterialTheme.typography.titleMedium)}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("sistema" to tr("Sistema"),"claro" to tr("Claro"),"oscuro" to tr("Oscuro")).forEach{(codigo,nombre)->FilterChip(prefs.tema==codigo,{vm.tema(codigo)},label={Text(nombre)})}}}
            item{FilaSwitch(tr("Ocultar importes"),prefs.ocultar,vm::ocultar)}
            item{FilaSwitch(tr("Autenticación biométrica"),prefs.biometria,vm::biometria)}
            item{Text(tr("El PIN sigue disponible. La app se bloquea al volver después de un minuto fuera."),style=MaterialTheme.typography.bodySmall)}
            item{HorizontalDivider()}
            item{Text(tr("Saldo inicial y moneda"),style=MaterialTheme.typography.titleMedium)}
            item{OutlinedTextField(saldo,{saldo=it},label={Text(tr("Saldo al comienzo de la fecha elegida"))},modifier=Modifier.fillMaxWidth())}
            item{OutlinedButton(onClick={picker=true}){Text(tr("Fecha inicial: {0}", fecha))}}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("COP","USD","EUR","BRL").forEach{c->FilterChip(moneda==c,{moneda=c},label={Text(c)})}}}
            item{Text(tr("La moneda solo puede cambiarse antes de registrar importes. No convierte el historial. La detección bancaria automática está disponible para COP."),style=MaterialTheme.typography.bodySmall)}
            item{Button(onClick={Dinero.parsear(saldo,true)?.let{vm.guardar(config.copy(saldoInicialCentavos=it,fechaInicial=fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),moneda=moneda))}},enabled=!vm.ocupado&&Dinero.parsear(saldo,true)!=null){Text(tr("Guardar saldo inicial"))}}
            listOf(tr("Administrar categorías") to "categorias",tr("Configurar movimientos recurrentes") to "recurrentes",tr("Exportar reportes") to "reportes",tr("Revisar configuración inicial") to "onboarding").forEach{(nombre,ruta)->item{OutlinedButton(onClick={navController.navigate(ruta)},enabled=!vm.ocupado,modifier=Modifier.fillMaxWidth()){Text(nombre)}}}
            item{OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},enabled=!vm.ocupado,modifier=Modifier.fillMaxWidth()){Text(tr("Acceso a notificaciones bancarias"))}}
            item{HorizontalDivider()}
            item{Text(tr("Respaldo y restauración"),style=MaterialTheme.typography.titleMedium)}
            item{Text(tr("Guarda una copia cifrada con contraseña. Incluye historial, planes y comprobantes. Conserva la contraseña para restaurarla en otro teléfono."),style=MaterialTheme.typography.bodySmall)}
            item{Button(onClick={accion="exportar";password=""},enabled=!vm.ocupado,modifier=Modifier.fillMaxWidth()){Text(tr("Crear respaldo"))}}
            item{OutlinedButton(onClick={accion="restaurar";password=""},enabled=!vm.ocupado,modifier=Modifier.fillMaxWidth()){Text(tr("Restaurar respaldo"))}}
            item{TextButton(onClick={accion="borrar";password=""},enabled=!vm.ocupado){Text(tr("Eliminar mis datos"),color=MaterialTheme.colorScheme.error)}}
            if(vm.ocupado)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            vm.mensaje?.let{m->item{Text(m)}}
        }
    }
    if (vm.ocupado) AlertDialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(tr("Procesando datos")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(tr("Espera a que termine la operación."))
        } },
        confirmButton = {}
    )
    if(picker)DialogoFecha(fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),{fecha=Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()},{picker=false})
    accion?.let{a->AlertDialog(onDismissRequest={accion=null;password=""},title={Text(when(a){"borrar"->tr("Eliminar mis datos");"restaurar"->tr("Restaurar respaldo");else->tr("Contraseña del respaldo")})},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(when(a){"borrar"->tr("Se borrarán movimientos, planes y comprobantes de este dispositivo. Escribe tu PIN para confirmar.");"restaurar"->tr("La copia reemplazará los datos actuales. Crea antes un respaldo si quieres conservarlos.");else->tr("Utiliza una contraseña de al menos 8 caracteres. No se guarda en la app.")})
        OutlinedTextField(password,{password=it},visualTransformation=PasswordVisualTransformation(),singleLine=true,label={Text(if(a=="borrar")"PIN" else tr("Contraseña"))})
    }},confirmButton={TextButton(enabled=if(a=="borrar")password.length>=4 else password.length>=8,onClick={
        if(a=="borrar")vm.borrar(password){(context as? Activity)?.recreate()}
        else{clavePendiente=password;if(a=="exportar")crear.launch("MisFinanzas-${LocalDate.now()}.mfin")else abrir.launch(arrayOf("*/*"))}
        password="";accion=null
    }){Text(tr("Confirmar"))}},dismissButton={TextButton(onClick={accion=null;password=""}){Text(tr("Cancelar"))}})}
}

@Composable private fun FilaSwitch(titulo:String,valor:Boolean,cambiar:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(titulo,modifier=Modifier.weight(1f));Switch(valor,cambiar)}}

private data class SolicitudReporte(val desde:LocalDate,val hasta:LocalDate,val movimientos:List<com.usuario.finanzas.data.local.entities.Transaccion>,val moneda:String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaReportes(navController:NavHostController,vm:AjustesViewModel=hiltViewModel()) {
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val config by vm.configuracion.collectAsStateWithLifecycle()
    var desde by remember{mutableStateOf(LocalDate.now().withDayOfMonth(1))}
    var hasta by remember{mutableStateOf(LocalDate.now())}
    var picker by remember{mutableStateOf<String?>(null)}
    var solicitud by remember{mutableStateOf<SolicitudReporte?>(null)}
    val zona=ZoneId.systemDefault()
    val lista=remember(movimientos,desde,hasta){val a=desde.atStartOfDay(zona).toInstant().toEpochMilli();val b=hasta.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli();movimientos.filter{it.fecha>=a&&it.fecha<b}}
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->if(uri!=null)solicitud?.let{vm.reporte(uri,it.movimientos,it.moneda,it.desde,it.hasta,false)};solicitud=null}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)solicitud?.let{vm.reporte(uri,it.movimientos,it.moneda,it.desde,it.hasta,true)};solicitud=null}
    Scaffold(topBar={TopAppBar(title={Text(tr("Exportar reportes"))},navigationIcon={TextButton(onClick={navController.popBackStack()}){Text(tr("Volver"))}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            OutlinedButton(onClick={picker="desde"}){Text(tr("Fecha inicio: {0}", desde))}
            OutlinedButton(onClick={picker="hasta"}){Text(tr("Fecha fin: {0}", hasta))}
            Text(tr("{0} movimientos en el período", lista.size))
            if(desde>hasta)Text(tr("La fecha inicial debe ser anterior o igual a la final."),color=MaterialTheme.colorScheme.error)
            Button(enabled=lista.isNotEmpty()&&desde<=hasta&&!vm.ocupado,onClick={solicitud=SolicitudReporte(desde,hasta,lista.toList(),config.moneda);pdf.launch("MisFinanzas-$desde-$hasta.pdf")},modifier=Modifier.fillMaxWidth()){Text(tr("Generar PDF"))}
            OutlinedButton(enabled=lista.isNotEmpty()&&desde<=hasta&&!vm.ocupado,onClick={solicitud=SolicitudReporte(desde,hasta,lista.toList(),config.moneda);csv.launch("MisFinanzas-$desde-$hasta.csv")},modifier=Modifier.fillMaxWidth()){Text(tr("Exportar CSV"))}
            Text(tr("El reporte contiene importes completos aunque esté activo el modo privacidad."),style=MaterialTheme.typography.bodySmall)
            if(vm.ocupado)LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.mensaje?.let{Text(it)}
        }
    }
    picker?.let{p->DialogoFecha((if(p=="desde")desde else hasta).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),{val f=Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate();if(p=="desde")desde=f else hasta=f},{picker=null})}
}
