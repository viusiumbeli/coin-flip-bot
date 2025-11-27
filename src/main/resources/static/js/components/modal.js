/**
 * Modal Component - Reusable confirmation modal
 */

let confirmModalCallback = null;

function initModal() {
    const modalHTML = `
        <div id="confirmModal" class="confirm-modal-overlay">
            <div class="confirm-modal">
                <div class="confirm-modal-header" id="confirmModalHeader">
                    <span class="confirm-modal-icon">!</span>
                    <span id="confirmModalTitle">Confirm</span>
                </div>
                <div class="confirm-modal-body" id="confirmModalMessage"></div>
                <div class="confirm-modal-actions">
                    <button class="btn-secondary" onclick="closeConfirmModal()">Cancel</button>
                    <button class="btn-danger" id="confirmModalAction">Confirm</button>
                </div>
            </div>
        </div>
    `;
    document.body.insertAdjacentHTML('beforeend', modalHTML);
}

function showConfirmModal(title, message, confirmText, onConfirm, isDanger = true) {
    const modal = document.getElementById('confirmModal');
    const header = document.getElementById('confirmModalHeader');
    const titleEl = document.getElementById('confirmModalTitle');
    const messageEl = document.getElementById('confirmModalMessage');
    const actionBtn = document.getElementById('confirmModalAction');

    titleEl.textContent = title;
    messageEl.textContent = message;
    actionBtn.textContent = confirmText;

    // Set header style based on danger level
    header.classList.toggle('warning', !isDanger);
    actionBtn.className = isDanger ? 'btn-danger' : 'btn-warning';

    confirmModalCallback = onConfirm;

    // Show modal with animation
    modal.style.display = 'flex';
    requestAnimationFrame(() => {
        modal.classList.add('show');
    });

    // Handle confirm button click
    actionBtn.onclick = () => {
        const callback = confirmModalCallback;
        closeConfirmModal();
        if (callback) {
            callback();
        }
    };
}

function closeConfirmModal() {
    const modal = document.getElementById('confirmModal');
    modal.classList.remove('show');
    setTimeout(() => {
        modal.style.display = 'none';
    }, 200);
    confirmModalCallback = null;
}

// Auto-initialize when DOM is ready
document.addEventListener('DOMContentLoaded', initModal);
