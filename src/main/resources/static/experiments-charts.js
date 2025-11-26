// Chart instances
let returnDistChart = null;
let winLossChart = null;
let equityCurveChart = null;
let expectancyPerRunChart = null;
let expectancyDistChart = null;
let cumulativeExpectancyChart = null;
let stdDevChart = null;
let isRenderingCharts = false;

function destroyCharts() {
    if (returnDistChart) { returnDistChart.destroy(); returnDistChart = null; }
    if (winLossChart) { winLossChart.destroy(); winLossChart = null; }
    if (equityCurveChart) { equityCurveChart.destroy(); equityCurveChart = null; }
    if (expectancyPerRunChart) { expectancyPerRunChart.destroy(); expectancyPerRunChart = null; }
    if (expectancyDistChart) { expectancyDistChart.destroy(); expectancyDistChart = null; }
    if (cumulativeExpectancyChart) { cumulativeExpectancyChart.destroy(); cumulativeExpectancyChart = null; }
    if (stdDevChart) { stdDevChart.destroy(); stdDevChart = null; }
}

async function renderCharts(experiment) {
    if (isRenderingCharts) return;
    isRenderingCharts = true;
    try {
        destroyCharts();

        // For large experiments, disable most charts (they require fetching all run details)
        const isLargeExperiment = experiment.numBacktests > 1000;

        if (isLargeExperiment) {
            // Show message for charts that are disabled
            const disabledMessage = '<div style="display: flex; align-items: center; justify-content: center; height: 100%; color: #666; font-size: 14px; text-align: center;">Charts disabled for experiments with &gt;1,000 runs</div>';

            document.getElementById('returnDistChart').parentElement.innerHTML =
                '<h4>Return Distribution Across Runs</h4>' + disabledMessage;
            document.getElementById('equityCurveChart').parentElement.innerHTML =
                '<h4>Equity Curves (All Runs)</h4>' + disabledMessage;
            document.getElementById('expectancyPerRunChart').parentElement.innerHTML =
                '<h4>Expectancy Per Run ($/trade)</h4>' + disabledMessage;
            document.getElementById('expectancyDistChart').parentElement.innerHTML =
                '<h4>Expectancy Distribution</h4>' + disabledMessage;
            document.getElementById('cumulativeExpectancyChart').parentElement.innerHTML =
                '<h4>Cumulative Expectancy (All Runs)</h4>' + disabledMessage;
            document.getElementById('stdDevChart').parentElement.innerHTML =
                '<h4>Std Dev of P/L Per Run</h4>' + disabledMessage;

            // Only render the Win/Loss pie chart (uses aggregated data, not individual runs)
            renderWinLossPie(experiment);
        } else {
            // For small experiments, fetch first page of runs for basic charts
            const response = await fetch(`${API_BASE}/experiments/${experiment.id}/runs?page=0&size=1000`);
            if (response.ok) {
                const data = await response.json();
                const runs = data.runs;

                if (runs.length > 0) {
                    renderReturnDistribution(runs);
                    renderWinLossPie(experiment);
                    await renderEquityCurves(runs);
                    await renderExpectancyPerRun(runs);
                    await renderExpectancyDistribution(runs);
                    await renderCumulativeExpectancy(runs);
                    await renderStdDevChart(runs);
                } else {
                    renderWinLossPie(experiment);
                }
            } else {
                renderWinLossPie(experiment);
            }
        }
    } finally {
        isRenderingCharts = false;
    }
}

function renderReturnDistribution(runs) {
    const ctx = document.getElementById('returnDistChart').getContext('2d');
    returnDistChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: runs.map(r => `Run ${r.runNumber}`),
            datasets: [{
                label: 'Return %',
                data: runs.map(r => parseFloat(r.totalReturnPercent)),
                backgroundColor: runs.map(r =>
                    r.totalReturnPercent >= 0 ? 'rgba(16, 185, 129, 0.7)' : 'rgba(239, 68, 68, 0.7)'
                ),
                borderColor: runs.map(r =>
                    r.totalReturnPercent >= 0 ? 'rgba(16, 185, 129, 1)' : 'rgba(239, 68, 68, 1)'
                ),
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    title: { display: true, text: 'Return %' }
                }
            }
        }
    });
}

function renderWinLossPie(experiment) {
    const ctx = document.getElementById('winLossChart').getContext('2d');
    const wins = experiment.winningTrades;
    const losses = experiment.losingTrades;
    const total = wins + losses;

    winLossChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: [`Wins (${wins})`, `Losses (${losses})`],
            datasets: [{
                data: [wins, losses],
                backgroundColor: ['rgba(16, 185, 129, 0.7)', 'rgba(239, 68, 68, 0.7)'],
                borderColor: ['rgba(16, 185, 129, 1)', 'rgba(239, 68, 68, 1)'],
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: {
                    position: 'bottom'
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const value = context.raw;
                            const pct = total > 0 ? ((value / total) * 100).toFixed(1) : 0;
                            return `${context.label}: ${pct}%`;
                        }
                    }
                }
            }
        }
    });
}

