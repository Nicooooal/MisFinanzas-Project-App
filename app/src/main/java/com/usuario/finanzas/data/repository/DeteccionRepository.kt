package com.usuario.finanzas.data.repository

import com.usuario.finanzas.domain.model.TransaccionDetectada
import androidx.room.withTransaction
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.entities.RegistroDeteccion
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.usuario.finanzas.data.preferences.CoordinadorDatos

/**
 * Puente entre los servicios de detección (NotificationListener / SmsReceiver)
 * y la BD. Ahora las detecciones se persisten en `transacciones_pendientes`
 * para que no se pierdan si el usuario borra la notificación o cierra la app.
 */
@Singleton
class DeteccionRepository @Inject constructor(
    private val pendientesRepository: PendientesRepository,
    private val db: FinanzasDatabase,
    private val coordinador: CoordinadorDatos
) {
    /**
     * Guarda la detección en la bandeja de pendientes.
     * Devuelve el id asignado (útil para abrir directo la pantalla de confirmación
     * desde la notificación push).
     */
    suspend fun publicar(deteccion: TransaccionDetectada): Int? = coordinador.capturar { db.withTransaction {
        if ((db.finanzasDao().configuracion()?.moneda ?: "COP") != "COP") return@withTransaction null
        val dao = db.registroDeteccionDao()
        val clave = huella(deteccion.claveOrigen
            ?: "${deteccion.canal}|${deteccion.textoOriginal}|${deteccion.fecha}")
        if (dao.existe(clave)) return@withTransaction null
        val coincidencia = huella(listOf(
            normalizar(deteccion.banco.removePrefix("PSE / ")),
            deteccion.monto.toString(), normalizar(deteccion.comercio), deteccion.direccion
        ).joinToString("|"))
        // Solo cruzar canales cuando hay banco y contraparte identificables.
        val tieneContexto = deteccion.canal.isNotBlank() &&
            deteccion.banco !in listOf("SMS", "Desconocido", "WhatsApp") &&
            deteccion.comercio.isNotBlank() &&
            deteccion.comercio !in listOf("Dinero recibido", "Envío de dinero", "Retiro", "PSE") &&
            !deteccion.comercio.startsWith("Movimiento") &&
            normalizar(deteccion.comercio) != normalizar(deteccion.banco)
        val pareja = if (tieneContexto) dao.buscarEnOtroCanal(
            coincidencia, deteccion.canal, deteccion.fecha - 90_000, deteccion.fecha + 90_000
        ) else null
        // Cada aviso solo puede emparejarse una vez: dos pagos iguales siguen siendo dos.
        pareja?.let { dao.emparejar(it) }
        dao.insertar(RegistroDeteccion(clave, coincidencia, deteccion.canal, deteccion.fecha, pareja != null))
        dao.depurar(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        if (pareja != null) null else pendientesRepository.agregar(deteccion)
    } }

    private fun normalizar(texto: String): String = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun huella(texto: String): String = MessageDigest.getInstance("SHA-256")
        .digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Elimina una detección (ya guardada como transacción o descartada). */
    suspend fun consumir(id: Int) = pendientesRepository.eliminar(id)
}
