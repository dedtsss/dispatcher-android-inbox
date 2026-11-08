package ru.bruce.dispatcherinbox.drive

internal class DriveFolders(private val account: String, private val drive: DriveRestClient, private val ids: DriveIdStore) {
    suspend fun reserved(key: String): String = ids.get("$account/$key") ?: drive.generateId().also { ids.put("$account/$key", it) }
    suspend fun folder(key: String, name: String, parent: String, discover: Boolean): String {
        val existing = ids.get("$account/$key")
        if (existing != null) {
            val metadata = drive.metadata(existing)
            if (metadata != null) {
                check(metadata["trashed"]?.asBoolean != true) { "Каталог Drive находится в корзине. Восстановите его" }
                return existing
            }
        }
        val found = if (existing == null && discover) drive.findFolder(name, parent) else null
        if (found != null) { ids.put("$account/$key", found); return found }
        val allocated = reserved(key)
        try { drive.createFolder(allocated, name, parent) }
        catch (e: DriveHttpException) { if (e.code != 409 || drive.metadata(allocated) == null) throw e }
        return allocated
    }
    suspend fun inbox(): String = folder("inbox", "Inbox", folder("root", "Catching", "root", true), true)
}
