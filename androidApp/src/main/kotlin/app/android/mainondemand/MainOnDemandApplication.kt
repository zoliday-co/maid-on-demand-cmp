package app.android.mainondemand

import android.app.Application
import app.android.mainondemand.data.persistence.SharedPreferencesKeyValueStore
import app.android.mainondemand.di.AppContainer

class MainOnDemandApplication : Application() {
    /** Process-wide so the fake backend's state outlives activity recreation. */
    val container: AppContainer by lazy { AppContainer(SharedPreferencesKeyValueStore(this)) }
}
