package com.usuario.finanzas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.usuario.finanzas.data.local.dao.RegistroDeteccionDao
import com.usuario.finanzas.data.local.entities.RegistroDeteccion
import com.usuario.finanzas.data.local.dao.AprendizajeCategoriaDao
import com.usuario.finanzas.data.local.dao.CategoriaDao
import com.usuario.finanzas.data.local.dao.HistorialPagoDao
import com.usuario.finanzas.data.local.dao.ServicioDao
import com.usuario.finanzas.data.local.dao.TransaccionDao
import com.usuario.finanzas.data.local.dao.TransaccionPendienteDao
import com.usuario.finanzas.data.local.entities.AprendizajeCategoria
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.HistorialPago
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import com.usuario.finanzas.data.local.entities.ConfiguracionFinanciera
import com.usuario.finanzas.data.local.entities.Presupuesto
import com.usuario.finanzas.data.local.entities.Meta
import com.usuario.finanzas.data.local.entities.AporteMeta
import com.usuario.finanzas.data.local.entities.Recurrente
import com.usuario.finanzas.data.local.entities.OcurrenciaRecurrente
import com.usuario.finanzas.data.local.dao.FinanzasDao

@Database(
    entities = [
        Categoria::class, Transaccion::class, Servicio::class,
        HistorialPago::class, AprendizajeCategoria::class,
        TransaccionPendiente::class, RegistroDeteccion::class,
        ConfiguracionFinanciera::class, Presupuesto::class, Meta::class,
        AporteMeta::class, Recurrente::class, OcurrenciaRecurrente::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FinanzasDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun transaccionDao(): TransaccionDao
    abstract fun servicioDao(): ServicioDao
    abstract fun historialPagoDao(): HistorialPagoDao
    abstract fun aprendizajeCategoriaDao(): AprendizajeCategoriaDao
    abstract fun transaccionPendienteDao(): TransaccionPendienteDao
    abstract fun registroDeteccionDao(): RegistroDeteccionDao
    abstract fun finanzasDao(): FinanzasDao

    companion object {
        const val NOMBRE_BD = "finanzas.db"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categorias ADD COLUMN direccion TEXT NOT NULL DEFAULT 'ambas'")
                db.execSQL("ALTER TABLE categorias ADD COLUMN archivada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transacciones ADD COLUMN montoCentavos INTEGER")
                db.execSQL("ALTER TABLE transacciones ADD COLUMN etiquetas TEXT NOT NULL DEFAULT ''")
                // Los importes históricos se adaptan con BigDecimal al leerlos; no se redondean en SQL.
                db.execSQL("CREATE TABLE IF NOT EXISTS configuracion_financiera (id INTEGER NOT NULL PRIMARY KEY, moneda TEXT NOT NULL, saldoInicialCentavos INTEGER NOT NULL, fechaInicial INTEGER NOT NULL, onboardingCompleto INTEGER NOT NULL)")
                db.execSQL("INSERT INTO configuracion_financiera VALUES (1, 'COP', 0, 0, 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS presupuestos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre TEXT NOT NULL, limiteCentavos INTEGER NOT NULL, categoriaId INTEGER, ciclo TEXT NOT NULL, activo INTEGER NOT NULL, FOREIGN KEY(categoriaId) REFERENCES categorias(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_presupuestos_categoriaId ON presupuestos(categoriaId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS metas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre TEXT NOT NULL, objetivoCentavos INTEGER NOT NULL, archivada INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS aportes_meta (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metaId INTEGER NOT NULL, montoCentavos INTEGER NOT NULL, fecha INTEGER NOT NULL, nota TEXT NOT NULL, FOREIGN KEY(metaId) REFERENCES metas(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_aportes_meta_metaId ON aportes_meta(metaId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurrentes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre TEXT NOT NULL, montoCentavos INTEGER NOT NULL, categoriaId INTEGER NOT NULL, direccion TEXT NOT NULL, frecuencia TEXT NOT NULL, fechaInicio INTEGER NOT NULL, activo INTEGER NOT NULL, FOREIGN KEY(categoriaId) REFERENCES categorias(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrentes_categoriaId ON recurrentes(categoriaId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ocurrencias_recurrentes (recurrenteId INTEGER NOT NULL, fechaProgramada INTEGER NOT NULL, transaccionId INTEGER, registradaEn INTEGER NOT NULL, PRIMARY KEY(recurrenteId, fechaProgramada), FOREIGN KEY(recurrenteId) REFERENCES recurrentes(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(transaccionId) REFERENCES transacciones(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ocurrencias_recurrentes_transaccionId ON ocurrencias_recurrentes(transaccionId)")
                precargarIngresos(db)
            }
        }

        private fun precargarIngresos(db: SupportSQLiteDatabase) {
            listOf("Nómina", "Trabajo independiente", "Regalos").forEach { nombre ->
                db.execSQL("INSERT INTO categorias(nombre, color, icono, direccion, archivada) SELECT ?, '#26A69A', 'Category', 'entrada', 0 WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = ? COLLATE NOCASE)", arrayOf(nombre, nombre))
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transacciones ADD COLUMN direccion TEXT NOT NULL DEFAULT 'salida'")
                db.execSQL("ALTER TABLE transacciones ADD COLUMN tipo TEXT NOT NULL DEFAULT 'compra'")
                db.execSQL("ALTER TABLE transacciones ADD COLUMN banco TEXT NOT NULL DEFAULT ''")
                // No inferir dirección de descripciones libres: el histórico se registró como salida.
                db.execSQL("ALTER TABLE transacciones_pendientes ADD COLUMN direccion TEXT NOT NULL DEFAULT 'salida'")
                db.execSQL("UPDATE transacciones_pendientes SET direccion = 'entrada' WHERE tipo = 'ingreso'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transacciones_fecha ON transacciones(fecha)")
                db.execSQL("CREATE TABLE IF NOT EXISTS registro_detecciones (clave TEXT NOT NULL PRIMARY KEY, coincidencia TEXT NOT NULL, canal TEXT NOT NULL, fecha INTEGER NOT NULL, emparejada INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_registro_detecciones_fecha ON registro_detecciones(fecha)")
            }
        }

        /**
         * v1 -> v2: agrega la tabla de aprendizaje de categorías por comercio.
         * Migración manual (NO destructiva) para conservar los datos del usuario.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `aprendizaje_categorias` (
                        `nombreComercio` TEXT NOT NULL,
                        `categoriaId` INTEGER NOT NULL,
                        `vecesUsada` INTEGER NOT NULL,
                        `ultimaActualizacion` INTEGER NOT NULL,
                        PRIMARY KEY(`nombreComercio`),
                        FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_aprendizaje_categorias_categoriaId` " +
                        "ON `aprendizaje_categorias` (`categoriaId`)"
                )
            }
        }

        /**
         * v2 -> v3: agrega la tabla de transacciones pendientes de confirmar.
         * Permite que las detecciones automáticas persistan aunque el usuario
         * borre la notificación o cierre la app antes de revisarlas.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transacciones_pendientes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `monto` REAL NOT NULL,
                        `comercio` TEXT NOT NULL,
                        `fecha` INTEGER NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `banco` TEXT NOT NULL,
                        `textoOriginal` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Precarga las categorías por defecto al crear la base de datos. */
        val CALLBACK_PRECARGA = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                    INSERT INTO categorias (nombre, color, icono, direccion) VALUES
                    ('Otros',           '#9E9E9E', 'Category', 'ambas'),
                    ('Servicios',       '#29B6F6', 'ReceiptLong', 'salida'),
                    ('Comida',          '#FF7043', 'Restaurant', 'salida'),
                    ('Transporte',      '#FFA726', 'DirectionsCar', 'salida'),
                    ('Entretenimiento', '#AB47BC', 'Movie', 'salida'),
                    ('Salud',           '#EF5350', 'LocalHospital', 'salida'),
                    ('Hogar',           '#66BB6A', 'Home', 'salida')
                    """.trimIndent()
                )
                precargarIngresos(db)
                db.execSQL("INSERT INTO configuracion_financiera VALUES (1, 'COP', 0, 0, 0)")
            }
        }
    }
}
