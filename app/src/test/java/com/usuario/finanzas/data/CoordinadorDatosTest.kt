package com.usuario.finanzas.data

import com.usuario.finanzas.data.preferences.CoordinadorDatos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoordinadorDatosTest {
    @Test fun unaCapturaPreparadaAntesDeRestaurarNoSeGuardaDespues() = runBlocking {
        val coordinador = CoordinadorDatos()
        val revision = coordinador.revisionActual()
        coordinador.mantener { Unit }
        var escrito = false
        assertNull(coordinador.capturar(revision) { escrito = true })
        assertFalse(escrito)
        assertEquals("nuevo", coordinador.capturar { "nuevo" })
    }

    @Test fun mantenimientoEsperaLaCopiaYConfirmacionCompleta() = runBlocking {
        val coordinador = CoordinadorDatos()
        val continuar = CompletableDeferred<Unit>()
        val eventos = mutableListOf<String>()
        val captura = async(start = CoroutineStart.UNDISPATCHED) {
            coordinador.capturar {
                eventos += "copiar"
                continuar.await()
                eventos += "confirmar"
            }
        }
        val mantenimiento = async(start = CoroutineStart.UNDISPATCHED) {
            coordinador.mantener { eventos += "restaurar" }
        }
        assertEquals(listOf("copiar"), eventos)
        continuar.complete(Unit)
        captura.await(); mantenimiento.await()
        assertEquals(listOf("copiar", "confirmar", "restaurar"), eventos)
    }
}
