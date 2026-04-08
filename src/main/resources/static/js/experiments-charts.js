// Chart type registry
const CHART_TYPES = {
    returnDist: {
        title: 'Return Distribution Across Runs',
        render: renderReturnDistribution,
        needsRuns: true
    },
    returnHistogram: {
        title: 'Return Distribution (Histogram)',
        render: renderReturnHistogram,
        needsRuns: true
    },
    returnBoxPlot: {
        title: 'Return Distribution (Box Plot)',
        render: renderReturnBoxPlot,
        needsRuns: false
    },
    winLoss: {
        title: 'Win/Loss Ratio',
        render: renderWinLossPie,
        needsRuns: false
    },
    equity: {
        title: 'Equity Curves (All Runs)',
        render: renderEquityCurves,
        needsRuns: true,
        fullWidth: true
    },
    expectancyPerRun: {
        title: 'Expectancy Per Run ($/trade)',
        render: renderExpectancyPerRun,
        needsRuns: true
    },
    expectancyDist: {
        title: 'Expectancy Distribution',
        render: renderExpectancyDistribution,
        needsRuns: true
    },
    cumulativeExpectancy: {
        title: 'Cumulative Expectancy (All Runs)',
        render: renderCumulativeExpectancy,
        needsRuns: true,
        fullWidth: true
    },
    stdDev: {
        title: 'Std Dev of P/L Per Run',
        render: renderStdDevChart,
        needsRuns: true
    },
    beatBuyHold: {
        title: 'Strategy vs Buy & Hold',
        render: renderBeatBuyHoldPie,
        needsRuns: false
    }
};

let activeCharts = new Map();
let cachedRuns = null;

function toggleChartMenu() {
    const menu = document.getElementById('chartMenu');
    menu.classList.toggle('hidden');
}

// Close menu when clicking outside
document.addEventListener('click', function(e) {
    const dropdown = document.querySelector('.add-chart-dropdown');
    const menu = document.getElementById('chartMenu');
    if (dropdown && menu && !dropdown.contains(e.target)) {
        menu.classList.add('hidden');
    }
});

function addChart(type) {
    const chartInfo = CHART_TYPES[type];
    if (!chartInfo) return;

    const chartId = `chart_${type}_${Date.now()}`;

    // Create container
    const container = document.createElement('div');
    container.className = 'chart-container' + (chartInfo.fullWidth ? ' full-width' : '');
    container.id = chartId;
    container.innerHTML = `
        <button class="remove-chart" onclick="removeChart('${chartId}')">&times;</button>
        <h4>${chartInfo.title}</h4>
        <canvas id="canvas_${chartId}"></canvas>
    `;

    document.getElementById('chartsGrid').appendChild(container);

    // Render chart
    renderSingleChart(type, chartId);

    // Hide menu
    document.getElementById('chartMenu').classList.add('hidden');
}

function removeChart(chartId) {
    if (activeCharts.has(chartId)) {
        activeCharts.get(chartId).destroy();
        activeCharts.delete(chartId);
    }
    const container = document.getElementById(chartId);
    if (container) {
        container.remove();
    }
}

function clearAllCharts() {
    activeCharts.forEach((chart) => {
        chart.destroy();
    });
    activeCharts.clear();
    cachedRuns = null;
    const grid = document.getElementById('chartsGrid');
    if (grid) {
        grid.innerHTML = '';
    }
}

async function fetchAllRunSummaries(experimentId) {
    const allRuns = [];
    let page = 0;
    const size = 500;
    let totalPages = 1;

    while (page < totalPages) {
        const response = await fetch(`${API_BASE}/experiments/${experimentId}/runs?page=${page}&size=${size}`);
        if (!response.ok) break;
        const data = await response.json();
        allRuns.push(...data.runs);
        totalPages = data.totalPages;
        page++;
    }
    return allRuns;
}

