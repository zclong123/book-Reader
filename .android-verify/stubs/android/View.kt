@file:Suppress("unused", "UNUSED_PARAMETER")

package android.view

class View {
    var systemUiVisibility: Int = 0
    val isInEditMode: Boolean = false
    companion object { const val SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 8192 }
}
