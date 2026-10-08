package app.android.mainondemand.data.persistence

import platform.Foundation.NSUserDefaults

class UserDefaultsKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)

    override fun remove(key: String) = defaults.removeObjectForKey(key)
}
