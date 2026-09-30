const AUTH_STORAGE_KEY = 'terravision.auth';
const USER_LOGIN_PAGE = 'login.html';
const ADMIN_LOGIN_PAGE = 'login.html?intent=admin';

function saveSession(token, email, role, expiresInSeconds) {
    const expiresAt = Date.now() + expiresInSeconds * 1000;
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token, email, role, expiresAt }));
}

/** Removes the JWT and the cached email/role -- everything the frontend keeps about the user. */
function clearSession() {
    try {
        localStorage.removeItem(AUTH_STORAGE_KEY);
        sessionStorage.removeItem(AUTH_STORAGE_KEY);
    } catch (e) {
        // storage blocked: nothing to clear
    }
}

function getSession() {
    try {
        const raw = localStorage.getItem(AUTH_STORAGE_KEY);
        if (!raw) return null;
        const session = JSON.parse(raw);
        const looksLikeJwt = typeof session.token === 'string' && session.token.split('.').length === 3;
        if (!looksLikeJwt || !session.email || !session.role || !(Date.now() < session.expiresAt)) {
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

/**
 * replace(), not href: the protected page must not stay in session history, or the
 * back button would land on it again after logout.
 */
function logout() {
    const session = getSession();
    clearSession();
    window.location.replace(session && session.role === 'ADMIN' ? ADMIN_LOGIN_PAGE : USER_LOGIN_PAGE);
}

/** For a 401 from the API: the stored token is no longer accepted, so treat it as logged out. */
function handleUnauthorized() {
    clearSession();
    window.location.replace(USER_LOGIN_PAGE);
}

/**
 * Call once from every protected page. Checks the session on load AND on every
 * `pageshow` (which also fires when the browser restores the page from the
 * back-forward cache, where no script would otherwise re-run), and when another tab
 * logs out. Failing the check hides the page and replace()s it with the login page.
 */
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

/**
 * For login/register/landing pages: someone who's already logged in goes straight to
 * their dashboard, also when the page is restored via back/forward.
 */
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

    // Document-level listeners are bound once even if renderNav runs again (bfcache
    // restore); they look the elements up fresh each time.
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
