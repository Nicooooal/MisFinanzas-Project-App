package com.usuario.finanzas.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.usuario.finanzas.MainActivity
import com.usuario.finanzas.R
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.domain.model.TransaccionDetectada
import com.usuario.finanzas.ui.util.formatearMonto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Crea los canales y emite las notificaciones locales de la app. */
@Singleton
class NotificacionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        crearCanales()
    }

    private fun crearCanales() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CANAL_DETECCIONES,
                "Transacciones detectadas",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CANAL_RECORDATORIOS,
                "Recordatorios de pagos",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** "Nueva transacción detectada: $XX.XXX en [comercio] - Tocar para revisar" */
    fun notificarDeteccion(deteccion: TransaccionDetectada, pendienteId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACCION_VER_PENDIENTES
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PENDIENTE_ID, pendienteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 100 + pendienteId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val direccion = if (deteccion.direccion == "entrada") "Posible entrada" else "Posible salida"
        val notificacion = NotificationCompat.Builder(context, CANAL_DETECCIONES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$direccion de dinero")
            .setContentText("${formatearMonto(deteccion.monto, "COP")} · ${deteccion.comercio} · Tocar para revisar")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${formatearMonto(deteccion.monto, "COP")} · ${deteccion.comercio} " +
                        "(${deteccion.banco}) · Tocar para revisar en Pendientes"
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificar(CANAL_DETECCIONES, pendienteId, notificacion)
    }

    /** "Recordatorio: [Servicio] vence en X días - $XX.XXX" */
    fun notificarRecordatorio(servicio: Servicio, diasRestantes: Long, moneda: String = "COP") {
        val cuandoVence = when (diasRestantes) {
            0L -> "vence HOY"
            1L -> "vence mañana"
            else -> "vence en $diasRestantes días"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 200 + servicio.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacion = NotificationCompat.Builder(context, CANAL_RECORDATORIOS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recordatorio de pago")
            .setContentText("${servicio.nombre} $cuandoVence - ${formatearMonto(servicio.montoEstimado, moneda)}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // Un id por servicio para no apilar recordatorios duplicados
        notificar(CANAL_RECORDATORIOS, servicio.id, notificacion)
    }

    private fun notificar(canal: String, id: Int, notificacion: android.app.Notification) {
        // En Android 13+ POST_NOTIFICATIONS es permiso de ejecución
        val permitido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (permitido) {
            NotificationManagerCompat.from(context).notify(canal, id, notificacion)
        }
    }

    companion object {
        const val CANAL_DETECCIONES = "detecciones"
        const val CANAL_RECORDATORIOS = "recordatorios"
        const val ACCION_VER_PENDIENTES = "com.usuario.finanzas.ACCION_VER_PENDIENTES"
        const val EXTRA_PENDIENTE_ID = "pendiente_id"
        const val ID_DETECCION_BASE = 1000
    }
}
