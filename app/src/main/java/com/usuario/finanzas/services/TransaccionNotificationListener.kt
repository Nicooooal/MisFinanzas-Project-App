package com.usuario.finanzas.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.usuario.finanzas.data.repository.DeteccionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Escucha las notificaciones del sistema y filtra las de apps bancarias conocidas.
 *
 * IMPORTANTE: el usuario debe conceder el acceso manualmente en:
 *   Ajustes -> Notificaciones -> Acceso a notificaciones -> Mis Finanzas
 * (la app incluye un botón en el Dashboard que abre esa pantalla).
 */
@AndroidEntryPoint
class TransaccionNotificationListener : NotificationListenerService() {

    @Inject lateinit var deteccionRepository: DeteccionRepository
    @Inject lateinit var notificacionHelper: NotificacionHelper

    // El parseo y el guardado se realizan en un hilo de fondo.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val paquete = sbn.packageName ?: return
        // Solo nos interesan los paquetes de bancos conocidos
        if (paquete !in TransaccionParser.PAQUETES_BANCOS) return

        val notificacion = sbn.notification ?: return
        if (notificacion.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notificacion.extras ?: return
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val textoGrande = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        // El BigText suele traer el mensaje completo; si no, usamos el texto normal
        val cuerpo = if (textoGrande.length > texto.length) textoGrande else texto
        val completo = listOf(titulo, cuerpo).filter { it.isNotBlank() }.joinToString(". ")
        if (completo.isBlank()) return

        scope.launch {
            val detectada = TransaccionParser.parsear(paquete, completo)?.copy(
                fecha = sbn.postTime, claveOrigen = "push:${sbn.key}:${sbn.postTime}", canal = "notificacion"
            ) ?: return@launch
            val id = deteccionRepository.publicar(detectada) ?: return@launch
            notificacionHelper.notificarDeteccion(detectada, id)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}



