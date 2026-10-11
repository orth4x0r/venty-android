package io.github.orth4x0r.venty

import android.app.Application
import io.github.orth4x0r.venty.ble.VentyController

class VentyApp : Application() {
    val controller: VentyController by lazy { VentyController(this) }
}
