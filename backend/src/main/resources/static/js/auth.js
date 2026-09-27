const AUTH_STORAGE_KEY = 'terravision.auth';

function saveSession(token, email, role, expiresInSeconds) {
    const expiresAt = Date.now() + expiresInSeconds * 1000;
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token, email, role, expiresAt }));
}

function getSession() {
    try {
        const raw = localStorage.getItem(AUTH_STORAGE_KEY);
        if (!raw) return null;
        const session = JSON.parse(raw);
        if (!session.token || Date.now() >= session.expiresAt) {
            localStorage.removeItem(AUTH_STORAGE_KEY);
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

function logout() {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    window.location.href = 'login.html';
}

/** Call at the top of every protected page's script. */
function requireAuth() {
    if (!isLoggedIn()) {
        window.location.href = 'login.html';
    }
}

/** Call in addition to requireAuth() on admin.html. */
function requireAdmin() {
    const session = getSession();
    if (!session || session.role !== 'ADMIN') {
        window.location.href = 'classify.html';
    }
}

/** If someone who's already logged in lands on login/register, send them onward. */
function redirectIfAlreadyLoggedIn() {
    if (isLoggedIn()) {
        window.location.href = 'classify.html';
    }
}

function renderNav(activePage) {
    const session = getSession();
    const container = document.getElementById('topbar');
    if (!container) return;

    const links = [
        { href: 'classify.html', label: 'Classify', key: 'classify' },
        { href: 'my-history.html', label: 'My History', key: 'history' },
        { href: 'model-info.html', label: 'Model Info', key: 'model-info' },
        { href: 'about.html', label: 'About', key: 'about' },
    ];
    if (session && session.role === 'ADMIN') {
        links.push({ href: 'admin.html', label: 'Admin', key: 'admin' });
    }

    const linksHtml = links
        .map(l => `<a href="${l.href}" class="${l.key === activePage ? 'active' : ''}">${l.label}</a>`)
        .join('');

    container.innerHTML = `
        <div class="topbar-inner">
            <a href="classify.html" class="brand">TerraVision</a>
            <nav class="nav-links">${linksHtml}</nav>
            <div class="topbar-user">
                <span class="user-email">${session ? session.email : ''}</span>
                <button id="logoutBtn" class="btn-icon" type="button" title="Log out" aria-label="Log out">${ICONS.logout}</button>
            </div>
        </div>
    `;

    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', logout);
    }
}
