# macOS scrolling fix — desktop 0.1.1

Both the Android side strip and circular wheel use the same type-3 scroll packets. The receiver's previous handwritten declaration of `CGEventCreateScrollWheelEvent` was incorrect: it declared only the first three fixed parameters, omitting the fixed `int32_t wheel1` parameter before the variadic arguments.

On Apple Silicon, fixed and variadic arguments are passed differently. The old caller put the primary scroll delta on the stack while the system function expected it in a register, so the native event could contain an unrelated, enormous delta instead of the small signed pixel amount. This is consistent with a slight touch jumping directly to the end of a page. It is a Mac receiver bug, not an Android sensitivity setting.

The receiver now calls the fully fixed-argument `CGEventCreateScrollWheelEvent2(source, units, wheelCount, wheel1, wheel2, wheel3)`, with pixel units, two axes and an explicit zero third axis. Subpixel values accumulate per connection instead of being rounded away; zero output is not posted. Releasing or disconnecting resets those fractions. Existing Android direction and speed settings still apply; the wire protocol is unchanged. Windows input translation is unchanged.

The Mac-only regression test creates real Quartz scroll events, reads their signed vertical/horizontal pixel fields, and checks small deltas, both signs and both axes. **It does not post any events or move your mouse.** It runs automatically under `cargo test` in the Mac build script and the native macOS CI jobs. Portable tests cover subpixel accumulation, bounds, cancellation and independent axes. Physical macOS scrolling cannot be verified on the Windows development host; the user's new Mac build must still be tried in the affected applications.

## Update the installed Mac app

```sh
git pull --ff-only
bash scripts/build-macos.sh
```

Quit the old ThumbPad from its menu-bar menu. Open the resulting DMG in `dist/`, drag ThumbPad into Applications, choose Replace, and launch the installed copy. Confirm version 0.1.1 in the app's Finder Get Info panel. If macOS asks, approve the unsigned development build and enable Accessibility for the newly installed copy. Existing pairing data is retained by the same application identity. No Android update is required for this fix.

Retest with short upward/downward edge swipes, then a small clockwise/counterclockwise wheel turn, first at the normal scroll-speed setting. Horizontal scrolling should move only horizontally when enabled. A tiny gesture should move the document a small amount; it should not jump to either endpoint. Test the actual browser/editor you use rather than inferring behavior solely from the native event test.

References: [Apple's original API signature](https://developer.apple.com/documentation/coregraphics/cgeventcreatescrollwheelevent?language=objc), [fixed-argument API](https://developer.apple.com/documentation/coregraphics/cgevent/init(scrollwheelevent2source:units:wheelcount:wheel1:wheel2:wheel3:)?language=objc), and [Apple ARM64 argument-passing rules](https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms).
