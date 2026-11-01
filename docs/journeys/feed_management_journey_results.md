# Journey: Feed Management Journey

## Results

### Action: Type "hnrss.org/frontpage" into the "RSS Feed URL" input field ✅
- **Commands**:
  - `adb shell input tap 540 326`
  - `adb shell input text "hnrss.org/frontpage"`
- **Screenshot**: [step1_typed_url.png](../../screenshots/pr1/step1_typed_url.png)
- **Comment**: Focused the URL input field and typed raw URL without protocol.

### Action: Tap "Add Feed" ✅
- **Commands**:
  - `adb shell input tap 540 590`
- **Comment**: Tapped the Add Feed button.

### Action: Verify that "https://hnrss.org/frontpage" is displayed in the feed sources list ✅
- **Screenshot**: [step2_add_feed_result.png](../../screenshots/pr1/step2_add_feed_result.png)
- **Comment**: Verified in UI layout that `https://` was automatically prepended: `https://hnrss.org/frontpage`.

### Action: Verify that the "RSS Feed URL" input field is now empty ✅
- **Comment**: Verified in UI layout that `edit_rss_url` has been cleared.

### Action: Tap the feed item "https://hnrss.org/frontpage" in the feed sources list ✅
- **Commands**:
  - `adb shell input tap 434 726`
- **Comment**: Tapped the added feed source URL item.

### Action: Verify that the "RSS Feed URL" input field contains "https://hnrss.org/frontpage" ✅
- **Screenshot**: [step3_edit_mode.png](../../screenshots/pr1/step3_edit_mode.png)
- **Comment**: Confirmed input field was populated with selected URL and gained focus.

### Action: Verify that the button text changes to "Update Feed" ✅
- **Comment**: Confirmed `button_add_feed` text transitioned from "Add Feed" to "Update Feed".

### Action: Clear the input field and type "https://hnrss.org/newest" ✅
- **Commands**:
  - `adb shell input text "https://hnrss.org/newest"`
- **Comment**: Replaced input content with new URL target.

### Action: Tap "Update Feed" ✅
- **Commands**:
  - `adb shell input tap 540 590`
- **Comment**: Tapped Update Feed button to commit the edit.

### Action: Verify that "https://hnrss.org/newest" is displayed in the sources list and "https://hnrss.org/frontpage" is replaced ✅
- **Screenshot**: [step4_updated_feed_result.png](../../screenshots/pr1/step4_updated_feed_result.png)
- **Comment**: Confirmed that `https://hnrss.org/newest` replaced `https://hnrss.org/frontpage` in `sourcesContainer`.

### Action: Verify that the button text resets to "Add Feed" ✅
- **Comment**: Confirmed `button_add_feed` text reset to "Add Feed".
