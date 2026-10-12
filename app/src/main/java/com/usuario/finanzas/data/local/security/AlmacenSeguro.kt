package com.usuario.finanzas.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacenamiento seguro de la app:
 * - Passphrase de SQLCipher: se genera aleatoriamente la primera vez y se guarda en
 *   EncryptedSharedPreferences (cifrado con una llave maestra del Android Keystore).
 *   Nunca está hardcodeada en el código.
 * - PIN: PBKDF2 con sal individual; los hashes anteriores se migran al validar.
 * - Los intentos fallidos y la espera sobreviven al cierre de la app.
 */
@Singleton
class AlmacenSeguro private constructor(crearPreferencias: () -> SharedPreferences) {
    @Inject constructor(@ApplicationContext context: Context) : this({
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "finanzas_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    })

    internal constructor(preferencias: SharedPreferences) : this({ preferencias })

    private val prefs: SharedPreferences by lazy(crearPreferencias)

    // ---------- Passphrase de la base de datos ----------

    @Synchronized fun obtenerPassphraseBd(): String {
        prefs.getString(KEY_DB_PASSPHRASE, null)?.let { return it }
        // Primera ejecución: generar 32 bytes aleatorios y guardarlos como hex
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = bytes.joinToString("") { "%02x".format(it) }
        check(prefs.edit().putString(KEY_DB_PASSPHRASE, passphrase).commit()) { "No se pudo guardar la llave local." }
        return passphrase
    }

    // ---------- PIN de desbloqueo ----------

    fun tienePin(): Boolean = prefs.contains(KEY_PIN_HASH)

    @Synchronized fun guardarPin(pin: String) {
        require(pin.length == 4 && pin.all { it in '0'..'9' }) { "El PIN debe tener 4 dígitos." }
        val sal = ByteArray(32).also(SecureRandom()::nextBytes)
        check(prefs.edit().putString(KEY_PIN_HASH, derivarPin(pin, sal).hex())
            .putString(KEY_PIN_SAL, sal.hex()).putInt(KEY_INTENTOS, 0).putLong(KEY_BLOQUEADO_HASTA, 0)
            .commit()) { "No se pudo guardar el PIN." }
    }

    @Synchronized fun validarPin(pin: String): Boolean {
        if (segundosBloqueoPin() > 0) return false
        val guardado = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val sal = prefs.getString(KEY_PIN_SAL, null)
        val calculado = if (sal == null) hash(pin) else derivarPin(pin, sal.bytesHex()).hex()
        val valido = pin.length == 4 && pin.all { it in '0'..'9' } &&
            MessageDigest.isEqual(guardado.toByteArray(Charsets.UTF_8), calculado.toByteArray(Charsets.UTF_8))
        if (valido) {
            if (sal == null) guardarPin(pin)
            else check(prefs.edit().putInt(KEY_INTENTOS, 0).putLong(KEY_BLOQUEADO_HASTA, 0).commit())
        } else {
            val intentos = (prefs.getInt(KEY_INTENTOS, 0) + 1).coerceAtMost(35)
            val espera = if (intentos < 5) 0 else (30_000L shl ((intentos - 5) / 5)).coerceAtMost(1_800_000L)
            check(prefs.edit().putInt(KEY_INTENTOS, intentos)
                .putLong(KEY_BLOQUEADO_HASTA, System.currentTimeMillis() + espera).commit())
        }
        return valido
    }

    fun segundosBloqueoPin(): Long =
        ((prefs.getLong(KEY_BLOQUEADO_HASTA, 0) - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000

    private fun derivarPin(pin: String, sal: ByteArray): ByteArray {
        val caracteres = pin.toCharArray()
        val especificacion = PBEKeySpec(caracteres, sal, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded }
        finally { especificacion.clearPassword(); caracteres.fill('\u0000') }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun String.bytesHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hash(texto: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SAL = "pin_salt"
        const val KEY_INTENTOS = "pin_failed_attempts"
        const val KEY_BLOQUEADO_HASTA = "pin_blocked_until"
    }
}
