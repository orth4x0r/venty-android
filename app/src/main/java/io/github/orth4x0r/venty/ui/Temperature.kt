package io.github.orth4x0r.venty.ui

import kotlin.math.roundToInt

/** Converts between the device's tenths of °C and whole display degrees in the device's unit. */
object Temperature {
    fun toDisplay(tenthsC: Int, fahrenheit: Boolean): Int =
        if (fahrenheit) (tenthsC * 9 / 50.0 + 32).roundToInt() else (tenthsC / 10.0).roundToInt()

    fun fromDisplay(degrees: Int, fahrenheit: Boolean): Int =
        if (fahrenheit) ((degrees - 32) * 50 / 9.0).roundToInt() else degrees * 10

    fun unit(fahrenheit: Boolean) = if (fahrenheit) "F" else "C"
}
