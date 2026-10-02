const AUTH_STORAGE_KEY = 'terravision.auth';
const USER_LOGIN_PAGE = 'login.html';
const ADMIN_LOGIN_PAGE = 'login.html?intent=admin';

function saveSession(token, email, role, expiresInSeconds, refreshToken, refreshExpiresInSeconds) {
    const now = Date.now();
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({
        token,
        email,
        role,
        expiresAt: now + expiresInSeconds * 1000,
        refreshToken: refreshToken || null,
        refreshExpiresAt: refreshToken ? now + refreshExpiresInSeconds * 1000 : null,
    }));
}

function clearSession() {
    try {
        localStorage.removeItem(AUTH_STORAGE_KEY);
        sessionStorage.removeItem(AUTH_STORAGE_KEY);
    } catch (e) {
    }
}

function getSession() {
    try {
        const raw = localStorage.getItem(AUTH_STORAGE_KEY);
        if (!raw) return null;
        const session = JSON.parse(raw);
        const looksLikeJwt = typeof session.token === 'string' && session.token.split('.').length === 3;
        const sessionEndsAt = session.refreshExpiresAt || session.expiresAt;
        if (!looksLikeJwt || !session.email || !session.role || !(Date.now() < sessionEndsAt)) {
            clearSession();
            return null;
        }
        return session;
    } catch (e) {
        return null;
    }
}

function isLoggedIn() {
    return getSession() !== null;
}

function homePageFor(role) {
    return role === 'ADMIN' ? 'admin.html' : 'classify.html';
}

function logout() {
    const session = getSession();
    clearSession();
    if (session && session.refreshToken) {
        fetch('/api/v1/auth/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: session.refreshToken }),
            keepalive: true,
        }).catch(() => {});
    }
    window.location.replace(session && session.role === 'ADMIN' ? ADMIN_LOGIN_PAGE : USER_LOGIN_PAGE);
}

let refreshInFlight = null;

function refreshSession() {
    if (refreshInFlight) return refreshInFlight;

    const run = async () => {
        const current = getSession();
        if (!current || !current.refreshToken) return null;
        if (Date.now() < current.expiresAt - 30000) return current;

        try {
            const response = await fetch('/api/v1/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: current.refreshToken }),
            });
            if (!response.ok) {
                if (response.status === 401 || response.status === 400) clearSession();
                return null;
            }
            const r = await response.json();
            saveSession(r.token, r.email, r.role, r.expiresInSeconds, r.refreshToken, r.refreshExpiresInSeconds);
            return getSession();
        } catch (e) {
            return null;
        }
    };

    const locked = navigator.locks ? navigator.locks.request('terravision-refresh', run) : run();
    refreshInFlight = locked.finally(() => { refreshInFlight = null; });
    return refreshInFlight;
}

async function getValidAccessToken() {
    const session = getSession();
    if (!session) return null;
    if (!session.refreshToken || Date.now() < session.expiresAt - 30000) return session.token;
    const renewed = await refreshSession();
    return renewed ? renewed.token : null;
}

function handleUnauthorized() {
    clearSession();
    window.location.replace(USER_LOGIN_PAGE);
}

function initProtectedPage(activePage, { admin = false } = {}) {
    const loginPage = admin ? ADMIN_LOGIN_PAGE : USER_LOGIN_PAGE;

    function allowed() {
        const session = getSession();
        if (!session) {
            document.documentElement.style.visibility = 'hidden';
            window.location.replace(loginPage);
            return false;
        }
        if (admin && session.role !== 'ADMIN') {
            document.documentElement.style.visibility = 'hidden';
            window.location.replace('classify.html');
            return false;
        }
        return true;
    }

    if (!allowed()) return;
    renderNav(activePage);

    window.addEventListener('pageshow', event => {
        if (allowed() && event.persisted) renderNav(activePage);
    });
    window.addEventListener('storage', event => {
        if (event.key === AUTH_STORAGE_KEY || event.key === null) allowed();
    });
}

function redirectIfAlreadyLoggedIn() {
    const go = () => {
        const session = getSession();
        if (session) window.location.replace(homePageFor(session.role));
    };
    go();
    window.addEventListener('pageshow', go);
}

