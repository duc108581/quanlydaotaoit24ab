const nestApi = window.NEST_API_BASE_URL
    || `${window.location.protocol}//${window.location.hostname}:3000/api`;
const springApi = '/api';
const state = {
    accessToken: localStorage.getItem('nestAccessToken') || '',
    refreshToken: localStorage.getItem('nestRefreshToken') || '',
    user: null
};
let refreshInProgress;

const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const authStatus = document.getElementById('authStatus');
const registerStatus = document.getElementById('registerStatus');
const logoutButton = document.getElementById('logoutButton');

function setStatus(element, message, success = false) {
    element.textContent = message;
    element.classList.toggle('success', success);
}

async function refreshAccessToken() {
    if (!state.refreshToken) return false;
    if (!refreshInProgress) {
        refreshInProgress = (async () => {
            const response = await fetch(`${springApi}/auth/refresh`, {
                method: 'POST',
                headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: state.refreshToken })
            });
            const payload = await response.json().catch(() => null);
            if (!response.ok) {
                saveTokens('', '');
                return false;
            }
            saveTokens(payload.accessToken, payload.refreshToken);
            return true;
        })();
    }
    try {
        return await refreshInProgress;
    } finally {
        refreshInProgress = null;
    }
}

async function request(path, options = {}) {
    const headers = { Accept: 'application/json', ...(options.headers || {}) };
    if (options.body) headers['Content-Type'] = 'application/json';
    if (state.accessToken) headers.Authorization = `Bearer ${state.accessToken}`;
    const baseUrl = path.startsWith('/auth/') ? springApi : nestApi;
    const url = `${baseUrl}${path}`;
    let response = await fetch(url, { ...options, headers });
    const publicAuthPaths = [
        '/auth/login',
        '/auth/register',
        '/auth/forgot-password',
        '/auth/reset-password',
        '/auth/refresh',
        '/auth/logout'
    ];
    if (response.status === 401 && state.refreshToken && !publicAuthPaths.includes(path)) {
        if (await refreshAccessToken()) {
            headers.Authorization = `Bearer ${state.accessToken}`;
            response = await fetch(url, { ...options, headers });
        }
    }
    if (response.status === 204) return null;
    let payload = null;
    try {
        payload = await response.json();
    } catch {
        payload = null;
    }
    if (!response.ok) {
        throw new Error(payload?.message || `Yêu cầu thất bại (HTTP ${response.status})`);
    }
    return payload;
}

function saveTokens(accessToken, refreshToken) {
    state.accessToken = accessToken || '';
    state.refreshToken = refreshToken || '';
    if (state.accessToken) localStorage.setItem('nestAccessToken', state.accessToken);
    else localStorage.removeItem('nestAccessToken');
    if (state.refreshToken) localStorage.setItem('nestRefreshToken', state.refreshToken);
    else localStorage.removeItem('nestRefreshToken');
}

async function loadCurrentUser() {
    if (!state.accessToken) return;
    try {
        state.user = await request('/auth/me');
        logoutButton.hidden = false;
        setStatus(authStatus, `Đã đăng nhập: ${state.user.fullName} (${state.user.roles.join(', ')})`, true);
    } catch (error) {
        saveTokens('', '');
        state.user = null;
        logoutButton.hidden = true;
        setStatus(authStatus, 'Phiên đăng nhập đã hết hạn.');
    }
}

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    setStatus(authStatus, 'Đang đăng nhập...');
    const payload = Object.fromEntries(new FormData(loginForm));
    try {
        const result = await request('/auth/login', { method: 'POST', body: JSON.stringify(payload) });
        saveTokens(result.accessToken, result.refreshToken);
        state.user = result.user;
        logoutButton.hidden = false;
        setStatus(authStatus, `Đã đăng nhập: ${result.user.fullName} (${result.user.roles.join(', ')})`, true);
    } catch (error) {
        setStatus(authStatus, error.message);
    }
});

registerForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    setStatus(registerStatus, 'Đang xử lý đăng ký...');
    const payload = Object.fromEntries(new FormData(registerForm));
    try {
        const result = await request('/auth/register', { method: 'POST', body: JSON.stringify(payload) });
        setStatus(registerStatus, result.message, true);
    } catch (error) {
        setStatus(registerStatus, error.message);
    }
});