async function renderSingleChart(type, chartId) {
    const chartInfo = CHART_TYPES[type];
    const canvasId = `canvas_${chartId}`;

    console.log('renderSingleChart:', { type, chartId, canvasId, needsRuns: chartInfo.needsRuns, currentExperiment: currentExperiment?.id });

    // Check for large experiment - skip for summaryOnly charts
    if (currentExperiment && currentExperiment.numBacktests > 1000 && chartInfo.needsRuns && !chartInfo.summaryOnly) {
        const container = document.getElementById(chartId);
        if (container) {
            container.innerHTML = `
                <button class="remove-chart" onclick="removeChart('${chartId}')">&times;</button>
                <h4>${chartInfo.title}</h4>
                <div style="display: flex; align-items: center; justify-content: center; height: 80%; color: #666; font-size: 14px; text-align: center;">
                    Charts disabled for experiments with &gt;1,000 runs
                </div>
            `;
        }
        return;
    }

    // Fetch runs if needed
    let runsData = cachedRuns;
    const needsFetch = chartInfo.needsRuns && (!runsData || runsData.length === 0);
    const isLargeExperiment = currentExperiment && currentExperiment.numBacktests > 1000;

    if (needsFetch) {
        try {
            if (chartInfo.summaryOnly && isLargeExperiment) {
                // Fetch all runs using pagination for summaryOnly charts
                console.log(`Fetching all runs for large experiment ${currentExperiment.id}...`);
                runsData = await fetchAllRunSummaries(currentExperiment.id);
                console.log(`Fetched ${runsData.length} runs`);
            } else {
                const response = await fetch(`${API_BASE}/experiments/${currentExperiment.id}/runs?page=0&size=1000`);
                if (response.ok) {
                    runsData = (await response.json()).runs;
                    cachedRuns = runsData;
                }
            }
        } catch (e) {
            console.error('Failed to fetch runs:', e);
            return;
        }
    }

    try {
        const dataToPass = chartInfo.needsRuns ? runsData : currentExperiment;
        console.log('renderSingleChart calling render with:', { type, dataToPass: dataToPass ? (Array.isArray(dataToPass) ? `${dataToPass.length} runs` : dataToPass) : 'null' });

        const chart = await chartInfo.render(
            canvasId,
            dataToPass
        );

        console.log('renderSingleChart result:', { type, chartCreated: !!chart });
        if (chart) {
            activeCharts.set(chartId, chart);
        }
    } catch (e) {
        console.error(`Failed to render chart ${type}:`, e);
    }
}

