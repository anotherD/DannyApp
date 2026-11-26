package com.danny.dannyapp.ui.navigation

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display

object ExternalDisplayHelper {

    private const val EXTRA_TARGET_DISPLAY_ID = "extra_target_display_id"

    fun findExternalDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val presentationDisplay = presentationDisplays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF
        }
        if (presentationDisplay != null) {
            return presentationDisplay
        }

        val allDisplays = displayManager.displays
        return allDisplays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF
        }
    }

    fun isOnExternalDisplay(activity: Activity): Boolean {
        return activity.display?.displayId != null && activity.display?.displayId != Display.DEFAULT_DISPLAY
    }

    fun moveActivityToDisplay(activity: Activity, targetDisplayId: Int) {
        val currentDisplayId = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        if (currentDisplayId == targetDisplayId) {
            return
        }

        val intent = Intent(activity.intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_TARGET_DISPLAY_ID, targetDisplayId)
        }

        val options = ActivityOptions.makeBasic().apply {
            setLaunchDisplayId(targetDisplayId)
        }

        activity.startActivity(intent, options.toBundle())
        activity.finish()
    }
}
