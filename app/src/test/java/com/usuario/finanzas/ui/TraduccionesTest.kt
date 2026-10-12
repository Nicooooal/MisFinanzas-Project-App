package com.usuario.finanzas.ui

import com.usuario.finanzas.ui.i18n.tr
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TraduccionesTest {
    private val original = Locale.getDefault()

    @After fun restaurarIdioma() { Locale.setDefault(original) }

    @Test fun cambiarIdiomaReutilizaCatalogoSinRetenerElAnterior() {
        listOf("en" to "Settings", "pt" to "Configurações", "fr" to "Réglages", "es" to "Ajustes")
            .forEach { (codigo, esperado) ->
                Locale.setDefault(Locale.forLanguageTag(codigo))
                assertEquals(esperado, tr("Ajustes"))
            }
    }

    @Test fun interpolacionConservaDatosLiteralesDelUsuario() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Withdraw from Viaje {1}", tr("Retirar de {0}", "Viaje {1}"))
        assertEquals("  Camera", tr("  Cámara"))
        assertEquals("Nombre de mi tienda", tr("Nombre de mi tienda"))
    }

    @Test fun idiomaNoSoportadoUsaEspanolYConservaArgumentos() {
        Locale.setDefault(Locale.GERMAN)
        assertEquals("Saldo anterior: 123", tr("Saldo anterior: {0}", 123))
    }
}
