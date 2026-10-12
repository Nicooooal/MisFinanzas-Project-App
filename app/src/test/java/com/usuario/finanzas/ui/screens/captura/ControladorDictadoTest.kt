package com.usuario.finanzas.ui.screens.captura

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.time.Duration
import java.util.Locale
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ControladorDictadoTest {
    @Test fun `local sin servicio nunca abre servicio de red automaticamente`() {
        val fabrica = FabricaFalsa(local = false)
        val controlador = ControladorDictado(fabrica, {})
        controlador.iniciar()
        assertEquals(ControladorDictado.LOCAL_NO_DISPONIBLE, controlador.estado.value.codigoError)
        assertTrue(fabrica.modos.isEmpty())
        controlador.iniciar(ModoDictado.SISTEMA)
        assertEquals(listOf(ModoDictado.SISTEMA), fabrica.modos)
        assertFalse(fabrica.motores.single().intents.single().getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
    }

    @Test fun `permisos faltantes no crean ni activan microfono`() {
        val fabrica = FabricaFalsa(permiso = false)
        val controlador = ControladorDictado(fabrica, {})
        controlador.iniciar()
        assertEquals(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, controlador.estado.value.codigoError)
        assertFalse(controlador.estado.value.microfonoActivo)
        assertTrue(fabrica.motores.isEmpty())
    }

    @Test fun `elige variante instalada y enciende indicador solo al estar listo`() {
        val fabrica = FabricaFalsa(consultar = true)
        val controlador = ControladorDictado(fabrica, {})
        controlador.iniciar(idioma = Locale("es"))
        assertEquals(FaseDictado.PREPARANDO, controlador.estado.value.fase)
        assertFalse(controlador.estado.value.microfonoActivo)
        val motor = fabrica.motores.single()
        motor.soporte!!(listOf("en-US", "es-ES"), emptyList())
        assertEquals("es-ES", motor.intents.single().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(motor.intents.single().getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        motor.listener.onReadyForSpeech(null)
        assertTrue(controlador.estado.value.microfonoActivo)
        motor.listener.onRmsChanged(4f)
        assertEquals(0.5f, controlador.estado.value.nivel, 0f)
        motor.listener.onEndOfSpeech()
        assertFalse(controlador.estado.value.microfonoActivo)
        assertEquals(FaseDictado.PROCESANDO, controlador.estado.value.fase)
    }

    @Test fun `modelo pendiente no activa microfono ni descarga automaticamente`() {
        val fabrica = FabricaFalsa(consultar = true)
        val controlador = ControladorDictado(fabrica, {})
        controlador.iniciar(idioma = Locale("es", "CO"))
        val motor = fabrica.motores.single()
        motor.soporte!!(listOf("en-US"), listOf("es-CO"))
        assertEquals(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, controlador.estado.value.codigoError)
        assertTrue(motor.intents.isEmpty())
        assertTrue(motor.destruido)
    }

    @Test fun `consulta que no responde cae solo a idioma completo en motor local`() {
        val fabrica = FabricaFalsa(consultar = true)
        val controlador = ControladorDictado(fabrica, {})
        controlador.iniciar(idioma = Locale("es"))
        val motor = fabrica.motores.single()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500))
        assertEquals("es-CO", motor.intents.single().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        motor.soporte!!(listOf("es-ES"), emptyList())
        assertEquals(1, motor.intents.size)
        assertEquals(listOf(ModoDictado.LOCAL), fabrica.modos)
    }

    @Test fun `doble pulsacion no crea recognizers simultaneos y reintento ignora callbacks viejos`() {
        val fabrica = FabricaFalsa()
        val resultados = mutableListOf<String>()
        val controlador = ControladorDictado(fabrica, resultados::add)
        controlador.iniciar()
        controlador.iniciar()
        assertEquals(1, fabrica.motores.size)
        val viejo = fabrica.motores.single()
        controlador.cancelar()
        controlador.iniciar()
        viejo.listener.onReadyForSpeech(null)
        viejo.listener.onResults(resultado("viejo 5000"))
        viejo.listener.onError(SpeechRecognizer.ERROR_AUDIO)
        assertEquals(FaseDictado.PREPARANDO, controlador.estado.value.fase)
        assertTrue(resultados.isEmpty())
        fabrica.motores.last().listener.onResults(resultado("Almuerzo 25 mil"))
        assertEquals(listOf("Almuerzo 25 mil"), resultados)
        assertTrue(fabrica.motores.all { it.destruido })
    }

    @Test fun `al destruir no llegan resultados ni se puede volver a escuchar`() {
        val fabrica = FabricaFalsa()
        val resultados = mutableListOf<String>()
        val controlador = ControladorDictado(fabrica, resultados::add)
        controlador.iniciar()
        val motor = fabrica.motores.single()
        controlador.destruir()
        motor.listener.onResults(resultado("No guardar 99 mil"))
        controlador.iniciar()
        assertEquals(1, fabrica.motores.size)
        assertTrue(resultados.isEmpty())
        assertEquals(FaseDictado.INACTIVO, controlador.estado.value.fase)
    }

    @Test fun `detener entrega resultado final una sola vez y libera microfono`() {
        val fabrica = FabricaFalsa()
        val resultados = mutableListOf<String>()
        val controlador = ControladorDictado(fabrica, resultados::add)
        controlador.iniciar()
        val motor = fabrica.motores.single()
        motor.listener.onReadyForSpeech(null)
        motor.listener.onPartialResults(resultado("almuerzo"))
        assertEquals("almuerzo", controlador.estado.value.parcial)
        controlador.detener()
        assertFalse(controlador.estado.value.microfonoActivo)
        assertEquals(1, motor.paradas)
        motor.listener.onResults(resultado("almuerzo 25 mil"))
        motor.listener.onResults(resultado("almuerzo 25 mil"))
        assertEquals(listOf("almuerzo 25 mil"), resultados)
        assertEquals(FaseDictado.INACTIVO, controlador.estado.value.fase)
    }

    @Test fun `servicio que no inicia libera recursos tras timeout`() {
        val fabrica = FabricaFalsa()
        val controlador = ControladorDictado(fabrica, {})
        controlador.iniciar()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(ControladorDictado.PREPARACION_AGOTADA, controlador.estado.value.codigoError)
        assertTrue(fabrica.motores.single().destruido)
    }

    @Test fun `servicio que no termina libera recursos sin guardar transcripcion parcial`() {
        val fabrica = FabricaFalsa()
        val resultados = mutableListOf<String>()
        val controlador = ControladorDictado(fabrica, resultados::add)
        controlador.iniciar()
        val motor = fabrica.motores.single()
        motor.listener.onReadyForSpeech(null)
        motor.listener.onPartialResults(resultado("almuerzo"))
        controlador.detener()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(ControladorDictado.RESULTADO_AGOTADO, controlador.estado.value.codigoError)
        assertTrue(motor.destruido)
        assertTrue(resultados.isEmpty())
    }

    @Test fun `idioma elegido conserva region exacta y no cambia a otro idioma`() {
        assertEquals("es-CO", IdiomaDictado.preferido(Locale("es")))
        assertEquals("pt-BR", IdiomaDictado.preferido(Locale("pt")))
        assertEquals("es-MX", IdiomaDictado.preferido(Locale("es", "MX")))
        assertEquals("es-CO", IdiomaDictado.elegirInstalado("es-CO", listOf("es-ES", "es_CO")))
        assertNull(IdiomaDictado.elegirInstalado("es-CO", listOf("en-US")))
        assertEquals(0f, nivelDictado(Float.NaN), 0f)
        assertEquals(0f, nivelDictado(-100f), 0f)
        assertEquals(1f, nivelDictado(100f), 0f)
    }

    @Test fun `errores frecuentes tienen diagnosticos distintos`() {
        val codigos = listOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_NETWORK,
            ControladorDictado.LOCAL_NO_DISPONIBLE)
        assertEquals(codigos.size, codigos.map(::mensajeErrorDictado).distinct().size)
    }

    private fun resultado(texto: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(texto))
    }

    private class FabricaFalsa(
        val local: Boolean = true, val permiso: Boolean = true, val consultar: Boolean = false
    ) : FabricaDictado {
        val modos = mutableListOf<ModoDictado>()
        val motores = mutableListOf<MotorFalso>()
        override fun tienePermiso() = permiso
        override fun disponible(modo: ModoDictado) = modo == ModoDictado.SISTEMA || local
        override fun puedeConsultarIdiomas() = consultar
        override fun crear(modo: ModoDictado): MotorDictado {
            modos += modo
            return MotorFalso().also { motores += it }
        }
    }

    private class MotorFalso : MotorDictado {
        lateinit var listener: RecognitionListener
        val intents = mutableListOf<Intent>()
        var soporte: ((List<String>, List<String>) -> Unit)? = null
        var destruido = false
        var paradas = 0
        override fun escuchar(listener: RecognitionListener) { this.listener = listener }
        override fun consultar(intent: Intent, executor: Executor, resultado: (List<String>, List<String>) -> Unit, error: (Int) -> Unit) {
            soporte = { a, b -> executor.execute { resultado(a, b) } }
        }
        override fun iniciar(intent: Intent) { intents += intent }
        override fun detener() { paradas++ }
        override fun cancelar() { listener.onError(SpeechRecognizer.ERROR_CLIENT) }
        override fun destruir() { destruido = true }
    }
}
