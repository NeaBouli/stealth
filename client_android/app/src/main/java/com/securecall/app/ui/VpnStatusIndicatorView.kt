package com.securecall.app.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.securecall.app.R

/**
 * Compact LED that is visible only while the device's default network routes
 * through an Android system VPN.
 *
 * Purely informational: the LED does not claim anonymity or provider trust
 * (see its flavor-specific content description).
 *
 * While system animations are enabled the LED pulses subtly; when animations
 * are disabled it stays a static, fully visible green dot. It reserves its
 * space while hidden (INVISIBLE), so its appearance causes no layout shift.
 */
class VpnStatusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pulseAnimator: ObjectAnimator? = null
    private var vpnActive = false

    init {
        visibility = INVISIBLE
        alpha = 1f
        contentDescription = context.getString(R.string.cd_external_vpn_active)
    }

    /** Show or hide the LED. Safe to call repeatedly with the same value. */
    fun setVpnActive(active: Boolean) {
        val targetVisibility = if (active) VISIBLE else INVISIBLE
        if (active == vpnActive && visibility == targetVisibility) return
        vpnActive = active
        if (active) {
            visibility = targetVisibility
            startPulseIfAllowed()
        } else {
            stopPulse()
            visibility = targetVisibility
        }
    }

    private fun startPulseIfAllowed() {
        stopPulse()
        if (!shouldPulse(animatorDurationScale())) {
            // Animations disabled (accessibility / battery): static green LED.
            alpha = 1f
            return
        }
        pulseAnimator = ObjectAnimator.ofFloat(this, ALPHA, PULSE_MAX_ALPHA, PULSE_MIN_ALPHA).apply {
            duration = PULSE_HALF_PERIOD_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        alpha = 1f
    }

    private fun animatorDurationScale(): Float = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    } catch (e: Exception) {
        1f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (vpnActive && visibility == VISIBLE) startPulseIfAllowed()
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    internal companion object {
        private const val PULSE_HALF_PERIOD_MS = 900L
        private const val PULSE_MIN_ALPHA = 0.35f
        private const val PULSE_MAX_ALPHA = 1f

        /** Pulse only while system animations are enabled. Pure and unit-testable. */
        internal fun shouldPulse(animatorDurationScale: Float): Boolean =
            animatorDurationScale > 0f
    }
}
