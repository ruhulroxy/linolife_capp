/* Startup handoff: authentication is resolved by Laravel before this document is sent. */
(() => {
    'use strict';
    if (window.top && window.top !== window) return;
    let sent = false;
    const ready = () => {
        if (sent || document.documentElement.dataset.appReady !== 'true') return;
        const api = window.Capacitor?.Plugins?.LinoLifeStartup;
        if (!api?.ready) return;
        sent = true;
        api.ready().catch(() => { sent = false; });
    };
    window.addEventListener('linolife:ready', ready);
    document.addEventListener('DOMContentLoaded', ready, { once: true });
    // Older hosted releases can still hand off once their final app document has rendered.
    const rendered = () => {
        if (document.querySelector('meta[name="linolife-startup"]')) return;
        // Recognized server error pages are final documents too (e.g. a useful 403).
        if (!document.querySelector('[data-capp],[data-papp],[data-aapp],[data-error-surface]') ||
            document.querySelector('[data-ca-entry-script],[data-ca-splash]')) return;
        requestAnimationFrame(() => requestAnimationFrame(() => {
            document.documentElement.dataset.appReady = 'true';
            ready();
        }));
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', rendered, { once: true });
    else rendered();
})();
