package ru.bruce.dispatcherinbox

import android.app.Application
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.quick.QuickCaptureTileService
import ru.bruce.dispatcherinbox.ui.capture.CatChingCaptureOverlay
import ru.bruce.dispatcherinbox.ui.capture.CaptureScreen
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.queue.QueueScreen
import ru.bruce.dispatcherinbox.ui.queue.QueueViewModel
import ru.bruce.dispatcherinbox.ui.settings.SettingsScreen
import ru.bruce.dispatcherinbox.ui.settings.SettingsViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherBackground
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

open class MainActivity : ComponentActivity() {
    protected open val captureSource: String = InboxSource.MANUAL
    private val requestedDestination = mutableStateOf(AppDestination.CAPTURE.route)

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) "CatChing выбран ассистентом"
            else "Роль ассистента не назначена"
        } else {
            "Проверьте выбор ассистента в настройках Android"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DispatcherApplication).container
        container.applicationScope.launch {
            container.syncScheduler.scheduleAutomatic()
        }
        if (AppEntryPolicy.isAssistantAction(intent.action)) {
            openAssistantCapture(intent)
            finish()
            return
        }
        requestedDestination.value = AppEntryPolicy.destination(
            intent.getStringExtra(EXTRA_START_DESTINATION),
        ).route
        setContent {
            DispatcherInboxTheme {
                InboxApp(
                    application = application,
                    container = container,
                    captureSource = captureSource,
                    requestedDestination = requestedDestination.value,
                    onClose = ::finish,
                    onRequestAssistantRole = ::requestAssistantRole,
                    onOpenAssistantSettings = ::openAssistantSettings,
                    onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                    onRequestQuickSettingsTile = ::requestQuickSettingsTile,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (AppEntryPolicy.isAssistantAction(intent.action)) {
            openAssistantCapture(intent)
        } else {
            requestedDestination.value = AppEntryPolicy.destination(
                intent.getStringExtra(EXTRA_START_DESTINATION),
            ).route
        }
    }

    private fun openAssistantCapture(sourceIntent: Intent) {
        startActivity(
            Intent(this, ru.bruce.dispatcherinbox.assistant.AssistantEntryActivity::class.java).apply {
                action = sourceIntent.action ?: Intent.ACTION_ASSIST
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            when {
                !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) -> openAssistantSettings()
                roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT) ->
                    Toast.makeText(this, "CatChing уже выбран ассистентом", Toast.LENGTH_SHORT).show()
                else -> roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            }
        } else {
            openAssistantSettings()
        }
    }

    private fun openAssistantSettings() {
        openFirstAvailable(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openDefaultAppsSettings() {
        openFirstAvailable(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                this,
                "Откройте шторку, нажмите «Изменить» и добавьте плитку «CatChing»",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val statusBarManager = getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
            ComponentName(this, QuickCaptureTileService::class.java),
            getString(R.string.quick_settings_tile_label),
            Icon.createWithResource(this, R.mipmap.ic_launcher),
            ContextCompat.getMainExecutor(this),
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    "Плитка добавлена в шторку"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    "Плитка уже добавлена"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                    "Плитка не добавлена"
                else ->
                    "Не удалось добавить автоматически. Добавьте плитку через редактирование шторки"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun openFirstAvailable(vararg intents: Intent) {
        intents.forEach { intent ->
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next standard Android settings screen.
            }
        }
        Toast.makeText(this, "Системные настройки недоступны", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_START_DESTINATION = "ru.bruce.dispatcherinbox.START_DESTINATION"
    }
}

private data class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun InboxApp(
    application: Application,
    container: AppContainer,
    captureSource: String,
    requestedDestination: String,
    onClose: () -> Unit,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onRequestQuickSettingsTile: () -> Unit,
) {
    val navController = rememberNavController()
    var captureSheetVisible by rememberSaveable { mutableStateOf(true) }
    val destinations = listOf(
        Destination("capture", "Запись", Icons.Default.EditNote),
        Destination("queue", "Очередь", Icons.Default.Inbox),
        Destination("settings", "Настройки", Icons.Default.Settings),
    )
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    fun navigateRoot(route: String) {
        navController.navigate(route) {
            popUpTo(AppDestination.CAPTURE.route) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    LaunchedEffect(requestedDestination) {
        navigateRoot(AppEntryPolicy.destination(requestedDestination).route)
    }

    Scaffold(
        containerColor = DispatcherBackground,
        bottomBar = {
            if (currentRoute != null) {
                NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (destination.route == AppDestination.CAPTURE.route) {
                                    captureSheetVisible = true
                                }
                                navigateRoot(destination.route)
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.CAPTURE.route,
            modifier = Modifier.padding(padding),
        ) {
            composable("capture") {
                val captureViewModel: CaptureViewModel = viewModel(
                    factory = CaptureViewModel.Factory(
                        application,
                        container.inboxRepository,
                        container.settingsRepository,
                        captureSource,
                        container.applicationScope,
                    ),
                )
                if (captureSheetVisible) {
                    CatChingCaptureOverlay(
                        viewModel = captureViewModel,
                        allowAutoStartVoice = false,
                        onDismiss = { captureSheetVisible = false },
                        onSaved = onClose,
                        onOpenSettings = { navigateRoot(AppDestination.SETTINGS.route) },
                        onOpenQueue = { navigateRoot(AppDestination.QUEUE.route) },
                    )
                } else {
                    CaptureScreen(
                        viewModel = captureViewModel,
                        onClose = onClose,
                        isAssistantMode = false,
                        panelAlpha = 1f,
                        allowAutoStartVoice = false,
                        voiceAsDraft = true,
                        onOpenSettings = { navigateRoot(AppDestination.SETTINGS.route) },
                        onOpenQueue = { navigateRoot(AppDestination.QUEUE.route) },
                    )
                }
            }
            composable("queue") {
                val queueViewModel: QueueViewModel = viewModel(
                    factory = QueueViewModel.Factory(container.inboxRepository),
                )
                QueueScreen(queueViewModel)
            }
            composable("settings") {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(container.settingsRepository),
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onRequestAssistantRole = onRequestAssistantRole,
                    onOpenAssistantSettings = onOpenAssistantSettings,
                    onOpenDefaultAppsSettings = onOpenDefaultAppsSettings,
                    onRequestQuickSettingsTile = onRequestQuickSettingsTile,
                )
            }
        }
    }
}
