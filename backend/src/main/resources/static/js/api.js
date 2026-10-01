const API_BASE = '/api/v1';

/**
 * Attaches the JWT from the current session (if any) as a Bearer token. Deliberately
 * does NOT auto-redirect to login on a 401 here: /auth/login itself returns 401 for a
 * wrong password, and this function is used for that call too -- an automatic
 * redirect-to-login on 401 would misfire on the login page itself. Each caller
 * decides what a failure means for its own page instead.
 */
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

    // Access token rejected despite looking valid (e.g. revoked server-side): renew once
    // and retry before giving up.
    if (response.status === 401 && !isAuthPath && !_retried) {
        const renewed = await refreshSession();
        if (renewed) {
            return apiFetch(path, { ...options, _retried: true });
        }
    }

    // Read as text first and parse only if non-empty: some endpoints correctly
    // return an empty body on success (e.g. 201 from /auth/register) or on error,
    // and calling response.json() directly on an empty body throws "Unexpected end
    // of JSON input" instead of the real error/result.
    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    // A 401 anywhere except the /auth/* endpoints (where it means "wrong password")
    // means the stored token is expired or rejected: log out instead of leaving the
    // user on a page whose data can no longer load.
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
