# MASSIVE To Android And AppFunctions Reference

**Status:** research reference
**Date:** 2026-07-29
**Dataset:** Amazon MASSIVE v1.1

## Executive Summary

MASSIVE contains 60 intent labels across 18 voice-assistant scenarios. It is useful
for multilingual NLU pretraining and taxonomy design, but its labels are not an
Android capability contract.

The labels fall into four implementation groups:

1. deterministic local logic or a documented Android system surface;
2. a user-confirmed system UI, dangerous permission or app-owned database;
3. a provider integration, deep link, Home API or Matter integration;
4. an informational answer that belongs in local logic, search or an answer model.

Becoming the user-selected Android assistant does **not** automatically grant access
to AppFunctions. Cross-app AppFunction execution requires
`android.permission.EXECUTE_APP_FUNCTIONS` or its system equivalent. The current
platform documentation classifies `EXECUTE_APP_FUNCTIONS` as
`internal|privileged|knownSigner` and says it is granted only to privileged system
apps or certificates listed by the device in
`config_executeAppFunctionsKnownSigners`.

Therefore, a normal Play-installed Perplexity build does not gain AppFunctions
solely because the user selects it for `ROLE_ASSISTANT`. It can gain access only
when the OEM/system image also preloads it, grants a privileged permission or
allowlists its signing certificate. The target app must additionally expose and
enable the requested AppFunction.

## Legend

| Code | Meaning |
| --- | --- |
| `LOCAL` | Deterministic app code, no external provider |
| `SYSTEM_UI` | Documented implicit intent; user confirms in another app |
| `PERMISSION` | Direct provider/API access requiring runtime or special permission |
| `PROVIDER` | Specific app, web service, Home API, Matter or deep link required |
| `ANSWER` | Informational response, not an Android action |

## Complete 60-Intent Mapping

