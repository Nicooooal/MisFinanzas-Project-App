package com.usuario.finanzas.ui.screens.bloqueo

import com.usuario.finanzas.ui.i18n.tr

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla inicial. Primera vez: configuración del PIN de 4 dígitos.
 * Después: desbloqueo con huella (BiometricPrompt) o PIN.
 */
@Composable
fun PantallaBloqueo(
    onDesbloqueado: () -> Unit,
    viewModel: BloqueoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity

    var configurando by remember { mutableStateOf(!viewModel.tienePin()) }
    var pin by remember { mutableStateOf("") }
    var pinConfirmacion by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var procesando by remember { mutableStateOf(false) }
    var espera by remember { mutableStateOf(viewModel.segundosBloqueoPin()) }
    val scope = rememberCoroutineScope()
    val preferencias by viewModel.preferencias.collectAsStateWithLifecycle()
    val desbloquear by rememberUpdatedState(onDesbloqueado)

    val biometriaDisponible = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    desbloquear()
                }
            }
        )
    }
    DisposableEffect(prompt) { onDispose { prompt.cancelAuthentication() } }

    fun mostrarBiometria() {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(tr("Desbloquear Mis Finanzas"))
            .setSubtitle(tr("Usa tu huella"))
            .setNegativeButtonText(tr("Usar PIN"))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    // Al entrar (si ya hay PIN y hay huella registrada) se lanza la biometría directamente
    LaunchedEffect(configurando, preferencias.biometria) {
        if (!configurando && biometriaDisponible && preferencias.biometria) mostrarBiometria()
    }
    LaunchedEffect(espera) {
        if (espera > 0) { delay(1000); espera = viewModel.segundosBloqueoPin() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(tr("Mis Finanzas"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        if (configurando) {
            // ---------- Configuración inicial del PIN ----------
            Text(tr("Configura tu PIN de 4 dígitos"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            CampoPin(valor = pin, etiqueta = tr("PIN nuevo")) { pin = it }
            Spacer(Modifier.height(8.dp))
            CampoPin(valor = pinConfirmacion, etiqueta = tr("Confirma el PIN")) { pinConfirmacion = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    when {
                        pin.length != 4 -> error = tr("El PIN debe tener 4 dígitos")
                        pin != pinConfirmacion -> error = tr("Los PIN no coinciden")
                        else -> {
                            procesando = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { viewModel.guardarPin(pin) }
                                    error = null
                                    desbloquear()
                                } catch (e: Exception) { error = e.message?.let { tr(it) } ?: tr("No se pudo guardar el PIN") }
                                finally { procesando = false }
                            }
                        }
                    }
                },
                enabled = !procesando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Guardar PIN")) }
        } else {
            // ---------- Desbloqueo ----------
            CampoPin(valor = pin, etiqueta = "PIN") { pin = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    procesando = true
                    scope.launch {
                        try {
                            if (withContext(Dispatchers.IO) { viewModel.validarPin(pin) }) desbloquear()
                            else {
                                error = tr("PIN incorrecto")
                                pin = ""
                                espera = viewModel.segundosBloqueoPin()
                            }
                        } catch (e: Exception) { error = e.message?.let { tr(it) } ?: tr("No se pudo validar el PIN") }
                        finally { procesando = false }
                    }
                },
                enabled = !procesando && espera == 0L && pin.length == 4,
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Desbloquear")) }

            if (biometriaDisponible && preferencias.biometria) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { mostrarBiometria() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(tr("  Desbloquear con huella"))
                }
            }
        }

        if (espera > 0) {
            Spacer(Modifier.height(12.dp))
            Text(tr("Demasiados intentos. Espera {0} segundos.", espera), color = MaterialTheme.colorScheme.error)
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(tr(it), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CampoPin(valor: String, etiqueta: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> if (nuevo.length <= 4 && nuevo.all { it in '0'..'9' }) onCambio(nuevo) },
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}
