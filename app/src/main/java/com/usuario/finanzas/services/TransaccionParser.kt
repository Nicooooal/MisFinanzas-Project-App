package com.usuario.finanzas.services

import com.usuario.finanzas.domain.model.TransaccionDetectada
import java.text.Normalizer
import java.util.Locale

/**
 * Clasificador local y conservador: solo propone movimientos para confirmación.
 * Los patrones de ingresos/envíos son sintéticos hasta validarlos con mensajes reales.
 * Un remitente o una marca en el SMS es una señal, nunca una autenticación del banco.
 */
object TransaccionParser {
    const val BANCO_NEQUI = "Nequi"
    const val BANCO_BANCOLOMBIA = "Bancolombia"
    const val BANCO_DAVIPLATA = "DaviPlata"
    const val BANCO_DAVIVIENDA = "Davivienda"
    const val BANCO_WHATSAPP = "WhatsApp"
    const val BANCO_NU = "Nu"
    const val BANCO_GOOGLE_WALLET = "Google Wallet"
    const val BANCO_PAYPAL = "PayPal"
    const val BANCO_CAJA_SOCIAL = "Banco Caja Social"

    val PAQUETES_BANCOS: Map<String, String> = mapOf(
        "com.nequi.MobileApp" to BANCO_NEQUI,
        "com.todo1.mobile" to BANCO_BANCOLOMBIA,
        "com.bancolombia.personas" to BANCO_BANCOLOMBIA,
        "com.davivienda.daviplataapp" to BANCO_DAVIPLATA,
        "com.davivienda.mobileapp" to BANCO_DAVIVIENDA,
        "com.nu.production" to BANCO_NU,
        "com.google.android.apps.walletnfcrel" to BANCO_GOOGLE_WALLET,
        "com.paypal.android.p2pmobile" to BANCO_PAYPAL,
        "com.bcsc.app" to BANCO_CAJA_SOCIAL
    )

    private fun patron(expresion: String) = Regex(expresion, RegexOption.IGNORE_CASE)
    private val diacriticos = Regex("\\p{M}+")
    private fun normalizar(texto: String): String = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(diacriticos, "").replace(' ', ' ').lowercase(Locale.ROOT)

    // Todo el importe se captura antes de interpretarlo; nunca se acepta un prefijo de $25000.
    private const val DINERO = """(?:\$\s*|COP\s*\$?\s*)(\d+(?:[.,]\d+)*)(?!\d)"""
    private val monedaExtranjera = patron("""\b(?:USD|EUR|GBP|dolares|euros)\b|US\$|[€£]""")
    private val promocion = patron("""\b(?:promo(?:cion(?:es)?)?|ofertas?|descuentos?|cashback|aprovecha|participa|consigue|premios?|ganaste\s+un\s+premio|gana|ganate|obten|recibe|acumula|redime|recarga|comprando|compra\s+ahora|paga\s+ahora|envia\s+ahora|solo\s+por|desde(?=\s+\$)|hasta(?=\s+\$)|a\s+partir\s+de|por\s+tus\s+compras|por\s+compras|vigencia|valido\s+hasta|aplican\s+tyc|terminos\s+y\s+condiciones|codigo\s+de\s+descuento|2x1)\b""")
    private val noRealizado = patron("""\b(?:rechazad[oa]|declinad[oa]|fallid[oa]|pendiente|en\s+proceso|en\s+curso|procesando|programa|agenda|autoriza|confirma|programad[oa]|agendad[oa]|cancelad[oa]|anulad[oa]|reversad[oa]|revertid[oa]|fondos\s+insuficientes|saldo\s+insuficiente|intento|solicitud|solicita|solicitaste|solicitada|por\s+confirmar|por\s+aprobar|no\s+(?:se\s+)?(?:pudimos|pudiste|pudo|fue|ha\s+sido|has\s+(?:recibido|enviado|pagado)|(?:te\s+)?(?:envio|transfirio|consigno)|completad[oa]|realizaste|recibiste|enviaste)|si\s+(?:pagas|compras|envias|recibes)|puedes\s+(?:pagar|comprar|enviar|recibir)|debes\s+pagar|pago\s+minimo|fecha\s+limite|saldo\s+disponible\s+para\s+compras|codigo\s+(?:de\s+)?(?:seguridad|verificacion|autorizacion)|clave\s+(?:dinamica|temporal)|otp|para\s+(?:aprobar|autorizar|confirmar|realizar)\s+(?:tu\s+|la\s+|el\s+)?(?:compra|pago|transferencia))\b""")
    private val realizado = patron("""\b(?:pagaste|compraste|has\s+pagado|hiciste\s+(?:un\s+)?pago|realizaste\s+(?:un[oa]?\s+)?(?:pago|compra)|aprobada?|exitos[oa]|realizad[oa]|procesad[oa]|completad[oa]|pagad[oa]|debitad[oa])\b""")
    private val marcaBanco = listOf(
        patron("""\bbancolombia\b""") to BANCO_BANCOLOMBIA,
        patron("""\bnequi\b""") to BANCO_NEQUI,
        patron("""\bdaviplata\b""") to BANCO_DAVIPLATA,
        patron("""\bdavivienda\b""") to BANCO_DAVIVIENDA,
        patron("""\b(?:nu|nubank)\b""") to BANCO_NU,
        patron("""\bbanco\s+caja\s+social\b""") to BANCO_CAJA_SOCIAL,
        patron("""\bpaypal\b""") to BANCO_PAYPAL
    )
    private fun bancoSms(texto: String, remitente: String?): String {
        val emisor = normalizar(remitente.orEmpty())
        marcaBanco.firstOrNull { it.first.containsMatchIn(emisor) }?.let { return it.second }
        // Una marca al principio identifica el emisor; una marca tras "a" puede ser el destino.
        val inicio = normalizar(texto).trimStart().take(50)
        marcaBanco.firstOrNull { it.first.find(inicio)?.range?.first == 0 }?.let { return it.second }
        if (normalizar(texto).trimEnd('.', ' ').endsWith("banco caja social")) return BANCO_CAJA_SOCIAL
        return "SMS"
    }

