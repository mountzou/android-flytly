#!/usr/bin/env python3
"""Prepare an isolated UI-test copy of the unchanged iOS app for parity captures."""
from pathlib import Path
import hashlib,json,shutil
source=Path('/Users/mountzou/ios-flytly')
work=Path('/private/tmp/flytly-places-reference-project')
work.mkdir(exist_ok=True)
for name in ['flytly.xcodeproj','ios-where-travel','WidgetShared','FlytlyWidgets','flytlyTests']:
 target=work/name
 if target.exists(): shutil.rmtree(target)
 shutil.copytree(source/name,target)
(work/'firebase').mkdir(exist_ok=True)
for name in ['Info.plist','GoogleService-Info.plist']:
 if (source/'firebase'/name).exists(): shutil.copyfile(source/'firebase'/name,work/'firebase'/name)
tests=work/'flytlyUITests';tests.mkdir(exist_ok=True)
(tests/'PlacesReferenceUITests.swift').write_text('''import XCTest
final class PlacesReferenceUITests: XCTestCase {
 override func setUpWithError() throws { continueAfterFailure = false }
 @MainActor func testLightReference() { capture(theme: "light") }
 @MainActor func testDarkReference() { capture(theme: "dark") }
 @MainActor private func capture(theme: String) {
  let app = XCUIApplication()
  let fixture = UUID().uuidString
  app.launchArguments = ["-map-testing", "-place-experience-test", fixture, "-visit-store-test", fixture, "-collection-layout-test", "-appearanceTheme", theme]
  app.launch()
  XCTAssertTrue(app.buttons["addVisitsButton"].waitForExistence(timeout: 30))
  app.buttons["navigationMenuButton"].tap()
  app.buttons["My travels"].tap()
  XCTAssertTrue(app.buttons["my-place-GRC"].waitForExistence(timeout: 10))
  attach(app, "ios-" + theme + "-travels")
  app.navigationBars.buttons.element(boundBy: 0).tap()
  app.buttons["Wishlist"].tap()
  XCTAssertTrue(app.buttons["wishlist-country-BEL"].waitForExistence(timeout: 10))
  attach(app, "ios-" + theme + "-wishlist")
  app.navigationBars.buttons.element(boundBy: 0).tap()
  app.buttons["closeNavigationMenuButton"].tap()
  app.buttons["addVisitsButton"].tap()
  let search = app.textFields["addCountrySearch"]
  XCTAssertTrue(search.waitForExistence(timeout: 10)); search.tap(); search.typeText("Greece")
  XCTAssertTrue(app.buttons["add-country-GRC"].waitForExistence(timeout: 10))
  app.buttons["add-country-GRC"].tap()
  XCTAssertTrue(app.buttons["suggested-city-ghsl-r2024a-854"].waitForExistence(timeout: 10))
  attach(app, "ios-" + theme + "-country")
  app.buttons["suggested-city-ghsl-r2024a-854"].tap()
  XCTAssertTrue(app.buttons["markVisitedButton"].waitForExistence(timeout: 10))
  attach(app, "ios-" + theme + "-city")
  app.buttons["saveWishlistButton"].tap()
  XCTAssertTrue(app.buttons["Undo"].waitForExistence(timeout: 10))
  let toastGone = XCTNSPredicateExpectation(predicate: NSPredicate { _,_ in !app.buttons["Undo"].exists }, object: nil)
  XCTAssertEqual(XCTWaiter.wait(for: [toastGone], timeout: 10), .completed)
  attach(app, "ios-" + theme + "-city-saved")
  app.buttons["markVisitedButton"].tap()
  XCTAssertTrue(app.buttons["Undo"].waitForExistence(timeout: 10))
  let visitToastGone = XCTNSPredicateExpectation(predicate: NSPredicate { _,_ in !app.buttons["Undo"].exists }, object: nil)
  XCTAssertEqual(XCTWaiter.wait(for: [visitToastGone], timeout: 10), .completed)
  attach(app, "ios-" + theme + "-city-visited")
 }
 @MainActor private func attach(_ app: XCUIApplication, _ name: String) {
  // Accessibility removal precedes SwiftUI's final toast transition frame.
  Thread.sleep(forTimeInterval: 1)
  let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = name; shot.lifetime = .keepAlways; add(shot)
 }
}
''')
files=['PlaceDetailView.swift','MyPlacesView.swift','WishlistView.swift','PlacesCollection.swift','PlacesCollectionLayout.swift','AtlasTypography.swift','PlaceSelectionView.swift']
record={name:hashlib.sha256((source/'ios-where-travel'/name).read_bytes()).hexdigest() for name in files}
Path('artifacts/place-parity/reference-provenance.json').write_text(json.dumps({'source':str(source),'copy':str(work),'appSourcesUnchanged':True,'testOnlyFixture':'matching UUIDs through original PlaceExperienceTestHost; no account records','themeOverride':'appearanceTheme launch argument','sourceSHA256':record},indent=2)+'\n')
print(work)
