let currentPage = 0;
const pageSize = 10;

document.getElementById('prevIcon').innerHTML = ICONS.chevronLeft;
document.getElementById('nextIcon').innerHTML = ICONS.chevronRight;

document.getElementById('applyFiltersBtn').addEventListener('click', () => {
    currentPage = 0;
    loadPredictions();
});
document.getElementById('resetFiltersBtn').addEventListener('click', () => {
    document.getElementById('filterUserId').value = '';
    document.getElementById('filterClass').value = '';
    document.getElementById('filterLowConfidence').checked = false;
    currentPage = 0;
    loadPredictions();
});
document.getElementById('prevPageBtn').addEventListener('click', () => {
    if (currentPage > 0) {
        currentPage -= 1;
        loadPredictions();
    }
});
document.getElementById('nextPageBtn').addEventListener('click', () => {
    currentPage += 1;
    loadPredictions();
});

async function loadUsers() {
    const wrapper = document.getElementById('usersWrapper');
    try {
        const page = await apiFetch('/admin/users?size=100');

        if (!page.content || page.content.length === 0) {
            wrapper.innerHTML = `
                <div class="empty-state">
                    <span id="usersEmptyIcon"></span>
                    <div class="empty-title">No registered users</div>
                </div>`;
            document.getElementById('usersEmptyIcon').innerHTML = ICONS.tiles;
            return;
        }

        const rows = page.content.map(u => `
            <tr>
                <td class="numeric">${u.id}</td>
                <td>${u.email}</td>
                <td>${u.emailVerified
                    ? '<span class="badge badge-ok">Verified</span>'
                    : '<span class="badge badge-low">Unverified</span>'}</td>
                <td>${u.role === 'ADMIN' ? '<span class="badge badge-admin">Admin</span>' : 'User'}</td>
                <td class="mono">${formatDateTime(u.createdAt)}</td>
                <td class="numeric">${u.predictionCount}</td>
            </tr>
        `).join('');

        wrapper.innerHTML = `
            <div class="table-card">
                <table class="data-table">
                    <thead>
                        <tr><th>ID</th><th>Email</th><th>Status</th><th>Role</th><th>Joined</th><th>Predictions</th></tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        `;
    } catch (err) {
        wrapper.innerHTML = `<div class="alert alert-error">${err.message}</div>`;
    }
}

function buildPredictionsQuery() {
    const params = new URLSearchParams();
    params.set('page', currentPage);
    params.set('size', pageSize);
    params.set('sort', 'createdAt,desc');

    const userId = document.getElementById('filterUserId').value;
    if (userId) params.set('userId', userId);

    const predictedClass = document.getElementById('filterClass').value;
    if (predictedClass) params.set('predictedClass', predictedClass);

    if (document.getElementById('filterLowConfidence').checked) {
        params.set('lowConfidenceOnly', 'true');
    }

    return params.toString();
}

async function loadPredictions() {
    const wrapper = document.getElementById('predictionsWrapper');
    const pagination = document.getElementById('pagination');

    try {
        const page = await apiFetch(`/admin/predictions?${buildPredictionsQuery()}`);

        if (!page.content || page.content.length === 0) {
            wrapper.innerHTML = `
                <div class="empty-state">
                    <span id="predictionsEmptyIcon"></span>
                    <div class="empty-title">No predictions match these filters</div>
                </div>`;
            document.getElementById('predictionsEmptyIcon').innerHTML = ICONS.tiles;
            pagination.style.display = 'none';
            return;
        }

        const rows = page.content.map(p => `
            <tr>
                <td class="mono">${formatDateTime(p.createdAt)}</td>
                <td>${p.ownerEmail || '<span style="color:var(--color-ink-faint);">(no account)</span>'}</td>
                <td>${p.predictedClass}</td>
                <td class="numeric">${formatPercent(p.confidence)}</td>
                <td>${p.lowConfidence
                    ? '<span class="badge badge-low">Low confidence</span>'
                    : '<span class="badge badge-ok">OK</span>'}</td>
                <td class="numeric">${p.inferenceTimeMs} ms</td>
            </tr>
        `).join('');

        wrapper.innerHTML = `
            <div class="table-card">
                <table class="data-table">
                    <thead>
                        <tr><th>Time</th><th>Owner</th><th>Class</th><th>Confidence</th><th>Status</th><th>Inference</th></tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        `;

        pagination.style.display = 'flex';
        document.getElementById('pageIndicator').textContent = `Page ${page.number + 1} of ${Math.max(page.totalPages, 1)}`;
        document.getElementById('prevPageBtn').disabled = page.number <= 0;
        document.getElementById('nextPageBtn').disabled = page.number + 1 >= page.totalPages;
    } catch (err) {
        wrapper.innerHTML = `<div class="alert alert-error">${err.message}</div>`;
        pagination.style.display = 'none';
    }
}

loadUsers();
loadPredictions();