logoutButton.addEventListener('click', async () => {
    try {
        if (state.refreshToken) await request('/auth/logout', {
            method: 'POST',
            body: JSON.stringify({ refreshToken: state.refreshToken })
        });
    } catch {
        // Local logout remains valid even if the server refresh token is already revoked.
    }
    saveTokens('', '');
    state.user = null;
    logoutButton.hidden = true;
    setStatus(authStatus, 'Đã đăng xuất.', true);
});

async function loadOpenSections() {
    const container = document.getElementById('openSections');
    container.textContent = 'Đang tải...';
    try {
        const sections = await request('/course-registrations/open-sections');
        container.replaceChildren();
        if (!sections.length) {
            container.textContent = 'Hiện không có lớp học phần mở đăng ký.';
            return;
        }
        sections.forEach((section) => {
            const item = document.createElement('div');
            item.className = 'nest-item';
            const remaining = section.maxStudents == null
                ? 'Không giới hạn'
                : `${Math.max(section.maxStudents - section.enrolled, 0)} chỗ`;
            item.innerHTML = `<div><strong>${escapeHtml(section.code)}</strong> — ${escapeHtml(section.name)}<br><small>${escapeHtml(section.courseName || '')} · Còn ${escapeHtml(String(remaining))}</small></div><button class="btn btn-success btn-sm" type="button">Đăng ký</button>`;
            item.querySelector('button').addEventListener('click', () => enroll(section.id));
            container.append(item);
        });
    } catch (error) {
        container.textContent = error.message;
    }
}

async function enroll(sectionId) {
    try {
        await request('/course-registrations', {
            method: 'POST',
            body: JSON.stringify({ courseSectionId: sectionId })
        });
        await Promise.all([loadOpenSections(), loadMine()]);
    } catch (error) {
        alert(error.message);
    }
}

async function loadMine() {
    const container = document.getElementById('myRegistrations');
    container.textContent = 'Đang tải...';
    try {
        const registrations = await request('/course-registrations/mine');
        container.replaceChildren();
        if (!registrations.length) {
            container.textContent = 'Bạn chưa đăng ký học phần.';
            return;
        }
        registrations.forEach((registration) => {
            const item = document.createElement('div');
            item.className = 'nest-item';
            item.innerHTML = `<div><strong>${escapeHtml(registration.sectionCode)}</strong> — ${escapeHtml(registration.sectionName)}<br><small>${escapeHtml(registration.status || '')}</small></div><button class="btn btn-outline-danger btn-sm" type="button">Hủy</button>`;
            item.querySelector('button').addEventListener('click', async () => {
                if (!confirm('Hủy đăng ký học phần này?')) return;
                try {
                    await request(`/course-registrations/${encodeURIComponent(registration.id)}`, {
                        method: 'DELETE',
                        body: JSON.stringify({})
                    });
                    await Promise.all([loadOpenSections(), loadMine()]);
                } catch (error) {
                    alert(error.message);
                }
            });
            container.append(item);
        });
    } catch (error) {
        container.textContent = error.message;
    }
}

async function loadNotifications() {
    const container = document.getElementById('notifications');
    container.textContent = 'Đang tải...';
    try {
        const notifications = await request('/notifications');
        container.replaceChildren();
        if (!notifications.length) {
            container.textContent = 'Chưa có thông báo.';
            return;
        }
        notifications.forEach((notification) => {
            const item = document.createElement('div');
            item.className = 'nest-item';
            item.innerHTML = `<div><strong>${escapeHtml(notification.title)}</strong><br><small>${escapeHtml(notification.content)}</small></div>${notification.readAt ? '' : '<button class="btn btn-outline-primary btn-sm" type="button">Đã đọc</button>'}`;
            if (!notification.readAt) {
                item.querySelector('button').addEventListener('click', async () => {
                    await request(`/notifications/${encodeURIComponent(notification.id)}/read`, { method: 'PATCH' });
                    await loadNotifications();
                });
            }
            container.append(item);
        });
    } catch (error) {
        container.textContent = error.message;
    }
}

