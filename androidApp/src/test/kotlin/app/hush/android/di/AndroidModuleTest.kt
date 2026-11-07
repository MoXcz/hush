// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.di

import android.content.Context
import app.hush.di.sharedModules
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

class AndroidModuleTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `shared and Android modules have all their dependencies`() {
        module { includes(sharedModules + androidModule + blockerModule) }.verify(
            // Context comes from androidContext(); BlockListViewModel's list id is a parameter.
            extraTypes = listOf(Context::class, CoroutineDispatcher::class, String::class),
        )
    }
}
