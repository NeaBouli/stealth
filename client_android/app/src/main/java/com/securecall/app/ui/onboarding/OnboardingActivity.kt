package com.securecall.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.securecall.app.MainActivity
import com.securecall.app.R

class OnboardingActivity : AppCompatActivity() {

    private val pages = listOf(
        OnboardingPage(R.drawable.ic_lock, R.string.onboarding_title_1, R.string.onboarding_desc_1),
        OnboardingPage(R.drawable.ic_shield, R.string.onboarding_title_2, R.string.onboarding_desc_2),
        OnboardingPage(R.drawable.ic_shield, R.string.onboarding_title_3, R.string.onboarding_desc_3),
        OnboardingPage(R.drawable.ic_call, R.string.onboarding_title_4, R.string.onboarding_desc_4)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this)
        setContentView(R.layout.activity_onboarding)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnSkip = findViewById<Button>(R.id.btnSkip)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val indicators = listOf(
            findViewById<ImageView>(R.id.dot0),
            findViewById<ImageView>(R.id.dot1),
            findViewById<ImageView>(R.id.dot2),
            findViewById<ImageView>(R.id.dot3)
        )

        viewPager.adapter = OnboardingAdapter(pages)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(indicators, position)
                btnNext.text = if (position == pages.size - 1) {
                    getString(R.string.onboarding_done)
                } else {
                    getString(R.string.onboarding_next)
                }
            }
        })

        btnSkip.setOnClickListener { finishOnboarding() }

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pages.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateIndicators(indicators: List<ImageView>, selected: Int) {
        indicators.forEachIndexed { i, dot ->
            dot.alpha = if (i == selected) 1.0f else 0.3f
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences("securecall_prefs", MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