async function renderEquityCurves(runs) {
    // Fetch trade data for each run to build equity curves
    const colors = [
        '#667eea', '#f093fb', '#4ade80', '#f97316', '#06b6d4',
        '#8b5cf6', '#ec4899', '#10b981', '#f59e0b', '#3b82f6'
    ];

    const datasets = [];

    for (let i = 0; i < Math.min(runs.length, 10); i++) {
        const run = runs[i];
        try {
            const response = await fetch(`${API_BASE}/experiments/runs/${run.id}`);
            if (response.ok) {
                const runDetail = await response.json();
                const trades = runDetail.trades;

                if (trades.length > 0) {
                    // Build equity curve from trades
                    const equityData = [{ x: 0, y: parseFloat(trades[0].balanceBeforeOpen) }];
                    trades.forEach((trade, idx) => {
                        equityData.push({ x: idx + 1, y: parseFloat(trade.balanceAfterClose) });
                    });

                    datasets.push({
                        label: `Run ${run.runNumber}`,
                        data: equityData,
                        borderColor: colors[i % colors.length],
                        backgroundColor: 'transparent',
                        borderWidth: 2,
                        tension: 0.1,
                        pointRadius: 0
                    });
                }
            }
        } catch (e) {
            console.error(`Failed to load run ${run.id}:`, e);
        }
    }

    if (datasets.length === 0) return;

    const ctx = document.getElementById('equityCurveChart').getContext('2d');
    equityCurveChart = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: {
                    position: 'top',
                    labels: { boxWidth: 12, padding: 10 }
                }
            },
            scales: {
                x: {
                    type: 'linear',
                    title: { display: true, text: 'Trade #' }
                },
                y: {
                    title: { display: true, text: 'Balance ($)' }
                }
            }
        }
    });
}

async function renderExpectancyPerRun(runs) {
    // Fetch run details to get totalReturn in $ for accurate expectancy
    const expectancies = [];

    for (let i = 0; i < runs.length; i++) {
        const run = runs[i];
        try {
            const response = await fetch(`${API_BASE}/experiments/runs/${run.id}`);
            if (response.ok) {
                const runDetail = await response.json();
                const expectancy = runDetail.totalTrades > 0
                    ? parseFloat(runDetail.totalReturn) / runDetail.totalTrades
                    : 0;
                expectancies.push({ runNumber: run.runNumber, expectancy });
            }
        } catch (e) {
            console.error(`Failed to load run ${run.id}:`, e);
        }
    }

    if (expectancies.length === 0) return;

    const ctx = document.getElementById('expectancyPerRunChart').getContext('2d');
    expectancyPerRunChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: expectancies.map(e => `Run ${e.runNumber}`),
            datasets: [{
                label: 'Expectancy ($/trade)',
                data: expectancies.map(e => e.expectancy),
                backgroundColor: expectancies.map(e =>
                    e.expectancy >= 0 ? 'rgba(16, 185, 129, 0.7)' : 'rgba(239, 68, 68, 0.7)'
                ),
                borderColor: expectancies.map(e =>
                    e.expectancy >= 0 ? 'rgba(16, 185, 129, 1)' : 'rgba(239, 68, 68, 1)'
                ),
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    title: { display: true, text: 'Expectancy ($/trade)' }
                }
            }
        }
    });
}

