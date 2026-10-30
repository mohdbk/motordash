package app.motordash.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale

// UI text in Kotlin rather than res/values: hot-loaded code can't add Android resources, and
// this way a language or wording change reloads like any other edit.

internal enum class Language { Auto, English, Arabic }

internal val LocalStrings = staticCompositionLocalOf { English }

/**
 * Wraps Latin text (paths, sizes, versions) in a Unicode left-to-right isolate, so inside Arabic
 * it keeps its own order: "36 GB" instead of "GB 36".
 */
private fun ltr(text: String) = "\u2066$text\u2069"

/** Latin text (sizes, file names) shown inside this language's text, kept in its own order. */
internal fun Strings.isolate(text: String) = if (isArabic) ltr(text) else text

/** Arabic letters join, so tracking (letter spacing) must be 0 or words break apart. */
internal fun Strings.tracking(latin: TextUnit): TextUnit = if (isArabic) 0.sp else latin

internal class Strings(
    val isArabic: Boolean,
    /** For dates: month/day names in this language, Western digits in both. */
    val locale: Locale,
    /** Status bar date, e.g. "Fri, 30 Oct". */
    val datePattern: String,
    // Navigation
    val files: String,
    val settings: String,
    val technicalDetails: String,
    val back: String,
    // Receive card
    val receiveFiles: String,
    val serverNotRunning: String,
    val starting: String,
    val noNetwork: String,
    val connectHint: String,
    val scanToConnect: String,
    val ready: String,
    val devicesConnected: (Int) -> String,
    val cantScan: String,
    // PIN dialog
    val connectWithoutQr: String,
    val connectWithoutQrHint: String,
    val address: String,
    val pin: String,
    val done: String,
    // Files card
    val allFiles: String,
    val sortNewest: String,
    val sortName: String,
    val sortSize: String,
    val itemsFree: (count: Int, free: String) -> String,
    val noFilesYet: String,
    /** Empty state: how to get files here, in order. */
    val emptySteps: List<String>,
    val folderEmpty: String,
    val folder: String,
    val delete: String,
    val fileBadge: String,
    val sendToDevice: String,
    val sendToDeviceHint: String,
    // Messages
    val fromDevice: (time: String) -> String,
    val dismiss: String,
    val moreMessages: (Int) -> String,
    val openLink: String,
    val navigate: String,
    val copy: String,
    // Transfers
    val waitingForDevice: String,
    val received: String,
    val failed: String,
    // Settings
    val version: (String) -> String,
    val general: String,
    val language: String,
    val languageAuto: String,
    val fileSharing: String,
    val addressInQr: String,
    val noNetworkShort: String,
    val savedTo: String,
    /** Where files go, with the free space. */
    val savedIn: (path: String, free: String) -> String,
    val change: String,
    val saveFolderTitle: String,
    val location: String,
    val folderName: String,
    val moveItems: (Int) -> String,
    val save: String,
    val cancel: String,
    val messagesFromDevices: String,
    val messageCount: (Int) -> String,
    val clear: String,
    val about: String,
    val beta: String,
    val feedback: String,
    val open: String,
    val technicalSummary: (developer: Boolean) -> String,
    // Updates
    val updates: String,
    val checking: String,
    val upToDate: (time: String) -> String,
    val versionAvailable: (String) -> String,
    val downloading: (version: String, percent: Int) -> String,
    val readyToInstall: (String) -> String,
    val somethingWrong: String,
    val update: String,
    val install: String,
    val tryAgain: String,
    val checkNow: String,
    val whatsNew: String,
    // Technical details
    val thisApp: String,
    val versionLabel: String,
    val appVersion: (name: String, build: Long) -> String,
    val thisDisplay: String,
    val androidVersion: String,
    val device: String,
    val screen: String,
    val fileServer: String,
    val status: String,
    val notRunning: String,
    val runningOnPort: (Int) -> String,
    val runningNoNetwork: (Int) -> String,
    val addresses: String,
    val developer: String,
    val hotReload: String,
    val hotReloadApi: String,
    val changeServer: String,
)

