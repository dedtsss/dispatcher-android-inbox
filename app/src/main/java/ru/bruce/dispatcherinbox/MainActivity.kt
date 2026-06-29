package ru.bruce.dispatcherinbox

import android.app.Application
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CaptureScreen
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.queue.QueueScreen
import ru.bruce.dispatcherinbox.ui.queue.QueueViewModel
import ru.bruce.dispatcherinbox.ui.settings.SettingsScreen
import ru.bruce.dispatcherinbox.ui.settings.SettingsViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

open class MainActivity : ComponentActivity() {
    protected open val forceVoiceStart: Boolean = false
    protected open val captureSource: String = InboxSource.MANUAL

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) "Приёмник выбран ассистентом"
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
        setContent {
            DispatcherInboxTheme {
                InboxApp(
                    application = application,
                    container = container,
                    forceVoiceStart = forceVoiceStart,
                    captureSource = captureSource,
                    onClose = ::finish,
                    onRequestAssistantRole = ::requestAssistantRole,
                    onOpenAssistantSettings = ::openAssistantSettings,
                    onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                )
            }
        }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            when {
                !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) -> openAssistantSettings()
                roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT) ->
                    Toast.makeText(this, "Приёмник уже выбран ассистентом", Toast.LENGTH_SHORT).show()
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
    forceVoiceStart: Boolean,
    captureSource: String,
    onClose: () -> Unit,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val destinations = listOf(
        Destination("capture", "Записать", Icons.Default.EditNote),
        Destination("queue", "Очередь", Icons.Default.Inbox),
        Destination("settings", "Настройки", Icons.Default.Settings),
    )
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo("capture") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "capture",
            modifier = Modifier.padding(padding),
        ) {
            composable("capture") {
                val captureViewModel: CaptureViewModel = viewModel(
                    factory = CaptureViewModel.Factory(
                        application,
                        container.inboxRepository,
                        container.settingsRepository,
                        captureSource,
                    ),
                )
                CaptureScreen(captureViewModel, forceVoiceStart, onClose)
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
                )
            }
        }
    }
}
