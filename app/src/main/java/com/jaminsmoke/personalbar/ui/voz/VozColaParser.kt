package com.jaminsmoke.personalbar.ui.voz

import com.jaminsmoke.personalbar.data.Destino

/** Resultado de parsear una orden hablada sobre una cola. */
sealed class OrdenColaVoz {
    /** Marcar preparado: `<nombre> Cola N Destino preparado`. */
    data class Preparado(val nombre: String?, val numeroCola: Int, val destino: Destino) : OrdenColaVoz()

    /** Marcar recogido: `[nombre] Cola N Destino recogido`. El nombre se ignora al casar. */
    data class Recogido(val numeroCola: Int, val destino: Destino) : OrdenColaVoz()

    /** No se reconoció una orden válida. */
    data object NoEntendido : OrdenColaVoz()
}

/**
 * Parser de la gramática de cola por voz (puro, sin Android, testeable):
 *
 * - Preparado: `<nombre del preparador> Cola N <Bebida|Comida> preparado`
 * - Recogido:   `[nombre] Cola N <Bebida|Comida> recogido`
 *
 * El STT devuelve «cola uno bebida», «cola 1 bebida», «cola 1 de bebida», rara vez
 * `Cola1Bebida` pegado. Acepta espacios, números en letra (1–99), relleno («de», «la»)
 * y sinónimos de acción. El destino hablado es **Bebida/Comida** (nunca «Cocina»).
 */
object VozColaParser {

    /** Palabras que se ignoran entre los elementos significativos. */
    private val relleno = setOf(
        "el", "la", "los", "las", "de", "del", "al", "a", "para", "por", "y", "e",
    )

    private val accionesPreparado = setOf("preparado", "preparada", "prepara", "listo", "lista", "hecho", "hecha")
    private val accionesRecogido = setOf("recogido", "recogida", "recoge", "recoger", "servido", "servida", "entregado", "entregada")

    private val numerosTexto: Map<String, Int> = buildMap {
        put("cero", 0)
        for ((k, v) in listOf(
            "un" to 1, "una" to 1, "uno" to 1, "unos" to 1, "unas" to 1,
            "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
            "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        )) put(k, v)
        for ((k, v) in listOf(
            "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13,
            "catorce" to 14, "quince" to 15,
            "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        )) put(k, v)
        for ((k, v) in listOf(
            "veinte" to 20,
            "veintiuno" to 21, "veintiun" to 21, "veintidos" to 22, "veintitres" to 23,
            "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
            "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        )) put(k, v)
        for ((k, v) in listOf(
            "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
            "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        )) put(k, v)
        put("cien", 100); put("ciento", 100)
    }

    private val decenasCompuestas = setOf(
        "veinte", "treinta", "cuarenta", "cincuenta",
        "sesenta", "setenta", "ochenta", "noventa",
    )

    /** Normaliza el texto del STT: minúsculas, sin tildes, puntuación fuera. */
    fun normalizar(texto: String): String {
        val sinTildes = texto.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u').replace('ñ', 'n')
        return sinTildes
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Parsea una frase y devuelve la orden reconocida o [OrdenColaVoz.NoEntendido]. */
    fun parsear(texto: String): OrdenColaVoz {
        val tokens = normalizar(texto).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return OrdenColaVoz.NoEntendido

        // 1. Acción (preparado|recogido): buscarla explícitamente.
        val accionIdx = tokens.indexOfLast { it in accionesPreparado || it in accionesRecogido }
        if (accionIdx < 0) return OrdenColaVoz.NoEntendido
        val esPreparado = tokens[accionIdx] in accionesPreparado

        // 2. «Cola N»: el número significativo debe venir tras la palabra «cola».
        val colaIdx = tokens.indexOfFirst { it == "cola" }
        if (colaIdx < 0) return OrdenColaVoz.NoEntendido

        val numero = extraerNumero(tokens, colaIdx + 1) ?: return OrdenColaVoz.NoEntendido
        if (numero < 1) return OrdenColaVoz.NoEntendido

        // 3. Destino: Bebida/Comida, en cualquier posición (habitualmente tras el número).
        val destino = tokens
            .firstNotNullOfOrNull { tok -> destinoDesdeToken(tok) }
            ?: return OrdenColaVoz.NoEntendido

        // 4. Nombre opcional (solo en preparado): tokens significativos antes de «cola».
        val nombre = if (esPreparado) {
            val antes = tokens.subList(0, colaIdx)
                .filter { it !in relleno && it != "cola" }
            if (antes.isEmpty()) null else antes.joinToString(" ")
        } else null

        return if (esPreparado) {
            OrdenColaVoz.Preparado(nombre, numero, destino)
        } else {
            OrdenColaVoz.Recogido(numero, destino)
        }
    }

    private fun destinoDesdeToken(tok: String): Destino? = when {
        tok in setOf("bebida", "bebidas", "barra", "canas", "cana") -> Destino.BARRA
        tok in setOf("comida", "comidas") -> Destino.COCINA
        else -> null
    }

    /** Extrae un número (dígito o en letra, simple o compuesto «treinta y cinco») desde [desde]. */
    private fun extraerNumero(tokens: List<String>, desde: Int): Int? {
        if (desde >= tokens.size) return null
        // Saltar relleno inmediato («cola uno de bebida» → el «de» no es número).
        var i = desde
        while (i < tokens.size && tokens[i] in relleno) i++
        if (i >= tokens.size) return null

        // Compuesto «treinta y cinco» (3 tokens).
        if (i + 2 < tokens.size && tokens[i] in decenasCompuestas &&
            (tokens[i + 1] == "y" || tokens[i + 1] == "e")
        ) {
            val decena = numerosTexto[tokens[i]] ?: return null
            val unidad = numerosTexto[tokens[i + 2]] ?: return null
            if (unidad in 1..9) return decena + unidad
        }

        val tok = tokens[i]
        return tok.toIntOrNull() ?: numerosTexto[tok]
    }
}
