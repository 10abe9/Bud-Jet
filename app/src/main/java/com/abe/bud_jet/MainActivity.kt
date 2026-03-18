package com.abe.bud_jet

import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
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

        hideUiForHelloFragments(navController)

        if (true){ // TODO: is first init
            navController.navigate(R.id.hello1Fragment)
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
            Toast.makeText(this, "Add transaction", Toast.LENGTH_SHORT).show()
        }
        binding.topBar.profileAvatar.setOnClickListener {
            vibrator.success()
            findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_profile)
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