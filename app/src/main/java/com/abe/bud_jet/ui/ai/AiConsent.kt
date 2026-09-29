package com.abe.bud_jet.ui.ai

import android.content.Context
import com.abe.bud_jet.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Explicit consent before any spending data may be sent for AI analysis. */
object AiConsent {

    fun show(context: Context, onResult: (accepted: Boolean) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.ai_assistant_consent_title)
            .setMessage(R.string.ai_assistant_consent_message)
            .setNegativeButton(R.string.common_cancel) { _, _ -> onResult(false) }
            .setPositiveButton(R.string.ai_assistant_consent_accept) { _, _ -> onResult(true) }
            .setOnCancelListener { onResult(false) }
            .show()
    }
}