    private fun descartado(texto: String): Boolean {
        val normal = normalizar(texto)
        return texto.isBlank() || promocion.containsMatchIn(normal) ||
            noRealizado.containsMatchIn(normal) || monedaExtranjera.containsMatchIn(normal)
    }

    fun parsear(paquete: String, texto: String): TransaccionDetectada? {
        val banco = PAQUETES_BANCOS[paquete] ?: return null
        return analizar(texto, banco, origenBancario = true)
    }

    fun parsearTextoLibre(texto: String, remitente: String? = null): TransaccionDetectada? {
        val banco = bancoSms(texto, remitente)
        return analizar(texto, banco, origenBancario = banco != "SMS")
    }

    private val ingresos = listOf(
        patron("""\b(?:recibiste|has\s+recibido|te\s+(?:enviaron|transfirieron|consignaron|abonaron|depositaron|llegaron|llego)|llego\s+a\s+tu\s+cuenta)\s+(?:(?:un[oa]?\s+)?(?:transferencia|pago|abono|deposito|consignacion)\s+(?:por|de)\s+)?$DINERO"""),
        patron("""\b(?:transferencia|consignacion|deposito|pago)\s+recibid[oa]\s+(?:(?:por|de)\s+)?$DINERO"""),
        patron("""\b(?:se\s+)?(?:abonaron|acreditaron|consignaron|depositaron)\s+$DINERO\s+en\s+tu\s+cuenta"""),
        patron("""\bte\s+(?:envio|transfirio|consigno)\s+$DINERO"""),
        patron("""\b(?:abono|deposito|consignacion)\s+(?:exitos[oa]|recibid[oa]|realizad[oa])\s+(?:(?:por|de)\s+)?$DINERO""")
    )
    private val envios = listOf(
        patron("""\b(?:enviaste|transferiste|pasaste|has\s+enviado|has\s+transferido)\s+(?:(?:un[oa]?\s+)?(?:transferencia|pago)\s+(?:por|de)\s+)?$DINERO"""),
        patron("""\b(?:transferencia|envio)\s+(?:enviad[oa]|realizad[oa]|exitos[oa]|completad[oa])\s+(?:(?:por|de)\s+)?$DINERO"""),
        patron("""\b(?:realizaste|hiciste)\s+(?:una?\s+)?transferencia\s+(?:(?:por|de)\s+)?$DINERO""")
    )
    private val retiros = listOf(
        patron("""\b(?:retiraste|sacaste)\s+$DINERO"""),
        patron("""\bretiro\s+(?:(?:exitoso|realizado|aprobado)\s+)?(?:por|de)\s+$DINERO""")
    )
    private val compras = listOf(
        patron("""\b(?:pagaste|compraste|has\s+pagado)\s+(?:por\s+)?$DINERO"""),
        patron("""\b(?:compra|pago|cargo|debito|transaccion)\s+(?:(?:aprobada?|exitos[oa]|realizad[oa]|pagad[oa])\s+)?(?:por|de)\s+$DINERO"""),
        patron("""\b(?:compra|pago)\s+(?:en|a)\s+.+?\s+por\s+$DINERO"""),
        patron("""\b(?:hiciste|realizaste)\s+(?:un\s+)?pago\s+por\s+$DINERO""")
    )
    private val transferenciaNominal = patron("""\btransferencia\s+(?:por|de)\s+$DINERO(?=\s+a\s+)""")
    private val transferenciaAmbigua = patron("""\b(?:transferencia|envio|recibiste|recibido|recibida|abono|consignacion|deposito)\b""")
    private val pse = patron("""\bpse\b|\bpago\s+en\s+linea\b""")
    private val caja = patron("""un\(a\)\s+(.+?)\s+por\s+""" + DINERO)
    private val comercioAntes = patron("""\b(?:compra|pago)\s+(?:en|a)\s+(.+?)\s+por\s+(?:\$|COP)""")
    private val nombreRemitente = patron("""(?:^|[.:\n])\s*([\p{L}][\p{L} .'-]{1,70}?)\s+te\s+(?:envio|transfirio|consigno)\s+""")
    private val nombreDespues = patron("""^\s*(?:COP\s*)?(?:de|a|en)\s+(.+?)(?:\s+(?:desde|con\s+tu|con\s+la|el\s+\d|a\s+las|fue|aprobada|realizado|realizada|exitoso|exitosa|saldo|referencia|ref\b)|[.!\n]|$)""")

