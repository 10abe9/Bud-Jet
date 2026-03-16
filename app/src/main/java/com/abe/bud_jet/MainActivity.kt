package com.abe.bud_jet

import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.abe.bud_jet.databinding.ActivityMainBinding
import com.abe.bud_jet.utils.VibrationManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerVibrationManager()   // ← ПЕРЕНЕСТИ СЮДА

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        navView.setupWithNavController(navController)

        setIconScalingAnimation(navView, navController)

        setupAddButton()
    }

    private fun setupAddButton(){
        val vibrator = VibrationManager.get()
        binding.fabAdd.setOnClickListener {
            vibrator.success()
        }
    }

    private fun registerVibrationManager(){
        VibrationManager.init(applicationContext)
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