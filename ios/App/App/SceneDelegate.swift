import UIKit
import Capacitor
import UserNotifications

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }

        let appWindow = window ?? UIWindow(windowScene: windowScene)
        let controller = (appWindow.rootViewController as? LinoLifeViewController) ?? LinoLifeViewController()
        appWindow.rootViewController = controller
        window = appWindow
        appWindow.makeKeyAndVisible()
        if let response = connectionOptions.notificationResponse {
            controller.loadViewIfNeeded()
            controller.bridge?.notificationRouter.userNotificationCenter(
                UNUserNotificationCenter.current(), didReceive: response, withCompletionHandler: {}
            )
        }

        SceneDelegateProxy.shared.scene(scene, willConnectTo: session, options: connectionOptions)
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        SceneDelegateProxy.shared.scene(scene, openURLContexts: URLContexts)
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        SceneDelegateProxy.shared.scene(scene, continue: userActivity)
    }
}
