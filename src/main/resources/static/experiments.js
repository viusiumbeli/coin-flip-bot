const API_BASE = '/api';
let selectedExperiments = new Set();
let currentExperimentId = null;
let currentPollingExperimentId = null;
let pollingInterval = null;
let currentExperiment = null;  // Store current experiment for pagination
let currentPage = 0;
let pageSize = 100;
let currentSortField = 'runNumber';
let currentSortDir = 'asc';

// Load config and experiments on page load
async function init() {
    await loadConfig();
    await loadExperiments();
}

async function loadConfig() {
    try {
        const [configRes, symbolsRes] = await Promise.all([
            fetch(`${API_BASE}/backtest/config`),
            fetch(`${API_BASE}/backtest/symbols`)
        ]);

        const symbols = await symbolsRes.json();

        // Populate symbols dropdown
        const symbolSelect = document.getElementById('symbol');
        symbolSelect.innerHTML = symbols.symbols.map(s =>
            `<option value="${s}">${s}</option>`
        ).join('');

        // Populate timeframes dropdown
        const timeframeSelect = document.getElementById('timeframe');
        timeframeSelect.innerHTML = symbols.timeframes.map(t =>
            `<option value="${t}">${t}</option>`
        ).join('');

        // Set default dates (last year)
        const endDate = new Date();
        const startDate = new Date();
        startDate.setFullYear(startDate.getFullYear() - 1);

        document.getElementById('startDate').value = startDate.toISOString().slice(0, 16);
        document.getElementById('endDate').value = endDate.toISOString().slice(0, 16);

    } catch (error) {
        showError('Failed to load configuration: ' + error.message);
    }
}

async function loadExperiments() {
    try {
        const response = await fetch(`${API_BASE}/experiments`);
        const experiments = await response.json();

        const tbody = document.getElementById('experimentsBody');

        if (experiments.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="12" style="text-align: center; padding: 40px; color: #666;">
                        No experiments yet. Create your first experiment above!
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = experiments.map(exp => `
            <tr>
                <td><input type="checkbox" data-id="${exp.id}" onchange="toggleSelection(${exp.id})" ${exp.status !== 'COMPLETED' ? 'disabled' : ''}></td>
                <td class="name-column">${exp.customName || exp.name}</td>
                <td>${getStatusBadge(exp.status, exp.progressPercent)}</td>
                <td><span class="badge">${exp.completedRuns}/${exp.numBacktests}</span></td>
                <td>${exp.symbol}</td>
                <td>${exp.timeframe}</td>
                <td>${formatDate(exp.startDate)} - ${formatDate(exp.endDate)}</td>
                <td class="${exp.status === 'COMPLETED' ? (exp.totalReturnPercent >= 0 ? 'positive' : 'negative') : ''}">${exp.status === 'COMPLETED' ? formatNumber(exp.totalReturnPercent) + '%' : '-'}</td>
                <td>${exp.status === 'COMPLETED' ? formatNumber(exp.winRate) + '%' : '-'}</td>
                <td>${exp.status === 'COMPLETED' ? formatNumber(exp.sharpeRatio) : '-'}</td>
                <td>${formatDateTime(exp.createdAt)}</td>
                <td class="actions">
                    ${exp.status === 'COMPLETED' ? `<button class="btn-info" onclick="viewExperiment(${exp.id})">View</button>` : ''}
                    ${exp.status === 'RUNNING' || exp.status === 'PENDING' ? `<button class="btn-secondary" onclick="cancelExperiment(${exp.id})">Cancel</button>` : ''}
                    <button class="btn-danger" onclick="deleteExperiment(${exp.id})">Delete</button>
                </td>
            </tr>
        `).join('');

        // Reset selections
        selectedExperiments.clear();
        updateCompareButton();

    } catch (error) {
        showError('Failed to load experiments: ' + error.message);
    }
}

async function createExperiment() {
    const symbol = document.getElementById('symbol').value;
    const timeframe = document.getElementById('timeframe').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    const numBacktests = parseInt(document.getElementById('numBacktests').value) || 1;
    const customName = document.getElementById('customName').value;
    const notes = document.getElementById('notes').value;

    if (!symbol || !timeframe || !startDate || !endDate) {
        showError('Please fill in all required fields');
        return;
    }

    if (numBacktests < 1 || numBacktests > 10000000) {
        showError('Number of backtests must be between 1 and 10,000,000');
        return;
    }

    showLoading(true, `Starting experiment with ${numBacktests} backtests...`);
    hideError();

    try {
        const response = await fetch(`${API_BASE}/experiments`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                symbol,
                timeframe,
                startDate: new Date(startDate).toISOString(),
                endDate: new Date(endDate).toISOString(),
                numBacktests,
                customName: customName || null,
                notes: notes || null
            })
        });

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const result = await response.json();

        // Clear form
        document.getElementById('customName').value = '';
        document.getElementById('notes').value = '';

        // Start polling for status
        currentPollingExperimentId = result.id;
        document.getElementById('progressContainer').classList.remove('hidden');
        document.getElementById('loadingText').textContent = `Experiment started. Running ${numBacktests} backtests...`;
        startPolling(result.id);

    } catch (error) {
        showError('Failed to create experiment: ' + error.message);
        showLoading(false);
    }
}

