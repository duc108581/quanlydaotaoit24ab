const nestApi = window.NEST_API_BASE_URL
    || `${window.location.protocol}//${window.location.hostname}:3000/api`;
const springApi = '/api';

const state = {
    accessToken: localStorage.getItem('nestAccessToken') || '',
    refreshToken: localStorage.getItem('nestRefreshToken') || '',
    user: null
};
let refreshInProgress;

const adminMenu = [
    { id: 'dashboard', label: 'Dashboard', icon: 'bi-grid-1x2-fill' },
    { id: 'students', label: 'Quản lý sinh viên', icon: 'bi-person-vcard' },
    { id: 'student-classes', label: 'Quản lý lớp sinh viên', icon: 'bi-people' },
    { id: 'lecturers', label: 'Quản lý giảng viên', icon: 'bi-person-badge' },
    { id: 'academic-catalogs', label: 'Danh mục đào tạo', icon: 'bi-collection' },
    { id: 'courses', label: 'Quản lý học phần', icon: 'bi-journal-text' },
    { id: 'course-sections', label: 'Quản lý lớp học phần', icon: 'bi-building' },
    { id: 'registrations', label: 'Quản lý đăng ký', icon: 'bi-card-checklist' },
    { id: 'reports', label: 'Thống kê đào tạo', icon: 'bi-bar-chart-line' },
    { id: 'accounts', label: 'Quản lý tài khoản', icon: 'bi-shield-lock' }
];

const studentMenu = [
    { id: 'student-dashboard', label: 'Dashboard', icon: 'bi-grid-1x2-fill' },
    { id: 'open-sections', label: 'Lớp mở đăng ký', icon: 'bi-journal-check' },
    { id: 'my-registrations', label: 'Đăng ký của tôi', icon: 'bi-card-checklist' },
    { id: 'notifications', label: 'Thông báo', icon: 'bi-bell' }
];

const loginScreen = document.getElementById('loginScreen');
const appShell = document.getElementById('appShell');
const loginForm = document.getElementById('loginForm');
const loginStatus = document.getElementById('loginStatus');
const portalMenu = document.getElementById('portalMenu');
const portalContent = document.getElementById('portalContent');
const portalTitle = document.getElementById('portalTitle');
const portalBreadcrumb = document.getElementById('portalBreadcrumb');

function saveTokens(accessToken, refreshToken) {
    state.accessToken = accessToken || '';
    state.refreshToken = refreshToken || '';
    if (state.accessToken) localStorage.setItem('nestAccessToken', state.accessToken);
    else localStorage.removeItem('nestAccessToken');
    if (state.refreshToken) localStorage.setItem('nestRefreshToken', state.refreshToken);
    else localStorage.removeItem('nestRefreshToken');
}

function clearAuthenticatedState() {
    saveTokens('', '');
    state.user = null;
    appShell.hidden = true;
    loginScreen.hidden = false;
    loginForm.reset();
    loginStatus.textContent = '';
    portalMenu.replaceChildren();
    portalContent.replaceChildren();
    document.querySelectorAll('dialog[open]').forEach((dialog) => dialog.close());
    document.getElementById('accountRole').textContent = 'Tài khoản';
    document.getElementById('accountName').textContent = 'Tài khoản';
    document.getElementById('accountMeta').textContent = 'Vai trò';
    document.getElementById('accountInitial').textContent = '';
    portalTitle.textContent = 'Đăng nhập';
    portalBreadcrumb.textContent = 'Chưa đăng nhập';
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
                clearAuthenticatedState();
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

async function apiRequest(baseUrl, path, options = {}) {
    const headers = { Accept: 'application/json', ...(options.headers || {}) };
    if (options.body) headers['Content-Type'] = 'application/json';
    if (state.accessToken) headers.Authorization = `Bearer ${state.accessToken}`;
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
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(payload?.detail || payload?.message || `Yêu cầu thất bại (HTTP ${response.status})`);
        error.status = response.status;
        throw error;
    }
    return payload;
}

async function nestRequest(path, options = {}) {
    return apiRequest(path.startsWith('/auth/') ? springApi : nestApi, path, options);
}

async function springRequest(path, options = {}) {
    return apiRequest(springApi, path, options);
}

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    loginStatus.textContent = 'Đang đăng nhập...';
    const payload = Object.fromEntries(new FormData(loginForm));
    try {
        const result = await nestRequest('/auth/login', { method: 'POST', body: JSON.stringify(payload) });
        saveTokens(result.accessToken, result.refreshToken);
        state.user = result.user;
        showShell();
    } catch (error) {
        loginStatus.textContent = error.message;
    }
});

document.getElementById('forgotPasswordButton').addEventListener('click', () => {
    openPasswordRecoveryDialog();
});

function openPasswordRecoveryDialog() {
    const dialog = document.createElement('dialog');
    dialog.className = 'unified-dialog';
    dialog.innerHTML = `
        <form id="forgotPasswordForm">
            <div class="dialog-heading"><div><small>HỖ TRỢ TÀI KHOẢN</small><h2>Quên mật khẩu</h2></div><button type="button" class="dialog-close forgot-close"><i class="bi bi-x-lg"></i></button></div>
            <div class="dialog-fields" style="grid-template-columns:1fr">
                <div class="dialog-field"><label>Tên đăng nhập, email, mã sinh viên hoặc mã giảng viên</label><input name="identifier" required maxlength="255" placeholder="Ví dụ: SV001 hoặc gv001@..." /></div>
                <div class="dialog-field" id="resetTokenGroup" hidden><label>Mã đặt lại mật khẩu</label><input name="resetToken" maxlength="100" /></div>
                <div class="dialog-field" id="newPasswordGroup" hidden><label>Mật khẩu mới</label><input name="password" type="password" minlength="12" maxlength="72" /></div>
            </div>
            <p id="forgotPasswordStatus" class="dialog-status"></p>
            <div class="dialog-actions"><button type="button" class="outline-button forgot-close">Đóng</button><button type="submit" class="primary-button" id="forgotPasswordSubmit">Gửi yêu cầu</button></div>
        </form>`;
    document.body.append(dialog);
    dialog.showModal();
    dialog.querySelectorAll('.forgot-close').forEach((button) =>
        button.addEventListener('click', () => dialog.close()));
    dialog.addEventListener('close', () => dialog.remove());

    let resetMode = false;
    const form = dialog.querySelector('#forgotPasswordForm');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const status = dialog.querySelector('#forgotPasswordStatus');
        const payload = Object.fromEntries(new FormData(form).entries());
        try {
            if (!resetMode) {
                const result = await nestRequest('/auth/forgot-password', {
                    method: 'POST',
                    body: JSON.stringify({ identifier: payload.identifier })
                });
                status.textContent = result.resetToken
                    ? `${result.message} Mã kiểm thử: ${result.resetToken}`
                    : result.message;
                if (result.resetToken) {
                    resetMode = true;
                    dialog.querySelector('#resetTokenGroup').hidden = false;
                    dialog.querySelector('#newPasswordGroup').hidden = false;
                    dialog.querySelector('[name="resetToken"]').value = result.resetToken;
                    dialog.querySelector('#forgotPasswordSubmit').textContent = 'Đặt lại mật khẩu';
                }
            } else {
                await nestRequest('/auth/reset-password', {
                    method: 'POST',
                    body: JSON.stringify({ token: payload.resetToken, password: payload.password })
                });
                status.textContent = 'Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.';
                dialog.querySelector('#forgotPasswordSubmit').disabled = true;
            }
        } catch (error) {
            status.textContent = error.message;
        }
    });
}

document.getElementById('portalLogout').addEventListener('click', async () => {
    const button = document.getElementById('portalLogout');
    button.disabled = true;
    let serverLogoutFailed = false;
    try {
        if (state.refreshToken) {
            await springRequest('/auth/logout', {
                method: 'POST',
                body: JSON.stringify({ refreshToken: state.refreshToken })
            });
        }
    } catch {
        serverLogoutFailed = true;
    } finally {
        clearAuthenticatedState();
        if (serverLogoutFailed) {
            loginStatus.textContent = 'Đã xóa thông tin đăng nhập trên trình duyệt nhưng không thể xác nhận thu hồi phiên phía máy chủ.';
        }
        button.disabled = false;
    }
});

async function restoreSession() {
    if (!state.accessToken) return;
    try {
        state.user = await nestRequest('/auth/me');
        showShell();
    } catch {
        clearAuthenticatedState();
    }
}

function showShell() {
    if (!state.accessToken || !state.user) {
        clearAuthenticatedState();
        return;
    }
    loginScreen.hidden = true;
    appShell.hidden = false;
    const isAdmin = state.user.roles.includes('ADMIN');
    const accountRole = isAdmin ? 'Quản trị viên' : state.user.roles.includes('LECTURER') ? 'Giảng viên' : 'Sinh viên';
    document.getElementById('accountRole').textContent = accountRole;
    document.getElementById('accountName').textContent = state.user.fullName;
    document.getElementById('accountMeta').textContent = translateRoles(state.user.roles);
    document.getElementById('accountInitial').textContent = state.user.fullName.charAt(0).toUpperCase();
    renderMenu(isAdmin);
    navigate(isAdmin ? 'dashboard' : 'student-dashboard');
}

function renderMenu(isAdmin) {
    const items = isAdmin ? adminMenu : studentMenu;
    portalMenu.replaceChildren();
    items.forEach((item) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'menu-button';
        button.dataset.page = item.id;
        button.innerHTML = `<i class="bi ${item.icon}"></i><span>${escapeHtml(item.label)}</span>`;
        button.addEventListener('click', () => navigate(item.id));
        portalMenu.append(button);
    });
}

async function navigate(pageId) {
    if (!state.accessToken || !state.user) {
        clearAuthenticatedState();
        return;
    }
    document.querySelectorAll('.menu-button').forEach((button) =>
        button.classList.toggle('active', button.dataset.page === pageId));
    const item = [...adminMenu, ...studentMenu].find((entry) => entry.id === pageId);
    portalTitle.textContent = item?.label || 'Bảng điều khiển';
    portalBreadcrumb.textContent = state.user?.roles.includes('ADMIN') ? 'Quản trị hệ thống' : 'Cổng sinh viên';

    if (pageId === 'dashboard') return renderAdminDashboard();
    if (pageId === 'student-dashboard') return renderStudentDashboard();
    if (pageId === 'students') return renderStudentManager();
    if (pageId === 'student-classes') return renderStudentClasses();
    if (pageId === 'lecturers') return renderSimpleResource('/lecturers', 'lecturer');
    if (pageId === 'academic-catalogs') return renderAcademicCatalogs();
    if (pageId === 'academic-years') return renderSimpleResource('/academic-years', 'academic-year');
    if (pageId === 'school-years') return renderSchoolYearPage();
    if (pageId === 'courses') return renderSimpleResource('/courses', 'course');
    if (pageId === 'course-sections') return renderSimpleResource('/course-sections', 'course-section');
    if (pageId === 'registrations') return renderRegistrationAdmin();
    if (pageId === 'reports') return renderReports();
    if (pageId === 'accounts') return renderAccountManager();
    if (pageId === 'open-sections') return renderOpenSections();
    if (pageId === 'my-registrations') return renderMyRegistrations();
    if (pageId === 'notifications') return renderNotifications();
}

async function renderAdminDashboard() {
    portalContent.innerHTML = `
        <section class="metric-grid" id="dashboardMetrics">
            ${metricCard('bi-person-vcard', 'Sinh viên', 'students', '—')}
            ${metricCard('bi-person-badge', 'Giảng viên', 'lecturers', '—')}
            ${metricCard('bi-journal-text', 'Học phần', 'courses', '—')}
            ${metricCard('bi-building', 'Lớp học phần', 'course-sections', '—')}
        </section>
        <section class="dashboard-grid">
            <article class="panel-card"><div class="panel-heading"><h2>Hoạt động hệ thống</h2></div>
                <div class="activity-list">
                    <div class="activity-item"><span class="activity-icon green"><i class="bi bi-check-circle"></i></span><div><strong>SQL Server đã kết nối</strong><small>Cơ sở dữ liệu đào tạo đang hoạt động</small></div></div>
                    <div class="activity-item"><span class="activity-icon blue"><i class="bi bi-cloud-check"></i></span><div><strong>Spring Boot API</strong><small>Xử lý xác thực và nghiệp vụ quản lý đào tạo</small></div></div>
                    <div class="activity-item"><span class="activity-icon violet"><i class="bi bi-shield-check"></i></span><div><strong>Tài khoản Admin đã xác thực</strong><small>Toàn quyền quản trị và theo dõi đăng ký</small></div></div>
                </div>
            </article>
            <article class="panel-card"><div class="panel-heading"><h2>Truy cập nhanh</h2></div>
                <div class="quick-grid">
                    ${quickAction('students', 'bi-person-plus', 'Thêm sinh viên', 'Tạo hồ sơ sinh viên')}
                    ${quickAction('lecturers', 'bi-person-badge', 'Giảng viên', 'Danh sách giảng viên')}
                    ${quickAction('course-sections', 'bi-building', 'Lớp học phần', 'Lịch học và sĩ số')}
                    ${quickAction('registrations', 'bi-card-checklist', 'Đăng ký', 'Duyệt và theo dõi')}
                </div>
            </article>
        </section>`;

    for (const metric of [
        ['students', 'students'],
        ['lecturers', 'lecturers'],
        ['courses', 'courses'],
        ['course-sections', 'course-sections']
    ]) {
        try {
            const rows = await springRequest(`/${metric[1]}`);
            const element = portalContent.querySelector(`[data-metric="${metric[0]}"]`);
            if (element) element.textContent = Array.isArray(rows) ? rows.length : 0;
        } catch {
            const element = portalContent.querySelector(`[data-metric="${metric[0]}"]`);
            if (element) element.textContent = '0';
        }
    }
    portalContent.querySelectorAll('[data-navigate]').forEach((button) =>
        button.addEventListener('click', () => navigate(button.dataset.navigate)));
}

