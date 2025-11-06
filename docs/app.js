// Configuration
const GITHUB_OWNER = 'alltechdev';
const GITHUB_REPO = 'AuroraStore-WhitelistEdition';
const GITHUB_API = 'https://api.github.com';
const WORKER_URL = 'https://apk-builder-worker.abesternheim.workers.dev';

function generateBuildId() {
    return 'aurora-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
}

function showErrorModal(text) {
    Swal.fire({
        icon: 'error',
        title: 'Error',
        text,
    });
}

function validateWhitelist(text) {
    try {
        const parsed = JSON.parse(text);

        if (!Array.isArray(parsed)) {
            return { valid: false, error: 'Must be a JSON array' };
        }

        if (parsed.length === 0) {
            return { valid: false, error: 'Array cannot be empty' };
        }

        if (parsed.length > 1000) {
            return { valid: false, error: 'Maximum 1000 apps allowed' };
        }

        for (let i = 0; i < parsed.length; i++) {
            if (typeof parsed[i] !== 'string') {
                return { valid: false, error: `Item ${i + 1} must be a string` };
            }
            if (parsed[i].trim() === '') {
                return { valid: false, error: `Item ${i + 1} cannot be empty` };
            }
        }

        return { valid: true, count: parsed.length, data: parsed };
    } catch (e) {
        return { valid: false, error: 'Invalid JSON: ' + e.message };
    }
}

// Real-time validation
$('#whitelist').on('input', function() {
    const text = $(this).val().trim();
    const validation = $('#validationMessage');

    if (text === '') {
        validation.html('').removeClass('validation-success validation-error');
        return;
    }

    const result = validateWhitelist(text);

    if (result.valid) {
        validation.html(`<i class="fas fa-check-circle"></i> Valid JSON - ${result.count} app${result.count !== 1 ? 's' : ''}`)
            .removeClass('validation-error')
            .addClass('validation-message validation-success');
    } else {
        validation.html(`<i class="fas fa-exclamation-circle"></i> ${result.error}`)
            .removeClass('validation-success')
            .addClass('validation-message validation-error');
    }
});

// Form submission
$('#auroraForm').on('submit', async (e) => {
    e.preventDefault();

    const whitelistText = $('#whitelist').val().trim();
    const validation = validateWhitelist(whitelistText);

    if (!validation.valid) {
        return showErrorModal(validation.error);
    }

    const buildId = generateBuildId();

    await createBuildRequest({
        buildId,
        whitelist: validation.data,
        type: 'aurora-store'
    });
});

async function createBuildRequest(config) {
    try {
        Swal.fire({
            icon: 'info',
            title: 'Creating build request...',
            html: 'Please wait while we set up your custom Aurora Store build.',
            didOpen: () => {
                Swal.showLoading();
            },
            showConfirmButton: false,
            allowOutsideClick: false,
        });

        const response = await fetch(WORKER_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(config)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to create build request');
        }

        const result = await response.json();
        const issueNumber = result.issueNumber;
        const gistUrl = result.gistUrl;

        // Monitor build
        if (issueNumber) {
            monitorBuildProgress(issueNumber, config, gistUrl);
        }

    } catch (error) {
        console.error('Build request error:', error);
        showErrorModal(error.message || 'Failed to create build request. Please try again.');
    }
}

async function monitorBuildProgress(issueNumber, config, gistUrl) {
    Swal.fire({
        icon: 'info',
        title: 'Building your Aurora Store...',
        html: `
            <div>The build process takes about 3-4 minutes.</div>
            <div class="mt-3"><b>Do not close this page</b></div>
            <div class="mt-2 text-muted small">
                <a href="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}" target="_blank">
                    View Issue #${issueNumber}
                </a>
            </div>
        `,
        didOpen: () => {
            Swal.showLoading();
        },
        showConfirmButton: false,
        allowOutsideClick: false,
    });

    const maxAttempts = 80;
    let attempts = 0;

    const checkStatus = async () => {
        try {
            attempts++;

            const response = await fetch(
                `${GITHUB_API}/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}/comments`,
                {
                    headers: {
                        'Accept': 'application/vnd.github.v3+json'
                    }
                }
            );

            if (!response.ok) {
                throw new Error('Failed to check build status');
            }

            const comments = await response.json();

            const completionComment = comments.find(c =>
                c.body.includes('✅ APK Build Complete') ||
                c.body.includes('Download APK')
            );

            const errorComment = comments.find(c =>
                c.body.includes('❌ Build Failed') ||
                c.body.includes('Build Error')
            );

            if (completionComment) {
                const urlMatch = completionComment.body.match(/https:\/\/github\.com\/[^\s)]+\.apk/);
                const downloadUrl = urlMatch ? urlMatch[0] : null;

                Swal.fire({
                    icon: 'success',
                    title: 'Aurora Store Built Successfully!',
                    html: `
                        <div class="mb-3">Your custom Aurora Store is ready!</div>
                        <div class="mb-3"><strong>Apps:</strong> ${config.whitelist.length}</div>
                        ${downloadUrl ? `
                            <div class="mt-4">
                                <a href="${downloadUrl}" class="btn btn-primary" download>
                                    <i class="fas fa-download"></i> Download APK
                                </a>
                            </div>
                        ` : ''}
                        <div class="mt-3 alert alert-info">
                            <strong>Your Whitelist URL:</strong><br/>
                            <small class="text-break">${gistUrl}</small><br/>
                            <small class="text-muted">Update this Gist to update all installed apps</small>
                        </div>
                        <div class="mt-3">
                            <a href="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}" target="_blank">
                                View build details
                            </a>
                        </div>
                    `,
                    showConfirmButton: true,
                    confirmButtonText: 'Build Another',
                    showCloseButton: true,
                    width: '600px'
                }).then((result) => {
                    if (result.isConfirmed) {
                        location.reload();
                    }
                });

            } else if (errorComment) {
                Swal.fire({
                    icon: 'error',
                    title: 'Build Failed',
                    html: `
                        <div>The Aurora Store build encountered an error.</div>
                        <div class="mt-3">
                            <a href="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}" target="_blank">
                                View error details
                            </a>
                        </div>
                    `,
                    showConfirmButton: true,
                });

            } else if (attempts >= maxAttempts) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Build Taking Longer Than Expected',
                    html: `
                        <div>The build is still in progress.</div>
                        <div class="mt-3">
                            <a href="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}" target="_blank">
                                Monitor build progress
                            </a>
                        </div>
                    `,
                    showConfirmButton: true,
                });

            } else {
                setTimeout(checkStatus, 10000);
            }

        } catch (error) {
            console.error('Polling error:', error);
            Swal.fire({
                icon: 'error',
                title: 'Error Checking Status',
                html: `
                    <div>Could not check build status.</div>
                    <div class="mt-3">
                        <a href="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}" target="_blank">
                            Check manually
                        </a>
                    </div>
                `,
                showConfirmButton: true,
            });
        }
    };

    setTimeout(checkStatus, 10000);
}
