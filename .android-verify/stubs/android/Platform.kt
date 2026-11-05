@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content

open class Context {
    val applicationContext: Context get() = this
    val filesDir: java.io.File get() = java.io.File(".")
    val assets: AssetManager get() = AssetManager()
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences()
    fun checkSelfPermission(permission: String): Int = 0
}

class SharedPreferences {
    fun getString(key: String, def: String?): String? = null
    fun getInt(key: String, def: Int): Int = def
    fun getLong(key: String, def: Long): Long = def
    fun getFloat(key: String, def: Float): Float = def
    fun getBoolean(key: String, def: Boolean): Boolean = def
    fun contains(key: String): Boolean = false
    fun edit(): Editor = Editor()
    class Editor {
        fun putString(key: String, value: String?): Editor = this
        fun putInt(key: String, value: Int): Editor = this
        fun putFloat(key: String, value: Float): Editor = this
        fun putBoolean(key: String, value: Boolean): Editor = this
        fun remove(key: String): Editor = this
        fun apply() = Unit
    }
    companion object { const val MODE_PRIVATE = 0 }
}

class AssetManager {
    fun open(fileName: String): java.io.InputStream = java.io.InputStream.nullInputStream()
}
