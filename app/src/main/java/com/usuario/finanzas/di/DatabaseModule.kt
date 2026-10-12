package com.usuario.finanzas.di

import android.content.Context
import androidx.room.Room
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.dao.AprendizajeCategoriaDao
import com.usuario.finanzas.data.local.dao.CategoriaDao
import com.usuario.finanzas.data.local.dao.HistorialPagoDao
import com.usuario.finanzas.data.local.dao.ServicioDao
import com.usuario.finanzas.data.local.dao.TransaccionDao
import com.usuario.finanzas.data.local.dao.TransaccionPendienteDao
import com.usuario.finanzas.data.local.dao.FinanzasDao
import com.usuario.finanzas.data.local.security.AlmacenSeguro
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        almacenSeguro: AlmacenSeguro
    ): FinanzasDatabase {
        // Carga las librerías nativas de SQLCipher
        System.loadLibrary("sqlcipher")
        val passphrase = almacenSeguro.obtenerPassphraseBd().toByteArray(Charsets.UTF_8)
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, FinanzasDatabase::class.java, FinanzasDatabase.NOMBRE_BD)
            .openHelperFactory(factory) // toda la BD queda cifrada con SQLCipher
            .addCallback(FinanzasDatabase.CALLBACK_PRECARGA)
            .addMigrations(FinanzasDatabase.MIGRATION_1_2, FinanzasDatabase.MIGRATION_2_3, FinanzasDatabase.MIGRATION_3_4, FinanzasDatabase.MIGRATION_4_5)
            .build()
    }

    @Provides
    fun provideCategoriaDao(db: FinanzasDatabase): CategoriaDao = db.categoriaDao()

    @Provides
    fun provideFinanzasDao(db: FinanzasDatabase): FinanzasDao = db.finanzasDao()

    @Provides
    fun provideTransaccionDao(db: FinanzasDatabase): TransaccionDao = db.transaccionDao()

    @Provides
    fun provideServicioDao(db: FinanzasDatabase): ServicioDao = db.servicioDao()

    @Provides
    fun provideHistorialPagoDao(db: FinanzasDatabase): HistorialPagoDao = db.historialPagoDao()

    @Provides
    fun provideAprendizajeCategoriaDao(db: FinanzasDatabase): AprendizajeCategoriaDao =
        db.aprendizajeCategoriaDao()

    @Provides
    fun provideTransaccionPendienteDao(db: FinanzasDatabase): TransaccionPendienteDao =
        db.transaccionPendienteDao()
}
