let currentPage = 0;
const pageSize = 10;

document.getElementById('prevIcon').innerHTML = ICONS.chevronLeft;
document.getElementById('nextIcon').innerHTML = ICONS.chevronRight;

document.getElementById('applyFiltersBtn').addEventListener('click', () => {
    currentPage = 0;
    loadHistory();
});
document.getElementById('resetFiltersBtn').addEventListener('click', () => {
    document.getElementById('filterClass').value = '';
    document.getElementById('filterFrom').value = '';
    document.getElementById('filterTo').value = '';
    document.getElementById('filterLowConfidence').checked = false;
    currentPage = 0;
    loadHistory();
});
document.getElementById('prevPageBtn').addEventListener('click', () => {
    if (currentPage > 0) {
        currentPage -= 1;
        loadHistory();
    }
});
document.getElementById('nextPageBtn').addEventListener('click', () => {
    currentPage += 1;
    loadHistory();
});

function buildHistoryQuery() {
    const params = new URLSearchParams();
    params.set('page', currentPage);
    params.set('size', pageSize);
    params.set('sort', 'createdAt,desc');

    const predictedClass = document.getElementById('filterClass').value;
    if (predictedClass) params.set('predictedClass', predictedClass);

    const from = document.getElementById('filterFrom').value;
    if (from) params.set('from', `${from}T00:00:00Z`);

    const to = document.getElementById('filterTo').value;
    if (to) params.set('to', `${to}T23:59:59Z`);

    if (document.getElementById('filterLowConfidence').checked) {
        params.set('lowConfidenceOnly', 'true');
    }

    return params.toString();
}

async function loadStats() {
    try {
        const stats = await apiFetch('/stats');

        document.getElementById('statsRow').innerHTML = `
            <div class="stat-card"><span class="stat-number">${stats.totalPredictions}</span><span class="stat-label">Total predictions</span></div>
            <div class="stat-card"><span class="stat-number">${formatPercent(stats.averageConfidence)}</span><span class="stat-label">Avg. confidence</span></div>
            <div class="stat-card"><span class="stat-number">${formatPercent(stats.lowConfidenceRate * 100)}</span><span class="stat-label">Low-confidence rate</span></div>
            <div class="stat-card"><span class="stat-number">${Math.round(stats.averageInferenceTimeMs)} ms</span><span class="stat-label">Avg. inference time</span></div>
        `;

        if (stats.totalPredictions > 0) {
            document.getElementById('chartsRow').style.display = 'grid';
            renderClassDistribution(stats.classCounts);
            renderConfidenceOverview(stats.averageConfidence, stats.lowConfidenceRate * 100);
        }
    } catch (err) {
        document.getElementById('statsRow').innerHTML =
            `<div class="alert alert-error" style="grid-column: 1 / -1;">${err.message}</div>`;
    }
}

function renderClassDistribution(classCounts) {
    const entries = Object.entries(classCounts);
    const container = document.getElementById('classDistributionChart');
    const max = Math.max(...entries.map(([, count]) => count));

    container.innerHTML = entries
        .sort((a, b) => b[1] - a[1])
        .map(([className, count]) => `
            <div class="bar-row">
                <span class="bar-label">${className}</span>
                <div class="bar-track"><div class="bar-fill" style="width:${(count / max) * 100}%"></div></div>
                <span class="bar-pct">${count}</span>
            </div>
        `).join('');
}

function renderConfidenceOverview(averageConfidence, lowConfidenceRatePercent) {
    document.getElementById('confidenceOverviewChart').innerHTML = `
        <div class="bar-row">
            <span class="bar-label">Average confidence</span>
            <div class="bar-track"><div class="bar-fill" style="width:${averageConfidence}%"></div></div>
            <span class="bar-pct">${formatPercent(averageConfidence)}</span>
        </div>
        <div class="bar-row">
            <span class="bar-label">Low-confidence rate</span>
            <div class="bar-track"><div class="bar-fill" style="width:${lowConfidenceRatePercent}%; background:var(--color-warning);"></div></div>
            <span class="bar-pct">${formatPercent(lowConfidenceRatePercent)}</span>
        </div>
    `;
}

async function loadHistory() {
    const wrapper = document.getElementById('tableWrapper');
    const pagination = document.getElementById('pagination');

    try {
        const page = await apiFetch(`/history?${buildHistoryQuery()}`);

        if (!page.content || page.content.length === 0) {
            wrapper.innerHTML = `
                <div class="table-card">
                    <div class="empty-state">
                        <span id="emptyIcon"></span>
                        <div class="empty-title">No predictions yet</div>
                        <div class="empty-hint">Classify an image on the Classify page and it'll show up here.</div>
                    </div>
                </div>
            `;
            document.getElementById('emptyIcon').innerHTML = ICONS.tiles;
            pagination.style.display = 'none';
            return;
        }

        const rows = page.content.map(item => `
            <tr>
                <td class="mono">${formatDateTime(item.createdAt)}</td>
                <td>${item.filename}</td>
                <td>${item.predictedClass}</td>
                <td class="numeric">${formatPercent(item.confidence)}</td>
                <td>${item.lowConfidence
                    ? '<span class="badge badge-low">Low confidence</span>'
                    : '<span class="badge badge-ok">OK</span>'}</td>
                <td class="numeric">${item.inferenceTimeMs} ms</td>
            </tr>
        `).join('');

        wrapper.innerHTML = `
            <div class="table-card">
                <table class="data-table">
                    <thead>
                        <tr><th>Time</th><th>Filename</th><th>Class</th><th>Confidence</th><th>Status</th><th>Inference</th></tr>
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

loadStats();
loadHistory();