internal val English = Strings(
    isArabic = false,
    locale = Locale.ENGLISH,
    datePattern = "EEE, d MMM",
    files = "Files",
    settings = "Settings",
    technicalDetails = "Technical Details",
    back = "Back",
    receiveFiles = "Receive Files",
    serverNotRunning = "File server isn't running",
    starting = "Starting…",
    noNetwork = "No network",
    connectHint = "Connect this screen to Wi-Fi or a hotspot",
    scanToConnect = "Scan to connect",
    ready = "Ready",
    devicesConnected = { n -> if (n == 1) "Device connected" else "$n devices connected" },
    cantScan = "Can't scan?",
    connectWithoutQr = "Connect without the QR code",
    connectWithoutQrHint = "Join the same Wi-Fi or hotspot as this screen, open this address in a browser and enter the PIN.",
    address = "Address",
    pin = "PIN",
    done = "Done",
    allFiles = "All files",
    sortNewest = "Newest",
    sortName = "Name",
    sortSize = "Size",
    itemsFree = { n, free -> "$n items · $free free" },
    noFilesYet = "No files yet",
    emptySteps = listOf("Scan the code with your device", "Pick APKs, media or any file", "They appear here"),
    folderEmpty = "This folder is empty",
    folder = "Folder",
    delete = "Delete",
    fileBadge = "FILE",
    sendToDevice = "Send to device",
    sendToDeviceHint = "Scan with a camera to download it. The device must be on the same Wi-Fi or hotspot.",
    fromDevice = { "From Device · $it" },
    dismiss = "Dismiss",
    moreMessages = { "+$it more" },
    openLink = "Open link",
    navigate = "Navigate",
    copy = "Copy",
    waitingForDevice = "waiting for device…",
    received = "Received",
    failed = "Failed",
    version = { "Version $it" },
    general = "General",
    language = "Language",
    languageAuto = "Automatic",
    fileSharing = "File Sharing",
    addressInQr = "Address in QR code",
    noNetworkShort = "No network · connect to Wi-Fi or a hotspot",
    savedTo = "Saved to",
    savedIn = { path, free -> "$path\n$free free" },
    change = "Change",
    saveFolderTitle = "Where to save files",
    location = "Location",
    folderName = "Folder name (optional)",
    moveItems = { n -> if (n == 1) "Move the 1 existing item there" else "Move the $n existing items there" },
    save = "Save",
    cancel = "Cancel",
    messagesFromDevices = "Messages from devices",
    messageCount = { n -> if (n == 0) "None" else if (n == 1) "1 message" else "$n messages" },
    clear = "Clear",
    about = "About",
    beta = "Beta",
    feedback = "Feedback",
    open = "Open",
    technicalSummary = { dev -> if (dev) "Display, file server, developer" else "Display, file server" },
    updates = "Updates",
    checking = "Checking…",
    upToDate = { "Up to date · checked $it" },
    versionAvailable = { "Version $it is available" },
    downloading = { v, p -> "Downloading $v… $p%" },
    readyToInstall = { "Version $it is ready to install" },
    somethingWrong = "Something went wrong",
    update = "Update",
    install = "Install",
    tryAgain = "Try again",
    checkNow = "Check now",
    whatsNew = "What's new",
    thisApp = "This App",
    versionLabel = "Version",
    appVersion = { name, build -> "$name (build $build)" },
    thisDisplay = "This Display",
    androidVersion = "Android version",
    device = "Device",
    screen = "Screen",
    fileServer = "File Server",
    status = "Status",
    notRunning = "Not running",
    runningOnPort = { "Running on port $it" },
    runningNoNetwork = { "Running on port $it · no network" },
    addresses = "Addresses",
    developer = "Developer",
    hotReload = "Hot reload",
    hotReloadApi = "Hot reload API",
    changeServer = "Change server",
)

