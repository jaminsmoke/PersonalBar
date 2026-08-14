package com.jaminsmoke.personalbar.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * TypeConverters de Room. Las líneas de ronda/ticket se guardan como JSON
 * (Bar no consulta por línea; evitar tablas/FKs extra).
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun lineasToJson(lineas: List<Linea>): String =
        json.encodeToString(ListSerializer(Linea.serializer()), lineas)

    @TypeConverter
    fun jsonToLineas(jsonStr: String): List<Linea> =
        json.decodeFromString(ListSerializer(Linea.serializer()), jsonStr)
}
