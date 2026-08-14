package com.jaminsmoke.personalbar.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos del nodo (fuente de verdad durable). v1 = primera versión con
 * persistencia; schema exportado a `app/schemas/` para versionar migraciones
 * futuras igual que Commander.
 */
@Database(
    entities = [
        Establecimiento::class,
        Sala::class,
        Mesa::class,
        Producto::class,
        Ticket::class,
        Ronda::class,
        Reserva::class,
        Invitacion::class,
        Camarero::class,
        IdentityConfig::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barDao(): BarDao
}
