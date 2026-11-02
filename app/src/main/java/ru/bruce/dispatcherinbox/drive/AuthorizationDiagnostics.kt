package ru.bruce.dispatcherinbox.drive

object AuthorizationDiagnostics {
    fun message(code: Int?, httpCode: Int? = null, reason: String = ""): String = when {
        code == 16 -> "Google сообщил об отмене подключения (код 16). Оригиналы на телефоне"
        code == 10 -> "Google отклонил конфигурацию приложения (код 10). Проверьте Android OAuth client: package и SHA-1 этого APK"
        code == 7 || code == 15 -> "Нет связи с Google или истекло время ожидания (код $code). Повторите подключение"
        httpCode == 403 && reason in setOf("accessNotConfigured", "serviceDisabled") -> "Drive API недоступен для проекта OAuth (HTTP 403, $reason). Проверьте включение Google Drive API"
        httpCode == 403 -> "Google Drive отказал в доступе (HTTP 403, ${reason.ifBlank { "причина не передана" }}). Проверьте разрешение drive.file и consent"
        httpCode != null -> "Google Drive вернул HTTP $httpCode. Подробности в диагностике подключения"
        code != null -> "Авторизация Google не завершена (код $code). Подробности в диагностике подключения"
        else -> "Подключение Google Drive не завершено. Причину нельзя определить по resultCode; откройте диагностику"
    }

    // Диагностика не должна содержать аккаунты, токены, коды доступа и содержимое Intent.
    fun safeMessage(value: String?): String = value.orEmpty()
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+"), "[аккаунт]")
        .replace(Regex("[A-Za-z0-9_./+=-]{32,}"), "[скрыто]")
        .replace(Regex("[\\r\\n]+"), " ").take(240)
}
