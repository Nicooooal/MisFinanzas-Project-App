package com.usuario.finanzas.data.preferences

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Evita que capturas que esperan escritura repueblen la BD después de restaurar/borrar. */
@Singleton
class CoordinadorDatos @Inject constructor() {
    private val mutex = Mutex()
    private val revision = AtomicLong(0)
    @Volatile private var mantenimiento = false
    fun revisionActual(): Long = revision.get()
    suspend fun <T> capturar(revisionEsperada: Long = revision.get(), bloque: suspend () -> T): T? {
        if (mantenimiento) return null
        return mutex.withLock { if (mantenimiento || revisionEsperada != revision.get()) null else bloque() }
    }
    suspend fun <T> mantener(bloque: suspend () -> T): T {
        return mutex.withLock {
            mantenimiento = true
            revision.incrementAndGet()
            try { bloque() } finally { mantenimiento = false; revision.incrementAndGet() }
        }
    }
}
