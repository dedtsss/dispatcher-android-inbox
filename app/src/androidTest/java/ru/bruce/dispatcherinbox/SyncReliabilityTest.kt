package ru.bruce.dispatcherinbox

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.data.*
import ru.bruce.dispatcherinbox.network.*
import ru.bruce.dispatcherinbox.sync.*
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Отдельный instrumentation-процесс: TestDriver заменяет системный планировщик, не Google Drive. */
class SyncReliabilityTest {
    @Test fun constraintsRecoveryFullGroupRetentionAndCaptureReplay() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as DispatcherApplication
        val settings = app.container.settingsRepository
        val previous = settings.settings.first()
        settings.setAutoSync(false)
        settings.setDriveAccount("reliability@example.test")
        settings.setLocalCopyPolicy("keep")
        val database = Room.inMemoryDatabaseBuilder(context, InboxDatabase::class.java).build()
        val dao = database.inboxDao()
        val calls = AtomicInteger()
        val gateway = object : DispatcherGateway {
            override suspend fun upload(item: InboxItemWithAttachments, settings: AppSettings): UploadResult {
                calls.incrementAndGet()
                return UploadResult.Success("confirmed-${item.item.id}")
            }
        }
        val store = AttachmentStore(context)
        val deps = SyncDependencies(dao, SyncItemProcessor(gateway), settings, store)
        val factory = object : WorkerFactory() {
            override fun createWorker(context: Context, workerClassName: String, params: WorkerParameters): ListenableWorker? =
                if (workerClassName == SyncWorker::class.java.name) SyncWorker(context, params, deps) else null
        }
        val configuration = Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val manager = WorkManager.getInstance(context)
        val scheduler = SyncScheduler(context, settings)
        val repo = InboxRepository(dao, store, InboxItemFactory(), scheduler)
        val now = System.currentTimeMillis()
        fun item(id: String, status: String = InboxStatus.QUEUED) = InboxItemEntity(
            id, InboxType.TEXT, "Тест синхронизации", now, now, status, InboxSource.MANUAL, 0, null, null,
        )
        suspend fun awaitState(id: String, expected: String): InboxItemWithAttachments = withTimeout(15000) {
            while (true) {
                val result = dao.getById(id)
                if (result?.item?.status == expected) return@withTimeout result
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") error("Нет состояния")
        }
        try {
            dao.insertItem(item("automatic-network"))
            settings.setAutoSync(true)
            val infos = manager.getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_WORK_NAME).get(10, TimeUnit.SECONDS)
            val work = infos.first { it.state == WorkInfo.State.ENQUEUED }
            assertEquals("Без network constraint gateway не должен вызываться", 0, calls.get())
            assertEquals(InboxStatus.QUEUED, dao.getById("automatic-network")!!.item.status)
            // Никакого scheduleManual/retry от пользователя: восстанавливается только constraint.
            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(work.id)
            val sent = awaitState("automatic-network", InboxStatus.SYNCED)
            assertEquals(1, calls.get())
            assertNotNull(sent.item.lastAttemptAt)
            assertNotNull(sent.item.syncedAt)
            assertTrue(settings.settings.first().syncLastAttemptAt > 0)
            assertNotNull(manager.getWorkInfosForUniqueWork(SyncScheduler.REPAIR_WORK_NAME).get(10, TimeUnit.SECONDS).firstOrNull())
            settings.setAutoSync(false)
            manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)

