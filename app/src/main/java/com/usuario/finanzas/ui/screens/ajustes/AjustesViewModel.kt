package com.usuario.finanzas.ui.screens.ajustes

import com.usuario.finanzas.ui.i18n.tr

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usuario.finanzas.data.backup.RespaldoRepository
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.data.preferences.PreferenciasApp
import com.usuario.finanzas.data.repository.FinanzasRepository
import com.usuario.finanzas.data.repository.TransaccionRepository
import com.usuario.finanzas.data.reportes.ExportadorReporte
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AjustesViewModel @Inject constructor(private val prefs:PreferenciasApp, private val finanzas:FinanzasRepository, private val respaldos:RespaldoRepository, transacciones:TransaccionRepository, private val exportador:ExportadorReporte) : ViewModel() {
    val preferencias=prefs.preferencias
    val configuracion=finanzas.observarConfiguracion().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),ConfiguracionFinanciera())
    val movimientos=transacciones.observarTodas().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    var ocupado by mutableStateOf(false);private set
    var mensaje by mutableStateOf<String?>(null);private set
    fun idioma(codigo:String) = prefs.idioma(codigo)
    fun tema(tema:String) = prefs.tema(tema)
    fun biometria(activada:Boolean) = prefs.biometria(activada)
    fun ocultar(activado:Boolean) = prefs.ocultar(activado)
    fun guardar(config:ConfiguracionFinanciera) = ejecutar {finanzas.guardarConfiguracion(config);mensaje=tr("Configuración guardada")}
    fun exportar(uri:Uri,password:String) = ejecutar {respaldos.exportar(uri,password);mensaje=tr("Respaldo cifrado creado")}
    fun restaurar(uri:Uri,password:String,onFin:()->Unit) = ejecutar {respaldos.restaurar(uri,password);mensaje=tr("Respaldo restaurado");onFin()}
    fun borrar(pin:String,onFin:()->Unit) = ejecutar {respaldos.borrarDatos(pin);onFin()}
    fun reporte(uri:Uri,lista:List<Transaccion>,moneda:String,desde:LocalDate,hasta:LocalDate,csv:Boolean) = ejecutar {
        if(csv) exportador.csv(uri,lista,moneda) else exportador.pdf(uri,lista,moneda,desde,hasta)
        mensaje=tr("Reporte generado")
    }
    private fun ejecutar(bloque:suspend()->Unit) {
        if(ocupado)return
        ocupado=true;mensaje=null
        viewModelScope.launch {try{bloque()}catch(e:CancellationException){throw e}catch(e:Exception){mensaje=e.message?.let { tr(it) }?:tr("No se pudo completar la operación")}finally{ocupado=false}}
    }
}
