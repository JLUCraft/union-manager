package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.model.AuditChainVerification
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.PushService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apiService: ApiService
    private lateinit var pushEvents: MutableSharedFlow<PushService.WebSocketEvent>
    private lateinit var pushService: PushService
    private lateinit var viewModel: AuditLogViewModel

    private val mockEntries = listOf(
        AuditEntry(
            id = 1,
            ts = 1700000000000L,
            actorPubkey = "pk-1",
            actorRole = "admin",
            cmdType = "create",
            target = "game-1",
            payloadHash = "hash-1",
            signatures = emptyList(),
            outcome = "success",
            error = null,
            prevHash = "prev-hash-0"
        ),
        AuditEntry(
            id = 2,
            ts = 1700000001000L,
            actorPubkey = "pk-2",
            actorRole = "member",
            cmdType = "update",
            target = "game-1",
            payloadHash = "hash-2",
            signatures = emptyList(),
            outcome = "success",
            error = null,
            prevHash = "hash-1"
        )
    )

    private val mockValidVerification = AuditChainVerification(
        valid = true,
        brokenCount = 0
    )

    private val mockInvalidVerification = AuditChainVerification(
        valid = false,
        brokenCount = 3
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiService = mockk()

        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        pushService = mockk()
        every { pushService.events } returns pushEvents
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockSuccess() {
        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.success(mockEntries)
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockValidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())
    }

    private fun createViewModel() {
        viewModel = AuditLogViewModel(apiService, pushService)
    }

    private fun makeEvent(type: String) = PushService.WebSocketEvent(
        type = type,
        data = JsonObject(emptyMap())
    )

    // ---------------------------------------------------------------
    // 1. Initial state loads entries and chain verification
    // ---------------------------------------------------------------

    @Test
    fun initial_state_loads_entries_and_chain_verification() {
        mockSuccess()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockEntries, viewModel.uiState.value.entries)
        assertEquals(mockValidVerification, viewModel.uiState.value.chainVerification)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        coVerify(exactly = 1) { apiService.listAuditEntries(limit = 100) }
        coVerify(exactly = 1) { apiService.verifyAuditChain() }
    }

    // ---------------------------------------------------------------
    // 2. Refresh updates entries and chain verification
    // ---------------------------------------------------------------

    @Test
    fun refresh_updates_entries_and_chain_verification() {
        mockSuccess()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedEntries = listOf(
            AuditEntry(
                id = 3,
                ts = 1700000002000L,
                actorPubkey = "pk-3",
                actorRole = "admin",
                cmdType = "delete",
                target = "game-2",
                payloadHash = "hash-3",
                signatures = emptyList(),
                outcome = "success",
                error = null,
                prevHash = "hash-2"
            )
        )
        val updatedVerification = AuditChainVerification(valid = true, brokenCount = 0)

        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.success(updatedEntries)
        coEvery { apiService.verifyAuditChain() } returns Result.success(updatedVerification)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(updatedEntries, viewModel.uiState.value.entries)
        assertEquals(updatedVerification, viewModel.uiState.value.chainVerification)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        coVerify(exactly = 2) { apiService.listAuditEntries(limit = 100) }
        coVerify(exactly = 2) { apiService.verifyAuditChain() }
    }

    // ---------------------------------------------------------------
    // 3. Push event "audit_entry" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_audit_entry_triggers_refresh() {
        mockSuccess()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("audit_entry"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { apiService.listAuditEntries(limit = 100) }
        coVerify(exactly = 2) { apiService.verifyAuditChain() }
    }

    // ---------------------------------------------------------------
    // 4. Push event "audit_created" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_audit_created_triggers_refresh() {
        mockSuccess()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("audit_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { apiService.listAuditEntries(limit = 100) }
        coVerify(exactly = 2) { apiService.verifyAuditChain() }
    }

    // ---------------------------------------------------------------
    // 5. Non-matching push event does not refresh
    // ---------------------------------------------------------------

    @Test
    fun non_matching_push_event_does_not_refresh() {
        mockSuccess()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("unrelated_event"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { apiService.listAuditEntries(limit = 100) }
        coVerify(exactly = 1) { apiService.verifyAuditChain() }
    }

    // ---------------------------------------------------------------
    // 6. Error state is handled
    // ---------------------------------------------------------------

    @Test
    fun error_state_is_handled() {
        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.failure(Exception("Network error"))
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockValidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertEquals(mockValidVerification, viewModel.uiState.value.chainVerification)
    }

    // ---------------------------------------------------------------
    // 7. Loading state is set correctly
    // ---------------------------------------------------------------

    @Test
    fun loading_state_is_set_correctly() {
        coEvery { apiService.listAuditEntries(limit = 100) } coAnswers {
            delay(Long.MAX_VALUE)
            Result.success(mockEntries)
        }
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockValidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loading_state_is_false_after_error() {
        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.failure(Exception("fail"))
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockValidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------
    // 8. Empty entries list is handled
    // ---------------------------------------------------------------

    @Test
    fun empty_entries_list_is_handled() {
        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.success(emptyList())
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockValidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------
    // 9. Chain verification valid shows correct state
    // ---------------------------------------------------------------

    @Test
    fun chain_verification_valid_shows_correct_state() {
        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.success(mockEntries)
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockValidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.chainVerification)
        assertTrue(viewModel.uiState.value.chainVerification!!.valid)
        assertTrue(viewModel.uiState.value.chainVerification!!.brokenCount == 0)
    }

    // ---------------------------------------------------------------
    // 10. Chain verification invalid shows correct state
    // ---------------------------------------------------------------

    @Test
    fun chain_verification_invalid_shows_correct_state() {
        coEvery { apiService.listAuditEntries(limit = 100) } returns Result.success(mockEntries)
        coEvery { apiService.verifyAuditChain() } returns Result.success(mockInvalidVerification)
        coEvery { apiService.getAuditAnomalies() } returns Result.success(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.chainVerification)
        assertFalse(viewModel.uiState.value.chainVerification!!.valid)
        assertEquals(3, viewModel.uiState.value.chainVerification!!.brokenCount)
    }
}
