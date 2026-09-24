package com.abe.bud_jet.ui.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import com.abe.bud_jet.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class BaseBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // The transparent navigation bar comes from the sheet theme
            // (ThemeOverlay.BudJet.BottomSheet): Window.setNavigationBarColor is deprecated
            // and ignored on Android 15.
        }

        val bottomSheet =
            dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
        configureBottomSheet(bottomSheet)

        (view?.parent as? ViewGroup)?.setBackgroundColor(Color.TRANSPARENT)
    }

    protected open fun configureBottomSheet(bottomSheet: FrameLayout?) {
        // Default floating-card spacing: side margins, no bottom gap.
        val margin = (16 * resources.displayMetrics.density).toInt()
        (bottomSheet?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.leftMargin = margin
            lp.rightMargin = margin
            lp.bottomMargin = 0
            bottomSheet.layoutParams = lp
            bottomSheet.requestLayout()
        }
    }
}
