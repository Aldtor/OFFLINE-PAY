package com.offlinepay.feature.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.offlinepay.core.common.dispatchers.AppDispatchers
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.usecase.transaction.ExportTransactionsUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

/**
 * UI state for the Transaction History screen.
 *
 * A flat data class (not sealed) so that the screen can always render:
 * — the search bar (searchQuery)
 * — the filter row (activeFilters / hasActiveFilters)
 * — a transient error snackbar (error)
 * independently of whether data is loading or loaded.
 *
 * Design reference: Section 10.2 (HistoryViewModel contract)
 * Requirements: Req 8.4–8.11 (history display and all filter types)
 *
 * @param searchQuery The current text in the search bar.
 * @param activeFilters The currently applied [TransactionFilters] (excludes searchQuery
 *   — that is kept separately so it can be debounced independently).
 * @param hasActiveFilters True when any filter criterion is active or searchQuery is non-empty.
 * @param error Non-null when a transient error occurred (e.g. export failure).
 */
data class HistoryUiState(
    val searchQuery: String = "",
    val activeFilters: TransactionFilters = TransactionFilters(),
    val hasActiveFilters: Boolean = false,
    val error: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI Events (one-shot side effects)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One-shot events emitted by [HistoryViewModel].
 *
 * Design reference: Section 10.2 (SEALED HistoryUiEvent)
 * Requirements: Req 8.14 (receipt), Req 8.17 (CSV), Req 8.18 (share sheet)
 */
sealed class HistoryUiEvent {
    /** Navigate to the Transaction Receipt screen for [transactionId]. */
    data class NavigateToReceipt(val transactionId: String) : HistoryUiEvent()

    /** Launch Android share sheet with the exported file [uri]. */
    data class ShareExport(val uri: Uri) : HistoryUiEvent()

    /** Show a non-fatal error snackbar to the user. */
    data class ShowError(val message: String) : HistoryUiEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// UI Actions (user intents)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * User-initiated interactions dispatched to [HistoryViewModel.onAction].
 *
 * Design reference: Section 10.2 (SEALED HistoryUiAction)
 * Requirements: Req 8.6 (search), Req 8.7–8.11 (filters), Req 8.17 (CSV export)
 */
sealed class HistoryUiAction {
    /** User typed or cleared text in the search bar. Debounced 300ms before applying. */
    data class SearchQueryChanged(val query: String) : HistoryUiAction()

    /**
     * User applied a new set of filter criteria.
     * The [filters.searchQuery] field is ignored here — use [SearchQueryChanged] instead.
     */
    data class FiltersChanged(val filters: TransactionFilters) : HistoryUiAction()

    /** User tapped "Clear all filters" — resets everything to defaults. */
    data object ClearFilters : HistoryUiAction()

    /** User tapped the CSV export button. */
    data object ExportAsCsv : HistoryUiAction()

    /** User tapped a transaction row to view its receipt. */
    data class OpenReceipt(val transactionId: String) : HistoryUiAction()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Transaction History screen.
 *
 * Exposes:
 * - [uiState]: flat [HistoryUiState] reflecting search query, filters, and transient errors.
 * - [events]: one-shot [HistoryUiEvent] stream for navigation, share sheet, and errors.
 * - [pagingData]: paged [Flow<PagingData<TransactionRecord>>] derived from a debounced
 *   (300ms) search query combined with the active [TransactionFilters], cached in
 *   [viewModelScope] to survive Compose recompositions.
 *
 * **Search debouncing**: the raw keystroke flow ([_searchQuery]) is debounced 300ms before
 * being combined with [_activeFilters] to produce [pagingData]. This avoids creating a new
 * [PagingSource] on every keystroke while still giving the user fast feedback in [uiState].
 *
 * **PagingData strategy**: The domain use case returns `Flow<List<TransactionRecord>>`.
 * To honour the Paging 3 contract at the ViewModel boundary without adding Android Paging
 * dependencies to the domain layer, a [ListBackedPagingSource] wraps each emission into
 * a [PagingData] stream via [Pager].
 *
 * Design reference: Section 10.2 (HistoryViewModel contract)
 * Requirements: Req 8.4–8.18
 *
 * @param getTransactionHistoryUseCase Paginated transaction retrieval with [TransactionFilters].
 * @param exportTransactionsUseCase One-shot list export for CSV file generation.
 * @param dispatchers Injected [AppDispatchers] for controlled dispatcher substitution in tests.
 * @param context [ApplicationContext] for CSV file creation in the app's cache directory.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val exportTransactionsUseCase: ExportTransactionsUseCase,
    private val dispatchers: AppDispatchers,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── Internal state flows ──────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(HistoryUiState())

    /** Publicly exposed UI state; always replays the last emission to new collectors. */
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** One-shot side-effect events. Buffered to survive brief UI suspension. */
    private val _events = Channel<HistoryUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Raw search query typed by the user.
     * The [pagingData] pipeline debounces this 300ms before producing a new paging source.
     * [uiState] is updated immediately (no debounce) to keep the text field responsive.
     *
     * Requirements: Req 8.6 (debounced text search against name/UPI ID)
     */
    private val _searchQuery = MutableStateFlow("")

    /**
     * Currently active filter criteria (excluding the search query — that lives in
     * [_searchQuery] so it can be debounced independently).
     *
     * Requirements: Req 8.7–8.11 (status / date / amount / merchant / method filters)
     */
    private val _activeFilters = MutableStateFlow(TransactionFilters())

    // ── Paged transaction flow ────────────────────────────────────────────────

    /**
     * Paged transaction stream, cached in [viewModelScope].
     *
     * Pipeline:
     * 1. Debounce [_searchQuery] 300ms to avoid a new paging source on every keystroke.
     * 2. Combine with [_activeFilters] to produce the effective [TransactionFilters].
     * 3. [flatMapLatest] cancels the previous paging source on every filter/query change.
     * 4. [cachedIn] keeps pages in memory across Compose recompositions.
     *
     * Design reference: Section 10.2 (HistoryViewModel — pagingData)
     * Requirements: Req 8.4 (reverse-chronological order), Req 8.6–8.11 (filter correctness)
     */
    val pagingData: Flow<PagingData<TransactionRecord>> =
        combine(
            _searchQuery.debounce(300L),
            _activeFilters,
        ) { query, filters ->
            filters.copy(searchQuery = query.ifBlank { null })
        }
            .flatMapLatest { combinedFilters ->
                buildPagingFlow(combinedFilters)
            }
            .cachedIn(viewModelScope)

    // ── Initialisation ────────────────────────────────────────────────────────

    init {
        // Sync combined query + filters back into _uiState so the screen always reflects
        // the latest state without waiting for the 300ms debounce on pagingData.
        viewModelScope.launch {
            combine(_searchQuery, _activeFilters) { q, f ->
                HistoryUiState(
                    searchQuery = q,
                    activeFilters = f,
                    hasActiveFilters = f != TransactionFilters() || q.isNotEmpty(),
                )
            }.collect { _uiState.value = it }
        }
    }

    // ── Action handler ────────────────────────────────────────────────────────

    /**
     * Single entry point for all user interactions from the History screen.
     *
     * @param action The [HistoryUiAction] dispatched by the composable.
     */
    fun onAction(action: HistoryUiAction) {
        when (action) {
            is HistoryUiAction.SearchQueryChanged -> handleSearchQueryChanged(action.query)
            is HistoryUiAction.FiltersChanged     -> handleFiltersChanged(action.filters)
            is HistoryUiAction.ClearFilters       -> handleClearFilters()
            is HistoryUiAction.ExportAsCsv        -> handleExportAsCsv()
            is HistoryUiAction.OpenReceipt        -> handleOpenReceipt(action.transactionId)
        }
    }

    // ── Private action handlers ───────────────────────────────────────────────

    /**
     * Updates the raw search query immediately (for UI feedback) and via debounced flow
     * (for paging source invalidation).
     *
     * Requirements: Req 8.6
     */
    private fun handleSearchQueryChanged(query: String) {
        _searchQuery.value = query
        // _uiState is kept in sync automatically by the init{ combine(...) } collector
    }

    /**
     * Replaces the active filters. The [filters.searchQuery] field is stripped and kept
     * in [_searchQuery] to preserve independent debouncing.
     *
     * Requirements: Req 8.7–8.11
     */
    private fun handleFiltersChanged(filters: TransactionFilters) {
        // If the caller embedded a query in the filters, sync it to the dedicated field
        filters.searchQuery?.let { _searchQuery.value = it }
        _activeFilters.value = filters.copy(searchQuery = null)
    }

    /**
     * Resets all filters and the search query.
     *
     * Requirements: Req 8.4 (full reverse-chronological list on clear)
     */
    private fun handleClearFilters() {
        _searchQuery.value = ""
        _activeFilters.value = TransactionFilters()
    }

    /**
     * Exports all transactions matching the current filters as a CSV file and emits a
     * [HistoryUiEvent.ShareExport] so the screen can launch the Android share sheet.
     *
     * The CSV is written to `<cacheDir>/exports/transactions_<timestamp>.csv` and
     * exposed via [androidx.core.content.FileProvider] with authority
     * `<packageName>.fileprovider`.
     *
     * Requirements: Req 8.17 (header row + one row per transaction)
     * Requirements: Req 8.18 (share via Android share sheet)
     */
    private fun handleExportAsCsv() {
        viewModelScope.launch(dispatchers.io) {
            val combinedFilters = _activeFilters.value.copy(
                searchQuery = _searchQuery.value.ifBlank { null },
            )
            when (val result = exportTransactionsUseCase(combinedFilters)) {
                is AppResult.Success -> {
                    try {
                        val uri = writeCsvAndGetUri(result.data)
                        _events.send(HistoryUiEvent.ShareExport(uri))
                    } catch (e: Exception) {
                        val msg = "Export failed: ${e.localizedMessage}"
                        _events.send(HistoryUiEvent.ShowError(msg))
                        _uiState.value = _uiState.value.copy(error = msg)
                    }
                }
                is AppResult.Failure -> {
                    val msg = "Export failed: ${result.error}"
                    _events.send(HistoryUiEvent.ShowError(msg))
                    _uiState.value = _uiState.value.copy(error = msg)
                }
                else -> Unit
            }
        }
    }

    /**
     * Emits a [HistoryUiEvent.NavigateToReceipt] for the screen to act on.
     *
     * Requirements: Req 8.14 (transaction receipt screen)
     */
    private fun handleOpenReceipt(transactionId: String) {
        viewModelScope.launch {
            _events.send(HistoryUiEvent.NavigateToReceipt(transactionId))
        }
    }

    // ── Paging source factory ─────────────────────────────────────────────────

    /**
     * Creates a [Pager] backed by [ListBackedPagingSource] for the given [filters].
     *
     * Page size is 30 rows with a prefetch distance of 10 and an initial load size of 60
     * (2 pages) to reduce initial scroll jank.
     */
    private fun buildPagingFlow(filters: TransactionFilters): Flow<PagingData<TransactionRecord>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
            ),
            pagingSourceFactory = { ListBackedPagingSource(getTransactionHistoryUseCase, filters) },
        ).flow
    }

    // ── CSV generation ────────────────────────────────────────────────────────

    /**
     * Writes [records] to `<cacheDir>/exports/transactions_<timestamp>.csv` and returns a
     * [FileProvider] URI that can be passed to the Android share sheet.
     *
     * CSV format:
     * ```
     * id,timestamp,payeeUpiId,payeeName,merchantName,merchantCategoryCode,
     * amountPaise,paymentMethod,status,operatorUsed,simSlotUsed,transactionReference,createdAt
     * ```
     *
     * Requirements: Req 8.17 (header row + one row per transaction)
     */
    private suspend fun writeCsvAndGetUri(records: List<TransactionRecord>): Uri =
        withContext(dispatchers.io) {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val csvFile = File(exportsDir, "transactions_${System.currentTimeMillis()}.csv")

            FileWriter(csvFile).use { writer ->
                writer.appendLine(CSV_HEADER)
                records.forEach { txn -> writer.appendLine(txn.toCsvRow()) }
            }

            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile,
            )
        }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val PAGE_SIZE = 30
        const val PREFETCH_DISTANCE = 10
        const val INITIAL_LOAD_SIZE = 60

        val CSV_HEADER = listOf(
            "id",
            "timestamp",
            "payeeUpiId",
            "payeeName",
            "merchantName",
            "merchantCategoryCode",
            "amountPaise",
            "paymentMethod",
            "status",
            "operatorUsed",
            "simSlotUsed",
            "transactionReference",
            "createdAt",
        ).joinToString(",")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PagingSource backed by GetTransactionHistoryUseCase (Flow<List<T>>)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A [PagingSource] that loads pages from a `Flow<List<TransactionRecord>>` emitted by
 * [GetTransactionHistoryUseCase].
 *
 * The domain layer intentionally does not depend on Android Paging. This adapter lives in
 * the feature layer and wraps each `List` emission into in-memory page slices so the UI
 * can use `collectAsLazyPagingItems()`.  For large datasets, the Room DAO applies SQL-level
 * filtering before the list reaches this layer.
 *
 * Requirements: Req 8.4 (reverse-chronological order enforced by DAO ORDER BY)
 */