function metricCard(icon, label, key, value) {
    return `<article class="metric-card"><div class="metric-icon"><i class="bi ${icon}"></i></div><div><strong data-metric="${key}">${value}</strong><span>${label}</span></div></article>`;
}

function quickAction(pageId, icon, title, description) {
    return `<button type="button" class="quick-action" data-navigate="${pageId}"><span><i class="bi ${icon}"></i></span><div><strong>${title}</strong><small>${description}</small></div><i class="bi bi-chevron-right quick-arrow"></i></button>`;
}

async function renderStudentDashboard() {
    portalContent.innerHTML = `
        <section class="metric-grid">
            ${metricCard('bi-journal-check', 'Lớp mở đăng ký', 'open', '—')}
            ${metricCard('bi-card-checklist', 'Đăng ký của tôi', 'mine', '—')}
            ${metricCard('bi-bell', 'Thông báo', 'notifications', '—')}
        </section>
        <section class="dashboard-grid">
            <article class="panel-card"><div class="panel-heading"><h2>Tiện ích sinh viên</h2></div><div class="quick-grid">
                ${quickAction('open-sections', 'bi-journal-check', 'Đăng ký học phần', 'Xem lớp học phần đang mở')}
                ${quickAction('my-registrations', 'bi-card-checklist', 'Lịch đăng ký', 'Theo dõi học phần đã đăng ký')}
                ${quickAction('notifications', 'bi-bell', 'Thông báo', 'Thông báo đăng ký và lịch học')}
            </div></article>
            <article class="panel-card"><div class="panel-heading"><h2>Tài khoản</h2></div>
                <div class="activity-item"><span class="activity-icon green"><i class="bi bi-person-check"></i></span><div><strong>${escapeHtml(state.user.fullName)}</strong><small>Tài khoản sinh viên đang hoạt động</small></div></div>
            </article>
        </section>`;
    try {
        const [open, mine, notifications] = await Promise.all([
            nestRequest('/course-registrations/open-sections').catch(() => []),
            nestRequest('/course-registrations/mine').catch(() => []),
            nestRequest('/notifications').catch(() => [])
        ]);
        portalContent.querySelector('[data-metric="open"]').textContent = open.length;
        portalContent.querySelector('[data-metric="mine"]').textContent = mine.length;
        portalContent.querySelector('[data-metric="notifications"]').textContent = notifications.length;
    } catch {
        // Dashboard metrics remain available as placeholders when an endpoint is unavailable.
    }
    portalContent.querySelectorAll('[data-navigate]').forEach((button) =>
        button.addEventListener('click', () => navigate(button.dataset.navigate)));
}

async function renderStudentManager() {
    let rows;
    let classes;
    portalContent.innerHTML = '<section class="panel-card"><div class="empty-state"><strong>Đang tải hồ sơ sinh viên...</strong></div></section>';
    try {
        [rows, classes] = await Promise.all([
            springRequest('/students'),
            springRequest('/student-form-options/classes?includeInactive=true')
        ]);
    } catch (error) {
        portalContent.innerHTML = `
            <section class="catalog-error" role="alert"><strong>Không tải được dữ liệu sinh viên hoặc lớp.</strong><div>${escapeHtml(error.message)}</div>
                <button type="button" class="outline-button" id="studentManagerRetry">Thử lại</button></section>`;
        document.getElementById('studentManagerRetry').addEventListener('click', renderStudentManager);
        return;
    }
    const activeClasses = classes.filter((item) => item.isActive !== false);
    renderResourceShell({
        title: 'Quản lý sinh viên',
        description: 'Thêm, sửa, xóa và tìm kiếm hồ sơ sinh viên.',
        endpoint: '/students',
        rows,
        fields: [
            ['code', 'Mã sinh viên'],
            ['fullName', 'Họ và tên'],
            ['email', 'Email'],
            ['phone', 'Số điện thoại'],
            ['studentClassessId', 'Lớp sinh viên']
        ],
        formFields: [
            ['code', 'Mã sinh viên'],
            ['fullName', 'Họ và tên'],
            ['email', 'Email'],
            ['phone', 'Số điện thoại'],
            ['studentClassessId', 'Lớp sinh viên'],
            ['initialPassword', 'Mật khẩu ban đầu']
        ],
        createOnlyFields: ['initialPassword'],
        inputTypes: { initialPassword: 'password', email: 'email', phone: 'tel' },
        requiredFields: ['code', 'fullName', 'studentClassessId', 'initialPassword'],
        inputAttributes: { initialPassword: { minlength: '12', maxlength: '72', autocomplete: 'new-password' } },
        formSelects: {
            studentClassessId: {
                rows: classes,
                label: (item) => [item.code, item.name, item.isActive === false ? '(Ngừng hoạt động)' : ''].filter(Boolean).join(' - '),
                visibleWhen: (item, record) => item.isActive !== false
                    || normalizeIdentifier(item.id) === normalizeIdentifier(record?.studentClassessId)
            }
        },
        formNotice: activeClasses.length ? '' : 'Chưa có lớp sinh viên đang hoạt động. Hãy tạo hoặc kích hoạt lớp trước khi thêm sinh viên.',
        lookupDefinitions: {
            studentClassessId: { rows: classes, codeKey: 'code', nameKey: 'name' }
        },
        addLabel: 'Thêm sinh viên'
    });
}

async function renderStudentClasses() {
    portalContent.innerHTML = '<section class="panel-card"><div class="empty-state"><strong>Đang tải lớp sinh viên...</strong></div></section>';
    let classes;
    let departments;
    let majors;
    let programs;
    let academicYears;
    let lecturers;
    let students;
    try {
        [classes, departments, majors, programs, academicYears, lecturers, students] = await Promise.all([
            springRequest('/student-classes'),
            springRequest('/departments'),
            springRequest('/majors'),
            springRequest('/training-programs'),
            springRequest('/academic-years'),
            springRequest('/lecturers'),
            springRequest('/students?includeInactive=true')
        ]);
    } catch (error) {
        portalContent.innerHTML = `
            <section class="catalog-error" role="alert"><strong>Không tải được dữ liệu quản lý lớp sinh viên.</strong><div>${escapeHtml(error.message)}</div>
                <button type="button" class="outline-button" id="studentClassesRetry">Thử lại</button></section>`;
        document.getElementById('studentClassesRetry').addEventListener('click', renderStudentClasses);
        return;
    }
    const lookup = {
        departments: new Map(departments.map((item) => [normalizeIdentifier(item.id), item])),
        majors: new Map(majors.map((item) => [normalizeIdentifier(item.id), item])),
        programs: new Map(programs.map((item) => [normalizeIdentifier(item.id), item])),
        academicYears: new Map(academicYears.map((item) => [normalizeIdentifier(item.id), item])),
        lecturers: new Map(lecturers.map((item) => [normalizeIdentifier(item.id), item]))
    };
    const studentCounts = new Map();
    students.forEach((student) => {
        const classId = normalizeIdentifier(student.studentClassessId || student.currentStudentClassId);
        if (classId) studentCounts.set(classId, (studentCounts.get(classId) || 0) + 1);
    });
    const display = (map, id) => {
        const item = map.get(normalizeIdentifier(id));
        if (!item) return '—';
        const code = item.code || item.lecturerCode || '';
        const name = item.name || item.fullName || '';
        return [code, name].filter(Boolean).join(' - ') || '—';
    };
    const state = { keyword: '', departmentId: '', majorId: '', academicYearId: '', status: 'all', page: 1, pageSize: 10 };
    const getFiltered = () => classes.filter((item) => {
        const keyword = state.keyword.trim().toLocaleLowerCase('vi');
        const matchesKeyword = !keyword || `${item.code || ''} ${item.name || ''}`.toLocaleLowerCase('vi').includes(keyword);
        return matchesKeyword
            && (!state.departmentId || normalizeIdentifier(item.departmentId) === normalizeIdentifier(state.departmentId))
            && (!state.majorId || normalizeIdentifier(item.majorId) === normalizeIdentifier(state.majorId))
            && (!state.academicYearId || normalizeIdentifier(item.academicYearId) === normalizeIdentifier(state.academicYearId))
            && (state.status === 'all' || (state.status === 'active') === (item.isActive !== false));
    });
    portalContent.innerHTML = `
        <section class="page-heading">
            <div><h2>Quản lý lớp sinh viên</h2><p>Quản lý lớp theo khoa, ngành, chương trình và niên khóa; lớp có hồ sơ sẽ được ngừng hoạt động thay vì xóa.</p></div>
            <button type="button" class="primary-button" id="studentClassAdd"><i class="bi bi-plus-lg"></i>Thêm lớp</button>
        </section>
        <section class="panel-card resource-card">
            <div class="resource-toolbar class-filter-toolbar">
                <div class="search-box"><i class="bi bi-search"></i><input id="studentClassSearch" type="search" placeholder="Tìm mã lớp hoặc tên lớp" /></div>
                <select class="catalog-filter" id="studentClassDepartment"><option value="">Tất cả khoa</option>${departments.map((item) => `<option value="${escapeHtml(item.id)}">${escapeHtml([item.code, item.name].filter(Boolean).join(' - '))}</option>`).join('')}</select>
                <select class="catalog-filter" id="studentClassMajor"><option value="">Tất cả ngành</option>${majors.map((item) => `<option value="${escapeHtml(item.id)}" data-department-id="${escapeHtml(item.departmentId)}">${escapeHtml([item.code, item.name].filter(Boolean).join(' - '))}</option>`).join('')}</select>
                <select class="catalog-filter" id="studentClassAcademicYear"><option value="">Tất cả niên khóa</option>${academicYears.map((item) => `<option value="${escapeHtml(item.id)}">${escapeHtml([item.code, item.name, item.year].filter(Boolean).join(' - '))}</option>`).join('')}</select>
                <select class="catalog-filter" id="studentClassStatus"><option value="all">Mọi trạng thái</option><option value="active">Đang hoạt động</option><option value="inactive">Ngừng hoạt động</option></select>
                <button type="button" class="outline-button" id="studentClassRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button>
            </div>
            <div id="studentClassMessage" class="catalog-error class-page-message" role="alert" hidden></div>
            <div id="studentClassTable"></div>
        </section>`;

    const departmentSelect = document.getElementById('studentClassDepartment');
    const majorSelect = document.getElementById('studentClassMajor');
    const updateMajorFilter = () => {
        Array.from(majorSelect.options).forEach((option, index) => {
            if (index === 0) return;
            option.hidden = Boolean(departmentSelect.value)
                && normalizeIdentifier(option.dataset.departmentId) !== normalizeIdentifier(departmentSelect.value);
        });
        if (majorSelect.selectedOptions[0]?.hidden) majorSelect.value = '';
        state.departmentId = departmentSelect.value;
        state.majorId = majorSelect.value;
        state.page = 1;
        renderTablePage();
    };
    const renderTablePage = () => {
        const filtered = getFiltered();
        const pageCount = Math.max(1, Math.ceil(filtered.length / state.pageSize));
        state.page = Math.min(state.page, pageCount);
        const pageRows = filtered.slice((state.page - 1) * state.pageSize, state.page * state.pageSize);
        document.getElementById('studentClassTable').innerHTML = pageRows.length ? `
            <div class="table-scroll"><table class="unified-table"><thead><tr>
                <th>Mã lớp / Tên lớp</th><th>Khoa</th><th>Ngành</th><th>Chương trình</th><th>Niên khóa</th><th>Cố vấn</th><th>Sĩ số</th><th>Trạng thái</th><th class="text-end">Thao tác</th>
            </tr></thead><tbody>${pageRows.map((item) => {
                const active = item.isActive !== false;
                const count = studentCounts.get(normalizeIdentifier(item.id)) || 0;
                return `<tr>
                    <td><strong>${escapeHtml(item.code)}</strong><small class="table-subtext">${escapeHtml(item.name)}</small></td>
                    <td>${escapeHtml(display(lookup.departments, item.departmentId))}</td>
                    <td>${escapeHtml(display(lookup.majors, item.majorId))}</td>
                    <td>${escapeHtml(display(lookup.programs, item.trainingProgramId))}</td>
                    <td>${escapeHtml(display(lookup.academicYears, item.academicYearId))}</td>
                    <td>${escapeHtml(display(lookup.lecturers, item.advisorId))}</td>
                    <td>${count}</td>
                    <td>${active ? '<span class="status-badge active">Đang hoạt động</span>' : '<span class="status-badge muted">Ngừng hoạt động</span>'}</td>
                    <td class="table-actions"><button type="button" class="status-action" data-class-action="view" data-class-id="${escapeHtml(item.id)}">Chi tiết</button><button type="button" class="status-action" data-class-action="edit" data-class-id="${escapeHtml(item.id)}">Sửa</button><button type="button" class="status-action ${active ? 'reject' : 'approve'}" data-class-action="toggle" data-class-id="${escapeHtml(item.id)}" data-class-active="${active ? '1' : '0'}">${active ? 'Ngừng' : 'Kích hoạt'}</button></td>
                </tr>`;
            }).join('')}</tbody></table></div>
            <div class="table-footer class-table-footer"><span>${filtered.length} lớp · Trang ${state.page}/${pageCount}</span><div><button type="button" class="outline-button" id="studentClassPrevious" ${state.page <= 1 ? 'disabled' : ''}>Trước</button><button type="button" class="outline-button" id="studentClassNext" ${state.page >= pageCount ? 'disabled' : ''}>Sau</button></div></div>`
            : '<div class="empty-state"><i class="bi bi-people"></i><strong>Không có lớp phù hợp</strong></div>';
        bindClassRowActions();
        document.getElementById('studentClassPrevious')?.addEventListener('click', () => { state.page--; renderTablePage(); });
        document.getElementById('studentClassNext')?.addEventListener('click', () => { state.page++; renderTablePage(); });
    };
    const bindClassRowActions = () => {
        portalContent.querySelectorAll('[data-class-action]').forEach((button) => button.addEventListener('click', async () => {
            const item = classes.find((row) => normalizeIdentifier(row.id) === normalizeIdentifier(button.dataset.classId));
            if (!item) return;
            if (button.dataset.classAction === 'view') {
                try {
                    const detail = await springRequest(`/student-classes/${encodeURIComponent(item.id)}`);
                    openStudentClassDetails(detail, lookup, display, studentCounts.get(normalizeIdentifier(item.id)) || 0);
                } catch (error) {
                    showClassPageError(error.message);
                }
            } else if (button.dataset.classAction === 'edit') {
                openStudentClassDialog(item, { classes, departments, majors, programs, academicYears, lecturers }, saveClass);
            } else {
                const active = button.dataset.classActive !== '1';
                const action = active ? 'kích hoạt' : 'ngừng hoạt động';
                if (!confirm(`Bạn có chắc muốn ${action} lớp ${item.code}?`)) return;
                try {
                    await springRequest(`/student-classes/${encodeURIComponent(item.id)}/status`, {
                        method: 'PATCH',
                        body: JSON.stringify({ isActive: active })
                    });
                    await renderStudentClasses();
                } catch (error) {
                    showClassPageError(error.message);
                }
            }
        }));
    };
    const saveClass = async (item, payload) => {
        await springRequest(`/student-classes${item ? `/${encodeURIComponent(item.id)}` : ''}`, {
            method: item ? 'PUT' : 'POST',
            body: JSON.stringify(payload)
        });
        await renderStudentClasses();
    };

    document.getElementById('studentClassSearch').addEventListener('input', (event) => {
        state.keyword = event.target.value;
        state.page = 1;
        renderTablePage();
    });
    departmentSelect.addEventListener('change', updateMajorFilter);
    majorSelect.addEventListener('change', () => { state.majorId = majorSelect.value; state.page = 1; renderTablePage(); });
    document.getElementById('studentClassAcademicYear').addEventListener('change', (event) => {
        state.academicYearId = event.target.value; state.page = 1; renderTablePage();
    });
    document.getElementById('studentClassStatus').addEventListener('change', (event) => {
        state.status = event.target.value; state.page = 1; renderTablePage();
    });
    document.getElementById('studentClassRefresh').addEventListener('click', renderStudentClasses);
    document.getElementById('studentClassAdd').addEventListener('click', () =>
        openStudentClassDialog(null, { classes, departments, majors, programs, academicYears, lecturers }, saveClass));
    updateMajorFilter();
}

