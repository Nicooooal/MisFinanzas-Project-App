package com.usuario.finanzas.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Formato portable: cabecera autenticada, sal aleatoria y AES-256-GCM. */
object CodecRespaldo {
    const val MAX_BYTES = 64 * 1024 * 1024
    private const val VERSION = 1
    private const val ITERACIONES = 210_000
    private val MAGIC = byteArrayOf(0x4d, 0x46, 0x52, 0x42) // MFRB
    private const val CABECERA_BYTES = 4 + 4 + 32 + 12

    fun cifrar(datos: ByteArray, password: String): ByteArray {
        validarPassword(password)
        require(datos.size <= MAX_BYTES - CABECERA_BYTES - 16) { "El respaldo supera el límite de 64 MB." }
        val sal = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cabecera = ByteBuffer.allocate(CABECERA_BYTES)
            .put(MAGIC).putInt(VERSION).put(sal).put(nonce).array()
        val llave = derivar(password, sal)
        return try {
            val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
            cifrador.init(Cipher.ENCRYPT_MODE, SecretKeySpec(llave, "AES"), GCMParameterSpec(128, nonce))
            cifrador.updateAAD(cabecera)
            cabecera + cifrador.doFinal(datos)
        } finally { llave.fill(0) }
    }

    fun descifrar(datos: ByteArray, password: String): ByteArray {
        validarPassword(password)
        require(datos.size in (CABECERA_BYTES + 16)..MAX_BYTES) { "El archivo no es un respaldo válido o supera 64 MB." }
        val entrada = ByteBuffer.wrap(datos)
        val magic = ByteArray(4).also(entrada::get)
        require(magic.contentEquals(MAGIC)) { "El archivo no es un respaldo de Mis Finanzas." }
        require(entrada.int == VERSION) { "Esta versión del respaldo no es compatible." }
        val sal = ByteArray(32).also(entrada::get)
        val nonce = ByteArray(12).also(entrada::get)
        val llave = derivar(password, sal)
        return try {
            val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
            cifrador.init(Cipher.DECRYPT_MODE, SecretKeySpec(llave, "AES"), GCMParameterSpec(128, nonce))
            cifrador.updateAAD(datos, 0, CABECERA_BYTES)
            cifrador.doFinal(datos, CABECERA_BYTES, datos.size - CABECERA_BYTES)
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("La contraseña es incorrecta o el respaldo está dañado.", error)
        } finally { llave.fill(0) }
    }

    private fun validarPassword(password: String) {
        require(password.length in 8..1024) { "Usa una contraseña de entre 8 y 1024 caracteres." }
    }

    private fun derivar(password: String, sal: ByteArray): ByteArray {
        val caracteres = password.toCharArray()
        val especificacion = PBEKeySpec(caracteres, sal, ITERACIONES, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded }
        finally { especificacion.clearPassword(); caracteres.fill('\u0000') }
    }
}
