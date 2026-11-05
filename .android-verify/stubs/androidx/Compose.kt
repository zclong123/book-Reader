@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.compose.runtime

interface State<out T> { val value: T }

class MutableStateHolder<T>(var value: T) : State<T>

fun <T> mutableStateOf(value: T, policy: Any? = null): MutableStateHolder<T> = MutableStateHolder(value)

annotation class Composable

class Getter<T>(private val state: MutableStateHolder<T>) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
}

class Setter<T>(private val state: MutableStateHolder<T>) {
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        state.value = value
    }
}

class GetSet<T>(private val state: MutableStateHolder<T>) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        state.value = value
    }
}