function startPolling(experimentId) {
    // Poll every 2 seconds
    pollingInterval = setInterval(() => pollExperimentStatus(experimentId), 2000);
    // Also poll immediately
    pollExperimentStatus(experimentId);
}

function stopPolling() {
    if (pollingInterval) {
        clearInterval(pollingInterval);
        pollingInterval = null;
    }
    currentPollingExperimentId = null;
}

async function pollExperimentStatus(experimentId) {
    try {
        const response = await fetch(`${API_BASE}/experiments/${experimentId}/status`);
        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const status = await response.json();

        // Update progress bar
        const progressBar = document.getElementById('progressBar');
        const progressText = document.getElementById('progressText');
        progressBar.style.width = `${status.progressPercent}%`;
        progressText.textContent = `${status.progressPercent.toFixed(1)}% complete (${status.completedRuns}/${status.totalRuns} runs)`;

        // Check if done
        if (status.status === 'COMPLETED' || status.status === 'FAILED' || status.status === 'CANCELLED') {
            stopPolling();
            document.getElementById('progressContainer').classList.add('hidden');
            showLoading(false);

            if (status.status === 'COMPLETED') {
                // Reload experiments list and view the completed experiment
                await loadExperiments();
                viewExperiment(experimentId);
            } else if (status.status === 'FAILED') {
                showError(`Experiment failed: ${status.errorMessage || 'Unknown error'}`);
                await loadExperiments();
            } else if (status.status === 'CANCELLED') {
                showError('Experiment was cancelled');
                await loadExperiments();
            }
        }

    } catch (error) {
        console.error('Error polling status:', error);
    }
}

