package com.usuario.finanzas.ui.screens.captura

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.Executor

enum class ModoDictado { LOCAL, SISTEMA }
enum class FaseDictado { INACTIVO, PREPARANDO, ESCUCHANDO, PROCESANDO, ERROR }

data class EstadoDictado(
    val fase: FaseDictado = FaseDictado.INACTIVO,
    val parcial: String = "",
    val nivel: Float = 0f,
    val error: String? = null,
    val codigoError: Int? = null,
    val modo: ModoDictado = ModoDictado.LOCAL,
    val idioma: String = "es-CO"
) {
    // Preparar el servicio todavía no confirma que tenga abierto el micrófono.
    val microfonoActivo: Boolean get() = fase == FaseDictado.ESCUCHANDO
    val ocupado: Boolean get() = fase in setOf(FaseDictado.PREPARANDO, FaseDictado.ESCUCHANDO, FaseDictado.PROCESANDO)
}

/** Una sesión por pantalla; llamar cancelar al pasar a segundo plano y destruir al salir. */
class ControladorDictado internal constructor(
    private val fabrica: FabricaDictado,
    private val onResultado: (String) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    constructor(context: Context, onResultado: (String) -> Unit) :
        this(FabricaAndroidDictado(context.applicationContext), onResultado)

    private val _estado = MutableStateFlow(EstadoDictado())
    val estado: StateFlow<EstadoDictado> = _estado.asStateFlow()
    private var motor: MotorDictado? = null
    private var sesion = 0L
    private var destruido = false
    private var temporizador: Runnable? = null
    private var consultaPendiente = false
    private var escuchaIniciada = false

    fun iniciar(modo: ModoDictado = ModoDictado.LOCAL, idioma: Locale = Locale.getDefault()) = principal {
        if (destruido || _estado.value.ocupado) return@principal
        liberar()
        val token = sesion
        val etiqueta = IdiomaDictado.preferido(idioma)
        _estado.value = EstadoDictado(fase = FaseDictado.PREPARANDO, modo = modo, idioma = etiqueta)
        try {
            if (!fabrica.tienePermiso()) {
                fallar(token, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                return@principal
            }
            if (!fabrica.disponible(modo)) {
                fallar(token, if (modo == ModoDictado.LOCAL) LOCAL_NO_DISPONIBLE else SERVICIO_NO_DISPONIBLE)
                return@principal
            }
            val nuevo = fabrica.crear(modo)
            motor = nuevo
            nuevo.escuchar(eventos(token))
            val intent = intentDictado(etiqueta, modo)
            if (modo == ModoDictado.LOCAL && fabrica.puedeConsultarIdiomas()) {
                consultaPendiente = true
                programar(token, 2_500L) { comenzar(token, intent) }
                try { nuevo.consultar(intent, Executor { accion -> principal { accion.run() } }, { instalados, descargables ->
                    if (vigente(token) && consultaPendiente) {
                        val elegido = IdiomaDictado.elegirInstalado(etiqueta, instalados)
                        when {
                            elegido != null -> comenzar(token, intentDictado(elegido, modo))
                            IdiomaDictado.elegirInstalado(etiqueta, descargables) != null ->
                                fallar(token, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                            instalados.isNotEmpty() || descargables.isNotEmpty() ->
                                fallar(token, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                            // Algunos servicios no implementan el inventario. El motor local sigue
                            // siendo local aunque probemos el BCP-47 solicitado sin esta consulta.
                            else -> comenzar(token, intent)
                        }
                    }
                }, { codigo ->
                    if (vigente(token) && consultaPendiente) {
                        if (codigo == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) fallar(token, codigo)
                        else comenzar(token, intent)
                    }
                }) } catch (_: UnsupportedOperationException) { comenzar(token, intent) }
            } else comenzar(token, intent)
        } catch (_: SecurityException) {
            fallar(token, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        } catch (_: RuntimeException) {
            // Nunca cambiamos a un servicio que pudiera enviar audio sin autorización del usuario.
            fallar(token, if (modo == ModoDictado.LOCAL) LOCAL_NO_DISPONIBLE else SERVICIO_NO_DISPONIBLE)
        }
    }

    fun detener() = principal {
        when (_estado.value.fase) {
            FaseDictado.PREPARANDO -> cancelar()
            FaseDictado.ESCUCHANDO -> {
                val token = sesion
                _estado.value = _estado.value.copy(fase = FaseDictado.PROCESANDO, nivel = 0f)
                programar(token, 12_000L) { fallar(token, RESULTADO_AGOTADO) }
                try { motor?.detener() } catch (_: RuntimeException) { fallar(token, SpeechRecognizer.ERROR_AUDIO) }
            }
            else -> Unit
        }
    }

    fun cancelar() = principal {
        liberar()
        _estado.value = EstadoDictado()
    }

    fun destruir() = principal {
        destruido = true
        cancelar()
    }

    private fun comenzar(token: Long, intent: Intent) {
        if (!vigente(token) || escuchaIniciada) return
        consultaPendiente = false
        escuchaIniciada = true
        _estado.value = _estado.value.copy(idioma = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE).orEmpty())
        programar(token, 10_000L) { fallar(token, PREPARACION_AGOTADA) }
        try { motor?.iniciar(intent) }
        catch (_: SecurityException) { fallar(token, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
        catch (_: RuntimeException) { fallar(token, SpeechRecognizer.ERROR_CLIENT) }
    }

    private fun eventos(token: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = recibir(token) {
            if (_estado.value.fase == FaseDictado.PREPARANDO) {
                _estado.value = _estado.value.copy(fase = FaseDictado.ESCUCHANDO)
                programar(token, 45_000L) { detener() }
            }
        }
        override fun onBeginningOfSpeech() = onReadyForSpeech(null)
        override fun onRmsChanged(rmsdB: Float) = recibir(token) {
            if (_estado.value.microfonoActivo) _estado.value = _estado.value.copy(nivel = nivelDictado(rmsdB))
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = recibir(token) {
            _estado.value = _estado.value.copy(fase = FaseDictado.PROCESANDO, nivel = 0f)
            programar(token, 12_000L) { fallar(token, RESULTADO_AGOTADO) }
        }
        override fun onError(error: Int) = recibir(token) { fallar(token, error) }
        override fun onPartialResults(partialResults: Bundle?) = recibir(token) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }?.let { _estado.value = _estado.value.copy(parcial = it.take(10_000)) }
        }
        override fun onResults(results: Bundle?) = recibir(token) {
            val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }?.trim()?.take(10_000)
            if (texto == null) fallar(token, SpeechRecognizer.ERROR_NO_MATCH)
            else {
                val anterior = _estado.value
                liberar()
                _estado.value = anterior.copy(fase = FaseDictado.INACTIVO, parcial = texto, nivel = 0f)
                onResultado(texto)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun recibir(token: Long, accion: () -> Unit) = principal { if (vigente(token)) accion() }
    private fun vigente(token: Long) = token == sesion && !destruido
    private fun principal(accion: () -> Unit) {
        if (Looper.myLooper() == handler.looper) accion() else handler.post { accion() }
    }
    private fun programar(token: Long, demora: Long, accion: () -> Unit) {
        temporizador?.let(handler::removeCallbacks)
        temporizador = Runnable { if (vigente(token)) accion() }.also { handler.postDelayed(it, demora) }
    }
    private fun fallar(token: Long, codigo: Int) {
        if (!vigente(token)) return
        val anterior = _estado.value
        liberar()
        _estado.value = anterior.copy(fase = FaseDictado.ERROR, nivel = 0f, error = mensajeErrorDictado(codigo), codigoError = codigo)
    }
    private fun liberar() {
        // Invalidar antes de cancelar: algunos servicios responden inmediatamente con ERROR_CLIENT.
        sesion++
        consultaPendiente = false
        escuchaIniciada = false
        temporizador?.let(handler::removeCallbacks)
        temporizador = null
        val anterior = motor
        motor = null
        runCatching { anterior?.cancelar() }
        runCatching { anterior?.destruir() }
    }

    companion object {
        const val LOCAL_NO_DISPONIBLE = -1
        const val SERVICIO_NO_DISPONIBLE = -2
        const val PREPARACION_AGOTADA = -3
        const val RESULTADO_AGOTADO = -4
    }
}

internal interface FabricaDictado {
    fun tienePermiso(): Boolean
    fun disponible(modo: ModoDictado): Boolean
    fun puedeConsultarIdiomas(): Boolean
    fun crear(modo: ModoDictado): MotorDictado
}
internal interface MotorDictado {
    fun escuchar(listener: RecognitionListener)
    fun consultar(intent: Intent, executor: Executor, resultado: (List<String>, List<String>) -> Unit, error: (Int) -> Unit)
    fun iniciar(intent: Intent)
    fun detener()
    fun cancelar()
    fun destruir()
}

private class FabricaAndroidDictado(private val context: Context) : FabricaDictado {
    override fun tienePermiso() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    override fun disponible(modo: ModoDictado) = if (modo == ModoDictado.LOCAL)
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        else SpeechRecognizer.isRecognitionAvailable(context)
    override fun puedeConsultarIdiomas() = Build.VERSION.SDK_INT >= 33
    override fun crear(modo: ModoDictado): MotorDictado {
        val recognizer = if (modo == ModoDictado.LOCAL && Build.VERSION.SDK_INT >= 31)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else if (modo == ModoDictado.SISTEMA) SpeechRecognizer.createSpeechRecognizer(context)
            else throw UnsupportedOperationException("Dictado local no disponible")
        return object : MotorDictado {
            override fun escuchar(listener: RecognitionListener) = recognizer.setRecognitionListener(listener)
            override fun consultar(intent: Intent, executor: Executor, resultado: (List<String>, List<String>) -> Unit, error: (Int) -> Unit) {
                if (Build.VERSION.SDK_INT >= 33) recognizer.checkRecognitionSupport(intent, executor, object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) = resultado(support.installedOnDeviceLanguages,
                        support.supportedOnDeviceLanguages + support.pendingOnDeviceLanguages)
                    override fun onError(errorCode: Int) = error(errorCode)
                }) else error(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
            }
            override fun iniciar(intent: Intent) = recognizer.startListening(intent)
            override fun detener() = recognizer.stopListening()
            override fun cancelar() = recognizer.cancel()
            override fun destruir() = recognizer.destroy()
        }
    }
}

internal fun intentDictado(idioma: String, modo: ModoDictado): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, modo == ModoDictado.LOCAL)
}

internal object IdiomaDictado {
    fun preferido(locale: Locale): String = if (locale.country.isNotBlank()) locale.toLanguageTag()
        else when (locale.language) { "en" -> "en-US"; "pt" -> "pt-BR"; "fr" -> "fr-FR"; else -> "es-CO" }

    fun elegirInstalado(preferido: String, disponibles: List<String>): String? {
        val idiomas = disponibles.map { Locale.forLanguageTag(it.replace('_', '-')).toLanguageTag() }.filter { it != "und" }
        val idioma = Locale.forLanguageTag(preferido).language
        return idiomas.firstOrNull { it.equals(preferido, ignoreCase = true) }
            ?: idiomas.firstOrNull { Locale.forLanguageTag(it).language == idioma }
    }
}

internal fun nivelDictado(rms: Float): Float = if (rms.isFinite()) ((rms + 2f) / 12f).coerceIn(0f, 1f) else 0f

fun mensajeErrorDictado(codigo: Int): String = when (codigo) {
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se escuchó voz. Acerca el teléfono y habla cuando aparezca «Micrófono activo»."
    SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió lo que dijiste. Prueba en un lugar silencioso: «Almuerzo 25 mil»."
    SpeechRecognizer.ERROR_AUDIO -> "No se pudo abrir el micrófono. Cierra otras apps que lo estén usando y revisa el acceso al micrófono de Android."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permite el micrófono para dictar. Puedes habilitarlo en los permisos de Mis Finanzas."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "El servicio de voz está ocupado. Espera unos segundos y vuelve a intentarlo."
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "El servicio de voz no admite este idioma. Revisa los idiomas de voz del teléfono o prueba el servicio del teléfono."
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Falta descargar el idioma para dictar sin conexión. Descárgalo en los ajustes de voz del teléfono o usa su servicio de voz."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El servicio de voz del teléfono no pudo conectarse. Revisa la conexión y vuelve a intentarlo."
    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "El servicio de voz dejó de responder. Vuelve a intentarlo o revisa sus actualizaciones en el teléfono."
    ControladorDictado.LOCAL_NO_DISPONIBLE -> "El dictado sin conexión no está disponible en este teléfono. Puedes probar el servicio de voz del teléfono."
    ControladorDictado.SERVICIO_NO_DISPONIBLE -> "No hay un servicio de voz disponible. Activa o actualiza el servicio de reconocimiento de voz del teléfono."
    ControladorDictado.PREPARACION_AGOTADA -> "El servicio de voz tardó demasiado en activar el micrófono. Vuelve a intentarlo o prueba el servicio del teléfono."
    ControladorDictado.RESULTADO_AGOTADO -> "El servicio no entregó un resultado. El micrófono se cerró; vuelve a intentarlo."
    else -> "El servicio de voz no pudo iniciar el dictado. Vuelve a intentarlo o prueba el servicio del teléfono."
}