internal class ListBackedPagingSource(
    private val useCase: GetTransactionHistoryUseCase,
    private val filters: TransactionFilters,
) : PagingSource<Int, TransactionRecord>() {

    /** Cached after the first [load] call so subsequent page loads do not re-query the DB. */
    private var cachedList: List<TransactionRecord>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionRecord> {
        return try {
            val list = cachedList ?: run {
                // Take only the first emission. The use case is backed by a Room
                // Flow<List<…>> which is hot and never completes, so collect() would
                // block forever (permanent History spinner). first() suspends until the
                // initial value then returns. Subsequent DB updates re-invalidate this
                // PagingSource via flatMapLatest in HistoryViewModel.
                val emission = useCase(filters).first()
                cachedList = emission
                emission
            }

            val page = params.key ?: 0
            val pageSize = params.loadSize
            val fromIndex = page * pageSize
            val toIndex = minOf(fromIndex + pageSize, list.size)

            LoadResult.Page(
                data = if (fromIndex < list.size) list.subList(fromIndex, toIndex) else emptyList(),
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (toIndex >= list.size) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TransactionRecord>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CSV row extension
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts a [TransactionRecord] to a CSV row string.
 *
 * All string fields are quoted and internal double-quotes are escaped per RFC 4180.
 * Requirements: Req 8.17 (one row per transaction with all stored fields)
 */
private fun TransactionRecord.toCsvRow(): String {
    fun String?.csvQuote(): String =
        if (this == null) "" else "\"${replace("\"", "\"\"")}\""
    return listOf(
        id.csvQuote(),
        timestampMs.toString(),
        payeeUpiId.csvQuote(),
        payeeName.csvQuote(),
        merchantName.csvQuote(),
        merchantCategoryCode.csvQuote(),
        amountPaise.toString(),
        paymentMethod.name,
        status.name,
        operatorUsed?.name.csvQuote(),
        simSlotUsed.toString(),
        transactionReference.csvQuote(),
        createdAt.toString(),
    ).joinToString(",")
}
