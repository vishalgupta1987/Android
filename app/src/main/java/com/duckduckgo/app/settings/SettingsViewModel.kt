/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import timber.log.Timber
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val variantManager: VariantManager,
    private val pixel: Pixel
) : ViewModel() {

    data class ViewState(
        val loading: Boolean = true,
        val version: String = "",
        val lightThemeEnabled: Boolean = false,
        val autoCompleteSuggestionsEnabled: Boolean = true,
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false,
        val automaticallyClearData: AutomaticallyClearData = AutomaticallyClearData(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_ONLY)
    )

    data class AutomaticallyClearData(
        val clearWhatOption: ClearWhatOption,
        val clearWhenOption: ClearWhenOption,
        val clearWhenOptionEnabled: Boolean = true
    )


    sealed class Command {
        object LaunchFeedback : Command()
        object UpdateTheme : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().apply {
        value = ViewState()
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        pixel.fire(SETTINGS_OPENED)
    }

    fun start() {

        val defaultBrowserAlready = defaultWebBrowserCapability.isCurrentlyConfiguredAsDefaultBrowser()
        val variant = variantManager.getVariant()
        val isLightTheme = settingsDataStore.theme == DuckDuckGoTheme.LIGHT
        val automaticallyClearWhat = settingsDataStore.automaticallyClearWhatOption
        val automaticallyClearWhen = settingsDataStore.automaticallyClearWhenOption
        val automaticallyClearWhenEnabled = isAutomaticallyClearingDataWhenSettingEnabled(automaticallyClearWhat)

        viewState.value = currentViewState().copy(
            loading = false,
            lightThemeEnabled = isLightTheme,
            autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
            isAppDefaultBrowser = defaultBrowserAlready,
            showDefaultBrowserSetting = defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration(),
            version = obtainVersion(variant.key),
            automaticallyClearData = AutomaticallyClearData(automaticallyClearWhat, automaticallyClearWhen, automaticallyClearWhenEnabled)
        )
    }

    fun userRequestedToSendFeedback() {
        command.value = Command.LaunchFeedback
    }

    fun onLightThemeToggled(enabled: Boolean) {
        Timber.i("User toggled light theme, is now enabled: $enabled")
        settingsDataStore.theme = if (enabled) DuckDuckGoTheme.LIGHT else DuckDuckGoTheme.DARK
        command.value = Command.UpdateTheme

        val pixelName = if (enabled) SETTINGS_THEME_TOGGLED_LIGHT else SETTINGS_THEME_TOGGLED_DARK
        pixel.fire(pixelName)
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        settingsDataStore.autoCompleteSuggestionsEnabled = enabled
    }

    private fun obtainVersion(variantKey: String): String {
        val formattedVariantKey = if (variantKey.isBlank()) " " else " $variantKey "
        return "${BuildConfig.VERSION_NAME}$formattedVariantKey(${BuildConfig.VERSION_CODE})"
    }

    fun onAutomaticallyWhatOptionSelected(clearWhatSetting: ClearWhatOption) {
        settingsDataStore.automaticallyClearWhatOption = clearWhatSetting

        viewState.value = currentViewState().copy(
            automaticallyClearData = AutomaticallyClearData(
                clearWhatOption = clearWhatSetting,
                clearWhenOption = settingsDataStore.automaticallyClearWhenOption,
                clearWhenOptionEnabled = isAutomaticallyClearingDataWhenSettingEnabled(clearWhatSetting)
            )
        )
    }

    private fun isAutomaticallyClearingDataWhenSettingEnabled(clearWhatOption: ClearWhatOption?): Boolean {
        return clearWhatOption != null && clearWhatOption != ClearWhatOption.CLEAR_NONE
    }

    fun onAutomaticallyWhenOptionSelected(clearWhenSetting: ClearWhenOption) {
        settingsDataStore.automaticallyClearWhenOption = clearWhenSetting
        viewState.value = currentViewState().copy(
            automaticallyClearData = AutomaticallyClearData(
                settingsDataStore.automaticallyClearWhatOption,
                clearWhenSetting
            )
        )
    }

    private fun currentViewState(): ViewState {
        return viewState.value!!
    }
}