(() => {
    'use strict';
    // This adapter is bundled into the native app and injected before hosted page
    // scripts. Keep permissions and account/token persistence in the website flow.
    const cap = window.Capacitor;
    if (window.top !== window || location.origin !== 'https://linolife.co' || !cap?.isNativePlatform()) return;
    const push = cap.Plugins?.PushNotifications;
    if (!push || window.median?.firebaseMessaging || window.gonative?.firebaseMessaging) return;

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
                if (!listeners.size) { pending = value; return; }
                listeners.forEach(listener => listener(value));
            }
        };
    };
    const received = event(), clicked = event();
    let registration;
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
            if (typeof result.value === 'string' && result.value) finish(null, result.value);
            else finish(Error('Firebase returned an empty registration token.'));
        }),
        push.addListener('registrationError', error => finish(Error(error.error || 'Notification registration failed.'))),
        push.addListener('pushNotificationReceived', notification => received.emit({ notification })),
        push.addListener('pushNotificationActionPerformed', action => {
            if (action.actionId === 'tap') clicked.emit(action);
        })
    ]);
    // Handle initialization rejection even if the user never enables notifications.
    ready.catch(() => console.warn('Native notification listeners could not be initialized.'));

    const token = async () => {
        await ready;
        const permission = await push.checkPermissions();
        if (permission.receive !== 'granted') throw Error('Notification permission has not been granted.');
        if (registration) return registration.promise;
        const current = {};
        current.promise = new Promise((resolve, reject) => { current.resolve = resolve; current.reject = reject; });
        current.timer = setTimeout(() => finish(Error('Firebase registration timed out. Check your connection and APNs setup.')), 14000);
        registration = current;
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
        checkPermission: options => call(options, async () => ({ granted: (await push.checkPermissions()).receive === 'granted' })),
        requestPermission: options => call(options, async () => ({ granted: (await push.requestPermissions()).receive === 'granted' })),
        register: options => call(options, token),
        getToken: options => call(options, token),
        deleteToken: options => call(options, async () => {
            finish(Error('Notification registration was cancelled.'));
            if (cap.getPlatform() === 'ios') {
                const firebase = cap.Plugins.LinoLifeFirebase;
                if (!firebase) throw Error('Native Firebase token deletion is unavailable. Rebuild the app.');
                await firebase.deleteToken();
            }
            await push.unregister();
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
        notificationClicked: { addListener: listener => clicked.addListener(listener) }
    };
    window.median = window.median || {};
    window.median.firebaseMessaging = api;
})();
