#!/usr/bin/env bash
# آزمون ابزاری مسیر دریافت (D64) روی شبیه‌ساز.
#
# `SMS_DELIVER` را فقط سیستم می‌فرستد، پس پیامک باید واقعاً از مودم شبیه‌ساز
# برسد. این اسکریپت هر آزمون ReceivePathTest را جدا اجرا می‌کند، منتظر خط
# «READY <متن>» در logcat می‌ماند و همان متن را با `adb emu sms send` می‌فرستد.
set -euo pipefail

APP=ir.asudehapp.sms
RUNNER=$APP.test/$APP.app.AsudehTestRunner
CLASS=$APP.app.ReceivePathTest
SENDER=09121234567

./gradlew --no-daemon :app:installDebug :app:installDebugAndroidTest

# اپ پیش‌فرض پیامک (فقط اپ پیش‌فرض SMS_DELIVER می‌گیرد). درست پس از بالا
# آمدن شبیه‌ساز، سرویس role گاهی با TimeoutException پاسخ نمی‌دهد؛ پس چند بار
# تلاش می‌شود و نتیجه با get-role-holders سنجیده می‌شود، نه با کد خروج.
holder=""
for attempt in 1 2 3 4 5; do
  adb shell cmd role add-role-holder android.app.role.SMS "$APP" 0 || true
  holder=$(adb shell cmd role get-role-holders android.app.role.SMS | tr -d '\r' || true)
  [ "$holder" = "$APP" ] && break
  echo "تلاش $attempt برای اپ پیش‌فرض شدن ناموفق بود؛ دوباره"
  sleep 10
done
if [ "$holder" != "$APP" ]; then
  echo "آسوده اپ پیش‌فرض پیامک نشد: «$holder»"
  exit 1
fi

run_case() {
  local method=$1 out line body pid
  out=$(mktemp)
  adb logcat -c
  adb shell am instrument -w -e class "$CLASS#$method" "$RUNNER" >"$out" 2>&1 &
  pid=$!

  line=""
  for _ in $(seq 1 120); do
    line=$(adb logcat -d -s AsudehReceiveTest:I | grep -o 'READY asudeh-d64-[A-Za-z0-9-]*' | head -1 || true)
    [ -n "$line" ] && break
    sleep 1
  done
  if [ -z "$line" ]; then
    echo "آزمون $method اعلام آمادگی نکرد"
    wait "$pid" || true
    cat "$out"
    return 1
  fi

  body=${line#READY }
  echo "$method: فرستادن «$body» از $SENDER"
  adb emu sms send "$SENDER" "$body"

  wait "$pid" || true
  cat "$out"
  if ! grep -q '^OK (1 test)' "$out"; then
    echo "--- logcat"
    adb logcat -d -s AsudehReceiveTest AsudehSmsReceiver AndroidRuntime TestRunner | tail -100
    return 1
  fi
}

run_case receivedSmsIsStoredAndClassified
run_case receivedSmsSurvivesClassifierCrash
