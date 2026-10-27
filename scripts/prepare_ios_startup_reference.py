#!/usr/bin/env python3
"""Prepare a standalone, Firebase-free copy of the real iOS startup views in /tmp."""
from pathlib import Path
import hashlib,json,plistlib,shutil

ios=Path('/Users/mountzou/ios-flytly')
target=Path('/private/tmp/flytly-startup-reference')
target.mkdir(parents=True,exist_ok=True)
app=target/'StartupReference.app';app.mkdir(exist_ok=True)
ui=ios/'ios-where-travel'
hashes={}
for name in ['AtlasLoadingView.swift','AtlasTypography.swift']:
    original=(ui/name).read_bytes();(target/name).write_bytes(original)
    hashes[name]=hashlib.sha256(original).hexdigest()
# Append a reference-only export in the same file to access its private frame enum.
with (target/'AtlasLoadingView.swift').open('a') as f:
    f.write('''
func exportStartupMasks() {
    let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("startup-masks")
    try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    for (index, image) in MetaballFrames.images.enumerated() {
        try? UIImage(cgImage: image).pngData()?.write(to: folder.appendingPathComponent("frame-\\(index).png"))
    }
}
''')
style=(ui/'HomeView.swift').read_text().split('struct HomeView: View')[0]
(target/'AtlasStyle.swift').write_text(style)
(target/'Reference.swift').write_text('''import SwiftUI
import UIKit

struct StoryboardReference: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        UIStoryboard(name: "LaunchScreen", bundle: nil).instantiateInitialViewController()!
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
@main struct StartupReference: App {
    init() { exportStartupMasks() }
    var body: some Scene {
        WindowGroup {
            Group {
                if ProcessInfo.processInfo.arguments.contains("--launch") {
                    StoryboardReference().ignoresSafeArea()
                } else {
                    AtlasLoadingView(title: "Preparing your Flytly..")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(AtlasStyle.paper.ignoresSafeArea())
                }
            }.preferredColorScheme(ProcessInfo.processInfo.arguments.contains("--dark") ? .dark : .light)
        }
    }
}
''')
shutil.copyfile(ui/'Resources/Fonts/BricolageGrotesque.ttf',app/'BricolageGrotesque.ttf')
assets=target/'Startup.xcassets';assets.mkdir(exist_ok=True)
for name in ['LaunchLogo.imageset','LaunchForeground.colorset','LaunchBackground.colorset']:
    shutil.copytree(ui/'Assets.xcassets'/name,assets/name,dirs_exist_ok=True)
with (app/'Info.plist').open('wb') as f:
    plistlib.dump({'CFBundleIdentifier':'com.mountzou.flytly.startupreference','CFBundleName':'StartupReference','CFBundleExecutable':'StartupReference','CFBundlePackageType':'APPL','CFBundleVersion':'1','CFBundleShortVersionString':'1.0','UILaunchStoryboardName':'LaunchScreen','UIAppFonts':['BricolageGrotesque.ttf'],'LSRequiresIPhoneOS':True,'UIApplicationSceneManifest':{'UIApplicationSupportsMultipleScenes':False}},f)
qa=Path(__file__).resolve().parents[1]/'artifacts/startup-parity'
(qa/'ios-reference-provenance.json').write_text(json.dumps({'sourceSHA256':hashes,'uiBodyUnchanged':True,'fixture':'Standalone app, same SwiftUI body and actual storyboard, no Firebase; appended frame export only.'},indent=2)+'\n')
print(target)