function openStudentClassDetails(record, lookup, display, studentCount) {
    const details = [
        ['Mã lớp', record.code],
        ['Tên lớp', record.name],
        ['Khoa', display(lookup.departments, record.departmentId)],
        ['Ngành', display(lookup.majors, record.majorId)],
        ['Chương trình đào tạo', display(lookup.programs, record.trainingProgramId)],
        ['Niên khóa', display(lookup.academicYears, record.academicYearId)],
        ['Cố vấn học tập', display(lookup.lecturers, record.advisorId)],
        ['Sĩ số hiện tại', studentCount],
        ['Trạng thái', record.isActive === false ? 'Ngừng hoạt động' : 'Đang hoạt động'],
        ['Ngày tạo', record.createdAt],
        ['Cập nhật lần cuối', record.updatedAt]
    ];
    const dialog = createClassDialogShell(record.code, 'CHI TIẾT LỚP SINH VIÊN', false);
    dialog.querySelector('.dialog-fields').innerHTML = details.map(([label, value]) =>
        `<div class="dialog-field"><label>${escapeHtml(label)}</label><div>${escapeHtml(value || '—')}</div></div>`).join('');
    dialog.querySelector('.dialog-actions').innerHTML = '<button type="button" class="outline-button class-dialog-close">Đóng</button>';
    dialog.querySelectorAll('.class-dialog-close').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.showModal();
}

function createClassDialogShell(title, eyebrow, withForm = true) {
    const dialog = document.createElement('dialog');
    dialog.className = 'unified-dialog';
    dialog.innerHTML = `
        <form>
            <div class="dialog-heading"><div><small>${escapeHtml(eyebrow)}</small><h2>${escapeHtml(title || 'Lớp sinh viên')}</h2></div><button type="button" class="dialog-close class-dialog-close"><i class="bi bi-x-lg"></i></button></div>
            <div class="dialog-fields"></div>
            <p class="dialog-status" role="alert"></p>
            <div class="dialog-actions"></div>
        </form>`;
    document.body.append(dialog);
    dialog.addEventListener('close', () => dialog.remove());
    dialog.querySelectorAll('.class-dialog-close').forEach((button) => button.addEventListener('click', () => dialog.close()));
    if (withForm) dialog.querySelector('form').addEventListener('submit', (event) => event.preventDefault());
    return dialog;
}

function openStudentClassDialog(record, options, onSave) {
    const selected = (value, current) => normalizeIdentifier(value) === normalizeIdentifier(current);
    const available = (item, current) => item.isActive !== false || selected(item.id, current);
    const labels = {
        department: (item) => [item.code, item.name, item.isActive === false ? '(Ngừng hoạt động)' : ''].filter(Boolean).join(' - '),
        major: (item) => [item.code, item.name, item.isActive === false ? '(Ngừng hoạt động)' : ''].filter(Boolean).join(' - '),
        program: (item) => [item.code, item.name, item.isActive === false ? '(Ngừng hoạt động)' : ''].filter(Boolean).join(' - '),
        year: (item) => [item.code, item.name, item.year].filter(Boolean).join(' - '),
        lecturer: (item) => [item.lecturerCode, item.fullName, item.isActive === false ? '(Ngừng hoạt động)' : ''].filter(Boolean).join(' - ')
    };
    const optionHtml = (items, current, label) => items.filter((item) => available(item, current))
        .map((item) => `<option value="${escapeHtml(item.id)}"${selected(item.id, current) ? ' selected' : ''}>${escapeHtml(label(item))}</option>`)
        .join('');
    const dialog = createClassDialogShell(record ? `Cập nhật lớp ${record.code}` : 'Thêm lớp sinh viên', 'QUẢN LÝ LỚP SINH VIÊN');
    const fields = dialog.querySelector('.dialog-fields');
    fields.innerHTML = `
        <div class="dialog-field"><label for="studentClassCode">Mã lớp</label><input id="studentClassCode" name="code" required maxlength="100" value="${escapeHtml(record?.code || '')}" placeholder="Ví dụ: IT24A" /></div>
        <div class="dialog-field"><label for="studentClassName">Tên lớp</label><input id="studentClassName" name="name" required maxlength="255" value="${escapeHtml(record?.name || '')}" placeholder="Ví dụ: Công nghệ thông tin 24A" /></div>
        <div class="dialog-field"><label for="studentClassFormDepartment">Khoa</label><select id="studentClassFormDepartment" name="departmentId" required><option value="">-- Chọn khoa --</option>${optionHtml(options.departments, record?.departmentId, labels.department)}</select></div>
        <div class="dialog-field"><label for="studentClassFormMajor">Ngành</label><select id="studentClassFormMajor" name="majorId" required><option value="">-- Chọn ngành --</option></select></div>
        <div class="dialog-field"><label for="studentClassFormProgram">Chương trình đào tạo</label><select id="studentClassFormProgram" name="trainingProgramId" required><option value="">-- Chọn chương trình --</option></select></div>
        <div class="dialog-field"><label for="studentClassFormYear">Niên khóa</label><select id="studentClassFormYear" name="academicYearId" required><option value="">-- Chọn niên khóa --</option>${optionHtml(options.academicYears, record?.academicYearId, labels.year)}</select></div>
        <div class="dialog-field"><label for="studentClassFormAdvisor">Cố vấn học tập (không bắt buộc)</label><select id="studentClassFormAdvisor" name="advisorId"><option value="">-- Chưa phân công --</option>${optionHtml(options.lecturers, record?.advisorId, labels.lecturer)}</select></div>
        <label class="catalog-checkbox"><input type="checkbox" name="isActive" ${record?.isActive === false ? '' : 'checked'} /> Đang hoạt động</label>`;
    const status = dialog.querySelector('.dialog-status');
    const departmentSelect = fields.querySelector('[name="departmentId"]');
    const majorSelect = fields.querySelector('[name="majorId"]');
    const programSelect = fields.querySelector('[name="trainingProgramId"]');
    const refreshMajors = (selectedMajor = '') => {
        const departmentId = departmentSelect.value;
        const rows = options.majors.filter((item) => normalizeIdentifier(item.departmentId) === normalizeIdentifier(departmentId));
        majorSelect.innerHTML = `<option value="">-- Chọn ngành --</option>${optionHtml(rows, selectedMajor || record?.majorId, labels.major)}`;
        if (!Array.from(majorSelect.options).some((item) => selected(item.value, selectedMajor || record?.majorId))) majorSelect.value = '';
        refreshPrograms(selectedMajor || majorSelect.value, record?.trainingProgramId);
    };
    const refreshPrograms = (majorId = '', selectedProgram = '') => {
        const departmentId = departmentSelect.value;
        const rows = options.programs.filter((item) =>
            normalizeIdentifier(item.departmentId) === normalizeIdentifier(departmentId)
            && normalizeIdentifier(item.majorId) === normalizeIdentifier(majorId));
        programSelect.innerHTML = `<option value="">-- Chọn chương trình --</option>${optionHtml(rows, selectedProgram, labels.program)}`;
    };
    departmentSelect.addEventListener('change', () => refreshMajors());
    majorSelect.addEventListener('change', () => refreshPrograms(majorSelect.value));
    refreshMajors(record?.majorId || '');
    dialog.querySelector('.dialog-actions').innerHTML = '<button type="button" class="outline-button class-dialog-close">Hủy</button><button type="submit" class="primary-button">Lưu lớp</button>';
    dialog.querySelectorAll('.class-dialog-close').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.querySelector('form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const formData = new FormData(event.currentTarget);
        const payload = {
            code: String(formData.get('code') || '').trim(),
            name: String(formData.get('name') || '').trim(),
            departmentId: formData.get('departmentId'),
            majorId: formData.get('majorId'),
            trainingProgramId: formData.get('trainingProgramId'),
            academicYearId: formData.get('academicYearId'),
            advisorId: formData.get('advisorId') || null,
            isActive: formData.has('isActive')
        };
        const major = options.majors.find((item) => selected(item.id, payload.majorId));
        const program = options.programs.find((item) => selected(item.id, payload.trainingProgramId));
        if (!major || !program
                || !selected(major.departmentId, payload.departmentId)
                || !selected(program.departmentId, payload.departmentId)
                || !selected(program.majorId, payload.majorId)) {
            status.textContent = 'Khoa, ngành và chương trình đào tạo phải thuộc đúng quan hệ.';
            return;
        }
        try {
            await onSave(record, payload);
            dialog.close();
        } catch (error) {
            status.textContent = error.message;
        }
    });
    dialog.showModal();
}

