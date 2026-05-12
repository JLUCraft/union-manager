package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.model.AuditChainVerification
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.model.GenericPushEventData
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var client: Libp2pClient
    private lateinit var pushEvents: MutableSharedFlow<PushService.PushEvent>
    private lateinit var pushService: PushService

    private val entries = listOf(
        AuditEntry(
            id = 1,
            ts = 1_700_000_000_000L,
            actorPubkey = "pk-1",
            actorRole = "admin",
            cmdType = "create",
            target = "game-1",
            payloadHash = "hash-1",
            signatures = emptyList(),
            outcome = "success",
            error = null,
            prevHash = ""
        )
    )

    private val verification = AuditChainVerification(valid = true, brokenCount = 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        client = mockk()
        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 8)
        pushService = mockk()
        every { pushService.events } returns pushEvents
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockSuccess() {
        coEvery { client.listAuditEntries(cmdType = any(), limit = 100) } returns Result.success(entries)
        coEvery { client.verifyAuditChain() } returns Result.success(verification)
        coEvery { client.getAuditAnomalies() } returns Result.success(emptyList())
    }

    private fun createViewModel(): AuditLogViewModel = AuditLogViewModel(client, pushService)

    private fun pushEvent(type: String) = PushService.PushEvent(type, GenericPushEventData(raw = ""))

    @Test
    fun `initial state loads audit entries and chain verification`() = runTest {
        mockSuccess()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(entries, viewModel.uiState.value.entries)
        assertEquals(verification, viewModel.uiState.value.chainVerification)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        coVerify(exactly = 1) { client.listAuditEntries(cmdType = null, limit = 100) }
        coVerify(exactly = 1) { client.verifyAuditChain() }
    }

    @Test
    fun `audit push event triggers refresh`() = runTest {
        mockSuccess()
        createViewModel()
        advanceUntilIdle()

        pushEvents.tryEmit(pushEvent("audit_entry"))
        advanceUntilIdle()

        coVerify(exactly = 2) { client.listAuditEntries(cmdType = null, limit = 100) }
        coVerify(exactly = 2) { client.verifyAuditChain() }
    }

    @Test
    fun `non audit push event does not refresh`() = runTest {
        mockSuccess()
        createViewModel()
        advanceUntilIdle()

        pushEvents.tryEmit(pushEvent("unrelated_event"))
        advanceUntilIdle()

        coVerify(exactly = 1) { client.listAuditEntries(cmdType = null, limit = 100) }
    }

    @Test
    fun `entry load failure is exposed as error`() = runTest {
        coEvery { client.listAuditEntries(cmdType = any(), limit = 100) } returns Result.failure(Exception("Network error"))
        coEvery { client.verifyAuditChain() } returns Result.success(verification)
        coEvery { client.getAuditAnomalies() } returns Result.success(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }
}
