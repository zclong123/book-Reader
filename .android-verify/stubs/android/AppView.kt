@file:Suppress("unused", "UNUSED_PARAMETER")

package android.app

open class Application : android.content.Context()

class Activity : android.content.Context() {
    val window: Window = Window()
    val decorView: android.view.View = android.view.View()
}

class Window { var statusBarColor: Int = 0 }