            // Сбой после первой части группы: id reservation переживает повтор,
            // локальные файлы не очищаются, полный commit только после второй части.
            val id = "partial-group-${System.nanoTime()}"
            val directory = File(context.filesDir, "inbox-items/$id").apply { mkdirs() }
            val a = File(directory, "a.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val b = File(directory, "b.jpg").apply { writeBytes(byteArrayOf(4, 5)) }
            val group = item(id).copy(type = InboxType.MIXED, text = null, createdAt = now + 1)
            val parts = listOf(a, b).mapIndexed { index, file ->
                InboxAttachmentEntity("part-$index", id, file.path, "application/octet-stream", file.name, file.length(), "checksum-$index")
            }
            dao.insertItemWithAttachments(group, parts)
            var interrupted = true
            val confirmed = mutableSetOf<String>()
            val groupGateway = object : DispatcherGateway {
                override suspend fun upload(item: InboxItemWithAttachments, settings: AppSettings): UploadResult {
                    for (part in item.attachments) {
                        val key = "${settings.driveAccount}/item/${item.item.id}/${part.id}"
                        val reserved = dao.driveId(key) ?: "remote-${part.id}".also { dao.putDriveId(ru.bruce.dispatcherinbox.drive.DriveCheckpoint(key, it)) }
                        confirmed += reserved
                        if (interrupted) return UploadResult.Failure("Разрыв после первой части", true)
                    }
                    return UploadResult.Success("remote-group")
                }
            }
            val drainer = QueueDrainer(dao, SyncItemProcessor(groupGateway))
            val policy = settings.settings.first().copy(localCopyPolicy = "after_upload", cleanupEnabledAt = now)
            assertTrue(drainer.drain(policy))
            assertEquals(InboxStatus.ERROR, dao.getById(id)!!.item.status)
            assertNull(dao.getById(id)!!.item.syncedAt)
            OriginalCleanup(dao, store).run(policy, now + 100)
            assertTrue(a.exists() && b.exists())
            assertEquals(1, confirmed.size)
            interrupted = false
            assertFalse(drainer.drain(policy))
            assertEquals(2, confirmed.size)
            assertEquals(InboxStatus.SYNCED, dao.getById(id)!!.item.status)
            OriginalCleanup(dao, store).run(policy)
            assertFalse(a.exists() || b.exists())
            val cleaned = dao.getById(id)!!
            assertNotNull(cleaned.item.originalsDeletedAt)
            assertEquals(5L, cleaned.attachments.sumOf { it.sizeBytes })
            assertEquals(2, cleaned.attachments.size)
            OriginalCleanup(dao, store).run(policy) // Идемпотентный повтор, метаданные остаются.
            assertEquals(cleaned, dao.getById(id))
            directory.deleteRecursively()

            // Повтор сохранения с тем же durable captureId не создаёт второй event.
            val captureId = "capture-${System.nanoTime()}"
            var committed = 0
            val one = repo.save("Повтор после перезапуска", emptyList(), captureId = captureId, onLocalCommit = { committed++ })
            val replay = repo.save("Повтор после перезапуска", emptyList(), captureId = captureId, onLocalCommit = { committed++ })
            assertEquals(one.id, replay.id)
            assertEquals(2, committed)
            assertEquals(1, dao.observeAll().first().count { it.item.id == captureId })
            dao.deleteById(captureId)
            val cancelledId = "cancel-${System.nanoTime()}"
            dao.insertItem(item(cancelledId))
            val cancelledGateway = object : DispatcherGateway {
                override suspend fun upload(item: InboxItemWithAttachments, settings: AppSettings): UploadResult = throw CancellationException("Тест остановки ОС")
            }
            try { QueueDrainer(dao, SyncItemProcessor(cancelledGateway)).drain(policy); fail("Отмена потеряна") }
            catch (_: CancellationException) { }
            assertEquals(InboxStatus.ERROR, dao.getById(cancelledId)!!.item.status)
            val evidence = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
            File(evidence, "sync-reliability.txt").writeText("SUCCESS: WorkManager constraint→тот же SyncWorker→Room; без ручной отправки. Частичная группа/retry/checkpoint/запрет ранней очистки/идемпотентная очистка/metadata/повтор captureId/отмена проверены. Gateway и network constraint управляемые, не физический Doze/Google OAuth.\n")
        } finally {
            manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
            settings.setAutoSync(false)
            settings.setDriveAccount(previous.driveAccount)
            settings.setLocalCopyPolicy(previous.localCopyPolicy)
            settings.setWifiOnly(previous.wifiOnly)
            database.close()
        }
    }
}