function renderReturnDistribution(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
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

function renderWinLossPie(canvasId, experiment) {
    if (!experiment) return null;

    const ctx = document.getElementById(canvasId).getContext('2d');
    const wins = experiment.winningTrades;
    const losses = experiment.losingTrades;
    const total = wins + losses;

    return new Chart(ctx, {
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

async function renderEquityCurves(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

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

    if (datasets.length === 0) return null;

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
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

async function renderExpectancyPerRun(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

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

    if (expectancies.length === 0) return null;

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
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

async function renderExpectancyDistribution(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

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

    if (expectancies.length === 0) return null;

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

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
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

async function renderCumulativeExpectancy(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

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

    if (datasets.length === 0) return null;

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
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

async function renderStdDevChart(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

    const stdDevs = [];

    for (let i = 0; i < runs.length; i++) {
        const run = runs[i];
        try {
            const response = await fetch(`${API_BASE}/experiments/runs/${run.id}`);
            if (response.ok) {
                const runDetail = await response.json();
                const trades = runDetail.trades;

                if (trades.length > 1) {
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

    if (stdDevs.length === 0) return null;

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
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

function renderBeatBuyHoldPie(canvasId, experiment) {
    if (!experiment) {
        console.error('renderBeatBuyHoldPie: no experiment data');
        return null;
    }

    const beat = experiment.runsBeatBuyHold ?? 0;
    const total = experiment.numBacktests ?? 0;
    const below = total - beat;
    const benchmark = parseFloat(experiment.buyAndHoldReturnPercent) || 0;

    console.log('renderBeatBuyHoldPie:', { beat, total, below, benchmark, runsBeatBuyHold: experiment.runsBeatBuyHold });

    // Don't render if no data
    if (total === 0) {
        console.warn('renderBeatBuyHoldPie: no backtests');
        return null;
    }

    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.error('renderBeatBuyHoldPie: canvas not found:', canvasId);
        return null;
    }

    const ctx = canvas.getContext('2d');
    return new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: [`Beat B&H (${beat})`, `Below B&H (${below})`],
            datasets: [{
                data: [beat, below],
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
                legend: { position: 'bottom' },
                title: {
                    display: true,
                    text: `B&H Return: ${benchmark.toFixed(2)}%`
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

function renderReturnHistogram(canvasId, runs) {
    if (!runs || runs.length === 0) return null;

    const returns = runs.map(r => parseFloat(r.totalReturnPercent));
    const min = Math.min(...returns);
    const max = Math.max(...returns);
    const range = max - min;
    const binCount = Math.min(12, Math.max(5, Math.ceil(runs.length / 3)));
    const binSize = range / binCount || 1;

    // Create bins
    const bins = Array(binCount).fill(0);
    returns.forEach(r => {
        const binIndex = Math.min(Math.floor((r - min) / binSize), binCount - 1);
        bins[binIndex]++;
    });

    // Labels for bins
    const labels = bins.map((_, i) => {
        const start = min + i * binSize;
        const end = start + binSize;
        return `${start.toFixed(0)}–${end.toFixed(0)}%`;
    });

    // Calculate mean and median for annotations
    const mean = returns.reduce((a, b) => a + b, 0) / returns.length;
    const sortedReturns = [...returns].sort((a, b) => a - b);
    const median = sortedReturns.length % 2 === 0
        ? (sortedReturns[sortedReturns.length / 2 - 1] + sortedReturns[sortedReturns.length / 2]) / 2
        : sortedReturns[Math.floor(sortedReturns.length / 2)];

    // Find bin index for mean and median
    const meanBinIndex = Math.min(Math.floor((mean - min) / binSize), binCount - 1);
    const medianBinIndex = Math.min(Math.floor((median - min) / binSize), binCount - 1);

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Number of Runs',
                data: bins,
                backgroundColor: 'rgba(59, 130, 246, 0.7)',
                borderColor: 'rgba(59, 130, 246, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false },
                annotation: {
                    annotations: {
                        meanLine: {
                            type: 'line',
                            xMin: meanBinIndex,
                            xMax: meanBinIndex,
                            borderColor: 'rgba(239, 68, 68, 0.8)',
                            borderWidth: 2,
                            borderDash: [5, 5],
                            label: {
                                display: true,
                                content: `Mean: ${mean.toFixed(1)}%`,
                                position: 'start',
                                backgroundColor: 'rgba(239, 68, 68, 0.8)',
                                color: 'white',
                                font: { size: 11 }
                            }
                        },
                        medianLine: {
                            type: 'line',
                            xMin: medianBinIndex + 0.2,
                            xMax: medianBinIndex + 0.2,
                            borderColor: 'rgba(16, 185, 129, 0.8)',
                            borderWidth: 2,
                            borderDash: [5, 5],
                            label: {
                                display: true,
                                content: `Median: ${median.toFixed(1)}%`,
                                position: 'end',
                                backgroundColor: 'rgba(16, 185, 129, 0.8)',
                                color: 'white',
                                font: { size: 11 }
                            }
                        }
                    }
                }
            },
            scales: {
                x: {
                    title: { display: true, text: 'Return Range' }
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

function renderReturnBoxPlot(canvasId, experiment) {
    if (!experiment) return null;

    const p5 = parseFloat(experiment.returnP5) || 0;
    const p25 = parseFloat(experiment.returnP25) || 0;
    const p50 = parseFloat(experiment.returnP50) || 0;
    const p75 = parseFloat(experiment.returnP75) || 0;
    const p95 = parseFloat(experiment.returnP95) || 0;
    const minVal = parseFloat(experiment.returnMin) || 0;
    const maxVal = parseFloat(experiment.returnMax) || 0;
    const mean = parseFloat(experiment.totalReturnPercent) || 0;

    // Calculate chart range with padding
    const dataMin = Math.min(minVal, p5) - 10;
    const dataMax = Math.max(maxVal, p95) + 10;

    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
        type: 'bar',
        data: {
            labels: ['Return %'],
            datasets: [
                // Left whisker (p5 to p25)
                {
                    label: 'P5-P25',
                    data: [[p5, p25]],
                    backgroundColor: 'rgba(148, 163, 184, 0.5)',
                    borderColor: 'rgba(100, 116, 139, 1)',
                    borderWidth: 1,
                    barPercentage: 0.3
                },
                // Box (p25 to p75)
                {
                    label: 'P25-P75 (IQR)',
                    data: [[p25, p75]],
                    backgroundColor: 'rgba(59, 130, 246, 0.7)',
                    borderColor: 'rgba(37, 99, 235, 1)',
                    borderWidth: 2,
                    barPercentage: 0.5
                },
                // Right whisker (p75 to p95)
                {
                    label: 'P75-P95',
                    data: [[p75, p95]],
                    backgroundColor: 'rgba(148, 163, 184, 0.5)',
                    borderColor: 'rgba(100, 116, 139, 1)',
                    borderWidth: 1,
                    barPercentage: 0.3
                }
            ]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const data = context.raw;
                            return `${context.dataset.label}: ${data[0].toFixed(1)}% – ${data[1].toFixed(1)}%`;
                        }
                    }
                },
                annotation: {
                    annotations: {
                        medianLine: {
                            type: 'line',
                            xMin: p50,
                            xMax: p50,
                            borderColor: 'rgba(234, 179, 8, 1)',
                            borderWidth: 3,
                            label: {
                                display: true,
                                content: `Median: ${p50.toFixed(1)}%`,
                                position: 'start',
                                backgroundColor: 'rgba(234, 179, 8, 0.9)',
                                color: 'black',
                                font: { size: 11, weight: 'bold' }
                            }
                        },
                        meanLine: {
                            type: 'line',
                            xMin: mean,
                            xMax: mean,
                            borderColor: 'rgba(239, 68, 68, 0.8)',
                            borderWidth: 2,
                            borderDash: [5, 5],
                            label: {
                                display: true,
                                content: `Mean: ${mean.toFixed(1)}%`,
                                position: 'end',
                                backgroundColor: 'rgba(239, 68, 68, 0.8)',
                                color: 'white',
                                font: { size: 11 }
                            }
                        },
                        minPoint: {
                            type: 'point',
                            xValue: minVal,
                            yValue: 0,
                            backgroundColor: 'rgba(239, 68, 68, 1)',
                            radius: 6,
                            borderWidth: 2,
                            borderColor: 'white'
                        },
                        maxPoint: {
                            type: 'point',
                            xValue: maxVal,
                            yValue: 0,
                            backgroundColor: 'rgba(16, 185, 129, 1)',
                            radius: 6,
                            borderWidth: 2,
                            borderColor: 'white'
                        },
                        minLabel: {
                            type: 'label',
                            xValue: minVal,
                            yValue: 0,
                            yAdjust: 25,
                            content: `Min: ${minVal.toFixed(1)}%`,
                            backgroundColor: 'rgba(239, 68, 68, 0.8)',
                            color: 'white',
                            font: { size: 10 }
                        },
                        maxLabel: {
                            type: 'label',
                            xValue: maxVal,
                            yValue: 0,
                            yAdjust: 25,
                            content: `Max: ${maxVal.toFixed(1)}%`,
                            backgroundColor: 'rgba(16, 185, 129, 0.8)',
                            color: 'white',
                            font: { size: 10 }
                        }
                    }
                }
            },
            scales: {
                x: {
                    min: dataMin,
                    max: dataMax,
                    title: { display: true, text: 'Return %' }
                },
                y: {
                    display: false
                }
            }
        }
    });
}
