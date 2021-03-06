package io.github.domi04151309.alwayson.alwayson

import android.annotation.TargetApi
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.domi04151309.alwayson.objects.Global


@TargetApi(Build.VERSION_CODES.N)
class AlwaysOnQS : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile(Global.currentAlwaysOnState(this))
    }

    override fun onClick() {
        Global.changeAlwaysOnState(this)
        updateTile(Global.currentAlwaysOnState(this))
    }

    fun updateTile(isActive: Boolean) {
        qsTile?.let {
            val newState: Int = if (isActive) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            it.state = newState
            it.updateTile()
        }
    }
}