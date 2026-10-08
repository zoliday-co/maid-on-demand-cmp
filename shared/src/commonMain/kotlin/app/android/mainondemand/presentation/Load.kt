package app.android.mainondemand.presentation

/** Loading / error / content for one piece of screen state. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

val <T> Load<T>.valueOrNull: T? get() = (this as? Load.Ready<T>)?.value