async function loadReports() {
    const container = document.getElementById('reports');
    container.textContent = 'Đang tải...';
    try {
        const [summary, capacity, sections] = await Promise.all([
            request('/reports/registrations'),
            request('/reports/course-sections'),
            request('/reports/registrations-by-section')
        ]);
        container.innerHTML = `
            <div class="nest-item"><strong>Tổng sinh viên đã đăng ký</strong><span>${escapeHtml(String(summary[0]?.registeredStudents ?? 0))}</span></div>
            <div class="nest-item"><strong>Tổng lượt đăng ký</strong><span>${escapeHtml(String(summary[0]?.totalRegistrations ?? 0))}</span></div>
            <div class="nest-item"><strong>Lớp đầy</strong><span>${escapeHtml(String(capacity[0]?.fullSections ?? 0))}</span></div>
            <div class="nest-item"><strong>Lớp đang mở</strong><span>${escapeHtml(String(capacity[0]?.openSections ?? 0))}</span></div>
            <div class="nest-item"><strong>Số chỗ còn lại</strong><span>${escapeHtml(String(capacity[0]?.remainingSeats ?? 0))}</span></div>
            <div class="nest-item"><strong>Số lớp có dữ liệu</strong><span>${escapeHtml(String(sections.length))}</span></div>`;
    } catch (error) {
        container.textContent = error.message;
    }
}

async function loadAdminRegistrations() {
    const container = document.getElementById('adminRegistrations');
    container.textContent = 'Đang tải...';
    try {
        const registrations = await request('/course-registrations/admin/all');
        container.replaceChildren();
        if (!registrations.length) {
            container.textContent = 'Chưa có đăng ký nào.';
            return;
        }
        registrations.forEach((registration) => {
            const item = document.createElement('div');
            item.className = 'nest-item';
            item.innerHTML = `
                <div><strong>${escapeHtml(registration.studentCode || 'Không rõ mã SV')}</strong> — ${escapeHtml(registration.studentName || '')}<br>
                <small>${escapeHtml(registration.sectionCode || '')} · ${escapeHtml(registration.courseName || registration.sectionName || '')} · ${escapeHtml(registration.status || 'REGISTERED')}</small></div>
                <div class="nest-actions">
                    <button class="btn btn-success btn-sm" type="button" data-status="CONFIRMED">Duyệt</button>
                    <button class="btn btn-danger btn-sm" type="button" data-status="CANCELLED">Từ chối</button>
                </div>`;
            item.querySelectorAll('[data-status]').forEach((button) => {
                button.addEventListener('click', async () => {
                    try {
                        await request(`/course-registrations/admin/${encodeURIComponent(registration.id)}/status`, {
                            method: 'PATCH',
                            body: JSON.stringify({ status: button.dataset.status })
                        });
                        await loadAdminRegistrations();
                    } catch (error) {
                        alert(error.message);
                    }
                });
            });
            container.append(item);
        });
    } catch (error) {
        container.textContent = error.message;
    }
}

function escapeHtml(value) {
    const element = document.createElement('span');
    element.textContent = value;
    return element.innerHTML;
}

document.getElementById('reloadOpen').addEventListener('click', loadOpenSections);
document.getElementById('reloadMine').addEventListener('click', loadMine);
document.getElementById('reloadNotifications').addEventListener('click', loadNotifications);
document.getElementById('reloadReports').addEventListener('click', loadReports);
document.getElementById('reloadAdminRegistrations').addEventListener('click', loadAdminRegistrations);
document.addEventListener('DOMContentLoaded', loadCurrentUser);

const nestMode = new URLSearchParams(window.location.search).get('mode');
if (nestMode) {
    document.querySelectorAll('.nest-grid > .nest-card').forEach((card) => {
        card.hidden = true;
    });
    if (nestMode === 'registrations') {
        document.getElementById('adminRegistrationsCard').hidden = false;
    } else if (nestMode === 'reports') {
        document.getElementById('reloadReports').closest('.nest-card').hidden = false;
    } else if (nestMode === 'open-sections') {
        document.getElementById('reloadOpen').closest('.nest-card').hidden = false;
    } else if (nestMode === 'my-registrations') {
        document.getElementById('reloadMine').closest('.nest-card').hidden = false;
    } else if (nestMode === 'notifications') {
        document.getElementById('reloadNotifications').closest('.nest-card').hidden = false;
    }
}
