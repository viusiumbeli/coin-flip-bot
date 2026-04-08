/**
 * Loading & Error State Utilities
 * Shared functions for managing loading spinners and error messages
 */

/**
 * Show loading indicator
 * @param {string} elementId - ID of the loading element to show
 * @param {boolean|string} showOrText - If boolean, show/hide. If string, custom loading text.
 */
function showLoading(elementId, showOrText) {
    const element = document.getElementById(elementId);
    if (!element) return;

    if (typeof showOrText === 'boolean') {
        element.style.display = showOrText ? 'block' : 'none';
    } else if (typeof showOrText === 'string') {
        element.style.display = 'block';
        const textEl = element.querySelector('p');
        if (textEl) {
            textEl.textContent = showOrText;
        }
    } else {
        element.style.display = 'block';
    }
}

/**
 * Hide loading indicator
 * @param {string} elementId - ID of the loading element to hide
 */
function hideLoading(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = 'none';
    }
}

/**
 * Show error message
 * @param {string} message - Error message to display
 * @param {string} elementId - ID of the error element (defaults to 'error')
 */
function showError(message, elementId = 'error') {
    const errorDiv = document.getElementById(elementId);
    if (errorDiv) {
        errorDiv.textContent = message;
        errorDiv.style.display = 'block';
        errorDiv.classList.remove('hidden');
    }
}

/**
 * Hide error message
 * @param {string} elementId - ID of the error element (defaults to 'error')
 */
function hideError(elementId = 'error') {
    const errorDiv = document.getElementById(elementId);
    if (errorDiv) {
        errorDiv.style.display = 'none';
        errorDiv.classList.add('hidden');
    }
}

/**
 * Show loading overlay (full-screen)
 * @param {string} text - Loading text to display
 */
function showLoadingOverlay(text) {
    const overlay = document.getElementById('loadingOverlay');
    const loadingText = document.getElementById('loadingText');

    if (overlay) {
        overlay.classList.add('show');
    }
    if (loadingText && text) {
        loadingText.textContent = text;
    }
}

/**
 * Hide loading overlay
 */
function hideLoadingOverlay() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) {
        overlay.classList.remove('show');
    }
}

/**
 * Set button loading state
 * @param {string} buttonId - ID of the button
 * @param {boolean} loading - Whether to show loading state
 * @param {string} loadingText - Text to show while loading (optional)
 */
function setButtonLoading(buttonId, loading, loadingText) {
    const button = document.getElementById(buttonId);
    if (!button) return;

    if (loading) {
        button.disabled = true;
        button.dataset.originalText = button.textContent;
        if (loadingText) {
            button.innerHTML = `<span class="spinner-small"></span>${loadingText}`;
        }
    } else {
        button.disabled = false;
        if (button.dataset.originalText) {
            button.textContent = button.dataset.originalText;
        }
    }
}
