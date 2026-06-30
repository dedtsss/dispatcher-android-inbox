package ru.bruce.dispatcherinbox.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ru.bruce.dispatcherinbox.R
import ru.bruce.dispatcherinbox.assistant.AssistantEntryActivity

class QuickCaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(R.string.quick_settings_tile_label)
            contentDescription = getString(R.string.quick_settings_tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launchCapture = Runnable(::launchCapture)
        if (isLocked && isSecure) {
            unlockAndRun(launchCapture)
        } else {
            launchCapture.run()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchCapture() {
        val intent = Intent(this, AssistantEntryActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                QUICK_CAPTURE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val QUICK_CAPTURE_REQUEST_CODE = 2001
    }
}
