package com.usuario.finanzas.domain

/**
 * Sugiere una categoría a partir de palabras clave en el nombre del comercio.
 * Diccionario simple y editable: agrega aquí los comercios que uses con frecuencia.
 */
object SugeridorCategoria {

    private val diccionario = mapOf(
        // Comida
        "rappi" to "Comida", "ifood" to "Comida", "mcdonald" to "Comida", "kfc" to "Comida",
        "corral" to "Comida", "frisby" to "Comida", "exito" to "Comida", "éxito" to "Comida",
        "carulla" to "Comida", "d1" to "Comida", "ara" to "Comida", "olimpica" to "Comida",
        "restaurante" to "Comida", "domicilio" to "Comida",
        // Transporte
        "uber" to "Transporte", "didi" to "Transporte", "cabify" to "Transporte",
        "indriver" to "Transporte", "taxi" to "Transporte", "transmilenio" to "Transporte",
        "terpel" to "Transporte", "gasolina" to "Transporte", "primax" to "Transporte",
        // Servicios
        "claro" to "Servicios", "movistar" to "Servicios", "tigo" to "Servicios",
        "wom" to "Servicios", "etb" to "Servicios", "epm" to "Servicios",
        "enel" to "Servicios", "codensa" to "Servicios", "vanti" to "Servicios",
        "acueducto" to "Servicios", "internet" to "Servicios", "gas natural" to "Servicios",
        // Entretenimiento
        "netflix" to "Entretenimiento", "spotify" to "Entretenimiento",
        "disney" to "Entretenimiento", "hbo" to "Entretenimiento", "max" to "Entretenimiento",
        "cine" to "Entretenimiento", "steam" to "Entretenimiento", "playstation" to "Entretenimiento",
        // Salud
        "drogueria" to "Salud", "droguería" to "Salud", "farmacia" to "Salud",
        "cruz verde" to "Salud", "rebaja" to "Salud", "eps" to "Salud",
        "clinica" to "Salud", "colsanitas" to "Salud",
        // Hogar
        "homecenter" to "Hogar", "ikea" to "Hogar", "falabella" to "Hogar",
        "arriendo" to "Hogar", "easy" to "Hogar"
    )

    /** Devuelve el NOMBRE de la categoría sugerida (debe existir en la tabla categorias). */
    fun sugerir(comercio: String): String {
        val texto = comercio.lowercase()
        return diccionario.entries.firstOrNull { texto.contains(it.key) }?.value ?: "Otros"
    }
}