async function cancelCurrentExperiment() {
    if (!currentPollingExperimentId) return;

    try {
        const response = await fetch(`${API_BASE}/experiments/${currentPollingExperimentId}/cancel`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        document.getElementById('loadingText').textContent = 'Cancelling experiment...';

    } catch (error) {
        showError('Failed to cancel experiment: ' + error.message);
    }
}

async function viewExperiment(id) {
    showLoading(true);
    hideError();

    try {
        const response = await fetch(`${API_BASE}/experiments/${id}`);
        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const experiment = await response.json();
        displayExperimentDetail(experiment);

    } catch (error) {
        showError('Failed to load experiment: ' + error.message);
    } finally {
        showLoading(false);
    }
}

function displayExperimentDetail(exp) {
    currentExperimentId = exp.id;
    document.getElementById('createForm').classList.add('hidden');
    document.getElementById('historySection').classList.add('hidden');
    document.getElementById('comparisonSection').classList.add('hidden');
    document.getElementById('runDetailSection').classList.add('hidden');
    document.getElementById('detailSection').classList.remove('hidden');

    const isProfit = exp.totalReturnPercent >= 0;

    document.getElementById('detailTitle').innerHTML = `
        ${exp.customName || exp.name}
        <span class="badge" style="margin-left: 10px;">${exp.numBacktests} runs</span>
        <span style="color: ${isProfit ? '#10b981' : '#ef4444'}; font-size: 18px; margin-left: 15px;">
            Avg ${formatNumber(exp.totalReturnPercent)}% Return
        </span>
    `;

    document.getElementById('detailMeta').innerHTML = `
        ${exp.symbol} | ${exp.timeframe} | ${formatDate(exp.startDate)} to ${formatDate(exp.endDate)} | Created: ${formatDateTime(exp.createdAt)}
    `;

    // Show notes if present
    const notesDiv = document.getElementById('detailNotes');
    if (exp.notes) {
        notesDiv.innerHTML = `<strong>Notes:</strong> ${exp.notes}`;
        notesDiv.classList.remove('hidden');
    } else {
        notesDiv.classList.add('hidden');
    }

    // Display aggregated metrics
    document.getElementById('detailMetrics').innerHTML = `
        <div class="metric-card">
            <div class="metric-label">Initial Capital</div>
            <div class="metric-value">$${formatNumber(exp.initialCapital)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Final Capital</div>
            <div class="metric-value ${isProfit ? 'positive' : 'negative'}">$${formatNumber(exp.finalCapital)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Total Return</div>
            <div class="metric-value ${isProfit ? 'positive' : 'negative'}">$${formatNumber(exp.totalReturn)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Max Drawdown</div>
            <div class="metric-value negative">${formatNumber(exp.maxDrawdownPercent)}%</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Win Rate</div>
            <div class="metric-value">${formatNumber(exp.winRate)}%</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Profit Factor</div>
            <div class="metric-value">${formatNumber(exp.profitFactor)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Sharpe Ratio</div>
            <div class="metric-value">${formatNumber(exp.sharpeRatio)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Avg Total Trades</div>
            <div class="metric-value">${exp.totalTrades}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Buy & Hold Return</div>
            <div class="metric-value">${formatNumber(exp.buyAndHoldReturnPercent)}%</div>
        </div>
    `;

    // Store experiment for use by pagination and charts
    currentExperiment = exp;
    currentPage = 0;
    // Reset sort to default when viewing a new experiment
    currentSortField = 'runNumber';
    currentSortDir = 'asc';

    // Load runs via paginated API
    loadExperimentRuns(exp.id, 0);

    // Render charts (with limits for large experiments)
    renderCharts(exp);
}

async function loadExperimentRuns(experimentId, page) {
    try {
        const response = await fetch(`${API_BASE}/experiments/${experimentId}/runs?page=${page}&size=${pageSize}&sortBy=${currentSortField}&sortDir=${currentSortDir}`);
        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }
        const data = await response.json();

        currentPage = data.page;

        // Render runs table
        if (data.runs.length === 0 && data.page === 0) {
            document.getElementById('runsBody').innerHTML = `
                <tr>
                    <td colspan="8" style="text-align: center; padding: 20px; color: #666;">
                        No runs available
                    </td>
                </tr>
            `;
        } else {
            document.getElementById('runsBody').innerHTML = data.runs.map(run => `
                <tr>
                    <td><strong>Run ${run.runNumber}</strong></td>
                    <td class="${run.totalReturnPercent >= 0 ? 'positive' : 'negative'}">${formatNumber(run.totalReturnPercent)}%</td>
                    <td>${formatNumber(run.winRate)}%</td>
                    <td>${formatNumber(run.sharpeRatio)}</td>
                    <td>${formatNumber(run.profitFactor)}</td>
                    <td class="negative">${formatNumber(run.maxDrawdownPercent)}%</td>
                    <td>${run.totalTrades}</td>
                    <td>
                        <button class="btn-info" onclick="viewRun(${run.id})">View Trades</button>
                    </td>
                </tr>
            `).join('');
        }

        // Render pagination controls
        renderPaginationControls(experimentId, data);

        // Update sort indicators
        updateSortIndicators();

    } catch (error) {
        showError('Failed to load runs: ' + error.message);
    }
}

function renderPaginationControls(experimentId, data) {
    // Remove existing pagination controls
    const existingControls = document.getElementById('paginationControls');
    if (existingControls) {
        existingControls.remove();
    }

    if (data.totalPages <= 1) return;

    const controls = document.createElement('div');
    controls.id = 'paginationControls';
    controls.style.cssText = 'margin-top: 15px; display: flex; justify-content: space-between; align-items: center; padding: 10px; background: #f5f7fa; border-radius: 6px;';
    controls.innerHTML = `
        <span style="font-size: 14px; color: #666;">
            Page ${data.page + 1} of ${data.totalPages} (${data.totalElements.toLocaleString()} total runs)
        </span>
        <div style="display: flex; gap: 10px;">
            <button class="btn-secondary" ${data.page === 0 ? 'disabled' : ''}
                onclick="loadExperimentRuns(${experimentId}, ${data.page - 1})"
                style="min-width: 100px;">Previous</button>
            <button class="btn-secondary" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}
                onclick="loadExperimentRuns(${experimentId}, ${data.page + 1})"
                style="min-width: 100px;">Next</button>
        </div>
    `;

    // Insert after the runs table
    const runsTable = document.querySelector('.runs-section .data-table');
    runsTable.parentNode.insertBefore(controls, runsTable.nextSibling);
}

