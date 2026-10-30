package app.motordash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val host get() = (application as MotorDashApp).host

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { host.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT <= 29 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(permission)
        } else {
            host.start()
        }
        setContent { DashboardHost(host) }
    }
}