internal val Arabic = Strings(
    isArabic = true,
    locale = Locale.forLanguageTag("ar-u-nu-latn"),
    datePattern = "EEEE، d MMMM",
    files = "الملفات",
    settings = "الإعدادات",
    technicalDetails = "التفاصيل التقنية",
    back = "رجوع",
    receiveFiles = "استقبال الملفات",
    serverNotRunning = "خادم الملفات لا يعمل",
    starting = "جارٍ التشغيل…",
    noNetwork = "لا توجد شبكة",
    connectHint = "اربط هذه الشاشة بشبكة Wi-Fi أو بنقطة اتصال",
    scanToConnect = "امسح للاتصال",
    ready = "جاهز",
    devicesConnected = { n ->
        when {
            n == 1 -> "جهاز متصل"
            n == 2 -> "جهازان متصلان"
            n in 3..10 -> "$n أجهزة متصلة"
            else -> "$n جهازًا متصلًا"
        }
    },
    cantScan = "لا يمكنك المسح؟",
    connectWithoutQr = "الاتصال بدون رمز QR",
    connectWithoutQrHint = "اتصل بشبكة Wi-Fi أو نقطة الاتصال نفسها المتصلة بها هذه الشاشة، ثم افتح هذا العنوان في المتصفح وأدخل الرمز السري.",
    address = "العنوان",
    pin = "الرمز السري",
    done = "تم",
    allFiles = "كل الملفات",
    sortNewest = "الأحدث",
    sortName = "الاسم",
    sortSize = "الحجم",
    itemsFree = { n, free -> "$n عنصر · ${ltr(free)} متاحة" },
    noFilesYet = "لا توجد ملفات بعد",
    emptySteps = listOf("امسح الرمز بجهازك", "اختر تطبيقات APK أو وسائط أو أي ملف", "ستظهر هنا"),
    folderEmpty = "هذا المجلد فارغ",
    folder = "مجلد",
    delete = "حذف",
    fileBadge = "ملف",
    sendToDevice = "إرسال إلى جهاز",
    sendToDeviceHint = "امسح الرمز بالكاميرا لتنزيل الملف. يجب أن يكون الجهاز على شبكة Wi-Fi أو نقطة الاتصال نفسها.",
    fromDevice = { "من الجهاز · $it" },
    dismiss = "إغلاق",
    moreMessages = { "+$it أخرى" },
    openLink = "فتح الرابط",
    navigate = "الملاحة",
    copy = "نسخ",
    waitingForDevice = "بانتظار الجهاز…",
    received = "تم الاستلام",
    failed = "فشل",
    version = { "الإصدار ${ltr(it)}" },
    general = "عام",
    language = "اللغة",
    languageAuto = "تلقائي",
    fileSharing = "مشاركة الملفات",
    addressInQr = "العنوان في رمز QR",
    noNetworkShort = "لا توجد شبكة · اتصل بشبكة Wi-Fi أو نقطة اتصال",
    savedTo = "مكان الحفظ",
    savedIn = { path, free -> "${ltr(path)}\n${ltr(free)} متاحة" },
    change = "تغيير",
    saveFolderTitle = "مكان حفظ الملفات",
    location = "الموقع",
    folderName = "اسم المجلد (اختياري)",
    moveItems = { n -> if (n == 1) "نقل العنصر الموجود إلى هناك" else "نقل العناصر الموجودة ($n) إلى هناك" },
    save = "حفظ",
    cancel = "إلغاء",
    messagesFromDevices = "رسائل من الأجهزة",
    messageCount = { n ->
        when {
            n == 0 -> "لا يوجد"
            n == 1 -> "رسالة واحدة"
            n == 2 -> "رسالتان"
            n in 3..10 -> "$n رسائل"
            else -> "$n رسالة"
        }
    },
    clear = "مسح",
    about = "حول التطبيق",
    beta = "تجريبي",
    feedback = "الملاحظات",
    open = "فتح",
    technicalSummary = { dev -> if (dev) "الشاشة، خادم الملفات، المطوّر" else "الشاشة، خادم الملفات" },
    updates = "التحديثات",
    checking = "جارٍ التحقق…",
    upToDate = { "محدّث · آخر تحقق $it" },
    versionAvailable = { "الإصدار ${ltr(it)} متاح" },
    downloading = { v, p -> "جارٍ تنزيل ${ltr(v)}… $p%" },
    readyToInstall = { "الإصدار ${ltr(it)} جاهز للتثبيت" },
    somethingWrong = "حدث خطأ ما",
    update = "تحديث",
    install = "تثبيت",
    tryAgain = "إعادة المحاولة",
    checkNow = "تحقق الآن",
    whatsNew = "الجديد",
    thisApp = "هذا التطبيق",
    versionLabel = "الإصدار",
    appVersion = { name, build -> "${ltr(name)} (البناء $build)" },
    thisDisplay = "هذه الشاشة",
    androidVersion = "إصدار أندرويد",
    device = "الجهاز",
    screen = "الشاشة",
    fileServer = "خادم الملفات",
    status = "الحالة",
    notRunning = "لا يعمل",
    runningOnPort = { "يعمل على المنفذ $it" },
    runningNoNetwork = { "يعمل على المنفذ $it · لا توجد شبكة" },
    addresses = "العناوين",
    developer = "المطوّر",
    hotReload = "إعادة التحميل الفوري",
    hotReloadApi = "واجهة إعادة التحميل الفوري",
    changeServer = "تغيير الخادم",
)

/** The user's choice, kept in the app's preferences so it survives restarts and hot reloads. */
internal class LanguageSetting(context: Context) {
    private val prefs = context.getSharedPreferences("motordash-ui", Context.MODE_PRIVATE)
    var choice by mutableStateOf(
        runCatching { Language.valueOf(prefs.getString("language", null) ?: "") }.getOrDefault(Language.Auto)
    )
        private set

    fun set(language: Language) {
        choice = language
        prefs.edit().putString("language", language.name).apply()
    }

    /** Auto follows the car's system language. */
    fun strings(): Strings = when (choice) {
        Language.English -> English
        Language.Arabic -> Arabic
        Language.Auto -> if (Locale.getDefault().language == "ar") Arabic else English
    }
}

@Composable
internal fun rememberLanguageSetting(): LanguageSetting {
    val context = LocalContext.current
    return remember { LanguageSetting(context.applicationContext) }
}