| MASSIVE intent | Class | Android implementation | Current assistant mapping |
| --- | --- | --- | --- |
| `datetime_query` | `LOCAL` | `java.time` and device time zone | `get_current_time` |
| `iot_hue_lightchange` | `PROVIDER` | Google Home API/Matter color trait or vendor API | unsupported |
| `transport_ticket` | `PROVIDER` | transport-provider app/API or web checkout | `web_search`, then validated link |
| `takeaway_query` | `ANSWER` | local/provider search | `web_search` |
| `qa_stock` | `ANSWER` | fresh market-data provider/search | `web_search` |
| `general_greet` | `ANSWER` | deterministic greeting or answer model | `unknown`/DeepSeek |
| `recommendation_events` | `ANSWER` | current local-event search | `web_search` |
| `music_dislikeness` | `PROVIDER` | active media provider rating/custom command | unsupported |
| `iot_wemo_off` | `PROVIDER` | Google Home API/Matter `OnOff` or vendor API | unsupported |
| `cooking_recipe` | `ANSWER` | recipe search or answer model | `web_search` |
| `qa_currency` | `ANSWER` | fresh exchange-rate provider plus local arithmetic | `web_search` |
| `transport_traffic` | `PROVIDER` | map/provider traffic data | `web_search` |
| `general_quirky` | `ANSWER` | answer model | `unknown`/DeepSeek |
| `weather_query` | `ANSWER` | cache or weather provider | `get_weather` |
| `audio_volume_up` | `LOCAL` | `AudioManager` with bounded stream adjustment | `set_volume` |
| `email_addcontact` | `SYSTEM_UI` | Contacts `ACTION_INSERT` with prefilled email | unsupported |
| `takeaway_order` | `PROVIDER` | provider deep link/API; checkout remains provider-owned | unsupported |
| `email_querycontact` | `SYSTEM_UI`/`PERMISSION` | contact picker or `READ_CONTACTS` | unsupported |
| `iot_hue_lightup` | `PROVIDER` | Google Home API/Matter level trait | unsupported |
| `recommendation_locations` | `ANSWER` | place search/maps provider | `web_search` |
| `play_audiobook` | `SYSTEM_UI`/`PROVIDER` | media search intent or provider deep link | partial `control_media`/`open_app` |
| `lists_createoradd` | `LOCAL` | app-owned Room list/task store | requires new `manage_list` |
| `news_query` | `ANSWER` | current news retrieval | `web_search` |
| `alarm_query` | `SYSTEM_UI` | `AlarmClock.ACTION_SHOW_ALARMS`; does not return alarm data | unsupported |
| `iot_wemo_on` | `PROVIDER` | Google Home API/Matter `OnOff` or vendor API | unsupported |
| `general_joke` | `ANSWER` | bundled content or answer model | `unknown`/DeepSeek |
| `qa_definition` | `ANSWER` | local dictionary or search | `unknown`/DeepSeek |
| `social_query` | `PROVIDER` | social provider API with user authorization | unsupported |
| `music_settings` | `SYSTEM_UI` | allowlisted sound/media settings surface | `open_setting` where allowlisted |
| `audio_volume_other` | `LOCAL` | `AudioManager` absolute/relative adjustment | `set_volume` |
| `calendar_remove` | `PERMISSION` | known event ID plus `WRITE_CALENDAR`; no generic safe delete intent | unsupported |
| `iot_hue_lightdim` | `PROVIDER` | Google Home API/Matter level trait | unsupported |
| `calendar_query` | `PERMISSION` | Calendar Provider with `READ_CALENDAR` | unsupported |
| `email_sendemail` | `SYSTEM_UI` | `ACTION_SENDTO` with `mailto:` composer | `compose_email` |
| `iot_cleaning` | `PROVIDER` | supported robot-cleaner Home/vendor command | unsupported |
| `audio_volume_down` | `LOCAL` | `AudioManager` with bounded stream adjustment | `set_volume` |
| `play_radio` | `SYSTEM_UI`/`PROVIDER` | media search intent or provider deep link | partial `control_media`/`open_app` |
| `cooking_query` | `ANSWER` | recipe/food-information search | `web_search` |
| `datetime_convert` | `LOCAL` | `java.time.ZoneId` conversion | requires new local intent |
| `qa_maths` | `LOCAL` | validated arithmetic parser | `calculate` |
| `iot_hue_lightoff` | `PROVIDER` | Google Home API/Matter `OnOff` | unsupported |
| `iot_hue_lighton` | `PROVIDER` | Google Home API/Matter `OnOff` | unsupported |
| `transport_query` | `SYSTEM_UI`/`ANSWER` | `geo:`/map directions or transport search | `start_navigation` when destination is actionable |
| `music_likeness` | `PROVIDER` | active media provider rating/custom command | unsupported |
| `email_query` | `PROVIDER` | mail-provider API/OAuth; Android has no generic inbox API | unsupported |
| `play_music` | `SYSTEM_UI`/`PROVIDER` | `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` or provider | partial `control_media`/`open_app` |
| `audio_volume_mute` | `LOCAL` | `AudioManager` mute adjustment | `set_volume` |
| `social_post` | `SYSTEM_UI` | `ACTION_SEND` and system Sharesheet | requires new `share_content` |
| `alarm_set` | `SYSTEM_UI` | `AlarmClock.ACTION_SET_ALARM` | `set_alarm` |
| `qa_factoid` | `ANSWER` | local knowledge or grounded search | `unknown` or `web_search` |
| `calendar_set` | `SYSTEM_UI` | `ACTION_INSERT` with `Events.CONTENT_URI` | `create_calendar_event` |
| `play_game` | `SYSTEM_UI` | launch a resolved installed package | `open_app` |
| `alarm_remove` | `SYSTEM_UI` | `AlarmClock.ACTION_DISMISS_ALARM`; handler support may vary | unsupported |
| `lists_remove` | `LOCAL` | app-owned Room list/task store | requires new `manage_list` |
| `transport_taxi` | `PROVIDER` | taxi-provider deep link/API | `web_search`, then validated link |
| `recommendation_movies` | `ANSWER` | catalog/search/recommendation provider | `web_search` |
| `iot_coffee` | `PROVIDER` | supported appliance Home/vendor command | unsupported |
| `music_query` | `PROVIDER`/`ANSWER` | media catalog/provider search | partial `control_media` or `web_search` |
| `play_podcasts` | `SYSTEM_UI`/`PROVIDER` | media search intent or provider deep link | partial `control_media`/`open_app` |
| `lists_query` | `LOCAL` | app-owned Room list/task store | requires new `manage_list` |

## Recommended Product Taxonomy