function showClassPageError(message) {
    const status = document.getElementById('studentClassMessage');
    if (status) {
        status.textContent = message;
        status.hidden = false;
    }
}

async function renderSimpleResource(endpoint, kind) {
    const rows = await springRequest(endpoint).catch(() => []);
    let lookupDefinitions = {};
    let formSelects = {};
    if (kind === 'lecturer') {
        const departments = await springRequest('/departments').catch(() => []);
        lookupDefinitions = {
            departmentId: { rows: departments, codeKey: 'code', nameKey: 'name' }
        };
        formSelects = {
            departmentId: {
                rows: departments,
                label: (item) => [item.code, item.name].filter(Boolean).join(' - ')
            }
        };
    } else if (kind === 'course') {
        const departments = await springRequest('/departments').catch(() => []);
        lookupDefinitions = {
            departmentId: { rows: departments, codeKey: 'code', nameKey: 'name' }
        };
    } else if (kind === 'course-section') {
        const [courses, semesters, gradingScales] = await Promise.all([
            springRequest('/courses').catch(() => []),
            springRequest('/semesters').catch(() => []),
            springRequest('/academic-form-options/grading-scales').catch(() => [])
        ]);
        lookupDefinitions = {
            courseId: { rows: courses, codeKey: 'code', nameKey: 'name' },
            semesterId: { rows: semesters, codeKey: 'code', nameKey: 'name' },
            gradingScaleId: { rows: gradingScales, codeKey: 'code', nameKey: 'name' }
        };
        formSelects = {
            courseId: {
                rows: courses,
                label: (item) => [item.code, item.name].filter(Boolean).join(' - ')
            },
            semesterId: {
                rows: semesters,
                label: (item) => [item.code, item.name].filter(Boolean).join(' - ')
            },
            gradingScaleId: {
                rows: gradingScales,
                label: (item) => [item.code, item.name, item.status].filter(Boolean).join(' - ')
            }
        };
    }
    const config = {
        lecturer: {
            title: 'Quản lý giảng viên',
            description: 'Thông tin giảng viên, liên hệ và khoa quản lý.',
            fields: [['lecturerCode', 'Mã giảng viên'], ['fullName', 'Họ và tên'], ['email', 'Email'], ['phone', 'Số điện thoại'], ['departmentId', 'Khoa']],
            formFields: [['lecturerCode', 'Mã giảng viên'], ['fullName', 'Họ và tên'], ['email', 'Email'], ['phone', 'Số điện thoại'], ['departmentId', 'Khoa'], ['initialPassword', 'Mật khẩu ban đầu']],
            createOnlyFields: ['initialPassword'],
            inputTypes: { initialPassword: 'password', email: 'email', phone: 'tel' },
            requiredFields: ['lecturerCode', 'fullName', 'departmentId', 'initialPassword'],
            inputAttributes: { initialPassword: { minlength: '12', maxlength: '72', autocomplete: 'new-password' } },
            addLabel: 'Thêm giảng viên'
        },
        'academic-year': {
            title: 'Quản lý niên khóa',
            description: 'Thiết lập niên khóa và thời gian đào tạo.',
            fields: [['code', 'Mã niên khóa'], ['name', 'Tên niên khóa'], ['year', 'Niên khóa'], ['startDate', 'Ngày bắt đầu'], ['endDate', 'Ngày kết thúc'], ['isActive', 'Trạng thái']],
            addLabel: 'Thêm niên khóa'
        },
        course: {
            title: 'Quản lý học phần',
            description: 'Danh mục học phần, tín chỉ, khoa và loại học phần.',
            fields: [['code', 'Mã học phần'], ['name', 'Tên học phần'], ['nameEn', 'Tên tiếng Anh'], ['credits', 'Số tín chỉ'], ['departmentId', 'Khoa'], ['courseType', 'Loại học phần']],
            addLabel: 'Thêm học phần'
        },
        'course-section': {
            title: 'Quản lý lớp học phần',
            description: 'Học phần, học kỳ, thang điểm, sĩ số và trạng thái lớp.',
            fields: [['code', 'Mã lớp'], ['name', 'Tên lớp'], ['courseId', 'Học phần'], ['semesterId', 'Học kỳ'], ['gradingScaleId', 'Thang điểm'], ['maxStudents', 'Sĩ số tối đa'], ['status', 'Trạng thái']],
            requiredFields: ['code', 'name', 'courseId', 'semesterId', 'gradingScaleId'],
            formNotice: lookupDefinitions.gradingScaleId.rows.length
                ? ''
                : 'Chưa có thang điểm trong cơ sở dữ liệu. Cần tạo thang điểm trước khi thêm lớp học phần.',
            addLabel: 'Thêm lớp học phần'
        }
    }[kind];
    renderResourceShell({
        ...config,
        endpoint,
        rows,
        lookupDefinitions,
        formSelects
    });
}

async function renderSchoolYearPage() {
    const [schoolYears, semesters] = await Promise.all([
        springRequest('/school-years').catch(() => []),
        springRequest('/semesters').catch(() => [])
    ]);
    const semesterLookup = {
        schoolYearId: { rows: schoolYears, codeKey: 'code', nameKey: 'name' }
    };
    const tabs = [
        { id: 'school-years', title: 'Năm học', rows: schoolYears, fields: [['code', 'Mã năm học'], ['name', 'Tên năm học'], ['startDate', 'Ngày bắt đầu'], ['endDate', 'Ngày kết thúc'], ['isActive', 'Trạng thái']], lookupDefinitions: null },
        { id: 'semesters', title: 'Học kỳ', rows: semesters, fields: [['code', 'Mã học kỳ'], ['name', 'Tên học kỳ'], ['schoolYearId', 'Năm học'], ['startDate', 'Ngày bắt đầu'], ['endDate', 'Ngày kết thúc'], ['isActive', 'Trạng thái']], lookupDefinitions: semesterLookup }
    ];
    renderTabbedResource('Năm học & học kỳ', 'Thiết lập năm học và học kỳ.', tabs);
}

const academicCatalogDefinitions = {
    departments: {
        title: 'Khoa',
        endpoint: '/departments',
        columns: [['code', 'Mã khoa'], ['name', 'Tên khoa'], ['establishedYear', 'Năm thành lập'], ['isActive', 'Trạng thái']],
        fields: [
            { name: 'code', label: 'Mã khoa', required: true, maxLength: 100 },
            { name: 'name', label: 'Tên khoa', required: true, maxLength: 255 },
            { name: 'establishedYear', label: 'Năm thành lập', type: 'number', min: 1, max: 32767, step: 1 },
            { name: 'description', label: 'Mô tả', type: 'textarea', maxLength: 500 },
            { name: 'isActive', label: 'Đang hoạt động', type: 'checkbox' }
        ]
    },
    majors: {
        title: 'Ngành',
        endpoint: '/majors',
        filters: [{ name: 'departmentId', label: 'Tất cả khoa', lookup: 'departments' }],
        columns: [['code', 'Mã ngành'], ['name', 'Tên ngành'], ['departmentId', 'Khoa'], ['effectiveDate', 'Ngày hiệu lực'], ['expiryDate', 'Ngày hết hiệu lực'], ['isActive', 'Trạng thái']],
        fields: [
            { name: 'departmentId', label: 'Khoa', type: 'lookup', lookup: 'departments', required: true },
            { name: 'code', label: 'Mã ngành', required: true, maxLength: 50 },
            { name: 'name', label: 'Tên ngành', required: true, maxLength: 255 },
            { name: 'effectiveDate', label: 'Ngày hiệu lực', type: 'date' },
            { name: 'expiryDate', label: 'Ngày hết hiệu lực', type: 'date' },
            { name: 'description', label: 'Mô tả', type: 'textarea', maxLength: 500 },
            { name: 'isActive', label: 'Đang hoạt động', type: 'checkbox' }
        ]
    },
    'training-programs': {
        title: 'Chương trình đào tạo',
        endpoint: '/training-programs',
        filters: [
            { name: 'departmentId', label: 'Tất cả khoa', lookup: 'departments' },
            { name: 'majorId', label: 'Tất cả ngành', lookup: 'majors', dependsOn: 'departmentId' }
        ],
        columns: [['code', 'Mã chương trình'], ['name', 'Tên chương trình'], ['departmentId', 'Khoa'], ['majorId', 'Ngành'], ['admissionYear', 'Năm tuyển sinh'], ['isActive', 'Trạng thái']],
        fields: [
            { name: 'departmentId', label: 'Khoa', type: 'lookup', lookup: 'departments', required: true },
            { name: 'majorId', label: 'Ngành', type: 'lookup', lookup: 'majors', dependsOn: 'departmentId', required: true },
            { name: 'code', label: 'Mã chương trình', required: true, maxLength: 50 },
            { name: 'name', label: 'Tên chương trình', required: true, maxLength: 255 },
            { name: 'admissionYear', label: 'Năm tuyển sinh', type: 'number', min: 1, max: 32767, step: 1 },
            { name: 'durationYears', label: 'Thời gian đào tạo (năm)', type: 'number', min: 0.1, max: 999.9, step: 0.1 },
            { name: 'totalCredits', label: 'Tổng số tín chỉ', type: 'number', min: 0, max: 9999.99, step: 0.01 },
            { name: 'effectiveDate', label: 'Ngày hiệu lực', type: 'date' },
            { name: 'expiryDate', label: 'Ngày hết hiệu lực', type: 'date' },
            { name: 'status', label: 'Trạng thái', maxLength: 30 },
            { name: 'description', label: 'Mô tả', type: 'textarea', maxLength: 500 },
            { name: 'isActive', label: 'Đang hoạt động', type: 'checkbox' }
        ]
    },
    'academic-years': {
        title: 'Niên khóa',
        endpoint: '/academic-years',
        columns: [['code', 'Mã niên khóa'], ['name', 'Tên niên khóa'], ['year', 'Niên khóa'], ['startDate', 'Ngày bắt đầu'], ['endDate', 'Ngày kết thúc'], ['isActive', 'Trạng thái']],
        fields: [
            { name: 'code', label: 'Mã niên khóa', required: true, maxLength: 100 },
            { name: 'name', label: 'Tên niên khóa', required: true, maxLength: 100 },
            { name: 'year', label: 'Khoảng niên khóa', placeholder: 'Ví dụ: 2024-2028', maxLength: 20 },
            { name: 'description', label: 'Mô tả', type: 'textarea', maxLength: 255 },
            { name: 'startDate', label: 'Ngày bắt đầu', type: 'date' },
            { name: 'endDate', label: 'Ngày kết thúc', type: 'date' },
            { name: 'isActive', label: 'Đang hoạt động', type: 'checkbox' }
        ]
    },
    'school-years': {
        title: 'Năm học',
        endpoint: '/school-years',
        columns: [['code', 'Mã năm học'], ['name', 'Tên năm học'], ['startDate', 'Ngày bắt đầu'], ['endDate', 'Ngày kết thúc'], ['isActive', 'Trạng thái']],
        fields: [
            { name: 'code', label: 'Mã năm học', required: true, maxLength: 100 },
            { name: 'name', label: 'Tên năm học', required: true, maxLength: 255 },
            { name: 'description', label: 'Mô tả', type: 'textarea', maxLength: 255 },
            { name: 'note', label: 'Ghi chú', type: 'textarea', maxLength: 255 },
            { name: 'startDate', label: 'Ngày bắt đầu', type: 'date' },
            { name: 'endDate', label: 'Ngày kết thúc', type: 'date' },
            { name: 'isActive', label: 'Đang hoạt động', type: 'checkbox' }
        ]
    },
    semesters: {
        title: 'Học kỳ',
        endpoint: '/semesters',
        filters: [{ name: 'schoolYearId', label: 'Tất cả năm học', lookup: 'school-years' }],
        columns: [['code', 'Mã học kỳ'], ['name', 'Tên học kỳ'], ['schoolYearId', 'Năm học'], ['startDate', 'Ngày bắt đầu'], ['endDate', 'Ngày kết thúc'], ['isActive', 'Trạng thái']],
        fields: [
            { name: 'schoolYearId', label: 'Năm học', type: 'lookup', lookup: 'school-years', required: true },
            { name: 'code', label: 'Mã học kỳ', required: true, maxLength: 100 },
            { name: 'name', label: 'Tên học kỳ', required: true, maxLength: 255 },
            { name: 'startDate', label: 'Ngày bắt đầu', type: 'date' },
            { name: 'endDate', label: 'Ngày kết thúc', type: 'date' },
            { name: 'isActive', label: 'Đang hoạt động', type: 'checkbox' }
        ]
    }
};

