@file:Suppress("unused", "UNUSED_PARAMETER")

package android.os

object Build {
    @JvmField
    val VERSION = Version()
    class Version { @JvmField val SDK_INT = 0 }
    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val M = 23
        const val S = 31
    }
}
