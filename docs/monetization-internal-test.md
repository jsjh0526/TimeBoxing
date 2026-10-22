# Ad-free subscription: internal experiment only

This feature is on `feature/monetization`. Do not merge it into `main` or publish
it to Production as a finished billing implementation.

## Console configuration

1. Open the existing `dev.jsjh.timebox` app in Play Console.
2. Create a subscription with product ID `timebox_ad_free` and a display name
   such as `TimeBox Ad-Free` / `TimeBox 광고 제거`. Product IDs cannot be changed.
3. Add an auto-renewing monthly base plan with ID `monthly` and billing period
   one month. Set the South Korea price to KRW 3,000. No trial or introductory
   offer is implemented in this experiment. Activate the base plan when ready
   for the licensed test; do not promote the experiment to Production.
4. If Play requires a payments profile or legal/business verification, complete
   only accurate, applicable information. Code integration does not bypass
   account requirements. Do not treat this document as legal approval to sell.
5. Register the Google account used by the phone's Play Store in Play Console
   Settings > License testing. An internal-track tester list alone is not enough
   to guarantee test payments. The app's Google sign-in account is separate.
6. If using an internal release, also enroll that account using the internal
   test opt-in link. Keep tester access limited during the experiment.
7. In the Google purchase dialog, verify both the account and the test payment
   method (for example, `Test card, always approves`). Do not confirm with a real
   payment method. The in-app warning is not a substitute for license setup.

## Builds

- Debug builds enable the experiment and use Google test ad units.
- Normal release builds leave the experiment OFF.
- A release build for internal testing must explicitly use
  `-PenableMonetization=true`. This also switches all ad units and the AdMob app
  ID to Google's test IDs, so this experiment does not produce real ad traffic.
- The subscription price is read from Play ProductDetails. The app does not
  hardcode KRW 3,000 or display an invented price if the product is unavailable.
- A new Play upload needs a higher UNUSED version code across all tracks. Use
  `-PinternalTestVersionCode=...` only after checking Console. Do not reuse 46.
- Do not overwrite the preserved `app/release/app-release.aab` with a test bundle.
- Signing, package name, Room schema, sync, and reminders are unchanged.
- Do not uninstall the production app just to sideload a differently signed APK.
  Check data backup/sync first. An internal-track update through Play preserves
  Play App Signing and is the preferred path for the installed production app.

Example APK command (no upload):

```powershell
.\gradlew.bat :app:assembleRelease -PenableMonetization=true
```

License testers can also use a sideloaded debug build with the existing package
name; uploading a new production release is not required.

## Included behavior

- Removes opening native ads, TODO/settings/stats banners and support-ad card.
- Stops corresponding ad load/preload requests once entitlement is known.
- Grants widget access while an active subscription is returned by Play.
- Keeps existing account-scoped rewarded widget time separate and untouched.
- Restores using the Google Play purchase account, including after reinstall.
- Only `PURCHASED` purchases of the expected product can grant access. Pending
  purchases do not grant access. Acknowledgement must succeed before new access.
- A canceled subscription can remain in Play's active purchases until expiry.
  The app does not revoke merely because auto-renew is off. A successful query
  with no active entitlement returns the app to its free behavior.
- External purchase UI requires a resumed, attached, focused Activity. The
  update/review prompts do not start while a purchase is in progress.
- Billing checks never block rendering the app. A failed initial check releases
  the temporary ad hold after eight seconds; restore/retry remains available.
- Rechecks on app resume and once per minute while an active/pending subscription
  is visible in the foreground. Polling stops when the Activity pauses.

## Important limitations: NOT production-ready purchase verification

This experiment uses Play Billing client queries and acknowledgement. It does
NOT implement a secure verification backend, RTDN, or an authoritative expiry
timestamp. Never embed a Google service-account credential in the Android app.

A last successful active query is cached for at most 24 hours for transient
connection failures and the widget. Wall-clock reversal or elapsed-time reversal
invalidates that cache. A fresh successful empty query revokes it immediately.
The cache is NOT the subscription's actual expiry date. Without an app refresh,
a background widget can temporarily retain access until this cache expires.
This is deliberately bounded but is not exact background expiry enforcement.

Before real sales, implement server verification/authoritative expiry, refund,
revocation and renewal handling; decide account binding and offline policy;
complete seller/administrative requirements; review translations and policies;
and run signed-release testing. The internal experiment flag alone is not a
production security boundary or proof that an account cannot be charged.

## Manual test checklist

- Confirm normal release hides the subscription section.
- With no subscription, all existing free functions and test ads work.
- Product unavailable: no fake price, disabled purchase, restore remains usable.
- Approve a TEST payment: access granted, all ad placements disappear, widget
  unlocks and account-scoped rewarded expiry is unchanged.
- Cancel the purchase dialog: no access and no stuck busy indicator.
- Declined TEST payment: no access; retry works.
- Pending TEST payment: no access until approved; a pending cancellation does not
  grant anything. Resume the app to refresh the state.
- Restart/reinstall with the same licensed Play account, then restore.
- Cancel auto-renew: keep access until Play reports expiry. Resume/restore after
  accelerated test expiry and verify ads/rewarded widget rules return.
- Disconnect during refresh: retain valid cached access, retry on resume.
- Install/enroll using a different Play purchase account with no subscription,
  then successfully restore: no entitlement. Merely switching the account shown
  in the Play Store does not necessarily switch the app's purchase account.
- Verify cold start, widget entry, Back then widget re-entry, app update/review
  dialog interaction, and no permanent black screen on a physical device.

## References

- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/test
- https://support.google.com/googleplay/android-developer/answer/140504
- https://support.google.com/googleplay/android-developer/answer/6062777

