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
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(LinoLifeFirebasePlugin())
        guard let url = Bundle.main.url(forResource: "public/native-push", withExtension: "js"),
              let source = try? String(contentsOf: url, encoding: .utf8) else {
            assertionFailure("Bundled native-push.js is missing. Run npx cap sync ios.")
            return
        }
        webView?.configuration.userContentController.addUserScript(
            WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        )
    }
}
