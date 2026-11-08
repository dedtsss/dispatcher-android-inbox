package ru.bruce.dispatcherinbox.drive

import android.accounts.AccountManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class DriveConnectActivity : ComponentActivity() {
    private val model: DriveConnectionViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                DriveConnectionViewModel(extras.createSavedStateHandle(), applicationContext) as T
        }
    }
    // Оба контракта зарегистрированы при создании Activity, до STARTED.
    private val accountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.flow.accountResult(result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME),
            result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE), result.resultCode == RESULT_CANCELED)
    }
    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        model.flow.consentResult(result.resultCode, result.data)
    }
    private fun restartFlow() {
        // Новая ActivityResultRegistry отделяет ответы прежнего системного окна от новой попытки.
        model.flow.cancel()
        startActivity(android.content.Intent(this, DriveConnectActivity::class.java))
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DispatcherInboxTheme {
                val ui by model.flow.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Подключение Google Drive", style = MaterialTheme.typography.headlineSmall)
                        Text(ui.message)
                        Text("1. Выберите аккаунт\n2. Разрешите доступ к файлам Catching\n3. Проверим папку Catching/Inbox")
                        if (ui.phase in setOf(ConnectionPhase.AUTHORIZE, ConnectionPhase.VERIFY)) CircularProgressIndicator()
                        if (ui.phase in setOf(ConnectionPhase.CONSENT, ConnectionPhase.RESUME)) {
                            Button(onClick = model.flow::resume) { Text("Продолжить подключение") }
                        }
                        if (ui.phase == ConnectionPhase.PICK_ACCOUNT && ui.externalLaunched) {
                            Button(onClick = ::restartFlow) { Text("Начать выбор заново") }
                        }
                        if (ui.phase in setOf(ConnectionPhase.ERROR, ConnectionPhase.CANCELED)) {
                            Button(onClick = ::restartFlow) { Text("Выбрать аккаунт и повторить") }
                            Text("Подробности доступны в настройках: «Диагностика подключения»")
                        }
                        TextButton(onClick = { if (ui.phase != ConnectionPhase.CONNECTED) model.flow.cancel(); finish() }) { Text(if (ui.phase == ConnectionPhase.CONNECTED) "Готово" else "Вернуться к записям") }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.flow.state.collect { ui ->
                    if (!ui.externalLaunched) try {
                        when (ui.phase) {
                            ConnectionPhase.PICK_ACCOUNT -> {
                                model.flow.externalStarted()
                                accountPicker.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), "Выберите аккаунт для Google Drive", null, null, null))
                            }
                            ConnectionPhase.CONSENT -> model.flow.pendingConsent?.let {
                                model.flow.externalStarted()
                                consent.launch(IntentSenderRequest.Builder(it).build())
                            }
                            else -> Unit
                        }
                    } catch (e: Exception) { model.flow.launchFailed(e) }
                }
            }
        }
    }
}