async function viewRun(runId) {
    showLoading(true);
    hideError();

    try {
        const response = await fetch(`${API_BASE}/experiments/runs/${runId}`);
        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const run = await response.json();
        displayRunDetail(run);

    } catch (error) {
        showError('Failed to load run: ' + error.message);
    } finally {
        showLoading(false);
    }
}

function displayRunDetail(run) {
    document.getElementById('detailSection').classList.add('hidden');
    document.getElementById('runDetailSection').classList.remove('hidden');

    const isProfit = run.totalReturnPercent >= 0;

    document.getElementById('runDetailTitle').innerHTML = `
        Run #${run.runNumber}
        <span style="color: ${isProfit ? '#10b981' : '#ef4444'}; font-size: 18px; margin-left: 15px;">
            ${formatNumber(run.totalReturnPercent)}% Return
        </span>
    `;

    document.getElementById('runDetailMeta').innerHTML = `
        ${run.totalTrades} trades | Win Rate: ${formatNumber(run.winRate)}% | Sharpe: ${formatNumber(run.sharpeRatio)}
    `;

    // Display run metrics
    document.getElementById('runMetrics').innerHTML = `
        <div class="metric-card">
            <div class="metric-label">Final Capital</div>
            <div class="metric-value ${isProfit ? 'positive' : 'negative'}">$${formatNumber(run.finalCapital)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Total Return</div>
            <div class="metric-value ${isProfit ? 'positive' : 'negative'}">$${formatNumber(run.totalReturn)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Max Drawdown</div>
            <div class="metric-value negative">${formatNumber(run.maxDrawdownPercent)}%</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Win Rate</div>
            <div class="metric-value">${formatNumber(run.winRate)}%</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Profit Factor</div>
            <div class="metric-value">${formatNumber(run.profitFactor)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Sharpe Ratio</div>
            <div class="metric-value">${formatNumber(run.sharpeRatio)}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Winning Trades</div>
            <div class="metric-value positive">${run.winningTrades}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Losing Trades</div>
            <div class="metric-value negative">${run.losingTrades}</div>
        </div>
    `;

    // Display trades
    document.getElementById('runTrades').innerHTML = run.trades.map(trade => `
        <tr>
            <td>${trade.tradeNumber}</td>
            <td>${trade.side}</td>
            <td>${formatDateTime(trade.entryTime)}</td>
            <td>$${formatNumber(trade.entryPrice)}</td>
            <td>$${formatNumber(trade.balanceBeforeOpen)}</td>
            <td>${formatNumber(trade.positionSize)}</td>
            <td>$${formatNumber(trade.balanceAfterOpen)}</td>
            <td>${formatDateTime(trade.exitTime)}</td>
            <td>$${formatNumber(trade.exitPrice)}</td>
            <td>$${formatNumber(trade.balanceBeforeClose)}</td>
            <td class="${trade.profitLoss >= 0 ? 'positive' : 'negative'}">$${formatNumber(trade.profitLoss)}</td>
            <td class="${trade.profitLossPercent >= 0 ? 'positive' : 'negative'}">${formatNumber(trade.profitLossPercent)}%</td>
            <td>$${formatNumber(trade.balanceAfterClose)}</td>
            <td>${trade.exitReason}</td>
        </tr>
    `).join('');
}

function hideRunDetail() {
    document.getElementById('runDetailSection').classList.add('hidden');
    document.getElementById('detailSection').classList.remove('hidden');
}

function hideDetail() {
    destroyCharts();
    document.getElementById('detailSection').classList.add('hidden');
    document.getElementById('runDetailSection').classList.add('hidden');
    document.getElementById('createForm').classList.remove('hidden');
    document.getElementById('historySection').classList.remove('hidden');
    currentExperimentId = null;
}

async function deleteExperiment(id) {
    if (!confirm('Are you sure you want to delete this experiment and all its runs?')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/experiments/${id}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        await loadExperiments();

    } catch (error) {
        showError('Failed to delete experiment: ' + error.message);
    }
}

function toggleSelection(id) {
    if (selectedExperiments.has(id)) {
        selectedExperiments.delete(id);
    } else {
        selectedExperiments.add(id);
    }
    updateCompareButton();
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAll');
    const checkboxes = document.querySelectorAll('#experimentsBody input[type="checkbox"]');

    checkboxes.forEach(cb => {
        cb.checked = selectAll.checked;
        const id = parseInt(cb.dataset.id);
        if (selectAll.checked) {
            selectedExperiments.add(id);
        } else {
            selectedExperiments.delete(id);
        }
    });

    updateCompareButton();
}

