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
