@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.lifecycle

open class ViewModel {
    val viewModelScope: kotlinx.coroutines.CoroutineScope get() = kotlinx.coroutines.CoroutineScope()
    protected open fun onCleared() {}
}

abstract class AndroidViewModel(app: android.app.Application) : ViewModel()
