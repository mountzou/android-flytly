"""Build a simulator-only reference from the unchanged iOS SwiftUI editor body.
No Firebase SDK/configuration is linked. Only a mock store and initial UI state are injected.
"""
from pathlib import Path
import hashlib, json, plistlib, shutil, subprocess
source = Path('/Users/mountzou/ios-flytly/ios-where-travel')
work = Path('/private/tmp/flytly-onboarding-reference')
app = work / 'OnboardingReference.app'
app.mkdir(parents=True, exist_ok=True)
text = (source / 'TravelPreferences.swift').read_text()
ui = text[text.index('enum TravelPreferenceCatalog'):]
ui = ui.replace('@State private var step = 0', '@State private var step = Int(ProcessInfo.processInfo.environment["REFERENCE_STEP"] ?? "0") ?? 0')
ui = ui.replace('@State private var expandedMultiSelect: MultiSelectField?', '@State private var expandedMultiSelect: MultiSelectField? = ProcessInfo.processInfo.environment["REFERENCE_EXPANDED"] == "mustHave" ? .mustHave : nil')
mock = '''import SwiftUI
import Observation
@Observable @MainActor final class TravelPreferencesStore {
 var travelPace = 2; var natureAccess = 2; var cultureHistory = 2; var foodImportance = 2
 var crowdTolerance = 2; var mobilityWithoutCar = 2; var weatherImportance = 2; var budgetStyle = 2
 var mustHave = Set<String>(); var mustAvoid = Set<String>()
 var isSaving = false; var shouldPresent = true; var errorMessage: String?
 func save(skipped: Bool = false) async -> Bool { true }
}
'''
(work / 'Editor.swift').write_text(mock + ui)
style = (source / 'HomeView.swift').read_text().split('struct HomeView:')[0]
(work / 'Style.swift').write_text(style)
shutil.copy(source / 'AtlasTypography.swift', work / 'Typography.swift')
shutil.copy(source / 'AtlasActionButtonStyle.swift', work / 'Action.swift')
profile = (source / 'TravelProfileView.swift').read_text()
illustration = profile[profile.index('private struct TravelProfileEmptyStateIllustration:'):profile.index('enum TravelPreferenceKey:')].replace('private struct', 'struct', 1)
(work / 'Illustration.swift').write_text('import SwiftUI\n' + illustration)
(work / 'App.swift').write_text('''import SwiftUI
import UIKit
@main struct ReferenceApp: App {
 var body: some Scene { WindowGroup { CaptureView().ignoresSafeArea() } }
}
struct CaptureView: UIViewControllerRepresentable {
 func makeUIViewController(context: Context) -> CaptureController { CaptureController() }
 func updateUIViewController(_ controller: CaptureController, context: Context) {}
}
final class CaptureController: UIViewController {
 var capture: UIView!
 override func viewDidLoad() {
  super.viewDidLoad()
  let env = ProcessInfo.processInfo.environment
  let dark = env["REFERENCE_DARK"] == "1"
  overrideUserInterfaceStyle = dark ? .dark : .light
  let store = TravelPreferencesStore()
  if env["REFERENCE_SELECTED"] == "1" {
   store.mustHave = ["Direct flights or simple connections"]
   store.mustAvoid = ["Safety concerns"]
  }
  let illustration = env["REFERENCE_ILLUSTRATION"] == "1"
  let content: AnyView = illustration ? AnyView(TravelProfileEmptyStateIllustration()) : AnyView(TravelPreferencesOnboardingView(preferences: store))
  let host = UIHostingController(rootView: content.environment(\\.colorScheme, dark ? .dark : .light))
  host.safeAreaRegions = []
  addChild(host)
  host.view.frame = CGRect(x: 0, y: 0, width: illustration ? 230 : 390, height: illustration ? 230 : 780)
  host.view.backgroundColor = .clear
  view.addSubview(host.view); host.didMove(toParent: self)
  capture = host.view
 }
 override func viewDidAppear(_ animated: Bool) {
  super.viewDidAppear(animated)
  DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
   self.capture.layoutIfNeeded()
   let format = UIGraphicsImageRendererFormat(); format.scale = 3; format.opaque = false
   let image = UIGraphicsImageRenderer(size: self.capture.bounds.size, format: format).image { _ in
    self.capture.drawHierarchy(in: self.capture.bounds, afterScreenUpdates: true)
   }
   let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("capture.png")
   try! image.pngData()!.write(to: url)
  }
 }
}
''')
shutil.copy(source / 'Resources/Fonts/BricolageGrotesque.ttf', app / 'BricolageGrotesque.ttf')
info={'CFBundleIdentifier':'dev.flytly.onboardingreference','CFBundleExecutable':'OnboardingReference','CFBundleName':'Onboarding Reference','CFBundlePackageType':'APPL','CFBundleVersion':'1','CFBundleShortVersionString':'1','MinimumOSVersion':'26.0','UIDeviceFamily':[1],'UIAppFonts':['BricolageGrotesque.ttf'],'UILaunchScreen':{},'UIApplicationSceneManifest':{'UIApplicationSupportsMultipleScenes':False}}
(app / 'Info.plist').write_bytes(plistlib.dumps(info))
sdk = subprocess.check_output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True).strip()
subprocess.run(['xcrun','swiftc','-parse-as-library','-default-isolation','MainActor','-sdk',sdk,'-target','arm64-apple-ios26.0-simulator',*map(str,work.glob('*.swift')),'-o',str(app / 'OnboardingReference')],check=True)
subprocess.run(['codesign','--force','--sign','-',str(app)],check=True,capture_output=True)
Path('artifacts/onboarding-parity/reference-provenance.json').write_text(json.dumps({'source':'/Users/mountzou/ios-flytly','reference':'original SwiftUI editor with mock data; no Firebase','viewportPoints':[390,780],'sourceFileSHA256':hashlib.sha256(text.encode()).hexdigest(),'referenceUIInitialStateInjectionOnly':True},indent=2)+'\n')
print(app)
