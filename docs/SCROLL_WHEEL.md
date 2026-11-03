# Android 0.1.1 — optional scroll wheel

Enable **Settings → Show scroll wheel → Save & close**. A circular wheel appears at the lower thumb-side corner of the trackpad. Turn the ring clockwise/counterclockwise to scroll in opposite directions. The existing natural-direction and scroll-speed settings apply; handedness moves the wheel to the opposite corner. Lift and turn again for continuous scrolling. The preference is saved across launches. The wheel is optional and starts hidden on an existing or new installation.

The wheel sends the existing type-3 scroll packets over the authenticated connection, so Windows and Mac receivers do not need an update. It never emits cursor movement or clicks, including a tap in its centre. Multi-touch, cancellation and lifting stop wheel tracking. Entering or crossing the centre, leaving/reentering the wheel and discontinuous angular changes reset the anchor to avoid sudden page jumps. It has no inertia or continuous vibration. Screen readers expose scroll-up/down actions.

Validation on Windows, 3 November 2026:

- Android debug/release and instrumentation APK builds passed. Debug APK version 0.1.1 / code 2, minSdk 26, targetSdk 36; development v2 signature verified.
- 15 Kotlin unit tests passed, including 7 rotary-gesture tests. Android lint passed with zero errors (18 existing dependency/platform/style warnings).
- Three Android API 29 emulator interaction/integration tests passed, covering adding/hiding the wheel, saved settings, left-handed positioning, scroll-only gestures, and the existing pinned-TLS pairing/click/drag/reconnect path. The connected scenario also turns the wheel through the live Android transport. The receiver uses dry native dispatch, so this does not claim physical desktop or Samsung hardware validation.
- Actual emulator screenshot: [scroll wheel](screenshots/android-scroll-wheel.png).

Install the updated debug APK over the previous debug build to retain pairs/settings, provided both builds use the same development signing key. Release APK remains unsigned without signing credentials. Physical device ergonomics and desktop scroll feel still need user validation.
