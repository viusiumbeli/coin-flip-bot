/**
 * Header Component - Injects shared header and navigation into pages
 */

function initHeader(activePage) {
    const pages = {
        backtest: { url: 'index.html', label: 'Backtest' },
        simulation: { url: 'simulation.html', label: 'Simulation' },
        experiments: { url: 'experiments.html', label: 'Experiments' },
        live: { url: 'live.html', label: 'Live' },
        data: { url: 'data.html', label: 'Data' }
    };

    const subtitles = {
        backtest: 'Backtest System - Web Interface',
        simulation: 'Live Candle-by-Candle Simulation',
        experiments: 'Experiments - Run and Compare Multiple Backtests',
        live: 'Live Trading - Real-time Binance WebSocket Trading',
        data: 'Manage historical candle data from Binance'
    };

    const navLinks = Object.entries(pages)
        .map(([key, page]) =>
            `<a href="${page.url}" class="nav-link${key === activePage ? ' active' : ''}">${page.label}</a>`
        )
        .join('\n                ');

    const headerHTML = `
        <div class="header">
            <div class="header-top">
                <h1>Van Tharp Coin-Flip Trading Bot</h1>
                <button id="theme-toggle" class="theme-toggle" onclick="toggleTheme()"></button>
            </div>
            <p class="subtitle">${subtitles[activePage] || ''}</p>
            <div class="nav-tabs">
                ${navLinks}
            </div>
        </div>
    `;

    const headerContainer = document.getElementById('app-header');
    if (headerContainer) {
        headerContainer.innerHTML = headerHTML;
    }

    // Update theme icon after header is rendered
    updateThemeIcon();
}