    private fun coincidencia(patrones: List<Regex>, normal: String): MatchResult? =
        patrones.firstNotNullOfOrNull { it.find(normal) }

    private fun analizar(texto: String, banco: String, origenBancario: Boolean): TransaccionDetectada? {
        if (descartado(texto)) return null
        val normal = normalizar(texto)
        val ingreso = coincidencia(ingresos, normal)
        val envio = coincidencia(envios, normal)
        // Mensajes con varios movimientos o direcciones no se reducen a una sola transacción.
        if (ingreso != null && envio != null) return null
        if (ingreso != null) return resultado(texto, banco, ingreso, TransaccionDetectada.TIPO_INGRESO)
        if (envio != null) return resultado(texto, banco, envio, TransaccionDetectada.TIPO_TRANSFERENCIA)
        val evidencia = realizado.containsMatchIn(normal)
        if (!evidencia && !origenBancario) return null
        val retiro = coincidencia(retiros, normal)
        if (retiro != null) return resultado(texto, banco, retiro, TransaccionDetectada.TIPO_RETIRO)
        if (origenBancario) transferenciaNominal.find(normal)?.let {
            return resultado(texto, banco, it, TransaccionDetectada.TIPO_TRANSFERENCIA)
        }
        // Evita transformar un ingreso ambiguo o una transferencia sin dirección en una compra.
        if (transferenciaAmbigua.containsMatchIn(normal)) return null
        if (pse.containsMatchIn(normal) && evidencia) {
            coincidencia(compras, normal)?.let {
                return resultado(texto, "PSE / $banco", it, TransaccionDetectada.TIPO_PAGO_SERVICIO)
            }
            // Conserva el flujo existente de PSE truncado, solo con título inequívoco de éxito.
            if (normal.contains("pago exitoso por pse")) {
                val entidad = Regex("""(?i)hiciste\s+un\s+pago\s+en\s+(.+?)(?:\s+por|\s+y\s+|\.\.\.|$)""")
                    .find(texto)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "PSE" }
                return TransaccionDetectada(0.0, entidad, tipo = TransaccionDetectada.TIPO_PAGO_SERVICIO,
                    banco = "PSE / $banco", textoOriginal = texto)
            }
        }
        if (banco == BANCO_CAJA_SOCIAL && evidencia) {
            caja.find(normal)?.let { match ->
                val monto = parsearMonto(match.groupValues[2]) ?: return null
                val rango = match.groups[1]!!.range
                return TransaccionDetectada(monto, texto.substring(rango), tipo = TransaccionDetectada.TIPO_PAGO_SERVICIO,
                    banco = banco, textoOriginal = texto)
            }
        }
        val compra = coincidencia(compras, normal) ?: return null
        return resultado(texto, banco, compra,
            if (pse.containsMatchIn(normal)) TransaccionDetectada.TIPO_PAGO_SERVICIO else TransaccionDetectada.TIPO_COMPRA)
    }

    private fun resultado(texto: String, banco: String, match: MatchResult, tipo: String): TransaccionDetectada? {
        val monto = parsearMonto(match.groupValues[1]) ?: return null
        val normal = normalizar(texto)
        val antes = comercioAntes.find(normal)?.groups?.get(1)?.range
        val remitente = nombreRemitente.find(normal)?.groups?.get(1)?.range
        val despues = nombreDespues.find(normal.substring(match.range.last + 1))?.groups?.get(1)?.range
        val comercio = when {
            antes != null && tipo in listOf(TransaccionDetectada.TIPO_COMPRA, TransaccionDetectada.TIPO_PAGO_SERVICIO) -> texto.substring(antes)
            despues != null -> texto.substring(despues.first + match.range.last + 1, despues.last + match.range.last + 2)
            tipo == TransaccionDetectada.TIPO_INGRESO && remitente != null -> texto.substring(remitente)
            tipo == TransaccionDetectada.TIPO_INGRESO -> "Dinero recibido"
            tipo == TransaccionDetectada.TIPO_TRANSFERENCIA -> "Envío de dinero"
            tipo == TransaccionDetectada.TIPO_RETIRO -> "Retiro"
            else -> "Movimiento en $banco"
        }.trim().trimEnd('.', ',')
        return TransaccionDetectada(monto, comercio, tipo = tipo, banco = banco, textoOriginal = texto)
    }

    // Entradas conservadas para clientes existentes; todas aplican el mismo filtro previo.
    fun parseNequi(texto: String) = analizar(texto, BANCO_NEQUI, true)
    fun parseBancolombia(texto: String) = analizar(texto, BANCO_BANCOLOMBIA, true)
    fun parseDaviplata(texto: String) = analizar(texto, BANCO_DAVIPLATA, true)
    fun parseDavivienda(texto: String) = analizar(texto, BANCO_DAVIVIENDA, true)
    fun parseNu(texto: String) = analizar(texto, BANCO_NU, true)
    fun parseGoogleWallet(texto: String) = analizar(texto, BANCO_GOOGLE_WALLET, true)
    fun parsePayPal(texto: String) = analizar(texto, BANCO_PAYPAL, true)
    fun parseCajaSocial(texto: String) = analizar(texto, BANCO_CAJA_SOCIAL, true)
    @Suppress("UNUSED_PARAMETER")
    fun parseWhatsapp(texto: String): TransaccionDetectada? = null
    fun parsePSE(texto: String, banco: String): TransaccionDetectada? =
        if (pse.containsMatchIn(normalizar(texto))) analizar(texto, banco, false) else null
    fun parseGenerico(texto: String, origen: String) = analizar(texto, origen, false)

    private val entero = Regex("""^\d+$""")
    private val agrupadoPunto = Regex("""^\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?$""")
    private val agrupadoComa = Regex("""^\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?$""")
    private val decimal = Regex("""^\d+[.,]\d{1,2}$""")

    /** Formatos COP y decimales; se rechazan agrupaciones malformadas, infinito y cero. */
    fun parsearMonto(texto: String): Double? {
        val limpio = texto.trim().trimEnd('.', ',')
        val normal = when {
            entero.matches(limpio) -> limpio
            agrupadoPunto.matches(limpio) -> limpio.replace(".", "").replace(',', '.')
            agrupadoComa.matches(limpio) -> limpio.replace(",", "")
            decimal.matches(limpio) -> limpio.replace(',', '.')
            else -> return null
        }
        return normal.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }
}




