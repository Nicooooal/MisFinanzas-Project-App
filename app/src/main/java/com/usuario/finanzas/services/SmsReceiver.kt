package com.usuario.finanzas.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.usuario.finanzas.data.repository.DeteccionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Respaldo de la detección por notificaciones: escucha los SMS entrantes
 * (los bancos colombianos suelen enviar SMS con el mismo formato) y los
 * pasa por el mismo TransaccionParser.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var deteccionRepository: DeteccionRepository
    @Inject lateinit var notificacionHelper: NotificacionHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val mensajes = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Los SMS largos llegan en partes: se concatena el cuerpo completo
        val cuerpo = mensajes.joinToString("") { it.messageBody.orEmpty() }
        if (cuerpo.isBlank()) return

        // El parseo y el guardado se realizan fuera del hilo principal; es
        // trabajo que no debe bloquear el receptor: se hace en un hilo de fondo
        // con goAsync() para no bloquear el hilo principal ni provocar ANR.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val remitente = mensajes.firstOrNull()?.originatingAddress
                val fecha = mensajes.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                val detectada = TransaccionParser.parsearTextoLibre(cuerpo, remitente)?.copy(
                    fecha = fecha, claveOrigen = "sms:$remitente:$fecha:$cuerpo", canal = "sms"
                )
                if (detectada != null) {
                    val id = deteccionRepository.publicar(detectada) ?: return@launch
                    notificacionHelper.notificarDeteccion(detectada, id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}