async function renderAcademicCatalogs(activeKey = 'departments') {
    portalContent.innerHTML = '<section class="panel-card"><div class="empty-state"><strong>Đang tải danh mục đào tạo...</strong></div></section>';
    const entries = Object.entries(academicCatalogDefinitions);
    let data;
    try {
        const values = await Promise.all(entries.map(([, definition]) => springRequest(definition.endpoint)));
        data = Object.fromEntries(entries.map(([key], index) => [key, values[index]]));
    } catch (error) {
        portalContent.innerHTML = `
            <section class="page-heading"><div><h2>Danh mục đào tạo</h2><p>Không tải được dữ liệu từ Spring Boot.</p></div></section>
            <div class="catalog-error" role="alert">${escapeHtml(catalogErrorMessage(error))}</div>
            <button type="button" class="outline-button" id="catalogRetry"><i class="bi bi-arrow-clockwise"></i>Thử lại</button>`;
        document.getElementById('catalogRetry').addEventListener('click', () => renderAcademicCatalogs(activeKey));
        return;
    }
    renderAcademicCatalogPage(data, activeKey);
}

function catalogErrorMessage(error) {
    const descriptions = {
        400: 'Dữ liệu gửi lên không hợp lệ',
        401: 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại',
        403: 'Tài khoản hiện tại không có quyền quản lý danh mục',
        409: 'Dữ liệu bị trùng hoặc xung đột với dữ liệu hiện có',
        500: 'Máy chủ gặp lỗi khi xử lý yêu cầu'
    };
    const description = descriptions[error.status];
    return description
        ? `${description} (HTTP ${error.status}): ${error.message}`
        : error.message || 'Không thể kết nối máy chủ.';
}

function renderAcademicCatalogPage(data, activeKey) {
    const definition = academicCatalogDefinitions[activeKey];
    const tabs = Object.entries(academicCatalogDefinitions).map(([key, item]) =>
        `<button type="button" class="resource-tab${key === activeKey ? ' active' : ''}" data-catalog-tab="${key}">${escapeHtml(item.title)}</button>`
    ).join('');
    const filters = (definition.filters || []).map((filter) => `
        <select class="catalog-filter" aria-label="${escapeHtml(filter.label)}" data-catalog-filter="${filter.name}">
            ${renderCatalogFilterOptions(filter, data, '')}
        </select>`).join('');
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>Danh mục đào tạo</h2><p>Quản lý các danh mục và quan hệ dữ liệu dùng chung trong hệ thống.</p></div>
            <button type="button" class="primary-button" id="catalogAdd"><i class="bi bi-plus-lg"></i>Thêm ${escapeHtml(definition.title.toLocaleLowerCase('vi'))}</button></section>
        <section class="panel-card resource-card">
            <div class="resource-toolbar catalog-tabs">${tabs}</div>
            <div class="resource-toolbar catalog-toolbar">
                <div class="search-box"><i class="bi bi-search"></i><input id="catalogSearch" type="search" placeholder="Tìm kiếm ${escapeHtml(definition.title.toLocaleLowerCase('vi'))}..." /></div>
                <select class="catalog-filter" aria-label="Lọc trạng thái" data-catalog-filter="isActive">
                    <option value="">Mọi trạng thái</option><option value="true">Đang hoạt động</option><option value="false">Ngừng hoạt động</option>
                </select>
                ${filters}
                <button type="button" class="outline-button" id="catalogRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button>
            </div>
            <div id="catalogPageStatus" class="catalog-error" role="alert" hidden></div>
            <div id="catalogTable"></div>
        </section>
        <dialog id="catalogDialog" class="unified-dialog"></dialog>`;

    const drawTable = () => {
        const search = document.getElementById('catalogSearch').value.trim().toLocaleLowerCase('vi');
        const activeFilter = portalContent.querySelector('[data-catalog-filter="isActive"]').value;
        const relatedFilters = [...portalContent.querySelectorAll('[data-catalog-filter]')]
            .filter((select) => select.dataset.catalogFilter !== 'isActive')
            .map((select) => [select.dataset.catalogFilter, select.value]);
        const rows = (data[activeKey] || []).filter((row) => {
            if (activeFilter !== '' && String(Boolean(row.isActive)) !== activeFilter) return false;
            if (relatedFilters.some(([key, value]) => value && normalizeIdentifier(row[key]) !== normalizeIdentifier(value))) return false;
            return !search || catalogSearchText(activeKey, row, data).includes(search);
        });
        document.getElementById('catalogTable').innerHTML = renderCatalogTable(activeKey, rows, data);
        bindCatalogActions(activeKey, data);
    };

    portalContent.querySelectorAll('[data-catalog-tab]').forEach((button) =>
        button.addEventListener('click', () => renderAcademicCatalogPage(data, button.dataset.catalogTab)));
    portalContent.querySelectorAll('[data-catalog-filter]').forEach((select) => {
        select.addEventListener('change', () => {
            if (select.dataset.catalogFilter === 'departmentId') {
                const majorFilter = portalContent.querySelector('[data-catalog-filter="majorId"]');
                if (majorFilter) {
                    majorFilter.innerHTML = renderCatalogFilterOptions(
                        academicCatalogDefinitions[activeKey].filters.find((filter) => filter.name === 'majorId'),
                        data, select.value);
                }
            }
            drawTable();
        });
    });
    document.getElementById('catalogSearch').addEventListener('input', drawTable);
    document.getElementById('catalogRefresh').addEventListener('click', () => renderAcademicCatalogs(activeKey));
    document.getElementById('catalogAdd').addEventListener('click', () => openCatalogForm(activeKey, null, data));
    drawTable();
}

function renderCatalogFilterOptions(filter, data, parentValue) {
    const options = (data[filter.lookup] || []).filter((item) =>
        (!parentValue || filter.name !== 'majorId'
            || normalizeIdentifier(item.departmentId) === normalizeIdentifier(parentValue))
        && Boolean(item.isActive)
    );
    return `<option value="">${escapeHtml(filter.label)}</option>${options.map((item) =>
        `<option value="${escapeHtml(item.id)}">${escapeHtml(catalogReferenceLabel(item))}</option>`
    ).join('')}`;
}

function catalogSearchText(key, row, data) {
    const values = Object.values(row).filter((value) => value != null).map(String);
    for (const [field, lookup] of Object.entries({ departmentId: 'departments', majorId: 'majors', schoolYearId: 'school-years' })) {
        if (row[field]) {
            const related = (data[lookup] || []).find((item) => normalizeIdentifier(item.id) === normalizeIdentifier(row[field]));
            if (related) values.push(catalogReferenceLabel(related));
        }
    }
    return values.join(' ').toLocaleLowerCase('vi');
}

function catalogReferenceLabel(item) {
    return [item.code, item.name].filter(Boolean).join(' - ') || item.fullName || 'Không có tên';
}

function catalogDisplayValue(key, value, data) {
    if (value == null || value === '') return '<span class="muted-value">Chưa cập nhật</span>';
    if (key === 'isActive') {
        return value
            ? '<span class="status-badge active">Đang hoạt động</span>'
            : '<span class="status-badge muted">Ngừng hoạt động</span>';
    }
    const relatedResources = { departmentId: 'departments', majorId: 'majors', schoolYearId: 'school-years' };
    const lookup = relatedResources[key];
    if (lookup) {
        const related = (data[lookup] || []).find((item) => normalizeIdentifier(item.id) === normalizeIdentifier(value));
        return escapeHtml(related ? catalogReferenceLabel(related) : 'Danh mục liên quan không còn hoạt động');
    }
    if (key === 'startDate' || key === 'endDate' || key === 'effectiveDate' || key === 'expiryDate') {
        return escapeHtml(String(value).slice(0, 10));
    }
    return escapeHtml(String(value));
}

function renderCatalogTable(key, rows, data) {
    const definition = academicCatalogDefinitions[key];
    if (!rows.length) {
        return `<div class="empty-state"><i class="bi bi-inbox"></i><strong>Không có bản ghi phù hợp</strong><span>Thử thay đổi từ khóa hoặc bộ lọc.</span></div>`;
    }
    const body = rows.map((row) => `
        <tr>${definition.columns.map(([field]) =>
            `<td>${catalogDisplayValue(field, row[field], data)}</td>`
        ).join('')}
            <td class="table-actions">
                <button type="button" class="icon-button" title="Xem chi tiết" aria-label="Xem chi tiết" data-catalog-action="detail" data-id="${escapeHtml(row.id)}"><i class="bi bi-eye"></i></button>
                <button type="button" class="icon-button edit" title="Sửa" aria-label="Sửa" data-catalog-action="edit" data-id="${escapeHtml(row.id)}"><i class="bi bi-pencil-square"></i></button>
                <button type="button" class="icon-button delete" title="Xóa mềm" aria-label="Xóa mềm" data-catalog-action="delete" data-id="${escapeHtml(row.id)}"><i class="bi bi-trash"></i></button>
            </td>
        </tr>`).join('');
    return `<div class="table-scroll"><table class="unified-table"><thead><tr>${definition.columns.map(([, label]) =>
        `<th>${escapeHtml(label)}</th>`
    ).join('')}<th class="text-end">Thao tác</th></tr></thead><tbody>${body}</tbody></table></div>
        <div class="table-footer"><span>${rows.length} bản ghi</span></div>`;
}

function bindCatalogActions(key, data) {
    document.querySelectorAll('[data-catalog-action]').forEach((button) => {
        button.addEventListener('click', async () => {
            const action = button.dataset.catalogAction;
            const definition = academicCatalogDefinitions[key];
            try {
                if (action === 'delete') {
                    if (!confirm('Xóa mềm bản ghi này? Dữ liệu vẫn được giữ trong cơ sở dữ liệu.')) return;
                    await springRequest(`${definition.endpoint}/${encodeURIComponent(button.dataset.id)}`, { method: 'DELETE' });
                    await renderAcademicCatalogs(key);
                    return;
                }
                const record = await springRequest(`${definition.endpoint}/${encodeURIComponent(button.dataset.id)}`);
                if (action === 'edit') openCatalogForm(key, record, data);
                else showCatalogDetails(key, record, data);
            } catch (error) {
                const status = document.getElementById('catalogPageStatus');
                status.textContent = catalogErrorMessage(error);
                status.hidden = false;
            }
        });
    });
}

function showCatalogDetails(key, record, data) {
    const definition = academicCatalogDefinitions[key];
    const dialog = document.getElementById('catalogDialog');
    const details = definition.fields.map((field) => `
        <div><dt>${escapeHtml(field.label)}</dt><dd>${catalogDisplayValue(field.name, record[field.name], data)}</dd></div>`
    ).join('');
    dialog.innerHTML = `
        <div class="dialog-heading"><div><small>CHI TIẾT DANH MỤC</small><h2>${escapeHtml(definition.title)}</h2></div>
            <button type="button" class="dialog-close" data-catalog-close aria-label="Đóng"><i class="bi bi-x-lg"></i></button></div>
        <dl class="catalog-details">${details}</dl>
        <div class="dialog-actions"><button type="button" class="outline-button" data-catalog-close>Đóng</button></div>`;
    dialog.querySelectorAll('[data-catalog-close]').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.showModal();
}

function openCatalogForm(key, record, data) {
    const definition = academicCatalogDefinitions[key];
    const dialog = document.getElementById('catalogDialog');
    const inputs = definition.fields.map((field) => renderCatalogField(field, record, data)).join('');
    dialog.innerHTML = `
        <form id="catalogForm">
            <div class="dialog-heading"><div><small>QUẢN LÝ ĐÀO TẠO</small><h2>${record ? 'Cập nhật' : 'Thêm'} ${escapeHtml(definition.title.toLocaleLowerCase('vi'))}</h2></div>
                <button type="button" class="dialog-close" data-catalog-close aria-label="Đóng"><i class="bi bi-x-lg"></i></button></div>
            <p id="catalogFormStatus" class="dialog-status" role="alert"></p>
            <div class="dialog-fields">${inputs}</div>
            <div class="dialog-actions"><button type="button" class="outline-button" data-catalog-close>Hủy</button><button type="submit" class="primary-button">Lưu dữ liệu</button></div>
        </form>`;
    dialog.querySelectorAll('[data-catalog-close]').forEach((button) => button.addEventListener('click', () => dialog.close()));
    const form = dialog.querySelector('#catalogForm');
    const majorSelect = form.elements.namedItem('majorId');
    const departmentSelect = form.elements.namedItem('departmentId');
    if (majorSelect && departmentSelect) {
        departmentSelect.addEventListener('change', () => {
            const currentMajorId = majorSelect.value;
            populateCatalogMajorOptions(majorSelect, data, departmentSelect.value, currentMajorId);
        });
    }
    dialog.showModal();
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const status = dialog.querySelector('#catalogFormStatus');
        const submit = form.querySelector('[type="submit"]');
        const payload = {};
        for (const field of definition.fields) {
            const input = form.elements.namedItem(field.name);
            if (field.type === 'checkbox') payload[field.name] = input.checked;
            else if (!input.value.trim()) payload[field.name] = null;
            else payload[field.name] = field.type === 'number' ? Number(input.value) : input.value.trim();
        }
        submit.disabled = true;
        try {
            await springRequest(`${definition.endpoint}${record ? `/${encodeURIComponent(record.id)}` : ''}`, {
                method: record ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            dialog.close();
            await renderAcademicCatalogs(key);
        } catch (error) {
            status.textContent = catalogErrorMessage(error);
        } finally {
            submit.disabled = false;
        }
    });
}

function renderCatalogField(field, record, data) {
    const value = record?.[field.name];
    const required = field.required ? ' required' : '';
    const maxLength = field.maxLength ? ` maxlength="${field.maxLength}"` : '';
    if (field.type === 'checkbox') {
        return `<label class="catalog-checkbox"><input type="checkbox" name="${field.name}"${value == null ? ' checked' : value ? ' checked' : ''}><span>${escapeHtml(field.label)}</span></label>`;
    }
    if (field.type === 'lookup') {
        const selectOptions = catalogLookupItems(field, record, data);
        const options = selectOptions.map((item) =>
            `<option value="${escapeHtml(item.id)}"${normalizeIdentifier(item.id) === normalizeIdentifier(value) ? ' selected' : ''}>${escapeHtml(catalogReferenceLabel(item))}</option>`
        ).join('');
        return `<div class="dialog-field"><label>${escapeHtml(field.label)}</label><select name="${field.name}"${required}><option value="">-- Chọn ${escapeHtml(field.label.toLocaleLowerCase('vi'))} --</option>${options}</select></div>`;
    }
    const type = field.type || 'text';
    const safeValue = value == null ? '' : type === 'date' ? String(value).slice(0, 10) : String(value);
    const limits = ['min', 'max', 'step'].filter((key) => field[key] != null)
        .map((key) => `${key}="${field[key]}"`).join(' ');
    if (type === 'textarea') {
        return `<div class="dialog-field"><label>${escapeHtml(field.label)}</label><textarea name="${field.name}" rows="3"${maxLength}>${escapeHtml(safeValue)}</textarea></div>`;
    }
    return `<div class="dialog-field"><label>${escapeHtml(field.label)}</label><input name="${field.name}" type="${type}" value="${escapeHtml(safeValue)}"${required}${maxLength}${limits ? ` ${limits}` : ''} placeholder="${escapeHtml(field.placeholder || '')}"></div>`;
}

function catalogLookupItems(field, record, data) {
    const parentValue = field.dependsOn
        ? document.querySelector(`#catalogForm [name="${field.dependsOn}"]`)?.value || record?.[field.dependsOn]
        : '';
    return (data[field.lookup] || []).filter((item) => {
        const active = Boolean(item.isActive) || normalizeIdentifier(item.id) === normalizeIdentifier(record?.[field.name]);
        const parentMatches = !field.dependsOn || (parentValue
            && normalizeIdentifier(item.departmentId) === normalizeIdentifier(parentValue));
        return active && parentMatches;
    });
}