function updateCompareButton() {
    const btn = document.getElementById('compareBtn');
    const text = document.getElementById('selectionText');
    const count = selectedExperiments.size;
    btn.disabled = count < 2;
    btn.innerHTML = `Compare <span class="count-badge">${count}</span>`;

    if (count === 0) {
        text.textContent = 'Select experiments to compare';
    } else if (count === 1) {
        text.textContent = 'Select at least 1 more experiment';
    } else {
        text.textContent = `${count} experiments selected`;
    }
}

async function compareSelected() {
    if (selectedExperiments.size < 2) {
        showError('Select at least 2 experiments to compare');
        return;
    }

    showLoading(true);
    hideError();

    try {
        const response = await fetch(`${API_BASE}/experiments/compare`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                experimentIds: Array.from(selectedExperiments)
            })
        });

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const comparison = await response.json();
        displayComparison(comparison);

    } catch (error) {
        showError('Failed to compare experiments: ' + error.message);
    } finally {
        showLoading(false);
    }
}

function displayComparison(data) {
    document.getElementById('createForm').classList.add('hidden');
    document.getElementById('historySection').classList.add('hidden');
    document.getElementById('detailSection').classList.add('hidden');
    document.getElementById('runDetailSection').classList.add('hidden');
    document.getElementById('comparisonSection').classList.remove('hidden');

    // Build header
    const headerRow = `
        <tr>
            <th>Metric</th>
            ${data.experiments.map(exp => `
                <th title="${exp.name}">${exp.customName || exp.symbol + ' ' + exp.timeframe}<br><small>${exp.numBacktests}x runs</small></th>
            `).join('')}
        </tr>
    `;
    document.getElementById('comparisonHead').innerHTML = headerRow;

    // Build body
    const bodyRows = data.metrics.map(metric => `
        <tr>
            <td><strong>${metric.label}</strong></td>
            ${data.experiments.map(exp => `
                <td>${metric.values[exp.id]}</td>
            `).join('')}
        </tr>
    `).join('');
    document.getElementById('comparisonBody').innerHTML = bodyRows;
}

function hideComparison() {
    document.getElementById('comparisonSection').classList.add('hidden');
    document.getElementById('createForm').classList.remove('hidden');
    document.getElementById('historySection').classList.remove('hidden');
}

function showLoading(show, text = 'Loading...') {
    document.getElementById('loading').classList.toggle('hidden', !show);
    document.getElementById('loadingText').textContent = text;
    document.getElementById('createBtn').disabled = show;
}

function showError(message) {
    const errorDiv = document.getElementById('error');
    errorDiv.textContent = message;
    errorDiv.classList.remove('hidden');
}

function hideError() {
    document.getElementById('error').classList.add('hidden');
}

function getStatusBadge(status, progressPercent) {
    const colors = {
        'PENDING': '#f59e0b',
        'RUNNING': '#3b82f6',
        'COMPLETED': '#10b981',
        'FAILED': '#ef4444',
        'CANCELLED': '#6b7280'
    };
    const color = colors[status] || '#6b7280';
    const text = status === 'RUNNING' ? `${status} (${progressPercent.toFixed(0)}%)` : status;
    return `<span style="background: ${color}; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600;">${text}</span>`;
}

async function cancelExperiment(experimentId) {
    if (!confirm('Are you sure you want to cancel this experiment?')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/experiments/${experimentId}/cancel`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        await loadExperiments();

    } catch (error) {
        showError('Failed to cancel experiment: ' + error.message);
    }
}

function sortRuns(field) {
    if (currentSortField === field) {
        // Toggle direction if same field
        currentSortDir = currentSortDir === 'asc' ? 'desc' : 'asc';
    } else {
        // New field, default to ascending
        currentSortField = field;
        currentSortDir = 'asc';
    }
    // Reset to first page and reload
    currentPage = 0;
    loadExperimentRuns(currentExperimentId, 0);
}

function updateSortIndicators() {
    // Remove all sort classes
    document.querySelectorAll('#runsTable th.sortable').forEach(th => {
        th.classList.remove('sort-asc', 'sort-desc');
    });
    // Add class to current sorted column
    const currentTh = document.querySelector(`#runsTable th[data-field="${currentSortField}"]`);
    if (currentTh) {
        currentTh.classList.add(currentSortDir === 'asc' ? 'sort-asc' : 'sort-desc');
    }
}

// Initialize on page load
init();
