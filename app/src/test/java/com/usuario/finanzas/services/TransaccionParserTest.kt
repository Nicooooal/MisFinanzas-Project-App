package com.usuario.finanzas.services

import com.usuario.finanzas.domain.model.TransaccionDetectada
import org.junit.Assert.*
import org.junit.Test

/** Casos sintéticos de regresión; no representan formatos certificados por las entidades. */
class TransaccionParserTest {
    private val nequi = "com.nequi.MobileApp"
    private val nu = "com.nu.production"

    @Test fun rechazaPromocionesAntesDeCualquierParser() {
        val mensajes = listOf(
            "Nequi: Compra por $25.000 en TIENDA y gana premios",
            "Pago PSE de $50.000 a CLARO. Aprovecha esta promoción",
            "Claro: Recarga $10000 y recibe el doble de datos",
            "Compra por $25000 en TIENDA. Aplica código de descuento",
            "Nu: Obtén hasta $50.000 por tus compras",
            "Bancolombia: Compra por $25000 en TIENDA desde $ 5000",
            "Nequi: Envía ahora $25000 y gana premios"
        )
        mensajes.forEach {
            assertNull(it, TransaccionParser.parsearTextoLibre(it))
            assertNull(it, TransaccionParser.parsear(nequi, it))
            assertNull(it, TransaccionParser.parsear(nu, it))
        }
    }

    @Test fun rechazaEstadosQueNoSonMovimientosCompletados() {
        listOf(
            "Compra rechazada por $25000 en TIENDA",
            "Transferencia pendiente por $25000 a Ana",
            "Enviaste $25000 a Ana. Operación fallida",
            "Código de verificación para tu compra por $25000",
            "Solicitud: recibiste una transferencia por $25000",
            "No recibiste $25000",
            "No pudimos procesar tu pago por $25000",
            "No has recibido $25000",
            "Ana no te envió $25000",
            "Compra por $25000 en TIENDA. Fondos insuficientes",
            "Programa tu pago de $25000 para mañana",
            "Puedes pagar $25000 en TIENDA",
            "Pago PSE de $25000 a CLARO fue rechazado",
            "Recibiste una solicitud de pago por $25000"
        ).forEach { assertNull(it, TransaccionParser.parsear(nequi, it)) }
    }

    @Test fun detectaIngresosDeNequiYNu() {
        listOf(nequi, nu).forEach { paquete ->
            listOf(
                "Recibiste $25000 de Ana",
                "Recibiste una transferencia por $25.000 de Ana",
                "Transferencia recibida por $25,000.00 de Ana",
                "Ana te envió $25.000",
                "Se abonaron $25000 en tu cuenta",
                "Te llegaron $25000 de Ana"
            ).forEach {
                val movimiento = TransaccionParser.parsear(paquete, it)
                assertNotNull(it, movimiento)
                assertEquals(it, 25000.0, movimiento!!.monto, 0.0)
                assertEquals(TransaccionDetectada.TIPO_INGRESO, movimiento.tipo)
                assertEquals(TransaccionDetectada.DIRECCION_ENTRADA, movimiento.direccion)
                assertEquals(it, movimiento.textoOriginal)
            }
        }
    }

    @Test fun detectaEnviosDeNequiYNu() {
        listOf(nequi, nu).forEach { paquete ->
            listOf("Enviaste $25000 a Ana", "Transferiste $25000 a Ana",
                "Transferencia exitosa por $25000 a Ana", "Realizaste una transferencia de $25000 a Ana"
            ).forEach {
                val movimiento = TransaccionParser.parsear(paquete, it)
                assertNotNull(it, movimiento)
                assertEquals(25000.0, movimiento!!.monto, 0.0)
                assertEquals(TransaccionDetectada.TIPO_TRANSFERENCIA, movimiento.tipo)
                assertEquals(TransaccionDetectada.DIRECCION_SALIDA, movimiento.direccion)
            }
        }
    }

    @Test fun aceptaRemitentesDesconocidosConVerboInequivoco() {
        val ingreso = TransaccionParser.parsearTextoLibre("Recibiste $25000 de Ana", "890123")!!
        assertEquals("SMS", ingreso.banco)
        assertEquals(TransaccionDetectada.TIPO_INGRESO, ingreso.tipo)
        val envio = TransaccionParser.parsearTextoLibre("Enviaste $25000 a Ana", "OtroBanco")!!
        assertEquals("SMS", envio.banco)
        assertEquals(TransaccionDetectada.TIPO_TRANSFERENCIA, envio.tipo)
    }

