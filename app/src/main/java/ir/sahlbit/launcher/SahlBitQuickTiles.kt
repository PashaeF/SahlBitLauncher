package ir.sahlbit.launcher

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings shortcuts are available on Android 7.0 (API 24) and newer.
 * Each tile starts a fresh launcher task so the user always lands on the home screen.
 */
abstract class SahlBitQuickTileService : TileService() {
    protected abstract val tileTitle: String
    protected open val tileIcon: Int = R.drawable.sahlbit_logo_transparent

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = tileTitle
            tile.icon = android.graphics.drawable.Icon.createWithResource(this, tileIcon)
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

class SahlBitTile1 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۱"
}

class SahlBitTile2 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۲"
}

class SahlBitTile3 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۳"
}

class SahlBitTile4 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۴"
}

class SahlBitTile5 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۵"
}

class SahlBitTile6 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۶"
}

class SahlBitTile7 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۷"
}

class SahlBitTile8 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۸"
}

class SahlBitTile9 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۹"
}

class SahlBitTile10 : SahlBitQuickTileService() {
    override val tileTitle = "سهل‌بیت ۱۰"
}
