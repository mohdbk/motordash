package app.motordash

import android.app.Application
import app.motordash.files.FileShareHost

class MotorDashApp : Application() {
    /** Process-wide so the server and transfers survive activity recreation. */
    val host by lazy { FileShareHost(this) }
}
