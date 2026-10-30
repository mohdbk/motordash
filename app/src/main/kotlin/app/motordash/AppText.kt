package app.motordash

import android.content.Context
import java.util.Locale

/**
 * The few messages the app shell shows itself (toasts, errors), in the language picked in the
 * dashboard's Settings. Reads the same preference as the dashboard's LanguageSetting.
 */
class AppText(context: Context) {
    private val prefs = context.getSharedPreferences("motordash-ui", Context.MODE_PRIVATE)

    private val arabic: Boolean
        get() = when (prefs.getString("language", null)) {
            "Arabic" -> true
            "English" -> false
            else -> Locale.getDefault().language == "ar"
        }

    private fun t(en: String, ar: String) = if (arabic) ar else en

    fun copied() = t("Copied", "تم النسخ")
    fun noBrowser() = t("No browser on this screen", "لا يوجد متصفح على هذه الشاشة")
    fun noMaps() = t("No maps app on this screen", "لا يوجد تطبيق خرائط على هذه الشاشة")
    fun cantOpen(name: String) = t("No app on this screen can open $name", "لا يوجد تطبيق على هذه الشاشة يمكنه فتح ⁦$name⁩")
    fun serverFailed(reason: String?) = t("Could not start server: $reason", "تعذّر تشغيل الخادم: $reason")
    fun checkFailed(reason: String) = t("Couldn't check for updates: $reason", "تعذّر التحقق من التحديثات: $reason")
    fun updateFailed(reason: String) = t("Update failed: $reason", "فشل التحديث: $reason")
    fun notOurKey() = t("Update isn't signed with the MotorDash release key", "التحديث غير موقّع بمفتاح MotorDash")
    fun allowInstall() = t("Allow MotorDash to install updates, then tap Install again", "اسمح لـ MotorDash بتثبيت التحديثات، ثم اضغط تثبيت مرة أخرى")
    fun badFolderName() = t("Use a folder name without / or \\", "استخدم اسم مجلد بدون / أو \\")
    fun waitForUploads() = t("Wait for uploads to finish, then change the folder", "انتظر حتى تكتمل عمليات الرفع، ثم غيّر المجلد")
    fun cantCreateFolder(path: String) = t("Couldn't create $path", "تعذّر إنشاء \u2066$path\u2069")
    fun someNotMoved() = t("Some files couldn't be moved and stayed in the old folder", "تعذّر نقل بعض الملفات وبقيت في المجلد القديم")
    fun noInstaller() = t("This screen can't open the installer", "لا يمكن لهذه الشاشة فتح أداة التثبيت")
}
