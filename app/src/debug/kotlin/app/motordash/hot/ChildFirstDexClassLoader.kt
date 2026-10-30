package app.motordash.hot

import dalvik.system.DexClassLoader

/**
 * The APK already contains the bundled dashboard classes, so a normal parent-first loader
 * would never see the new ones. Dashboard classes come from the payload first; everything
 * else (Compose, the api contract) is shared with the app.
 */
class ChildFirstDexClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    parent: ClassLoader,
) : DexClassLoader(dexPath, optimizedDirectory, null, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name.startsWith(HOT_PACKAGE)) {
            findLoadedClass(name)?.let { return it }
            try {
                return findClass(name)
            } catch (_: ClassNotFoundException) {
                // Fall through to the app's copy.
            }
        }
        return super.loadClass(name, resolve)
    }

    private companion object {
        const val HOT_PACKAGE = "app.motordash.dashboard."
    }
}
