package com.usuario.finanzas.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Preferencias(val idioma: String = "es", val tema: String = "sistema", val ocultar: Boolean = false, val biometria: Boolean = true)

@Singleton
class PreferenciasApp @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("finanzas_preferences", Context.MODE_PRIVATE)
    private fun leer() = Preferencias(prefs.getString("idioma", "es") ?: "es", prefs.getString("tema", "sistema") ?: "sistema", prefs.getBoolean("ocultar", false), prefs.getBoolean("biometria", true))
    private val estado = MutableStateFlow(leer())
    val preferencias = estado.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> estado.value = leer() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun idioma(valor: String) { require(valor in listOf("es", "en", "pt", "fr")); prefs.edit().putString("idioma", valor).apply() }
    fun tema(valor: String) { require(valor in listOf("sistema", "claro", "oscuro")); prefs.edit().putString("tema", valor).apply() }
    fun ocultar(valor: Boolean) { prefs.edit().putBoolean("ocultar", valor).apply() }
    fun biometria(valor: Boolean) { prefs.edit().putBoolean("biometria", valor).apply() }
}
