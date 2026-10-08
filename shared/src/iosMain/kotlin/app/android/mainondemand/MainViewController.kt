package app.android.mainondemand

import androidx.compose.ui.window.ComposeUIViewController
import app.android.mainondemand.data.persistence.UserDefaultsKeyValueStore
import app.android.mainondemand.di.AppContainer

private val container by lazy { AppContainer(UserDefaultsKeyValueStore()) }

fun MainViewController() = ComposeUIViewController { App(container) }
