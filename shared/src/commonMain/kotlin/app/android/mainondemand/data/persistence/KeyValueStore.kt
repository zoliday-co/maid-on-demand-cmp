package app.android.mainondemand.data.persistence

/**
 * The only platform-specific dependency of the data layer. Android backs it with
 * SharedPreferences, iOS with NSUserDefaults; tests use [InMemoryKeyValueStore].
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
