package com.abe.bud_jet.ui.capture

import androidx.fragment.app.Fragment
import com.abe.bud_jet.R
import com.abe.bud_jet.capture.CaptureAccess
import com.abe.bud_jet.ui.common.PromptBottomSheet

/** Explains why notification access is needed before sending the user to system settings. */
object CaptureRationale {

    private const val REQUEST_KEY = "prompt_capture_access"

    fun show(fragment: Fragment) {
        PromptBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            requestKey = REQUEST_KEY,
            icon = R.drawable.ic_notifications_black_24dp,
            title = R.string.capture_rationale_title,
            message = R.string.capture_rationale_message,
            note = R.string.capture_rationale_note,
            positive = R.string.capture_rationale_open_settings,
            negative = R.string.capture_rationale_not_now
        )
    }

    /** Call from onViewCreated of the fragment that calls [show]. */
    fun listen(fragment: Fragment, onDeclined: () -> Unit = {}) {
        PromptBottomSheet.listen(fragment, REQUEST_KEY) { accepted ->
            if (accepted) CaptureAccess.openAccessSettings(fragment.requireContext()) else onDeclined()
        }
    }
}
