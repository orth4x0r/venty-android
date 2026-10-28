package io.github.orth4x0r.venty.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import io.github.orth4x0r.venty.session.LiveSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Flames of heat haze licking up from the bottom of the screen, drawn by an AGSL shader. [level]
 * is 0 with the heater off (nothing drawn), about 0.4 to 1 while heating up (taller as it gets
 * closer), 0.5 when ready (a calm simmer), 1.2 in boost and 1.5 in superboost. Android 13+;
 * older phones simply don't get it.
 */
@Composable
fun HeatAura(level: Float, color: Color, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) return
    val strength by animateFloatAsState(level, tween(1600), label = "auraLevel")
    val tint by animateColorAsState(color, tween(1200), label = "auraTint")
    if (strength < 0.01f) return
    val shader = remember { AuraShader.create() } ?: return

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
    }
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val brush = ShaderBrush(shader as RuntimeShader)
            onDrawBehind {
                AuraShader.update(shader, size.width, size.height, time, strength, tint)
                drawRect(brush)
            }
        },
    )
}

/** Kept apart so phones before Android 13 never load RuntimeShader. */
private object AuraShader {
    // Five octaves of value noise scrolling upwards, bent sideways by a slow sine so it wobbles
    // like air over a hot plate. The flame front rises with the level; above it the noise fades.
    private const val SRC = """
        uniform float2 res;
        uniform float time;
        uniform float level;
        uniform half4 tint;

        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                       mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
            return v;
        }
        half4 main(float2 coord) {
            float2 uv = coord / res;
            float y = 1.0 - uv.y;
            float speed = 0.35 + 0.45 * level;
            float2 p = float2(uv.x * 3.2 * res.x / res.y * 1.8, y * 3.0 - time * speed);
            p.x += 0.35 * sin(y * 5.0 + time * 1.3) * (0.4 + level);
            float n = fbm(p + fbm(p * 0.5 + time * 0.1));
            float height = 0.18 + 0.32 * min(level, 1.5);
            float flame = smoothstep(0.0, 0.6, n - y / height * 0.55);
            float a = flame * clamp(level, 0.0, 1.0) * 0.42 * (1.0 - smoothstep(0.0, height * 1.4, y));
            half3 hot = mix(tint.rgb, half3(1.0, 0.92, 0.75), half(flame * flame * 0.6));
            return half4(hot * a, a);
        }
    """

    fun create(): Any? = if (Build.VERSION.SDK_INT >= 33) build() else null

    @RequiresApi(33)
    private fun build(): Any? = try {
        RuntimeShader(SRC)
    } catch (e: Exception) {
        null
    }

    fun update(shader: Any, w: Float, h: Float, time: Float, level: Float, tint: Color) {
        if (Build.VERSION.SDK_INT < 33) return
        val s = shader as RuntimeShader
        s.setFloatUniform("res", w, h)
        s.setFloatUniform("time", time)
        s.setFloatUniform("level", level)
        s.setColorUniform("tint", android.graphics.Color.valueOf(tint.red, tint.green, tint.blue, 1f))
    }
}

/** How far through the estimated heat-up a live session is, 0 to 1, or null without an estimate. */
fun LiveSession.heatProgress(nowMs: Long = System.currentTimeMillis()): Float? {
    val eta = etaSeconds ?: return null
    val elapsed = ((nowMs - startMs) / 1000f).coerceAtLeast(0f)
    val total = elapsed + eta
    if (total <= 0f) return null
    return (elapsed / total).coerceIn(0f, 1f)
}

/**
 * The phone's side of the heat-up: a heartbeat buzz that quickens as the Venty gets closer to
 * temperature, then a strong double pulse when it's ready. Only runs while [enabled] and the
 * screen showing it is up, so it stops the moment you leave the app.
 */
@Composable
fun HeatHaptics(heating: Boolean, ready: Boolean, progress: () -> Float?, enabled: Boolean) {
    val context = LocalContext.current
    val vibrator = remember { context.vibrator() }
    val currentProgress by rememberUpdatedState(progress)
    if (vibrator == null || !enabled) return

    LaunchedEffect(heating, ready) {
        if (!heating || ready) return@LaunchedEffect
        while (isActive) {
            val p = currentProgress() ?: 0f
            vibrator.heartbeat(strength = 0.35f + 0.5f * p)
            // From a slow resting pulse to a racing one as the heater closes in.
            delay((1_800 - 1_300 * p).toLong().coerceAtLeast(450))
        }
    }

    // Fires on the change to ready only, not when the app opens on an already-hot device.
    var wasReady by remember { mutableStateOf(ready) }
    LaunchedEffect(ready) {
        if (ready && !wasReady) vibrator.readyPulse()
        wasReady = ready
    }
}

private fun Context.vibrator(): Vibrator? {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }
    return v?.takeIf { it.hasVibrator() }
}

/** Lub-dub. Uses haptic primitives where the motor supports them, else two short buzzes. */
private fun Vibrator.heartbeat(strength: Float) {
    val s = strength.coerceIn(0.1f, 1f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK)
    ) {
        vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, s * 0.7f, 110)
                .compose(),
        )
    } else {
        val amp = (s * 255).toInt().coerceIn(1, 255)
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 110, 12), intArrayOf(0, amp, 0, amp * 2 / 3), -1))
    }
}

private fun Vibrator.readyPulse() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_THUD)
    ) {
        vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 60)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 140)
                .compose(),
        )
    } else {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 160), -1))
    }
}
