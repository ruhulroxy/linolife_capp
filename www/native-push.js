(() => {
    'use strict';
    // This adapter is bundled into the native app and injected before hosted page
    // scripts. Keep permissions and account/token persistence in the website flow.
    const cap = window.Capacitor;
    if (window.top !== window || location.origin !== 'https://linolife.co' || !cap?.isNativePlatform()) return;
    const push = cap.Plugins?.PushNotifications;
    if (!push || window.LinoLifeNativePush) return;
    const surface = 'capp';
    const platform = cap.getPlatform();
    const uuid = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;

    const event = () => {
        const listeners = new Set();
        let pending;
        return {
            addListener(listener) {
                listeners.add(listener);
                if (pending) { const value = pending; pending = undefined; listener(value); }
                return { remove: () => listeners.delete(listener) };
            },
            emit(value) {
                if (!listeners.size) { pending = value; return false; }
                listeners.forEach(listener => listener(value));
                return true;
            },
            takePending() { const value = pending; pending = undefined; return value; }
        };
    };
    const received = event(), clicked = event(), rotated = event();
    let registration, generation = 0, acceptsTokens = false, lastTap;
    const inSurface = data => !data?.surface || data.surface === surface;
    const handleTap = action => {
        const data = action.notification?.data;
        if (action.actionId !== 'tap' || !inSurface(data)) return;
        const id = data?.notification_id;
        const tapId = id || action.notification?.id;
        const now = Date.now();
        if (tapId) {
            try { lastTap ??= JSON.parse(window.sessionStorage?.getItem('linolife-push-tap') || 'null'); } catch (_) {}
            if (lastTap?.id === tapId && now - lastTap.time < 3000) return;
            lastTap = { id: tapId, time: now };
            try { window.sessionStorage?.setItem('linolife-push-tap', JSON.stringify(lastTap)); } catch (_) {}
        }
        if (clicked.emit(action)) return;
        const openWithoutPageListener = () => {
            const pending = clicked.takePending();
            if (!pending) return;
            const payload = pending.notification?.data;
            // Older servers send no surface. Their app inbox remains the tap destination.
            const path = payload?.surface === surface && uuid.test(payload.notification_id)
                ? `/notifications/open/${surface}/${payload.notification_id}` : `/${surface}/notifications`;
            location.assign(path);
        };
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', openWithoutPageListener, { once: true });
        else setTimeout(openWithoutPageListener, 0);
    };
    const finish = (error, token) => {
        if (!registration) return;
        const current = registration;
        registration = undefined;
        clearTimeout(current.timer);
        if (error) current.reject(error);
        else current.resolve({ token });
    };
    const ready = Promise.all([
        push.addListener('registration', result => {
            if (typeof result.value === 'string' && result.value) {
                if (registration) finish(null, result.value);
                else if (acceptsTokens) rotated.emit({ token: result.value });
            }
            else finish(Error('Firebase returned an empty registration token.'));
        }),
        push.addListener('registrationError', error => finish(Error(error.error || 'Notification registration failed.'))),
        push.addListener('pushNotificationReceived', notification => {
            if (inSurface(notification.data)) received.emit({ notification });
        }),
        push.addListener('pushNotificationActionPerformed', handleTap)
    ]);
    // Handle initialization rejection even if the user never enables notifications.
    ready.catch(() => console.warn('Native notification listeners could not be initialized.'));

    const token = async () => {
        const version = generation;
        await ready;
        const permission = await push.checkPermissions();
        if (version !== generation) throw Error('Notification registration was cancelled.');
        if (permission.receive !== 'granted') throw Error('Notification permission has not been granted.');
        if (registration) return registration.promise;
        const current = {};
        current.promise = new Promise((resolve, reject) => { current.resolve = resolve; current.reject = reject; });
        current.timer = setTimeout(() => finish(Error('Firebase registration timed out. Check your connection and APNs setup.')), 14000);
        registration = current;
        acceptsTokens = true;
        Promise.resolve().then(() => push.register()).catch(error => {
            if (registration === current) finish(error);
        });
        return current.promise;
    };
    const call = (options, operation) => Promise.resolve().then(operation).then(result => {
        options?.callback?.(result);
        return result;
    }, error => {
        const result = { granted: false, error: error.message || 'Native notification request failed.' };
        options?.callback?.(result);
        return result;
    });

    const api = {
        platform,
        surface,
        checkPermission: options => call(options, async () => ({ granted: (await push.checkPermissions()).receive === 'granted' })),
        requestPermission: options => call(options, async () => ({ granted: (await push.requestPermissions()).receive === 'granted' })),
        register: options => call(options, token),
        getToken: options => call(options, token),
        deleteToken: options => call(options, async () => {
            generation++;
            acceptsTokens = false;
            rotated.takePending();
            finish(Error('Notification registration was cancelled.'));
            try {
                if (platform === 'ios') {
                    const firebase = cap.Plugins.LinoLifeFirebase;
                    if (!firebase) throw Error('Native Firebase token deletion is unavailable. Rebuild the app.');
                    await firebase.deleteToken();
                }
            } finally {
                try { await push.unregister(); }
                finally { await push.removeAllDeliveredNotifications?.(); }
            }
            return {};
        }),
        createChannel: options => call(options, async () => {
            if (cap.getPlatform() !== 'android') return {};
            await push.createChannel({
                id: options.channelId || 'linolife_updates',
                name: options.channelName || 'Lino Life updates',
                importance: typeof options.importance === 'number' ? options.importance : 3,
                visibility: 0
            });
            return {};
        }),
        notificationReceived: { addListener: listener => received.addListener(listener) },
        notificationClicked: { addListener: listener => clicked.addListener(listener) },
        notificationTokenChanged: { addListener: listener => rotated.addListener(listener) }
    };
    window.LinoLifeNativePush = api;
    if (!window.median?.firebaseMessaging && !window.gonative?.firebaseMessaging) {
        window.median = window.median || {};
        window.median.firebaseMessaging = api;
    }
})();
