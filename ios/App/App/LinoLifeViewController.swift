import UIKit
import Capacitor
import FirebaseMessaging
import WebKit

@objc(LinoLifeFirebasePlugin)
class LinoLifeFirebasePlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "LinoLifeFirebasePlugin"
    let jsName = "LinoLifeFirebase"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "deleteToken", returnType: CAPPluginReturnPromise)
    ]

    @objc func deleteToken(_ call: CAPPluginCall) {
        Messaging.messaging().isAutoInitEnabled = false
        Messaging.messaging().deleteToken { error in
            if let error = error {
                call.reject(error.localizedDescription)
            } else {
                call.resolve()
            }
        }
    }
}

class LinoLifeViewController: CAPBridgeViewController {
    private var startupCover: UIView?
    private var recovery: UIStackView?
    private var connectionTimeout: DispatchWorkItem?

    override func webViewConfiguration(for instanceConfiguration: InstanceConfiguration) -> WKWebViewConfiguration {
        let configuration = super.webViewConfiguration(for: instanceConfiguration)
        // Keep the encrypted HttpOnly recaller in WebKit's persistent app sandbox.
        // Never copy passwords/cookies into UserDefaults, localStorage or bridge responses.
        configuration.websiteDataStore = WKWebsiteDataStore.default()
        return configuration
    }

    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(LinoLifeFirebasePlugin())
        let startup = LinoLifeStartupPlugin()
        startup.owner = self
        bridge?.registerPluginInstance(startup)
        for name in ["native-push", "native-startup"] {
            guard let url = Bundle.main.url(forResource: "public/" + name, withExtension: "js"),
                  let source = try? String(contentsOf: url, encoding: .utf8) else {
                assertionFailure("Bundled " + name + ".js is missing. Run npx cap sync ios.")
                continue
            }
            webView?.configuration.userContentController.addUserScript(
                WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true)
            )
        }
        showStartupCover()
    }

    fileprivate var isAppPage: Bool {
        guard let entry = bridge?.config.serverURL, let page = webView?.url else { return false }
        let path = entry.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return page.scheme == "https" && entry.host == page.host && entry.port == page.port &&
            (page.path == "/" + path || page.path.hasPrefix("/" + path + "/"))
    }

    fileprivate var isRecoveryDocument: Bool {
        guard let errorURL = bridge?.config.errorPathURL else { return false }
        return webView?.url == errorURL
    }

    private func showStartupCover() {
        if startupCover == nil {
            let cover = UIView()
            cover.backgroundColor = .white
            cover.translatesAutoresizingMaskIntoConstraints = false
            cover.accessibilityIdentifier = "linolife-startup-cover"
            let image = UIImageView(image: UIImage(named: "Splash"))
            image.contentMode = .scaleAspectFit
            image.translatesAutoresizingMaskIntoConstraints = false
            cover.addSubview(image)
            let message = UILabel()
            message.text = "Unable to connect. Your sign-in is kept on this device."
            message.textColor = .black
            message.numberOfLines = 0
            message.textAlignment = .center
            message.font = .preferredFont(forTextStyle: .body)
            let retry = UIButton(type: .system)
            retry.setTitle("Try again", for: .normal)
            retry.setTitleColor(.black, for: .normal)
            retry.backgroundColor = UIColor(red: 0.99, green: 0.18, blue: 0.54, alpha: 1)
            retry.layer.cornerRadius = 20
            retry.addTarget(self, action: #selector(retryStartup), for: .touchUpInside)
            let stack = UIStackView(arrangedSubviews: [message, retry])
            stack.axis = .vertical
            stack.spacing = 16
            stack.translatesAutoresizingMaskIntoConstraints = false
            cover.addSubview(stack)
            view.addSubview(cover)
            NSLayoutConstraint.activate([
                cover.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                cover.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                cover.topAnchor.constraint(equalTo: view.topAnchor),
                cover.bottomAnchor.constraint(equalTo: view.bottomAnchor),
                image.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
                image.trailingAnchor.constraint(equalTo: cover.trailingAnchor),
                image.topAnchor.constraint(equalTo: cover.topAnchor),
                image.bottomAnchor.constraint(equalTo: cover.bottomAnchor),
                stack.leadingAnchor.constraint(equalTo: cover.safeAreaLayoutGuide.leadingAnchor, constant: 24),
                stack.trailingAnchor.constraint(equalTo: cover.safeAreaLayoutGuide.trailingAnchor, constant: -24),
                stack.topAnchor.constraint(equalTo: cover.centerYAnchor, constant: 80),
                retry.heightAnchor.constraint(greaterThanOrEqualToConstant: 48)
            ])
            startupCover = cover
            recovery = stack
        }
        startupCover?.layer.removeAllAnimations()
        startupCover?.alpha = 1
        startupCover?.isHidden = false
        recovery?.isHidden = true
        connectionTimeout?.cancel()
        let timeout = DispatchWorkItem { [weak self] in self?.showConnectionRecovery() }
        connectionTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 20, execute: timeout)
    }

    fileprivate func finishStartup() {
        connectionTimeout?.cancel()
        guard let cover = startupCover else { return }
        UIView.animate(withDuration: UIAccessibility.isReduceMotionEnabled ? 0 : 0.2, animations: {
            cover.alpha = 0
        }, completion: { _ in cover.isHidden = true })
    }

    fileprivate func showConnectionRecovery() {
        if startupCover == nil { showStartupCover() }
        connectionTimeout?.cancel()
        startupCover?.layer.removeAllAnimations()
        startupCover?.alpha = 1
        startupCover?.isHidden = false
        recovery?.isHidden = false
    }

    @objc fileprivate func retryStartup() {
        guard let entry = bridge?.config.serverURL else { return }
        showStartupCover()
        webView?.stopLoading()
        // Safe entry GET only; never reload/replay a checkout POST on reconnect.
        webView?.load(URLRequest(url: entry, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30))
    }

    deinit {
        connectionTimeout?.cancel()
    }
}

@objc(LinoLifeStartupPlugin)
class LinoLifeStartupPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "LinoLifeStartupPlugin"
    let jsName = "LinoLifeStartup"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "ready", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "unavailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "retry", returnType: CAPPluginReturnPromise)
    ]
    weak var owner: LinoLifeViewController?

    @objc func ready(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let owner = self?.owner, owner.isAppPage else {
                call.reject("Untrusted startup document")
                return
            }
            owner.finishStartup()
            call.resolve()
        }
    }

    @objc func unavailable(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            if let owner = self?.owner, owner.isRecoveryDocument { owner.showConnectionRecovery() }
            call.resolve()
        }
    }

    @objc func retry(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            if let owner = self?.owner, owner.isRecoveryDocument { owner.retryStartup() }
            call.resolve()
        }
    }
}