function populateCatalogMajorOptions(select, data, departmentId, selectedMajorId) {
    const majors = (data.majors || []).filter((item) =>
        Boolean(item.isActive)
        && normalizeIdentifier(item.departmentId) === normalizeIdentifier(departmentId));
    select.innerHTML = '<option value="">-- Chọn ngành --</option>' + majors.map((item) =>
        `<option value="${escapeHtml(item.id)}">${escapeHtml(catalogReferenceLabel(item))}</option>`
    ).join('');
    if (majors.some((item) => normalizeIdentifier(item.id) === normalizeIdentifier(selectedMajorId))) {
        select.value = selectedMajorId;
    }
}

function renderTabbedResource(title, description, tabs) {
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>${escapeHtml(title)}</h2><p>${escapeHtml(description)}</p></div></section>
        <section class="panel-card resource-card">
            <div class="resource-toolbar">
                <div class="resource-tabs">${tabs.map((tab, index) => `<button type="button" class="resource-tab${index === 0 ? ' active' : ''}" data-tab-index="${index}">${escapeHtml(tab.title)}</button>`).join('')}</div>
            </div>
            <div id="tabbedResourceTable"></div>
        </section>`;
    const tableContainer = document.getElementById('tabbedResourceTable');
    const showTab = (index) => {
        document.querySelectorAll('.resource-tab').forEach((tab, tabIndex) =>
            tab.classList.toggle('active', tabIndex === index));
        tableContainer.innerHTML = renderTable(tabs[index].rows, tabs[index].fields, tabs[index].lookupDefinitions);
    };
    portalContent.querySelectorAll('.resource-tab').forEach((tab) =>
        tab.addEventListener('click', () => showTab(Number(tab.dataset.tabIndex))));
    showTab(0);
}

function renderResourceShell(config) {
    portalContent.innerHTML = `
        <section class="page-heading">
            <div><h2>${escapeHtml(config.title)}</h2><p>${escapeHtml(config.description)}</p></div>
            <button type="button" class="primary-button" id="resourceAdd"><i class="bi bi-plus-lg"></i>${escapeHtml(config.addLabel || 'Thêm mới')}</button>
        </section>
        <section class="panel-card resource-card">
            <div class="resource-toolbar">
                <div class="search-box"><i class="bi bi-search"></i><input id="resourceSearch" type="search" placeholder="Tìm kiếm..." /></div>
                <button type="button" class="outline-button" id="resourceRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button>
            </div>
            <div id="resourceTable">${renderTable(config.rows, config.fields, config.lookupDefinitions)}</div>
        </section>
        <dialog id="resourceDialog" class="unified-dialog">
            <form id="resourceForm">
                <div class="dialog-heading"><div><small>QUẢN LÝ ĐÀO TẠO</small><h2 id="resourceDialogTitle">${escapeHtml(config.addLabel || 'Thêm mới')}</h2></div><button type="button" class="dialog-close" id="resourceDialogClose"><i class="bi bi-x-lg"></i></button></div>
                <p id="resourceDialogStatus" class="dialog-status"></p>
                <div class="dialog-actions"><button type="button" class="outline-button" id="resourceDialogCancel">Hủy</button><button type="submit" class="primary-button">Lưu dữ liệu</button></div>
            </form>
        </dialog>`;

    const search = document.getElementById('resourceSearch');
    search.addEventListener('input', () => {
        const keyword = search.value.trim().toLocaleLowerCase('vi');
        const filtered = config.rows.filter((row) =>
            Object.values(row).some((value) => value != null && String(value).toLocaleLowerCase('vi').includes(keyword)));
        document.getElementById('resourceTable').innerHTML = renderTable(filtered, config.fields, config.lookupDefinitions);
        bindResourceTableActions(config);
    });
    document.getElementById('resourceRefresh').addEventListener('click', async () => {
        const rows = await springRequest(config.endpoint).catch(() => []);
        config.rows = rows;
        document.getElementById('resourceTable').innerHTML = renderTable(rows, config.fields);
        bindResourceTableActions(config);
    });
    bindResourceTableActions(config);

    document.getElementById('resourceAdd').addEventListener('click', () => openResourceDialog(config));
}

function renderTable(rows, fields, lookupDefinitions = null) {
    if (!Array.isArray(rows) || !rows.length) {
        return `<div class="empty-state"><i class="bi bi-inbox"></i><strong>Chưa có dữ liệu</strong><span>Danh sách trống hoặc dữ liệu chưa được tải.</span></div>`;
    }
    const body = rows.map((row) => `
        <tr>
            ${fields.map(([key, label]) => `<td><span class="mobile-label">${escapeHtml(label)}</span>${renderValue(key, row[key], lookupDefinitions)}</td>`).join('')}
            <td class="table-actions"><button type="button" class="icon-button edit" data-edit-id="${escapeHtml(row.id)}" title="Sửa"><i class="bi bi-pencil-square"></i></button><button type="button" class="icon-button delete" data-delete-id="${escapeHtml(row.id)}" title="Xóa"><i class="bi bi-trash"></i></button></td>
        </tr>`).join('');
    return `<div class="table-scroll"><table class="unified-table"><thead><tr>${fields.map(([, label]) => `<th>${escapeHtml(label)}</th>`).join('')}<th class="text-end">Thao tác</th></tr></thead><tbody>${body}</tbody></table></div><div class="table-footer"><span>${rows.length} bản ghi</span></div>`;
}

function renderValue(key, value, lookupDefinitions = null) {
    if (value == null || value === '') return '<span class="muted-value">Chưa cập nhật</span>';
    if (key === 'isActive') return value ? '<span class="status-badge active">Đang hiệu lực</span>' : '<span class="status-badge muted">Ngừng hiệu lực</span>';
    if (key === 'status') {
        const statusLabels = {
            open: 'Đang mở',
            planned: 'Dự kiến',
            closed: 'Đã đóng',
            canceled: 'Đã hủy',
            cancelled: 'Đã hủy',
            REGISTERED: 'Chờ duyệt',
            CONFIRMED: 'Đã duyệt',
            REJECTED: 'Đã từ chối',
            STUDYING: 'Đang học',
            COMPLETED: 'Hoàn thành',
            DROPPED: 'Đã rút'
        };
        const label = statusLabels[String(value)] || statusLabels[String(value).toUpperCase()] || String(value);
        const kind = ['open', 'CONFIRMED', 'COMPLETED'].includes(String(value)) ? 'active'
            : ['closed', 'canceled', 'cancelled', 'CANCELLED', 'DROPPED', 'REJECTED'].includes(String(value)) ? 'muted'
            : 'waiting';
        return `<span class="status-badge ${kind}">${escapeHtml(label)}</span>`;
    }
    if (key === 'courseType') {
        const courseLabels = { required: 'Bắt buộc', elective: 'Tự chọn', practical: 'Thực hành', GENERAL: 'Đại cương', MAJOR: 'Chuyên ngành' };
        return escapeHtml(courseLabels[String(value)] || courseLabels[String(value).toUpperCase()] || String(value));
    }
    const lookup = lookupDefinitions?.[key];
    if (lookup && Array.isArray(lookup.rows)) {
        const found = lookup.rows.find((item) => normalizeIdentifier(item.id) === normalizeIdentifier(value));
        if (found) {
            const code = found.code || found.lecturerCode || found.studentCode;
            const name = found.name || found.fullName || found.roomName;
            return escapeHtml([code, name].filter(Boolean).join(' — ') || String(value));
        }
    }
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)) return escapeHtml(value.slice(0, 16).replace('T', ' '));
    return escapeHtml(String(value));
}

function bindResourceTableActions(config) {
    document.querySelectorAll('[data-delete-id]').forEach((button) => {
        button.addEventListener('click', async () => {
            if (!confirm('Xóa mềm bản ghi này?')) return;
            await springRequest(`/${config.endpoint.split('/').filter(Boolean).pop()}/${encodeURIComponent(button.dataset.deleteId)}`, { method: 'DELETE' }).catch((error) => alert(error.message));
            const rows = await springRequest(config.endpoint).catch(() => []);
            config.rows = rows;
            document.getElementById('resourceTable').innerHTML = renderTable(rows, config.fields);
            bindResourceTableActions(config);
        });
    });
    document.querySelectorAll('[data-edit-id]').forEach((button) =>
        button.addEventListener('click', () => openResourceDialog(config, config.rows.find((row) => row.id === button.dataset.editId))));
}

function openResourceDialog(config, record = null) {
    const dialog = document.getElementById('resourceDialog');
    const status = document.getElementById('resourceDialogStatus');
    document.getElementById('resourceDialogTitle').textContent = record ? `Cập nhật ${config.title.toLocaleLowerCase('vi')}` : (config.addLabel || 'Thêm mới');
    const form = document.getElementById('resourceForm');
    const existingFields = form.querySelectorAll('.dialog-fields');
    existingFields.forEach((element) => element.remove());
    const formFields = config.formFields || config.fields;
    const fields = formFields
        .filter(([key]) => !record || !config.createOnlyFields?.includes(key))
        .map(([key, label]) => {
            const required = config.requiredFields?.includes(key)
                && (!record || !config.createOnlyFields?.includes(key));
            const select = config.formSelects?.[key];
            if (select) {
                const selectedValue = record?.[key] == null ? '' : String(record[key]);
                const options = (select.rows || []).filter((item) =>
                    !select.visibleWhen || select.visibleWhen(item, record)).map((item) => {
                    const value = String(item.id);
                    const selected = normalizeIdentifier(value) === normalizeIdentifier(selectedValue) ? ' selected' : '';
                    return `<option value="${escapeHtml(value)}"${selected}>${escapeHtml(select.label(item))}</option>`;
                }).join('');
                return `<div class="dialog-field"><label>${escapeHtml(label)}</label><select name="${escapeHtml(key)}"${required ? ' required' : ''}><option value="">-- Chọn --</option>${options}</select></div>`;
            }
            const type = config.inputTypes?.[key] || 'text';
            const attributes = Object.entries(config.inputAttributes?.[key] || {})
                .map(([name, value]) => `${escapeHtml(name)}="${escapeHtml(value)}"`).join(' ');
            return `<div class="dialog-field"><label>${escapeHtml(label)}</label><input name="${escapeHtml(key)}" type="${escapeHtml(type)}" value="${type === 'password' ? '' : escapeHtml(record?.[key] == null ? '' : String(record[key]))}"${required ? ' required' : ''}${attributes ? ` ${attributes}` : ''} /></div>`;
        }).join('');
    const actions = form.querySelector('.dialog-actions');
    const fieldContainer = document.createElement('div');
    fieldContainer.className = 'dialog-fields';
    fieldContainer.innerHTML = fields;
    form.insertBefore(fieldContainer, actions);
    status.textContent = config.formNotice || '';
    dialog.showModal();
    form.onsubmit = async (event) => {
        event.preventDefault();
        const payload = Object.fromEntries(new FormData(form).entries());
        for (const [key, value] of Object.entries(payload)) {
            if (value === '') payload[key] = null;
            else if (/^[-+]?\d+(\.\d+)?$/.test(value)) payload[key] = Number(value);
        }
        try {
            await springRequest(`${config.endpoint}${record ? `/${encodeURIComponent(record.id)}` : ''}`, {
                method: record ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            dialog.close();
            const rows = await springRequest(config.endpoint).catch(() => []);
            config.rows = rows;
            document.getElementById('resourceTable').innerHTML = renderTable(rows, config.fields);
            bindResourceTableActions(config);
        } catch (error) {
            status.textContent = error.message;
        }
    };
    document.getElementById('resourceDialogClose').onclick = () => dialog.close();
    document.getElementById('resourceDialogCancel').onclick = () => dialog.close();
}

async function renderRegistrationAdmin() {
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>Quản lý đăng ký</h2><p>Theo dõi danh sách đăng ký và xử lý yêu cầu của sinh viên.</p></div><button type="button" class="outline-button" id="registrationRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button></section>
        <section class="panel-card resource-card"><div class="resource-toolbar"><div class="search-box"><i class="bi bi-search"></i><input id="registrationSearch" type="search" placeholder="Tìm sinh viên, mã lớp, trạng thái..." /></div></div><div id="registrationTable"></div></section>`;
    await loadRegistrationTable();
    document.getElementById('registrationRefresh').addEventListener('click', loadRegistrationTable);
}

