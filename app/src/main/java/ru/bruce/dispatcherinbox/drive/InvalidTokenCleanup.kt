package ru.bruce.dispatcherinbox.drive

import kotlinx.coroutines.CancellationException

// Только HTTP401 подтверждает непригодность выданного токена. Отказ consent не отзывает доступ.
internal suspend fun <T> withInvalidTokenCleanup(token: String, clear: suspend (String) -> Unit, action: suspend () -> T): T {
    try { return action() }
    catch (e: DriveHttpException) {
        if (e.code == 401) {
            try { clear(token) }
            catch (canceled: CancellationException) { throw canceled }
            catch (_: Exception) { /* Исходный HTTP401 остаётся причиной ошибки подключения. */ }
        }
        throw e
    }
}
