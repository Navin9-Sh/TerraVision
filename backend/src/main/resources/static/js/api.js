const API_BASE = '/api/v1';

/**
 * Attaches the JWT from the current session (if any) as a Bearer token. Deliberately
 * does NOT auto-redirect to login on a 401 here: /auth/login itself returns 401 for a
 * wrong password, and this function is used for that call too -- an automatic
 * redirect-to-login on 401 would misfire on the login page itself. Each caller
 * decides what a failure means for its own page instead.
 */
async function apiFetch(path, options = {}) {
    const session = getSession();
    const headers = Object.assign({}, options.headers || {});
    if (session) {
        headers['Authorization'] = `Bearer ${session.token}`;
    }

    const response = await fetch(API_BASE + path, { ...options, headers });

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