Do not copy the 60 labels directly. Consolidate labels that share one validated
execution contract:

```text
audio_volume_*                         -> set_volume(action, level?)
play_music|radio|podcasts|audiobook   -> play_media(media_type, query)
alarm_set|query|remove                -> manage_alarm(action, time?, label?)
lists_createoradd|remove|query        -> manage_list(action, list?, item?)
transport_query|traffic|taxi|ticket   -> transport(action, destination?, provider?)
iot_*                                 -> control_home(device, trait, operation, value?)
```

The classifier may use a learned domain head, but routing must not use keyword
fallbacks. Every mutating function requires normalized parameters, capability
resolution and explicit confirmation appropriate to its risk.

## AppFunctions Permission Model

AppFunctions is available from Android 16/API 36 and remains a limited
beta/experimental pipeline.

There are two independent roles:

1. a provider app declares and implements an AppFunction;
2. an agent discovers and executes it through `AppFunctionManager`.

For cross-app calls, the agent needs `EXECUTE_APP_FUNCTIONS` or the documented
system equivalent. Selecting an app as `ROLE_ASSISTANT` only establishes the default
assist/voice-interaction app. It does not override the AppFunctions permission
check.

Execution also fails when:

- the device does not support AppFunctions;
- the target app exposes no AppFunctions;
- the function is disabled;
- the caller cannot query the target package/function;
- the OEM has not privileged or allowlisted the agent;
- the provider requires an additional permission or user confirmation.

### Perplexity Consequence

The accurate answer is conditional:

- **Play-installed + selected as default assistant:** no automatic AppFunctions
  access.
- **OEM-preloaded/privileged or known-signer allowlisted build:** potentially yes.
- **A future platform policy that grants the permission to role holders:** possible,
  but it is not the current public permission contract and must be re-verified.

On the OPPO CPH2765 inspected on 2026-07-29, Perplexity was not installed and the
current `ROLE_ASSISTANT` holder was
`com.google.android.googlequicksearchbox`. This observation does not establish what
another firmware or Perplexity distribution can access.

## Device Verification

Use read-only discovery before building an AppFunctions client:

```bash
adb shell cmd role get-role-holders android.app.role.ASSISTANT
adb shell pm list packages | grep -i perplex
adb shell dumpsys package <agent.package> | grep -E \
  'EXECUTE_APP_FUNCTIONS|EXECUTE_APP_FUNCTIONS_SYSTEM'
adb shell cmd app_function list-app-functions
```

An APK declaring the permission is not sufficient. Confirm that package-manager
state reports the permission as granted, then execute a harmless test function and
handle `AppFunctionDeniedException`.

## Sources

### Dataset

- [MASSIVE dataset card](https://huggingface.co/datasets/AmazonScience/massive)
- [MASSIVE v1.1 loader: scenarios, intents, locales and fields](https://huggingface.co/datasets/AmazonScience/massive/blob/main/massive.py)
- [MASSIVE tools and modeling repository](https://github.com/alexa/massive)

### AppFunctions And Assistant Role

- [AppFunctions overview](https://developer.android.com/ai/appfunctions)
- [Platform AppFunctions package overview](https://developer.android.com/reference/android/app/appfunctions/package-summary)
- [AppFunctionManager API](https://developer.android.com/reference/android/app/appfunctions/AppFunctionManager)
- [Manifest.permission: `EXECUTE_APP_FUNCTIONS`](https://developer.android.com/reference/android/Manifest.permission#EXECUTE_APP_FUNCTIONS)
- [Jetpack AppFunctions API](https://developer.android.com/reference/androidx/appfunctions/package-summary)
- [Assistant role API](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat#ROLE_ASSISTANT)

### Android Capabilities

- [Common implicit intents](https://developer.android.com/guide/components/intents-common.html)
- [AlarmClock API](https://developer.android.com/reference/android/provider/AlarmClock)
- [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider)
- [Contacts Provider](https://developer.android.com/identity/providers/contacts-provider)
- [Modify contacts using intents](https://developer.android.com/identity/providers/contacts-provider/modify-data)
- [MediaSession playback control](https://developer.android.com/media/media3/session/control-playback)
- [Google Home APIs for Android](https://developers.home.google.com/apis/android/overview)
- [Google Home device control](https://developers.home.google.com/apis/android/device/control)
