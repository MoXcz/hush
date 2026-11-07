// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.settings.DataStoreSettingsRepository
import app.hush.settings.SettingsRepository
import app.hush.settings.ui.SettingsViewModel
import app.hush.settings.ui.onboarding.OnboardingViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

// The DataStore<Preferences> instance comes from the platform module (it owns the file path).
val settingsModule =
    module {
        singleOf(::DataStoreSettingsRepository) bind SettingsRepository::class
        viewModelOf(::OnboardingViewModel)
        viewModelOf(::SettingsViewModel)
    }
