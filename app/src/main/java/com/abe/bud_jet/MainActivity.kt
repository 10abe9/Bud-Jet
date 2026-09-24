package com.abe.bud_jet

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.ActivityMainBinding
import com.abe.bud_jet.ui.operations.AddTransactionBottomSheet
import com.abe.bud_jet.utils.LocaleManager
import com.abe.bud_jet.utils.VibrationManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceManager = PreferenceManager.getInstance(applicationContext)
        LocaleManager.applyAppLanguage(preferenceManager.getAppLanguage())
        // Android 15 (SDK 35) always draws edge-to-edge; opt in on older versions too so
        // the layout is identical everywhere. Insets are applied in applySystemBarInsets().
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        registerVibrationManager()   // ← ПЕРЕНЕСТИ СЮДА

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        navView.setupWithNavController(navController)

        setIconScalingAnimation(navView, navController)

        setupAddButton()

        hideUiForHelloFragments(navController)
        seedDefaultCategories()

        // The flag is cleared when onboarding is finished or skipped, so closing the app
        // mid-onboarding shows it again. savedInstanceState guards against re-navigating
        // after recreation (e.g. language change), when the nav state is restored.
        if (savedInstanceState == null && preferenceManager.getIsFirstInit()) {
            navController.navigate(R.id.hello1Fragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // Used by notification reminders to check whether the user opened the app today.
        preferenceManager.setLastDashboardVisitTime(System.currentTimeMillis())
    }

    /**
     * Keeps content out from under the status bar, navigation bar, display cutout and
     * keyboard by padding the root. Insets are consumed here, so the bottom navigation
     * does not add its own padding on top.
     */
    private fun applySystemBarInsets() {
        val root = binding.root
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + maxOf(bars.bottom, ime.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun hideUiForHelloFragments(navController: NavController){
        navController.addOnDestinationChangedListener { _, destination, _ ->

            if (destination.id == R.id.hello1Fragment || destination.id == R.id.hello2Fragment || destination.id == R.id.navigation_profile) {

                binding.navView.visibility = View.GONE
                binding.topBar.root.visibility = View.GONE
                binding.fabAdd.visibility = View.GONE

            } else {

                binding.navView.visibility = View.VISIBLE
                binding.topBar.root.visibility = View.VISIBLE
                binding.fabAdd.visibility = View.VISIBLE

            }
        }
    }

    private fun setupAddButton(){
        val vibrator = VibrationManager.get()
        binding.fabAdd.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).duration = 80
                }
            vibrator.success()
            AddTransactionBottomSheet.newInstance(isIncomeDefault = false)
                .show(supportFragmentManager, "add_transaction")
        }
        binding.topBar.profileAvatar.setOnClickListener {
            vibrator.success()
            findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_profile)
        }
    }

    private fun registerVibrationManager(){
        VibrationManager.init(applicationContext)
    }

    private fun seedDefaultCategories() {
        lifecycleScope.launch {
            // Runs on every start, including the recreation after a language change,
            // so built-in category names always follow the app language.
            FinanceRepositoryProvider.get(applicationContext).syncDefaultCategories(
                FinanceRepositoryProvider.localizedDefaultCategoryNames(this@MainActivity)
            )
        }
    }

    private fun setIconScalingAnimation(
        navView: BottomNavigationView,
        navController: androidx.navigation.NavController
    ) {

        navController.addOnDestinationChangedListener { _, destination, _ ->

            val menuView = navView.getChildAt(0) as ViewGroup

            for (i in 0 until menuView.childCount) {

                val itemView = menuView.getChildAt(i)

                val itemId = navView.menu.getItem(i).itemId

                if (itemId == destination.id) {

                    itemView.animate()
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setDuration(150)
                        .start()

                } else {

                    itemView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
            }
        }
    }
}