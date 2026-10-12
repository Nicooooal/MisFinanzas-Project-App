package com.usuario.finanzas.data.backup

import org.junit.Assert.*
import org.junit.Test

class CodecRespaldoTest {
    private val password = "Mi contraseña de prueba 2026"
    private val contenido = "{\"version\":5,\"nota\":\"Café, niño y ahorro\",\"centavos\":12345}"
        .toByteArray(Charsets.UTF_8)

    @Test fun respaldoEsPortableYNoModificaLosDatosOriginales() {
        val original = contenido.copyOf()
        val cifrado = CodecRespaldo.cifrar(contenido, password)
        assertArrayEquals(original, CodecRespaldo.descifrar(cifrado, password))
        assertArrayEquals(original, contenido)
        assertFalse(cifrado.contentEquals(contenido))
        assertFalse(String(cifrado, Charsets.ISO_8859_1).contains("centavos"))
    }

    @Test fun respaldosIgualesUsanAleatoriedadIndependiente() {
        val primero = CodecRespaldo.cifrar(contenido, password)
        val segundo = CodecRespaldo.cifrar(contenido, password)
        assertFalse(primero.contentEquals(segundo))
        assertArrayEquals(contenido, CodecRespaldo.descifrar(primero, password))
        assertArrayEquals(contenido, CodecRespaldo.descifrar(segundo, password))
    }

    @Test fun passwordIncorrectoCorrupcionYTruncamientoNoEntreganDatos() {
        val cifrado = CodecRespaldo.cifrar(contenido, password)
        assertThrows(IllegalArgumentException::class.java) {
            CodecRespaldo.descifrar(cifrado, "Otra contraseña distinta")
        }
        // Sal, nonce, carga y etiqueta de autenticación quedan protegidos.
        listOf(8, 40, 52, cifrado.lastIndex).forEach { posicion ->
            val corrupto = cifrado.copyOf().also { it[posicion] = (it[posicion].toInt() xor 1).toByte() }
            assertThrows("Cambio en $posicion", IllegalArgumentException::class.java) {
                CodecRespaldo.descifrar(corrupto, password)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodecRespaldo.descifrar(cifrado.copyOf(cifrado.size - 1), password)
        }
    }

    @Test fun formatoAjenoYVersionFuturaSeRechazan() {
        val cifrado = CodecRespaldo.cifrar(contenido, password)
        assertThrows(IllegalArgumentException::class.java) {
            CodecRespaldo.descifrar(cifrado.copyOf().also { it[0] = 0 }, password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodecRespaldo.descifrar(cifrado.copyOf().also { it[7] = 2 }, password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodecRespaldo.descifrar(ByteArray(67), password)
        }
    }

    @Test fun limitesSeValidanAntesDeDescifrarOCifrar() {
        listOf("corta", "a".repeat(1025)).forEach { invalida ->
            assertThrows(IllegalArgumentException::class.java) { CodecRespaldo.cifrar(contenido, invalida) }
        }
        val excesivo = ByteArray(CodecRespaldo.MAX_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) { CodecRespaldo.cifrar(excesivo, password) }
        assertThrows(IllegalArgumentException::class.java) { CodecRespaldo.descifrar(excesivo, password) }
    }
}
