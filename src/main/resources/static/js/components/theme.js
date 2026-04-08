/**
 * Theme Component - Handles dark/light theme switching with localStorage persistence
 */

function initTheme() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('theme', next);
    updateThemeIcon();
}

function updateThemeIcon() {
    const btn = document.getElementById('theme-toggle');
    if (btn) {
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        btn.textContent = isDark ? '\u2600\uFE0F' : '\uD83C\uDF19';
        btn.title = isDark ? 'Switch to light mode' : 'Switch to dark mode';
    }
}

// Apply theme immediately to prevent flash
initTheme();
