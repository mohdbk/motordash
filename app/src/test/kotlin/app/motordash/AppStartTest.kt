package app.motordash

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The car has no ADB, so a crash on launch is invisible there: catch it here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h720dp-land-hdpi")
class AppStartTest {
    @Test
    fun `app starts and renders the dashboard`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        repeat(20) { ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
        controller.pause().stop().destroy()
    }
}