async function loadRegistrationTable() {
    const rows = await nestRequest('/course-registrations/admin/all').catch(() => []);
    const search = document.getElementById('registrationSearch');
    const render = () => {
        const keyword = search.value.trim().toLocaleLowerCase('vi');
        const filtered = rows.filter((row) => Object.values(row).some((value) =>
            value != null && String(value).toLocaleLowerCase('vi').includes(keyword)));
        document.getElementById('registrationTable').innerHTML = `
            <div class="table-scroll"><table class="unified-table"><thead><tr><th>Sinh viên</th><th>Lớp học phần</th><th>Ngày đăng ký</th><th>Trạng thái</th><th class="text-end">Thao tác</th></tr></thead><tbody>
            ${filtered.map((row) => `<tr>
                <td><strong>${escapeHtml(row.studentCode || 'Không rõ mã SV')}</strong><small class="table-subtext">${escapeHtml(row.studentName || '')}</small></td>
                <td><strong>${escapeHtml(row.sectionCode || '')}</strong><small class="table-subtext">${escapeHtml(row.courseName || row.sectionName || '')}</small></td>
                <td>${renderValue('registrationDate', row.registrationDate)}</td>
                <td>${statusBadge(row.status)}</td>
                <td class="table-actions"><button type="button" class="status-action approve" data-status-id="${escapeHtml(row.id)}" data-status="CONFIRMED"><i class="bi bi-check2"></i>Duyệt</button><button type="button" class="status-action reject" data-status-id="${escapeHtml(row.id)}" data-status="REJECTED"><i class="bi bi-x"></i>Từ chối</button></td>
            </tr>`).join('')}
            </tbody></table></div>${filtered.length ? `<div class="table-footer"><span>${filtered.length} đăng ký</span></div>` : '<div class="empty-state"><i class="bi bi-inbox"></i><strong>Chưa có đăng ký</strong></div>'}`;
        document.querySelectorAll('[data-status-id]').forEach((button) =>
            button.addEventListener('click', async () => {
                await nestRequest(`/course-registrations/admin/${encodeURIComponent(button.dataset.statusId)}/status`, {
                    method: 'PATCH',
                    body: JSON.stringify({ status: button.dataset.status })
                }).catch((error) => alert(error.message));
                await loadRegistrationTable();
            }));
    };
    search.addEventListener('input', render);
    render();
}

function statusBadge(status) {
    const label = {
        REGISTERED: 'Chờ duyệt',
        CONFIRMED: 'Đã duyệt',
        REJECTED: 'Đã từ chối',
        STUDYING: 'Đang học',
        COMPLETED: 'Hoàn thành',
        CANCELLED: 'Đã hủy',
        DROPPED: 'Đã rút'
    }[String(status)] || status || 'Chờ duyệt';
    const kind = String(status).toUpperCase() === 'CONFIRMED' || String(status).toUpperCase() === 'COMPLETED'
        ? 'active'
        : ['CANCELLED', 'DROPPED', 'REJECTED'].includes(String(status).toUpperCase())
            ? 'muted'
            : 'waiting';
    return `<span class="status-badge ${kind}">${escapeHtml(label)}</span>`;
}

async function renderReports() {
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>Thống kê đào tạo</h2><p>Báo cáo đăng ký theo học kỳ, học phần và sĩ số lớp.</p></div><button type="button" class="outline-button" id="reportRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button></section>
        <section class="metric-grid" id="reportMetrics"></section>
        <section class="panel-card resource-card"><div class="panel-heading"><h2>Đăng ký theo lớp học phần</h2></div><div id="reportTable"></div></section>`;
    await loadReports();
    document.getElementById('reportRefresh').addEventListener('click', loadReports);
}

async function loadReports() {
    const [summary, capacity, sections] = await Promise.all([
        nestRequest('/reports/registrations').catch(() => [{}]),
        nestRequest('/reports/course-sections').catch(() => [{}]),
        nestRequest('/reports/registrations-by-section').catch(() => [])
    ]);
    document.getElementById('reportMetrics').innerHTML = `
        ${metricCard('bi-person-vcard', 'Sinh viên đã đăng ký', 'report-students', summary[0]?.registeredStudents ?? 0)}
        ${metricCard('bi-card-checklist', 'Tổng lượt đăng ký', 'report-total', summary[0]?.totalRegistrations ?? 0)}
        ${metricCard('bi-door-open', 'Lớp đang mở', 'report-open', capacity[0]?.openSections ?? 0)}
        ${metricCard('bi-building-x', 'Lớp đã đầy', 'report-full', capacity[0]?.fullSections ?? 0)}
        ${metricCard('bi-person-plus', 'Chỗ còn lại', 'report-seats', capacity[0]?.remainingSeats ?? 0)}
    `;
    document.getElementById('reportTable').innerHTML = `
        <div class="table-scroll"><table class="unified-table"><thead><tr><th>Mã lớp</th><th>Tên lớp</th><th>Sĩ số tối đa</th><th>Đã đăng ký</th><th>Chỗ còn lại</th><th>Trạng thái</th></tr></thead><tbody>
        ${sections.map((row) => `<tr><td><strong>${escapeHtml(row.code)}</strong></td><td>${escapeHtml(row.name)}</td><td>${row.maxStudents ?? 'Không giới hạn'}</td><td>${row.enrolled ?? 0}</td><td>${row.remainingSeats == null ? 'Không giới hạn' : row.remainingSeats}</td><td>${row.isFull ? '<span class="status-badge muted">Đã đầy</span>' : '<span class="status-badge active">Còn chỗ</span>'}</td></tr>`).join('')}
        </tbody></table></div>${sections.length ? `<div class="table-footer"><span>${sections.length} lớp học phần</span></div>` : '<div class="empty-state"><i class="bi bi-bar-chart"></i><strong>Chưa có dữ liệu thống kê</strong></div>'}`;
}

async function renderOpenSections() {
    const sections = await nestRequest('/course-registrations/open-sections').catch(() => []);
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>Lớp mở đăng ký</h2><p>Chọn học phần phù hợp và kiểm tra điều kiện trước đăng ký.</p></div><button type="button" class="outline-button" id="openRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button></section>
        <section class="section-card-grid" id="openSectionsGrid"></section>`;
    renderOpenSectionCards(sections);
    document.getElementById('openRefresh').addEventListener('click', async () => {
        renderOpenSectionCards(await nestRequest('/course-registrations/open-sections').catch(() => []));
    });
}

function renderOpenSectionCards(sections) {
    const container = document.getElementById('openSectionsGrid');
    container.innerHTML = sections.length ? sections.map((section) => {
        const remaining = section.maxStudents == null ? null : Math.max(section.maxStudents - section.enrolled, 0);
        return `<article class="section-card"><div class="section-card-heading"><span class="section-code">${escapeHtml(section.code)}</span><span class="status-badge active">Đang mở</span></div><h3>${escapeHtml(section.name)}</h3><p>${escapeHtml(section.courseName || 'Học phần')}</p><div class="section-meta"><span><i class="bi bi-mortarboard"></i>${escapeHtml(String(section.credits ?? '—'))} tín chỉ</span><span><i class="bi bi-people"></i>${remaining == null ? 'Không giới hạn' : `${remaining} chỗ còn lại`}</span></div><button type="button" class="primary-button section-enroll" data-section-id="${escapeHtml(section.id)}">Đăng ký học phần</button></article>`;
    }).join('') : '<div class="empty-state wide"><i class="bi bi-journal-x"></i><strong>Hiện không có lớp mở đăng ký</strong><span>Hãy kiểm tra lại khi đến thời gian đăng ký.</span></div>';
    container.querySelectorAll('.section-enroll').forEach((button) =>
        button.addEventListener('click', async () => {
            try {
                await nestRequest('/course-registrations', {
                    method: 'POST',
                    body: JSON.stringify({ courseSectionId: button.dataset.sectionId })
                });
                await renderOpenSections();
            } catch (error) {
                alert(error.message);
            }
        }));
}

