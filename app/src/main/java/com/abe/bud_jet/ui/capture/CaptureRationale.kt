package com.abe.bud_jet.ui.capture

import android.content.Context
import com.abe.bud_jet.R
import com.abe.bud_jet.capture.CaptureAccess
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Explains why notification access is needed before sending the user to system settings. */
object CaptureRationale {

    fun show(context: Context, onDeclined: () -> Unit = {}) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.capture_rationale_title)
            .setMessage(R.string.capture_rationale_message)
            .setNegativeButton(R.string.capture_rationale_not_now) { _, _ -> onDeclined() }
            .setPositiveButton(R.string.capture_rationale_open_settings) { _, _ ->
                CaptureAccess.openAccessSettings(context)
            }
            .show()
    }
}
