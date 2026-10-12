package com.usuario.finanzas.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.usuario.finanzas.ui.screens.captura.EstadoDictado
import com.usuario.finanzas.ui.screens.captura.FaseDictado
import com.usuario.finanzas.ui.screens.captura.PanelDictado
import com.usuario.finanzas.ui.theme.FinanzasTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Real Compose layouts: no microphone, recognizer, Hilt graph or financial database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class PanelDictadoInterfazTest {
    @Test fun preparandoNuncaAfirmaQueElMicrofonoEstaActivoYPermiteCancelar() {
        verificar(EstadoDictado(fase = FaseDictado.PREPARANDO), "Preparando micrófono…", "Cancelar")
    }

    @Test fun escuchandoMuestraEstadoTextoParcialYPermiteTerminar() {
        verificar(EstadoDictado(fase = FaseDictado.ESCUCHANDO, parcial = "Almuerzo veinticinco mil pesos", nivel = .7f), "Micrófono activo", "Terminar dictado")
    }

    @Test fun errorPermiteReintentarUsarServicioYAbrirAjustes() {
        verificar(EstadoDictado(fase = FaseDictado.ERROR, error = "No escuché ninguna palabra. Acércate al micrófono y vuelve a intentarlo."), "No pudimos completar el dictado", "Intentar de nuevo")
    }

    private fun verificar(estado: EstadoDictado, titulo: String, accion: String) {
        val localeAnterior = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-CO"))
        try {
            listOf("claro", "oscuro").forEach { tema ->
                // Show the initially static activity before installing the animated Compose panel:
                // ActivityController.visible() drains the looper and must not see an endless spinner.
                Robolectric.buildActivity(CompatibilidadInterfazTest.ActividadPrueba::class.java).setup().visible().use { controller ->
                    val activity = controller.get()
                    var compuesto = false
                    var densidadAplicada: Density? = null
                    val acciones = mutableListOf<String>()
                    activity.setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                            FinanzasTheme(tema) {
                                val densidad = LocalDensity.current
                                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
                                            PanelDictado(estado,
                                        onDetener = { acciones += "Terminar dictado" },
                                        onCancelar = { acciones += if (estado.fase == FaseDictado.ERROR) "Cerrar" else "Cancelar" },
                                        onReintentar = { acciones += "Intentar de nuevo" },
                                        onServicio = { acciones += "Usar servicio de voz del teléfono" },
                                        onAjustes = { acciones += "Ajustes de voz y permisos" })
                                        }
                                    }
                                }
                                SideEffect { compuesto = true; densidadAplicada = densidad }
                            }
                        }
                    }
                    val contenido = activity.findViewById<View>(android.R.id.content)
                    assertTrue("El host debe estar adjunto", contenido.isAttachedToWindow)
                    assertTrue("El host debe estar visible", contenido.isShown)
                    assertTrue("El host debe estar iniciado", activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                    contenido.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
                    contenido.layout(0, 0, 320, 640)
                    // The indeterminate spinner schedules frames forever: never drain its queue.
                    // Four bounded tasks permit pending attachment/frame callbacks to settle.
                    repeat(4) { Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
                    assertTrue("$tema/${estado.fase} debe componer a 320dp y fuente 1.3", compuesto)
                    val raiz = buscarRaiz(contenido) ?: error("No se encontró la raíz de Compose")
                    raiz.measureAndLayoutForTest()
                    assertEquals("La ventana y Compose deben usar el mismo ancho de 320dp/mdpi", 320, raiz.view.width)
                    assertEquals(1f, raiz.density.density, 0.001f)
                    assertEquals(1.3f, requireNotNull(densidadAplicada).fontScale, 0.001f)
                    fun nodos() = aplanar(raiz.semanticsOwner.rootSemanticsNode)
                    fun textos(nodo: SemanticsNode) = nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                    val tituloNodo = nodos().firstOrNull { titulo in textos(it) }
                    assertNotNull("Título visible: $titulo", tituloNodo)
                    assertTrue("El título debe tener ancho útil, no una columna comprimida", tituloNodo!!.boundsInRoot.width >= 180f)
                    assertTrue("El título debe estar dentro del panel", tituloNodo.boundsInRoot.height > 0f)
                    if (estado.fase != FaseDictado.ESCUCHANDO) assertFalse(nodos().any { "Micrófono activo" in textos(it) })
                    if (estado.parcial.isNotBlank()) assertTrue(nodos().any { estado.parcial in textos(it) })
                    val nombre = "dictado-${estado.fase.name.lowercase()}-$tema"
                    guardarCaptura(contenido, nombre)
                    var desplazado = false
                    val diagnosticoScroll = mutableListOf<String>()
                    fun estadoScroll(): String = nodos().mapNotNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) }
                        .joinToString { "value=${it.value()}, max=${it.maxValue()}" }
                    val accionesEsperadas = if (estado.fase == FaseDictado.ERROR) listOf(accion, "Usar servicio de voz del teléfono", "Ajustes de voz y permisos", "Cerrar") else listOf(accion, "Cancelar").distinct()
                    accionesEsperadas.forEach { etiqueta ->
                        fun buscarBoton() = nodos().firstOrNull { etiqueta in textos(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
                        fun visible(nodo: SemanticsNode): Boolean {
                            val b = nodo.boundsInRoot
                            return b.width >= 180f && b.height >= nodo.size.height - 1f && b.height >= 40f &&
                                b.left >= 20f && b.right <= raiz.view.width - 20f && b.top >= 20f && b.bottom <= raiz.view.height - 20f
                        }
                        var boton = requireNotNull(buscarBoton()) { "Acción no encontrada: $etiqueta" }
                        var intentos = 0
                        while (!visible(boton) && intentos++ < 4) {
                            val desplazar = nodos().firstNotNullOfOrNull { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }
                            assertNotNull("El control fuera de vista debe poder alcanzarse con scroll", desplazar)
                            val antes = estadoScroll()
                            assertTrue(desplazar!!.invoke(0f, 120f))
                            // Scroll semantics launches a coroutine/animation. Advance a bounded
                            // duration so its frames complete without draining an infinite spinner.
                            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
                            raiz.measureAndLayoutForTest()
                            boton = requireNotNull(buscarBoton())
                            diagnosticoScroll += "$etiqueta/$intentos: antes[$antes], después[${estadoScroll()}], bounds=${boton.boundsInRoot}"
                            desplazado = true
                        }
                        if (!visible(boton)) guardarCaptura(contenido, "$nombre-acciones-fallo")
                        assertTrue("$etiqueta debe ser completamente visible, sin recorte horizontal ni vertical: ${boton.boundsInRoot}; scroll=$diagnosticoScroll; lifecycle=${activity.lifecycle.currentState}; attached=${contenido.isAttachedToWindow}; shown=${contenido.isShown}", visible(boton))
                        assertTrue(boton.config[SemanticsActions.OnClick].action!!.invoke())
                        assertTrue("Callback $etiqueta", etiqueta in acciones)
                    }
                    if (desplazado) guardarCaptura(contenido, "$nombre-acciones")
                }
            }
        } finally { Locale.setDefault(localeAnterior) }
    }

    private fun guardarCaptura(contenido: View, nombre: String) {
        // Draw exactly the measured Android view, not a larger synthetic bitmap.
        val bitmap = Bitmap.createBitmap(contenido.width, contenido.height, Bitmap.Config.ARGB_8888)
        contenido.draw(Canvas(bitmap))
        val proyecto = File(requireNotNull(System.getProperty("user.dir"))).let { if (it.name == "app") requireNotNull(it.parentFile) else it }
        val archivo = File(proyecto, "tools/previews/$nombre.png")
        requireNotNull(archivo.parentFile).mkdirs()
        archivo.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun buscarRaiz(vista: View): ViewRootForTest? {
        if (vista is ViewRootForTest) return vista
        if (vista is ViewGroup) repeat(vista.childCount) { buscarRaiz(vista.getChildAt(it))?.let { raiz -> return raiz } }
        return null
    }

    private fun aplanar(nodo: SemanticsNode): List<SemanticsNode> = listOf(nodo) + nodo.children.flatMap(::aplanar)
}
