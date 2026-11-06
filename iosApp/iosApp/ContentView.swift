import SwiftUI
import UIKit
import Shared

/// 承载 Compose 界面的 UIViewController 包装。
struct ComposeView: UIViewControllerRepresentable {

    /// 创建共享层导出的控制器。
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    /// Compose 内部自行管理状态，此处无需更新。
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// iOS 根视图。
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