function escapeHtml(value) {
    const entities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value).replace(/[&<>"']/g, c => entities[c]);
}

let navGlobalListenersBound = false;

function renderNav(activePage) {
    const session = getSession();
    const container = document.getElementById('topbar');
    if (!container || !session) return;

    const isAdmin = session.role === 'ADMIN';
    const links = [
        { href: 'classify.html', label: 'Classify', key: 'classify' },
        { href: 'my-history.html', label: 'My History', key: 'history' },
        { href: 'pune-map.html', label: 'Pune Map', key: 'pune-map' },
        { href: 'model-info.html', label: 'Model Info', key: 'model-info' },
        { href: 'about.html', label: 'About', key: 'about' },
    ];
    if (isAdmin) {
        links.push({ href: 'admin.html', label: 'Admin', key: 'admin' });
    }

    const linksHtml = links
        .map(l => `<a href="${l.href}" class="${l.key === activePage ? 'active' : ''}">${l.label}</a>`)
        .join('');

    const email = escapeHtml(session.email);
    const initial = escapeHtml(session.email.trim().charAt(0).toUpperCase() || '?');
    const roleBadge = isAdmin
        ? '<span class="badge badge-admin">Admin</span>'
        : '<span class="badge badge-user">User</span>';

    container.innerHTML = `
        <div class="topbar-inner">
            <a href="${homePageFor(session.role)}" class="brand">
                <span class="brand-logo">${ICONS.logo}</span>
                <span class="brand-name">TerraVision</span>
            </a>
            <button id="navToggle" class="nav-toggle" type="button" aria-label="Toggle menu"
                    aria-expanded="false" aria-controls="navLinks">${ICONS.menu}</button>
            <nav class="nav-links" id="navLinks">${linksHtml}</nav>
            <div class="user-menu" id="userMenu">
                <button id="userTrigger" class="user-trigger" type="button" aria-haspopup="true" aria-expanded="false">
                    <span class="avatar">${initial}</span>
                    <span class="user-email" title="${email}">${email}</span>
                    ${isAdmin ? '<span class="badge badge-admin user-trigger-badge">Admin</span>' : ''}
                    <span class="user-chevron">${ICONS.chevronDown}</span>
                </button>
                <div class="user-dropdown" id="userDropdown" hidden>
                    <div class="user-dropdown-head">
                        <span class="avatar avatar-lg">${initial}</span>
                        <div class="user-dropdown-id">
                            <span class="user-dropdown-email" title="${email}">${email}</span>
                            ${roleBadge}
                        </div>
                    </div>
                    <button id="logoutBtn" class="user-dropdown-item" type="button">
                        <span class="user-dropdown-icon">${ICONS.logout}</span> Log out
                    </button>
                </div>
            </div>
        </div>
    `;

    const trigger = document.getElementById('userTrigger');
    const dropdown = document.getElementById('userDropdown');
    const navToggle = document.getElementById('navToggle');

    trigger.addEventListener('click', e => {
        e.stopPropagation();
        dropdown.hidden = !dropdown.hidden;
        trigger.setAttribute('aria-expanded', String(!dropdown.hidden));
    });
    navToggle.addEventListener('click', e => {
        e.stopPropagation();
        const open = !container.classList.contains('nav-open');
        container.classList.toggle('nav-open', open);
        navToggle.setAttribute('aria-expanded', String(open));
    });
    document.getElementById('logoutBtn').addEventListener('click', logout);

    if (!navGlobalListenersBound) {
        navGlobalListenersBound = true;
        document.addEventListener('click', e => {
            const menu = document.getElementById('userMenu');
            const dd = document.getElementById('userDropdown');
            if (menu && dd && !menu.contains(e.target)) {
                dd.hidden = true;
                document.getElementById('userTrigger').setAttribute('aria-expanded', 'false');
            }
            const bar = document.getElementById('topbar');
            if (bar && !bar.contains(e.target)) bar.classList.remove('nav-open');
        });
        document.addEventListener('keydown', e => {
            if (e.key !== 'Escape') return;
            const dd = document.getElementById('userDropdown');
            if (dd) dd.hidden = true;
            const bar = document.getElementById('topbar');
            if (bar) bar.classList.remove('nav-open');
        });
    }
}
