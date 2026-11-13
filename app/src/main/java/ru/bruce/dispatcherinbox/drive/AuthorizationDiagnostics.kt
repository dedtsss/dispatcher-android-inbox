package ru.bruce.dispatcherinbox.drive

object AuthorizationDiagnostics {
    fun message(code: Int?, httpCode: Int? = null, reason: String = ""): String = when {
        code == 16 -> "Подключение отменено. Ваши записи остались на телефоне"
        code == 7 || code == 15 -> "Не удалось связаться с Google. Проверьте интернет и повторите подключение"
        code == 10 -> "Google не разрешил подключить это приложение. Требуется проверить его регистрацию в Google Cloud"
        httpCode == 401 -> "Доступ Google истёк. Выберите аккаунт и подключитесь повторно"
        httpCode == 403 -> "Google Drive не разрешил доступ. Проверьте разрешения приложения в Google Cloud"
        else -> "Не удалось подключить Google Drive. Записи сохранены на телефоне. Повторите попытку; подробности — в диагностике подключения"
    }

    // Диагностика не должна содержать аккаунты, токены, коды доступа и содержимое Intent.
    fun safeMessage(value: String?): String = value.orEmpty()
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+"), "[аккаунт]")
        .replace(Regex("[A-Za-z0-9_./+=-]{32,}"), "[скрыто]")
        .replace(Regex("[\\r\\n]+"), " ").take(240)
}
