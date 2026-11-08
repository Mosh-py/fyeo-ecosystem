let countdownInterval = null;

async function protectDocument(key, mode = 'STATIC') {
    const formData = new FormData();
    formData.append('key', key);
    formData.append('mode', mode);

    try {
        const resp = await fetch('/api/simulator/protect', {
            method: 'POST',
            body: formData
        });
        const data = await resp.json();
        if (data.success) {
            window.location.reload();
        } else {
            alert('Failed to scramble document: ' + (data.error || 'Unknown error'));
        }
    } catch (err) {
        alert('Network error: ' + err.message);
    }
}

async function inspectDocument(key) {
    const formData = new FormData();
    formData.append('key', key);

    try {
        const resp = await fetch('/api/simulator/ephemeral-inspect', {
            method: 'POST',
            body: formData
        });
        const data = await resp.json();
        if (data.success) {
            openReviewModal(data);
        } else {
            alert('Inspection failed: ' + data.error);
        }
    } catch (err) {
        alert('Inspection network error: ' + err.message);
    }
}

async function resetS3Bucket() {
    try {
        const resp = await fetch('/api/simulator/reset', { method: 'POST' });
        const data = await resp.json();
        if (data.success) {
            window.location.reload();
        }
    } catch (err) {
        alert('Reset failed: ' + err.message);
    }
}

function openReviewModal(payload) {
    const modal = document.getElementById('reviewModal');
    const img = document.getElementById('ephemeralImg');
    const timer = document.getElementById('countdownTimer');
    const metaFile = document.getElementById('metaFilename');
    const metaCust = document.getElementById('metaCustomer');
    const metaId = document.getElementById('metaIdNumber');

    img.src = payload.imageData;
    metaFile.textContent = payload.originalFilename || 'UNKNOWN';
    metaCust.textContent = payload.customerName || 'UNKNOWN';
    metaId.textContent = payload.idNumber || 'KYC_DOCUMENT';

    modal.classList.add('active');

    let remaining = payload.viewTtlSeconds || 15;
    timer.textContent = `${remaining} SEC REMAINING`;

    if (countdownInterval) clearInterval(countdownInterval);

    countdownInterval = setInterval(() => {
        remaining--;
        if (remaining <= 0) {
            clearInterval(countdownInterval);
            burnEphemeralMemory();
        } else {
            timer.textContent = `${remaining} SEC REMAINING`;
        }
    }, 1000);
}

function burnEphemeralMemory() {
    const img = document.getElementById('ephemeralImg');
    const metaFile = document.getElementById('metaFilename');
    const metaCust = document.getElementById('metaCustomer');
    const metaId = document.getElementById('metaIdNumber');

    // Wipe memory
    img.src = '';
    metaFile.textContent = '--';
    metaCust.textContent = '--';
    metaId.textContent = '--';

    closeReviewModal();
    alert('🔥 AUTO-BURN EXECUTED: Volatile RAM container wiped. Plaintext expired.');
}

function closeReviewModal() {
    const modal = document.getElementById('reviewModal');
    const img = document.getElementById('ephemeralImg');
    const metaFile = document.getElementById('metaFilename');
    const metaCust = document.getElementById('metaCustomer');
    const metaId = document.getElementById('metaIdNumber');

    if (countdownInterval) clearInterval(countdownInterval);
    img.src = '';
    if (metaFile) metaFile.textContent = '--';
    if (metaCust) metaCust.textContent = '--';
    if (metaId) metaId.textContent = '--';
    modal.classList.remove('active');
}

function triggerBreachModal() {
    document.getElementById('breachModal').classList.add('active');
}

function closeBreachModal() {
    document.getElementById('breachModal').classList.remove('active');
}
