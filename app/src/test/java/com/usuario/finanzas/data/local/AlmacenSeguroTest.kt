package com.usuario.finanzas.data.local

import android.app.Application
import android.content.Context
import com.usuario.finanzas.data.local.security.AlmacenSeguro
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlmacenSeguroTest {
    private fun preferencias() = RuntimeEnvironment.getApplication().getSharedPreferences("pin-${UUID.randomUUID()}", Context.MODE_PRIVATE)

    @Test fun cincoFallosBloqueanInclusoUnaInstanciaNuevaYElExitoReiniciaIntentos() {
        val prefs = preferencias()
        val seguro = AlmacenSeguro(prefs)
        seguro.guardarPin("1234")
        repeat(5) { assertFalse(seguro.validarPin("0000")) }
        assertTrue(seguro.segundosBloqueoPin() in 1..30)
        val recreado = AlmacenSeguro(prefs)
        assertFalse(recreado.validarPin("1234"))
        prefs.edit().putLong("pin_blocked_until", System.currentTimeMillis() - 1).commit()
        assertTrue(recreado.validarPin("1234"))
        assertEquals(0, prefs.getInt("pin_failed_attempts", -1))
        assertEquals(0L, recreado.segundosBloqueoPin())
    }

    @Test fun migraPinAnteriorSinCambiarLlaveYLaSalEsAleatoria() {
        val prefs = preferencias()
        val anterior = MessageDigest.getInstance("SHA-256").digest("1234".toByteArray()).joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pin_hash", anterior).putString("db_passphrase", "llave-existente").commit()
        val seguro = AlmacenSeguro(prefs)
        assertTrue(seguro.validarPin("1234"))
        assertNotEquals(anterior, prefs.getString("pin_hash", null))
        assertNotNull(prefs.getString("pin_salt", null))
        assertEquals("llave-existente", seguro.obtenerPassphraseBd())
        val otrasPrefs = preferencias()
        AlmacenSeguro(otrasPrefs).guardarPin("1234")
        assertNotEquals(prefs.getString("pin_hash", null), otrasPrefs.getString("pin_hash", null))
    }
}
