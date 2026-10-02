const API_BASE = '/api/v1';

async function apiFetch(path, options = {}) {
    const isAuthPath = path.startsWith('/auth/');
    const { _retried, ...fetchOptions } = options;
    const headers = Object.assign({}, fetchOptions.headers || {});

    if (!isAuthPath) {
        const token = await getValidAccessToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        } else if (getSession() === null) {
            handleUnauthorized();
            throw Object.assign(new Error('Your session has ended. Please log in again.'), { status: 401 });
        }
    }

    const response = await fetch(API_BASE + path, { ...fetchOptions, headers });

    if (response.status === 401 && !isAuthPath && !_retried) {
        const renewed = await refreshSession();
        if (renewed) {
            return apiFetch(path, { ...options, _retried: true });
        }
    }

    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    if (response.status === 401 && !path.startsWith('/auth/')) {
        handleUnauthorized();
    }

    if (!response.ok) {
        const message = (body && body.message) || `Request failed (${response.status})`;
        const error = new Error(message);
        error.status = response.status;
        throw error;
    }

    return body;
}

function formatPercent(value) {
    return `${value.toFixed(1)}%`;
}

function formatDateTime(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString();
}
