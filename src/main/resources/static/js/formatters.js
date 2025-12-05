/**
 * Shared number formatting utilities
 */

/**
 * Format a number with space as thousands separator and period as decimal separator
 * @param {number} num - The number to format
 * @returns {string} Formatted number (e.g., "2 227.41")
 */
function formatNumber(num) {
    // Format with space as thousand separator and period as decimal separator
    // e.g., "2 227.41" instead of "2,227.41" or "2 227,41"
    const fixed = num.toFixed(2);
    const [integer, decimal] = fixed.split('.');
    const formatted = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    return decimal ? `${formatted}.${decimal}` : formatted;
}

/**
 * Format position size with full precision (8 decimal places)
 * @param {number} num - The position size to format
 * @returns {string} Formatted number with 8 decimals (e.g., "0.33333333")
 */
function formatSize(num) {
    const fixed = num.toFixed(8);
    const [integer, decimal] = fixed.split('.');
    const formatted = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    return decimal ? `${formatted}.${decimal}` : formatted;
}

/**
 * Convert datetime-local input value to UTC ISO string for API
 * Treats the input as already being in UTC (appends Z suffix)
 * @param {string} datetimeLocalValue - Value from datetime-local input (e.g., "2024-01-01T00:00")
 * @returns {string|null} ISO string with Z suffix (e.g., "2024-01-01T00:00:00.000Z")
 */
function toUTCISOString(datetimeLocalValue) {
    if (!datetimeLocalValue) return null;
    return datetimeLocalValue + ':00.000Z';
}

/**
 * Format ISO date string for display (date only, UTC)
 * @param {string} isoString - ISO date string from backend
 * @returns {string} Formatted date (e.g., "2024-01-01")
 */
function formatDate(isoString) {
    if (!isoString) return '-';
    return isoString.slice(0, 10);
}

/**
 * Format ISO datetime string for display (UTC)
 * @param {string} isoString - ISO datetime string from backend
 * @returns {string} Formatted datetime (e.g., "2024-01-01 12:30:45")
 */
function formatDateTime(isoString) {
    if (!isoString) return '-';
    return isoString.replace('T', ' ').slice(0, 19);
}