    @Test fun noInventaBancoPorElOrdenDeLasReglasNiPorElDestino() {
        assertEquals("Nu", TransaccionParser.parsearTextoLibre("Compra por $25000 en TIENDA", "Nu")!!.banco)
        assertEquals("Davivienda", TransaccionParser.parsearTextoLibre("Davivienda: Compra por $25000 en TIENDA")!!.banco)
        assertEquals("SMS", TransaccionParser.parsearTextoLibre("Enviaste $25000 a Nequi")!!.banco)
        assertEquals("Nequi", TransaccionParser.parsearTextoLibre("Enviaste $25000 a Nu", "Nequi")!!.banco)
    }

    @Test fun noConfundeSaldoConMontoTransferido() {
        val movimiento = TransaccionParser.parsear(nu, "Saldo anterior $100000. Recibiste $25000 de Ana. Saldo $125000")!!
        assertEquals(25000.0, movimiento.monto, 0.0)
    }

    @Test fun conservaComprasYPseConocidos() {
        val compra = TransaccionParser.parsearTextoLibre("Nequi: Pagaste $263418.00 en INVERSIONES EL MERCADE.")!!
        assertEquals(263418.0, compra.monto, 0.0)
        assertEquals("INVERSIONES EL MERCADE", compra.comercio)
        val pse = TransaccionParser.parsear(nequi, "Pago exitoso por PSE. Hiciste un pago en Emermedica S.A. por $129.396 y t...")!!
        assertEquals(129396.0, pse.monto, 0.0)
        assertEquals("Emermedica S.A", pse.comercio)
        assertEquals(TransaccionDetectada.TIPO_PAGO_SERVICIO, pse.tipo)
        assertEquals(0.0, TransaccionParser.parsear(nequi, "Pago exitoso por PSE. Hiciste un pago en Emermedica...")!!.monto, 0.0)
    }

    @Test fun conservaCajaSocial() {
        val mensaje = "TX EXITOSA en *7350.El 2026.06.16 a las 20:42 un(a) UTILIZACION POR RECAUDOS / PAGOS por $50,000.00.Banco Caja Social"
        val movimiento = TransaccionParser.parsearTextoLibre(mensaje)!!
        assertEquals(50000.0, movimiento.monto, 0.0)
        assertEquals("Banco Caja Social", movimiento.banco)
    }

    @Test fun noTomaChatsComoComprobantesBancarios() {
        assertNull(TransaccionParser.parsear("com.whatsapp", "Ana: Recibiste $25000 de Pedro"))
        assertNull(TransaccionParser.parsear("com.app.desconocida", "Compra aprobada por $25000"))
    }

    @Test fun noMezclaDivisasExtranjerasConCop() {
        assertNull(TransaccionParser.parsear("com.paypal.android.p2pmobile", "Enviaste $25.00 USD a Ana"))
        assertNull(TransaccionParser.parsear(nu, "Recibiste EUR 25 de Ana"))
    }

    @Test fun montosSinSeparadorYDecimalesNoSeTruncan() {
        mapOf("25000" to 25000.0, "25.500" to 25500.0, "25,500" to 25500.0,
            "1.250.300" to 1250300.0, "85.000,25" to 85000.25, "50,000.25" to 50000.25,
            "263418.00" to 263418.0).forEach { (texto, esperado) ->
            assertEquals(esperado, TransaccionParser.parsearMonto(texto)!!, 0.0)
            assertEquals(esperado, TransaccionParser.parseGenerico("Pagaste $$texto en TIENDA", "SMS")!!.monto, 0.0)
        }
        listOf("0", "-2", "1.23.45", "NaN", "Infinity").forEach { assertNull(TransaccionParser.parsearMonto(it)) }
    }

    @Test fun conservaContrapartesDeTransferencias() {
        assertEquals("Ana", TransaccionParser.parsear(nu, "Ana te envió $25000")!!.comercio)
        assertEquals("Pedro", TransaccionParser.parsearTextoLibre("Bancolombia: Transferencia por $25000 a Pedro")!!.comercio)
    }

    @Test fun descartaMovimientosAmbiguos() {
        assertNull(TransaccionParser.parsear(nu, "Transferencia por $25000"))
        assertNull(TransaccionParser.parsear(nu, "Recibiste $25000 y enviaste $10000 a Ana"))
        assertNull(TransaccionParser.parsearTextoLibre("Compra por $25000 en TIENDA", "Tienda"))
        assertNull(TransaccionParser.parsear(nequi, "Saldo disponible $25000"))
    }
}



