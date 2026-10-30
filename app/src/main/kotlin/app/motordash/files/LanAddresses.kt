package app.motordash.files

import java.net.Inet4Address
import java.net.NetworkInterface

/** Site-local IPv4 addresses other devices can reach, Wi-Fi and hotspot interfaces first. */
internal fun lanAddresses(): List<String> {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
    return interfaces
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        // Mobile data and VPNs aren't reachable from a device on the hotspot.
        .filterNot { it.name.startsWith("rmnet") || it.name.startsWith("ccmni") || it.name.startsWith("tun") }
        .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
        .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress } }
        .mapNotNull { it.hostAddress }
        .distinct()
}