async function renderMyRegistrations() {
    const rows = await nestRequest('/course-registrations/mine').catch(() => []);
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>Đăng ký của tôi</h2><p>Theo dõi trạng thái và hủy đăng ký khi còn cho phép.</p></div></section>
        <section class="panel-card resource-card"><div id="myRegistrationTable">${renderMyRegistrationTable(rows)}</div></section>`;
}

function renderMyRegistrationTable(rows) {
    if (!rows.length) return '<div class="empty-state"><i class="bi bi-card-checklist"></i><strong>Bạn chưa đăng ký học phần</strong></div>';
    return `<div class="table-scroll"><table class="unified-table"><thead><tr><th>Mã lớp</th><th>Tên lớp</th><th>Học phần</th><th>Ngày đăng ký</th><th>Trạng thái</th><th class="text-end">Thao tác</th></tr></thead><tbody>${rows.map((row) => `<tr><td><strong>${escapeHtml(row.sectionCode)}</strong></td><td>${escapeHtml(row.sectionName)}</td><td>${escapeHtml(row.courseName || '')}</td><td>${renderValue('registrationDate', row.registrationDate)}</td><td>${statusBadge(row.status)}</td><td class="table-actions"><button type="button" class="status-action reject" data-cancel-id="${escapeHtml(row.id)}"><i class="bi bi-x"></i>Hủy</button></td></tr>`).join('')}</tbody></table></div>`;
}

async function renderNotifications() {
    const rows = await nestRequest('/notifications').catch(() => []);
    portalContent.innerHTML = `
        <section class="page-heading"><div><h2>Thông báo</h2><p>Cập nhật đăng ký, lịch học và thông báo từ quản trị.</p></div></section>
        <section class="panel-card resource-card"><div class="notification-list">${rows.length ? rows.map((row) => `<article class="notification-item ${row.readAt ? '' : 'unread'}"><span class="notification-icon"><i class="bi ${row.type === 'SCHEDULE' ? 'bi-calendar-event' : row.type === 'ANNOUNCEMENT' ? 'bi-megaphone' : 'bi-info-circle'}"></i></span><div><strong>${escapeHtml(row.title)}</strong><p>${escapeHtml(row.content)}</p><small>${renderValue('createdAt', row.createdAt)}</small></div>${row.readAt ? '' : `<button type="button" class="outline-button" data-read-id="${escapeHtml(row.id)}">Đánh dấu đã đọc</button>`}</article>`).join('') : '<div class="empty-state"><i class="bi bi-bell-slash"></i><strong>Chưa có thông báo</strong></div>'}</div></section>`;
    portalContent.querySelectorAll('[data-read-id]').forEach((button) =>
        button.addEventListener('click', async () => {
            await nestRequest(`/notifications/${encodeURIComponent(button.dataset.readId)}/read`, { method: 'PATCH' });
            await renderNotifications();
        }));
}

async function renderAccountManager() {
    portalContent.innerHTML = `
        <section class="page-heading">
            <div><h2>Quản lý tài khoản</h2><p>Xem tài khoản, khóa/mở khóa, đặt lại mật khẩu và phân quyền. Tài khoản STUDENT/LECTURER được tạo từ hồ sơ tương ứng.</p></div>
            <button type="button" class="primary-button" id="createAccountButton"><i class="bi bi-person-plus"></i>Tạo tài khoản đặc biệt</button>
        </section>
        <section class="panel-card resource-card">
            <div class="resource-toolbar">
                <div class="search-box"><i class="bi bi-search"></i><input id="accountSearch" type="search" placeholder="Tìm tài khoản, email, vai trò..." /></div>
                <button type="button" class="outline-button" id="accountRefresh"><i class="bi bi-arrow-clockwise"></i>Làm mới</button>
            </div>
            <div id="accountTable"></div>
        </section>`;

    await loadAccountTable();
    document.getElementById('accountRefresh').addEventListener('click', loadAccountTable);
    document.getElementById('createAccountButton').addEventListener('click', openCreateAccountDialog);
}

async function loadAccountTable() {
    const search = document.getElementById('accountSearch');
    let accounts;
    try {
        accounts = await springRequest('/auth/accounts');
    } catch (error) {
        document.getElementById('accountTable').innerHTML =
            `<div class="empty-state"><strong>Không tải được danh sách tài khoản</strong><span>${escapeHtml(error.message)}</span></div>`;
        return;
    }
    const render = () => {
        const keyword = search.value.trim().toLocaleLowerCase('vi');
        const filtered = accounts.filter((account) =>
            Object.values(account).some((value) => value != null && String(value).toLocaleLowerCase('vi').includes(keyword)));
        document.getElementById('accountTable').innerHTML = `
            <div class="table-scroll"><table class="unified-table"><thead><tr><th>Tên đăng nhập</th><th>Họ và tên</th><th>Email</th><th>Vai trò</th><th>Liên kết hồ sơ</th><th>Trạng thái</th><th class="text-end">Thao tác</th></tr></thead><tbody>
            ${filtered.map((account) => `
                <tr>
                    <td><strong>${escapeHtml(account.username)}</strong></td>
                    <td>${escapeHtml(account.fullName)}</td>
                    <td>${escapeHtml(account.email || 'Chưa cập nhật')}</td>
                    <td>${translateRoles(String(account.roles || '').split(',').filter(Boolean))}</td>
                    <td>${account.studentId ? 'Sinh viên đã liên kết' : account.lecturerId ? 'Giảng viên đã liên kết' : '<span class="muted-value">Chưa liên kết</span>'}</td>
                    <td>${account.isActive ? '<span class="status-badge active">Đang hoạt động</span>' : '<span class="status-badge muted">Đã khóa</span>'}</td>
                    <td class="table-actions">
                        <button type="button" class="status-action approve" data-account-action="toggle" data-account-id="${escapeHtml(account.id)}" data-account-active="${account.isActive ? '1' : '0'}">${account.isActive ? 'Khóa' : 'Mở khóa'}</button>
                        <button type="button" class="status-action" data-account-action="roles" data-account-id="${escapeHtml(account.id)}">Phân quyền</button>
                        <button type="button" class="status-action reject" data-account-action="reset" data-account-id="${escapeHtml(account.id)}">Đặt lại mật khẩu</button>
                    </td>
                </tr>`).join('')}
            </tbody></table></div>${filtered.length ? `<div class="table-footer"><span>${filtered.length} tài khoản</span></div>` : '<div class="empty-state"><i class="bi bi-person-lock"></i><strong>Không có tài khoản phù hợp</strong></div>'}`;

        document.querySelectorAll('[data-account-action]').forEach((button) =>
            button.addEventListener('click', async () => {
                if (button.dataset.accountAction === 'toggle') {
                    const active = button.dataset.accountActive !== '1';
                    await springRequest(`/auth/accounts/${encodeURIComponent(button.dataset.accountId)}/status`, {
                        method: 'PATCH',
                        body: JSON.stringify({ active })
                    }).catch((error) => alert(error.message));
                    await loadAccountTable();
                } else if (button.dataset.accountAction === 'roles') {
                    const account = accounts.find((item) => item.id === button.dataset.accountId);
                    if (account) openAccountRolesDialog(account);
                } else {
                    openResetPasswordDialog(button.dataset.accountId);
                }
            }));
    };
    search.addEventListener('input', render);
    render();
}

async function openCreateAccountDialog() {
    let roles;
    try {
        roles = await springRequest('/auth/independent-roles');
    } catch (error) {
        alert(error.message);
        return;
    }
    const dialog = document.createElement('dialog');
    dialog.className = 'unified-dialog';
    dialog.innerHTML = `
        <form id="createManagedAccountForm">
            <div class="dialog-heading"><div><small>QUẢN LÝ TÀI KHOẢN</small><h2>Tạo tài khoản đặc biệt</h2></div><button type="button" class="dialog-close account-close"><i class="bi bi-x-lg"></i></button></div>
            <div class="dialog-fields">
                <div class="dialog-field"><label>Role</label><select name="roleCode" required>${roles.map((role) => `<option value="${escapeHtml(role)}">${escapeHtml(translateRoles([role]))}</option>`).join('')}</select></div>
                <div class="dialog-field"><label>Tên đăng nhập</label><input name="username" required minlength="3" maxlength="100" /></div>
                <div class="dialog-field"><label>Họ và tên hiển thị</label><input name="displayName" required maxlength="255" /></div>
                <div class="dialog-field"><label>Email</label><input name="email" type="email" maxlength="254" /></div>
                <div class="dialog-field"><label>Mật khẩu</label><input name="password" type="password" required minlength="12" maxlength="72" /></div>
            </div>
            <p id="createManagedStatus" class="dialog-status">${roles.length ? '' : 'Không có role độc lập đang hoạt động.'}</p>
            <div class="dialog-actions"><button type="button" class="outline-button account-close">Hủy</button><button type="submit" class="primary-button" ${roles.length ? '' : 'disabled'}>Tạo tài khoản</button></div>
        </form>`;
    document.body.append(dialog);
    dialog.showModal();
    dialog.querySelectorAll('.account-close').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.addEventListener('close', () => dialog.remove());

    dialog.querySelector('#createManagedAccountForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const status = dialog.querySelector('#createManagedStatus');
        const payload = Object.fromEntries(new FormData(event.currentTarget).entries());
        payload.roles = [payload.roleCode];
        delete payload.roleCode;
        try {
            await springRequest('/auth/accounts', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            status.textContent = 'Tạo tài khoản thành công.';
            await loadAccountTable();
            setTimeout(() => dialog.close(), 650);
        } catch (error) {
            status.textContent = error.message;
        }
    });
}

async function openAccountRolesDialog(account) {
    let availableRoles;
    try {
        availableRoles = await springRequest('/auth/roles');
    } catch (error) {
        alert(error.message);
        return;
    }

    const assignedRoles = new Set(String(account.roles || '').split(',').filter(Boolean));
    const dialog = document.createElement('dialog');
    dialog.className = 'unified-dialog';
    dialog.innerHTML = `
        <form id="accountRolesForm">
            <div class="dialog-heading"><div><small>QUẢN LÝ TÀI KHOẢN</small><h2>Phân quyền: ${escapeHtml(account.username)}</h2></div><button type="button" class="dialog-close roles-close"><i class="bi bi-x-lg"></i></button></div>
            <div class="dialog-fields" style="grid-template-columns:1fr">
                ${availableRoles.map((role) => `<label class="dialog-field"><span><input type="checkbox" name="roles" value="${escapeHtml(role)}" ${assignedRoles.has(role) ? 'checked' : ''} /> ${escapeHtml(translateRoles([role]))}</span></label>`).join('')}
            </div>
            <p id="accountRolesStatus" class="dialog-status"></p>
            <div class="dialog-actions"><button type="button" class="outline-button roles-close">Hủy</button><button type="submit" class="primary-button">Lưu phân quyền</button></div>
        </form>`;
    document.body.append(dialog);
    dialog.showModal();
    dialog.querySelectorAll('.roles-close').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.addEventListener('close', () => dialog.remove());
    dialog.querySelector('#accountRolesForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const status = dialog.querySelector('#accountRolesStatus');
        const roles = Array.from(dialog.querySelectorAll('input[name="roles"]:checked'), (input) => input.value);
        try {
            await springRequest(`/auth/accounts/${encodeURIComponent(account.id)}/roles`, {
                method: 'PATCH',
                body: JSON.stringify({ roles })
            });
            status.textContent = 'Đã cập nhật phân quyền.';
            await loadAccountTable();
            setTimeout(() => dialog.close(), 650);
        } catch (error) {
            status.textContent = error.message;
        }
    });
}

function openResetPasswordDialog(userId) {
    const dialog = document.createElement('dialog');
    dialog.className = 'unified-dialog';
    dialog.innerHTML = `
        <form id="adminResetPasswordForm">
            <div class="dialog-heading"><div><small>HỖ TRỢ TÀI KHOẢN</small><h2>Đặt lại mật khẩu</h2></div><button type="button" class="dialog-close reset-close"><i class="bi bi-x-lg"></i></button></div>
            <div class="dialog-fields" style="grid-template-columns:1fr">
                <div class="dialog-field"><label>Mật khẩu mới</label><input name="password" type="password" required minlength="12" maxlength="72" /></div>
            </div>
            <p id="resetPasswordStatus" class="dialog-status"></p>
            <div class="dialog-actions"><button type="button" class="outline-button reset-close">Hủy</button><button type="submit" class="primary-button">Cập nhật mật khẩu</button></div>
        </form>`;
    document.body.append(dialog);
    dialog.showModal();
    dialog.querySelectorAll('.reset-close').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.addEventListener('close', () => dialog.remove());
    dialog.querySelector('#adminResetPasswordForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const status = dialog.querySelector('#resetPasswordStatus');
        const payload = Object.fromEntries(new FormData(event.currentTarget).entries());
        try {
            await springRequest(`/auth/accounts/${encodeURIComponent(userId)}/password`, {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            status.textContent = 'Đặt lại mật khẩu thành công.';
            await loadAccountTable();
            setTimeout(() => dialog.close(), 650);
        } catch (error) {
            status.textContent = error.message;
        }
    });
}

function escapeHtml(value) {
    const element = document.createElement('span');
    element.textContent = value == null ? '' : String(value);
    return element.innerHTML;
}

function translateRoles(roles) {
    const labels = {
        ADMIN: 'Quản trị viên',
        LECTURER: 'Giảng viên',
        STUDENT: 'Sinh viên'
    };
    return (Array.isArray(roles) ? roles : []).map((role) => labels[String(role).toUpperCase()] || role).join(', ');
}

function normalizeIdentifier(value) {
    return value == null ? '' : String(value).trim().toLowerCase();
}

window.addEventListener('pageshow', () => {
    if (!localStorage.getItem('nestAccessToken')) clearAuthenticatedState();
});

window.addEventListener('storage', (event) => {
    if ((event.key === 'nestAccessToken' || event.key === 'nestRefreshToken')
            && !localStorage.getItem('nestAccessToken')) {
        clearAuthenticatedState();
    }
});

restoreSession();
