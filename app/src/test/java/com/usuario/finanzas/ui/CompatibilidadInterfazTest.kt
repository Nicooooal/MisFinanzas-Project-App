package com.usuario.finanzas.ui

import android.app.Application
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.usuario.finanzas.MainActivity
import com.usuario.finanzas.R
import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.ui.components.SelectorMovimiento
import com.usuario.finanzas.ui.theme.FinanzasTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Comprueba el contrato AppCompat/Fragment requerido por idioma y biometría sin cargar SQLCipher. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompatibilidadInterfazTest {
    class ActividadPrueba : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_MisFinanzas)
            super.onCreate(savedInstanceState)
            setContentView(TextView(this).apply { text = "Mis Finanzas" })
        }
    }

    @Test fun temaRealAdmiteDialogosAppCompatYBiometria() {
        assertTrue(AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java))
        assertTrue(FragmentActivity::class.java.isAssignableFrom(MainActivity::class.java))
        Robolectric.buildActivity(ActividadPrueba::class.java).setup().use { controller ->
            val activity = controller.get()
            val dialogo = AlertDialog.Builder(activity).setTitle("Comprobar tema").setPositiveButton("Aceptar", null).show()
            assertTrue(dialogo.isShowing)
            dialogo.dismiss()
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {})
            prompt.cancelAuthentication()
        }
    }

    @Test fun selectorDeMovimientoComponeConTemaClaroYOscuro() {
        listOf("claro", "oscuro").forEach { tema ->
            Robolectric.buildActivity(ActividadPrueba::class.java).setup().use { controller ->
                val activity = controller.get()
                var compuesto = false
                activity.setContent {
                    FinanzasTheme(tema = tema) {
                        SelectorMovimiento(ClaseMovimiento.COMPRA, onSeleccionar = {})
                        SideEffect { compuesto = true }
                    }
                }
                val contenido = activity.findViewById<View>(android.R.id.content)
                contenido.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                contenido.layout(0, 0, 1080, 1920)
                ShadowLooper.idleMainLooper()
                assertTrue("Composición del selector en tema $tema", compuesto)
            }
        }
    }
}
