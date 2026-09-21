# Smartico Kotlin demo





https://github.com/user-attachments/assets/9f366715-3254-4912-b0f8-92d92eec1969



**A showcase of building a fully custom, native gamification UI on top of the
Smartico API.**

This is not a drop-in widget and not a wrapped web page. Every screen you see —
missions, tournaments, raffles, jackpots, levels, leaderboard, store, inbox — is
a plain Jetpack Compose screen, styled by us, fed by Smartico API calls.
Smartico is the backend; the look, the navigation and the interaction model are
entirely the app's own. Swap the theme and it becomes your brand.

It is a Smartico demo app (parity with the web https://play.smartico.ai) built on
**[`ai.smartico:kotlin-public-api`](https://central.sonatype.com/artifact/ai.smartico/kotlin-public-api)** —
Smartico's Kotlin/JVM SDK that talks the WebSocket protocol directly, with no
browser and no `smartico.js` page script. It supplies the typed API surface
(missions, tournaments, store, jackpots, raffles, leaderboard, inbox, …) as
suspend functions, plus the protocol documentation.

Kotlin 2.1 · Jetpack Compose (BOM 2024.12) · minSdk 24 · JDK 17. Pure JVM SDK —
no AAR, no manifest merging.

## Try it on a device

[**Download the Android demo APK →**](https://github.com/smarticoai/android-kotlin-demo-app/releases/latest)

No toolchain needed — install it and the whole demo runs against the live ICE
demo label. Sign in with Google.

The build is signed with the standard Android debug key, so Android will ask you
to allow installation from an unknown source and Play Protect may warn about it.
That is expected for a demo build.

## What is demonstrated

**Session & identity**
- Google sign-in through Credential Manager, verified by a demo backend
  (SL_SERVER) that owns the `user_ext_id` mapping, and session restore on cold
  start from a stored `cookie_token`.
- `Smartico.init` / identify with a hashed `extUserId`, logout, and a profile
  enrichment round-trip through custom events.
- One event bridge over the socket (`identify`, `props_change`, `engagement`,
  `execute_deeplink`, `reload_achievements`, `show_spin`) feeding a single
  `StateFlow` store, so no composable subscribes to the socket itself.

**Native gamification screens — API in, your own UI out**
- Missions & badges — list, detail, opt-in, claim reward
  (`getMissions`, `getBadges`, `requestMissionOptIn`, `requestMissionClaimReward`).
- Tournaments — lobby, detail with leaderboard / prizes tabs, registration
  (`getTournamentsList`, `getTournamentInstanceInfo`, `registerInTournament`).
- Raffles — list, draws, draw history, opt-in, prize claim
  (`getRaffles`, `getRaffleDrawRun`, `getRaffleDrawRunsHistory`,
  `requestRaffleOptin`, `claimRafflePrize`).
- Jackpots — pots, opt-in (`jackpotGet`, `jackpotOptIn`).
- Store — catalogue and purchase (`getStoreItems`, `buyStoreItem`).
- Levels / VIP progress (`getLevels`, `getCurrentLevel`) and live public props
  (points, balances, username) pushed over the socket.
- Leaderboard (`getLeaderBoard`).
- Inbox — messages, read / unread, favourites, delete, HTML bodies
  (`getInboxMessages`, `getInboxMessageBody`, `markInboxMessageAsRead`, …), plus
  the engagement analytics behind them (`reportImpressionEvent` when a body is
  opened, `reportClickEvent` for a CTA or an in-body link).
- Avatars, including AI avatar generation (`getAvatarsList`, `getAvatarPrompts`,
  `avatarsCustomize`, `getAvatarsCustomized`, `setAvatar`).
- Mini-games catalogue (`getMiniGames`) and per-game related items
  (`getRelatedItemsForGame`).

**Engagement & routing**
- Engagement popups — the SDK owns the queue, the dedupe and the bridge
  handshake, the app owns the presentation (transparent WebView host, fade,
  always composed so the handshake cannot be missed). The wrapper page reports
  its own impressions and clicks through the bridge, so no analytics code is
  needed here.
- Deep links (`dp:`) — the SDK parses and dispatches, the app binds them to its
  own screens, plus custom handlers (`dp:deposit`, `dp:opencashier`) and
  mapping of `play.smartico.ai/...` URLs onto native screens. Links also arrive
  as plain intents, at cold start and while the app is running.
- Push notifications (FCM) — token registration after identify (cid 1003), the
  notification built and posted by the app for data-only campaigns, and full
  lifecycle analytics (delivered / impression / action), including a tap that
  cold-starts the app: the deep link is parked until the user is identified.

**Three rendering tiers — you choose per surface**
1. **Native** — everything listed above. API call in, your composables out.
2. **In-app WebView** — mini-games (`gf_saw`, `gf_section`, `gf_quiz`,
   `gf_matchx`), engagement popups, and inbox HTML bodies.
3. **External browser** — widget sections this demo deliberately does not build
   natively (bonuses, clans) open the hosted wrapper page. Register a native
   handler for one later and it wins automatically.

The demo casino around it (lobby, game screen, fake wallet, slots, cashier) is
scaffolding, not integration surface.

> **Note on the identify hash.** This demo computes it client-side only because
> the demo label's hash salt is the literal string `"null"`. A production
> operator computes `md5(ext_user_id:SALT:ts)` on its **backend** with a secret
> salt and hands the result to the app. Do not ship `Sdk.demoHash()`.

## Build your own with Claude Code or Codex

The fastest path to your own app is not reading this repo line by line — it is
handing it to a coding agent as a worked example, together with the API
documentation, and describing the UI you want.

```bash
git clone https://github.com/smarticoai/android-kotlin-demo-app.git
git clone https://github.com/smarticoai/public-api.git
```

Then open your own project next to them and give the agent both references:

> Use `../android-kotlin-demo-app` as a reference implementation of a native
> Smartico integration — `Sdk.kt` is the integration surface (identify, the
> event bridge, public props, custom events), `MainActivity.kt` wires deep links
> and the engagement host, `Push.kt` covers notifications, and `Screens.kt`
> shows the API calls behind each screen. Use `../public-api` for the API
> reference and the protocol docs. Build me a `missions` screen (or
> `tournaments`, `raffles`, …) in my app, in my design system, using the same
> integration pattern.

Why this works: the integration surface is small, commented and separated from
the demo's own visual styling on purpose, so an agent can lift the pattern
without dragging the casino UI along.

## Setup

```bash
./gradlew :app:assembleDebug
```

The SDK comes from Maven Central
([`ai.smartico:kotlin-public-api`](https://central.sonatype.com/artifact/ai.smartico/kotlin-public-api)),
pinned in `app/build.gradle.kts` — Central releases are immutable, so that
number is what ties the demo to a given SDK. Coroutines and
kotlinx-serialization arrive transitively; OkHttp stays off the compile
classpath.

Needs JDK 17 or newer and an Android SDK (`local.properties` → `sdk.dir`,
written by Android Studio on first open). The Gradle wrapper is committed, so
nothing else has to be installed.

**Push notifications** need `app/google-services.json` (Firebase project
`btg-demo`, android client `ai.smartico.rnexpo`) — it is NOT committed (shared
demo Firebase project). Request it from the team; the app builds and runs
without it, and token registration is skipped gracefully.

## Run

```bash
./gradlew :app:installDebug   # device with USB debugging, or an emulator
```

Google sign-in works because the debug-keystore SHA-1 is registered for the
`ai.smartico.rnexpo` application id. The emulator must use a **Google Play**
system image, or sign-in (and FCM) have nothing to talk to.

A deep link can be handed to the app from the shell, which is the quickest way
to reach a screen without clicking through:

```bash
adb shell am start -n ai.smartico.rnexpo/ai.smartico.fakecasino.MainActivity --es dp dp:gf_missions
```

## Working on the SDK (optional)

Normally the SDK comes from Maven Central, which is exactly what a client gets.
To develop against a local checkout instead, add one line to
`settings.gradle.kts`:

```kotlin
includeBuild("../kotlin-public-api")
```

Gradle then substitutes the local project for the `ai.smartico:kotlin-public-api`
coordinate, so edits in the SDK are picked up on the next build with no
publishing step in between. Remove the line to go back to the published
artifact.

## Layout

```
app/src/main/kotlin/ai/smartico/fakecasino/
  Sdk.kt              ← the integration surface (what an operator replicates)
  MainActivity.kt       deep-link bindings, engagement host, push tap entry
  Auth.kt               SL_SERVER social login, session restore
  Providers.kt          Credential Manager (Google) plumbing
  Push.kt               FCM service, token registration, notifications, analytics
  PopupHost.kt          popup host (transparent WebView over PopupBridgeSession)
  WidgetScreen.kt       in-app WebView for mini-game deep links
  Nav.kt                tab routes and navigation
  Screens.kt | Details.kt | Raffles.kt | InboxScreen.kt | ProfileExtras.kt
                        ← the API calls behind each screen
  Casino|Catalog|GameScreen|CashierScreen|Economy|Chrome|Ui|App
                        ← demo-casino UI (not integration-relevant)
```

Label/brand config: `Sdk.kt` (ICE env4 demo label; its hash salt is literally
`"null"`, so the identify hash is computed client-side — production operators
compute it on their backend).

## Known manual steps

- The application id is `ai.smartico.rnexpo`, deliberately the same as the
  React Native demo, so both reuse the Google sign-in and FCM registrations.
  As a consequence the two demos cannot live side by side on one device:
  installing one replaces the other.
- Android emulator must use a **Google Play** system image, or Google Sign-In
  (and FCM) won't work.

## The SDK

[![Maven Central](https://img.shields.io/maven-central/v/ai.smartico/kotlin-public-api?label=Maven%20Central)](https://central.sonatype.com/artifact/ai.smartico/kotlin-public-api)

```kotlin
dependencies {
    implementation("ai.smartico:kotlin-public-api:0.1.0")
}
```

https://central.sonatype.com/artifact/ai.smartico/kotlin-public-api
