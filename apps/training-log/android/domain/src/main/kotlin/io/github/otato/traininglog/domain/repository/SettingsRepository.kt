package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun get(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
