package com.abe.bud_jet

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
        super.onCreate(savedInstanceState)

        registerVibrationManager()   // ← ПЕРЕНЕСТИ СЮДА

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        navView.setupWithNavController(navController)

        setIconScalingAnimation(navView, navController)

        setupAddButton()

        hideUiForHelloFragments(navController)
        seedDefaultCategories()

        if (preferenceManager.getIsFirstInit()) {
            preferenceManager.setIsFirstInit(false)
            navController.navigate(R.id.hello1Fragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // Used by notification reminders to check whether the user opened the app today.
        preferenceManager.setLastDashboardVisitTime(System.currentTimeMillis())
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
            FinanceRepositoryProvider.get(applicationContext).apply {
                seedDefaultCategoriesIfEmpty()
                enforceCategoryPolicy()
            }
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