package com.example.domain.tester

import com.example.data.model.ConnectionStatus
import com.example.data.model.EndpointProfile
import com.example.domain.adapter.ModelAdapter
import com.example.domain.adapter.ModelAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class ConnectionTester {
    private val currentAdapter = AtomicReference<ModelAdapter?>(null)

    suspend fun test(profile: EndpointProfile): Result<ConnectionStatus> = withContext(Dispatchers.IO) {
        val adapter = ModelAdapterFactory.create(profile)
        currentAdapter.set(adapter)
        try {
            adapter.testConnection()
        } catch (e: CancellationException) {
            adapter.cancel()
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            currentAdapter.set(null)
        }
    }

    fun cancel() {
        currentAdapter.get()?.cancel()
        currentAdapter.set(null)
    }
}
