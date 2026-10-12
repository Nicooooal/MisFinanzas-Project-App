package com.usuario.finanzas

import android.Manifest
import android.content.Intent
import android.content.Context
import android.os.SystemClock
import java.util.Locale
import javax.inject.Inject
import com.usuario.finanzas.data.preferences.PreferenciasApp
import com.usuario.finanzas.data.repository.FinanzasRepository
import androidx.compose.material3.CircularProgressIndicator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.usuario.finanzas.ui.util.FormatoFinanciero
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import com.usuario.finanzas.services.NotificacionHelper
import androidx.core.content.ContextCompat
import com.usuario.finanzas.ui.navigation.AppNavHost
import com.usuario.finanzas.ui.theme.FinanzasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferenciasApp: PreferenciasApp
    @Inject lateinit var finanzasRepository: FinanzasRepository
    private val bloqueoVersion = MutableStateFlow(0)
    private var salidaEn = 0L
    private val pendienteDeNotificacion = MutableStateFlow<Int?>(null)

    override fun attachBaseContext(newBase: Context) {
        val idioma = newBase.getSharedPreferences("finanzas_preferences", Context.MODE_PRIVATE).getString("idioma", "es") ?: "es"
        val locale = Locale.forLanguageTag(idioma)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onStop() { salidaEn = SystemClock.elapsedRealtime(); super.onStop() }
    override fun onStart() {
        super.onStart()
        if (salidaEn != 0L && SystemClock.elapsedRealtime() - salidaEn >= 60_000) bloqueoVersion.value++
        salidaEn = 0L
    }

    private val solicitudPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recibirPendiente(intent)
        solicitarPermisos()
        setContent {
            val preferencias by preferenciasApp.preferencias.collectAsStateWithLifecycle()
            val flujoConfiguracion = remember { finanzasRepository.observarConfiguracion() }
            val configuracion by flujoConfiguracion.collectAsStateWithLifecycle(initialValue = null)
            configuracion?.let { FormatoFinanciero.moneda = it.moneda }
            val bloqueo by bloqueoVersion.collectAsStateWithLifecycle()
            FinanzasTheme(tema = preferencias.tema) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val pendienteId by pendienteDeNotificacion.collectAsStateWithLifecycle()
                    if (configuracion == null) CircularProgressIndicator()
                    else AppNavHost(pendienteId = pendienteId, onboardingPendiente = configuracion?.onboardingCompleto == false, bloqueoVersion = bloqueo, onPendienteAtendido = {
                        pendienteDeNotificacion.value = null
                        intent.removeExtra(NotificacionHelper.EXTRA_PENDIENTE_ID)
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirPendiente(intent)
    }

    private fun recibirPendiente(intent: Intent?) {
        if (intent?.action == NotificacionHelper.ACCION_VER_PENDIENTES) {
            pendienteDeNotificacion.value = intent.getIntExtra(NotificacionHelper.EXTRA_PENDIENTE_ID, -1)
                .takeIf { it > 0 }
        }
    }

    private fun solicitarPermisos() {
        val necesarios = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (necesarios.isNotEmpty()) {
            solicitudPermisos.launch(necesarios.toTypedArray())
        }
    }
}
