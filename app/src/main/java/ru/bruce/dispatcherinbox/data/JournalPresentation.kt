package ru.bruce.dispatcherinbox.data

enum class JournalKind { TEXT, PHOTO, FILE, VOICE, VIDEO }

data class JournalPresentation(
    val kinds: List<JournalKind>,
    val typeSummary: String,
    val detail: String?,
    val duration: String?,
)

fun InboxItemWithAttachments.toJournalPresentation(): JournalPresentation {
    val hasText = !item.text.isNullOrBlank()
    val images = attachments.count { it.mimeType?.startsWith("image/") == true }
    val voices = attachments.count { it.mimeType?.startsWith("audio/") == true }
    val videos = attachments.count { it.mimeType?.startsWith("video/") == true }
    val files = attachments.size - images - voices - videos
    val kinds = buildList {
        if (hasText) add(JournalKind.TEXT)
        if (voices > 0) add(JournalKind.VOICE)
        if (images > 0) add(JournalKind.PHOTO)
        if (videos > 0) add(JournalKind.VIDEO)
        if (files > 0) add(JournalKind.FILE)
    }
    val parts = buildList {
        if (hasText) add("Текст")
        if (voices > 0) add(if (voices == 1) "Голос" else "$voices голоса")
        if (images > 0) add(if (images == 1) "Фото" else "$images фото")
        if (videos > 0) add(if (videos == 1) "Видео" else "$videos видео")
        if (files > 0) add(if (files == 1) "Файл" else "$files ${fileWord(files)}")
    }
    val detail = when {
        hasText -> item.text!!.trim().replace(Regex("\\s+"), " ").take(72)
        attachments.size == 1 && voices == 0 -> attachments.single().originalName?.take(72)
        else -> null
    }
    return JournalPresentation(
        kinds = kinds.ifEmpty { listOf(JournalKind.FILE) },
        typeSummary = parts.joinToString(" + ").ifBlank { "Файл" },
        detail = detail,
        duration = item.durationMillis?.takeIf { voices > 0 }?.let(::formatDuration),
    )
}

fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun fileWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "файлов"
        mod10 == 1 -> "файл"
        mod10 in 2..4 -> "файла"
        else -> "файлов"
    }
}