async function renderExpectancyDistribution(runs) {
    // Fetch run details for expectancy values
    const expectancies = [];

    for (let i = 0; i < runs.length; i++) {
        const run = runs[i];
        try {
            const response = await fetch(`${API_BASE}/experiments/runs/${run.id}`);
            if (response.ok) {
                const runDetail = await response.json();
                const expectancy = runDetail.totalTrades > 0
                    ? parseFloat(runDetail.totalReturn) / runDetail.totalTrades
                    : 0;
                expectancies.push(expectancy);
            }
        } catch (e) {
            console.error(`Failed to load run ${run.id}:`, e);
        }
    }

    if (expectancies.length === 0) return;

    // Create histogram buckets
    const min = Math.min(...expectancies);
    const max = Math.max(...expectancies);
    const range = max - min;
    const bucketCount = Math.min(10, expectancies.length);
    const bucketSize = range / bucketCount || 1;

    const buckets = [];
    for (let i = 0; i < bucketCount; i++) {
        const bucketMin = min + (i * bucketSize);
        const bucketMax = min + ((i + 1) * bucketSize);
        const count = expectancies.filter(e =>
            i === bucketCount - 1
                ? e >= bucketMin && e <= bucketMax
                : e >= bucketMin && e < bucketMax
        ).length;
        buckets.push({
            label: `${bucketMin.toFixed(2)} - ${bucketMax.toFixed(2)}`,
            count,
            midpoint: (bucketMin + bucketMax) / 2
        });
    }

    const ctx = document.getElementById('expectancyDistChart').getContext('2d');
    expectancyDistChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: buckets.map(b => b.label),
            datasets: [{
                label: 'Number of Runs',
                data: buckets.map(b => b.count),
                backgroundColor: buckets.map(b =>
                    b.midpoint >= 0 ? 'rgba(59, 130, 246, 0.7)' : 'rgba(239, 68, 68, 0.7)'
                ),
                borderColor: buckets.map(b =>
                    b.midpoint >= 0 ? 'rgba(59, 130, 246, 1)' : 'rgba(239, 68, 68, 1)'
                ),
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: {
                    title: { display: true, text: 'Expectancy Range ($/trade)' }
                },
                y: {
                    title: { display: true, text: 'Number of Runs' },
                    beginAtZero: true,
                    ticks: { stepSize: 1 }
                }
            }
        }
    });
}

async function renderCumulativeExpectancy(runs) {
    const colors = [
        '#667eea', '#f093fb', '#4ade80', '#f97316', '#06b6d4',
        '#8b5cf6', '#ec4899', '#10b981', '#f59e0b', '#3b82f6'
    ];

    const datasets = [];

    for (let i = 0; i < Math.min(runs.length, 10); i++) {
        const run = runs[i];
        try {
            const response = await fetch(`${API_BASE}/experiments/runs/${run.id}`);
            if (response.ok) {
                const runDetail = await response.json();
                const trades = runDetail.trades;

                if (trades.length > 0) {
                    // Build cumulative expectancy (running average P/L per trade)
                    const expectancyData = [];
                    let cumulativePL = 0;

                    trades.forEach((trade, idx) => {
                        cumulativePL += parseFloat(trade.profitLoss);
                        const avgPLPerTrade = cumulativePL / (idx + 1);
                        expectancyData.push({ x: idx + 1, y: avgPLPerTrade });
                    });

                    datasets.push({
                        label: `Run ${run.runNumber}`,
                        data: expectancyData,
                        borderColor: colors[i % colors.length],
                        backgroundColor: 'transparent',
                        borderWidth: 2,
                        tension: 0.1,
                        pointRadius: 0
                    });
                }
            }
        } catch (e) {
            console.error(`Failed to load run ${run.id}:`, e);
        }
    }

    if (datasets.length === 0) return;

    const ctx = document.getElementById('cumulativeExpectancyChart').getContext('2d');
    cumulativeExpectancyChart = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: {
                    position: 'top',
                    labels: { boxWidth: 12, padding: 10 }
                }
            },
            scales: {
                x: {
                    type: 'linear',
                    title: { display: true, text: 'Trade #' }
                },
                y: {
                    title: { display: true, text: 'Cumulative Avg P/L ($/trade)' }
                }
            }
        }
    });
}

async function renderStdDevChart(runs) {
    const stdDevs = [];

    for (let i = 0; i < runs.length; i++) {
        const run = runs[i];
        try {
            const response = await fetch(`${API_BASE}/experiments/runs/${run.id}`);
            if (response.ok) {
                const runDetail = await response.json();
                const trades = runDetail.trades;

                if (trades.length > 1) {
                    // Calculate standard deviation of P/L
                    const pls = trades.map(t => parseFloat(t.profitLoss));
                    const mean = pls.reduce((a, b) => a + b, 0) / pls.length;
                    const variance = pls.reduce((sum, pl) => sum + Math.pow(pl - mean, 2), 0) / pls.length;
                    const stdDev = Math.sqrt(variance);
                    stdDevs.push({ runNumber: run.runNumber, stdDev });
                } else {
                    stdDevs.push({ runNumber: run.runNumber, stdDev: 0 });
                }
            }
        } catch (e) {
            console.error(`Failed to load run ${run.id}:`, e);
        }
    }

    if (stdDevs.length === 0) return;

    const ctx = document.getElementById('stdDevChart').getContext('2d');
    stdDevChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: stdDevs.map(s => `Run ${s.runNumber}`),
            datasets: [{
                label: 'Std Dev ($)',
                data: stdDevs.map(s => s.stdDev),
                backgroundColor: 'rgba(139, 92, 246, 0.7)',
                borderColor: 'rgba(139, 92, 246, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    title: { display: true, text: 'Std Dev ($)' },
                    beginAtZero: true
                }
            }
        }
    });
}
