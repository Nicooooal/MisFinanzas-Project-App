package com.usuario.finanzas.ui.navigation

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.usuario.finanzas.ui.screens.historial.PantallaHistorialMes
import com.usuario.finanzas.ui.screens.bloqueo.PantallaBloqueo
import com.usuario.finanzas.ui.screens.confirmacion.PantallaConfirmacionTransaccion
import com.usuario.finanzas.ui.screens.dashboard.PantallaDashboard
import com.usuario.finanzas.ui.screens.manual.PantallaAgregarTransaccionManual
import com.usuario.finanzas.ui.screens.pendientes.PantallaPendientes
import com.usuario.finanzas.ui.screens.servicios.PantallaDetalleServicio
import com.usuario.finanzas.ui.screens.servicios.PantallaServicios
import com.usuario.finanzas.ui.screens.transacciones.PantallaDetalleTransaccion
import com.usuario.finanzas.ui.screens.transacciones.PantallaTransacciones
import com.usuario.finanzas.ui.screens.resumen.*
import com.usuario.finanzas.ui.screens.ajustes.*
import com.usuario.finanzas.ui.screens.captura.PantallaCaptura
import com.usuario.finanzas.ui.screens.planes.*
import com.usuario.finanzas.ui.screens.categorias.PantallaCategorias
import com.usuario.finanzas.ui.screens.onboarding.PantallaOnboarding

object Rutas {
    const val BLOQUEO = "bloqueo"
    const val DASHBOARD = "dashboard"
    const val TRANSACCIONES = "transacciones"
    const val SERVICIOS = "servicios"
    const val PENDIENTES = "pendientes"
    const val DETALLE_TRANSACCION = "detalle_transaccion/{id}"
    const val DETALLE_SERVICIO = "detalle_servicio/{id}"
    const val CONFIRMACION = "confirmacion/{pendienteId}"
    const val AGREGAR_MANUAL = "agregar_manual"
    const val HISTORIAL_MES = "historial_mes/{anio}/{mes}"

    fun detalleTransaccion(id: Int) = "detalle_transaccion/$id"
    fun detalleServicio(id: Int) = "detalle_servicio/$id"
    fun confirmacionDesde(pendienteId: Int) = "confirmacion/$pendienteId"
    fun historialMes(anio: Int, mes: Int) = "historial_mes/$anio/$mes"
}

@Composable
fun AppNavHost(pendienteId: Int? = null, onPendienteAtendido: () -> Unit = {}, onboardingPendiente: Boolean = false, bloqueoVersion: Int = 0) {
    var autenticado by remember(bloqueoVersion) { mutableStateOf(false) }
    if (!autenticado) {
        PantallaBloqueo(onDesbloqueado = { autenticado = true })
        return
    }
    val navController = rememberNavController()
    val entradaActual by navController.currentBackStackEntryAsState()
    val rutaActual = entradaActual?.destination?.route
    LaunchedEffect(pendienteId, rutaActual) {
        if (pendienteId != null && rutaActual != null && rutaActual != Rutas.BLOQUEO && rutaActual != "onboarding") {
            navController.navigate(Rutas.confirmacionDesde(pendienteId)) { launchSingleTop = true }
            onPendienteAtendido()
        }
    }

    NavHost(navController = navController, startDestination = if (onboardingPendiente) "onboarding" else Rutas.DASHBOARD) {
        composable("billetera") { PantallaBilletera(navController) }
        composable("estadisticas") { PantallaEstadisticas(navController) }
        composable("ajustes") { PantallaAjustes(navController) }
        composable("reportes") { PantallaReportes(navController) }
        composable("captura?modo={modo}", arguments = listOf(navArgument("modo") { defaultValue = "texto" })) { entrada ->
            PantallaCaptura(navController, modoInicial = entrada.arguments?.getString("modo") ?: "texto")
        }
        composable("planes") { PantallaPlanes(navController) }
        composable("recurrentes") { PantallaRecurrentes(navController) }
        composable("categorias") { PantallaCategorias(navController) }
        composable("onboarding") { PantallaOnboarding(navController) }

        composable(Rutas.BLOQUEO) {
            PantallaBloqueo(
                onDesbloqueado = {
                    navController.navigate(Rutas.DASHBOARD) {
                        popUpTo(Rutas.BLOQUEO) { inclusive = true }
                    }
                }
            )
        }

        composable(Rutas.DASHBOARD) {
            PantallaInicio(navController = navController)
        }

        composable(Rutas.TRANSACCIONES) {
            PantallaTransacciones(navController = navController)
        }

        composable(Rutas.SERVICIOS) {
            PantallaServicios(navController = navController)
        }

        composable(Rutas.PENDIENTES) {
            PantallaPendientes(navController = navController)
        }

        composable(
            route = Rutas.DETALLE_TRANSACCION,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) {
            PantallaDetalleTransaccion(navController = navController)
        }

        composable(
            route = Rutas.DETALLE_SERVICIO,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) {
            PantallaDetalleServicio(navController = navController)
        }

        composable(
            route = Rutas.CONFIRMACION,
            arguments = listOf(navArgument("pendienteId") { type = NavType.IntType })
        ) {
            PantallaConfirmacionTransaccion(navController = navController)
        }

        composable(Rutas.AGREGAR_MANUAL) {
            PantallaAgregarTransaccionManual(navController = navController)
        }

        composable(
            route = Rutas.HISTORIAL_MES,
            arguments = listOf(
                navArgument("anio") { type = NavType.IntType },
                navArgument("mes") { type = NavType.IntType }
            )
        ) {
            PantallaHistorialMes(navController = navController)
        }
    }
}

// ---------------- Barra de navegación inferior ----------------

@Composable
fun BarraInferior(
    navController: NavHostController,
    rutaActual: String,
    numPendientes: Int = 0
) {
    val colores = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        NavigationBarItem(
            colors = colores,
            selected = rutaActual == Rutas.DASHBOARD,
            onClick = { navController.navegarA(Rutas.DASHBOARD, rutaActual) },
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = tr("Inicio")) },
            label = { Text(tr("Inicio"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2) }
        )
        NavigationBarItem(
            colors = colores,
            selected = rutaActual in listOf("billetera", Rutas.TRANSACCIONES, Rutas.PENDIENTES, Rutas.SERVICIOS),
            onClick = { navController.navegarA("billetera", rutaActual) },
            icon = { BadgedBox(badge = { if (numPendientes > 0) Badge { Text(if (numPendientes > 99) "99+" else "$numPendientes") } }) { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = tr("Billetera")) } },
            label = { Text(tr("Billetera"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2) }
        )
        NavigationBarItem(
            colors = colores,
            selected = rutaActual == "estadisticas",
            onClick = { navController.navegarA("estadisticas", rutaActual) },
            icon = {
                BadgedBox(
                    badge = { }
                ) {
                    Icon(Icons.Filled.BarChart, contentDescription = tr("Estadísticas"))
                }
            },
            label = { Text(tr("Estadísticas"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2) }
        )
        NavigationBarItem(
            colors = colores,
            selected = rutaActual == "ajustes",
            onClick = { navController.navegarA("ajustes", rutaActual) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = tr("Ajustes")) },
            label = { Text(tr("Ajustes"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2) }
        )
    }
}

private fun NavHostController.navegarA(ruta: String, rutaActual: String) {
    if (rutaActual != ruta) {
        navigate(ruta) {
            popUpTo(Rutas.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
