#!/system/bin/sh
(
  count=0
  while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$count" -lt 120 ]; do
    sleep 1
    count=$((count + 1))
  done
  if [ "$(getprop sys.boot_completed)" = "1" ]; then
    sleep 3
    am start --user 0 -n tv.glasslauncher43/.MainActivity >/dev/null 2>&1
  fi
) >/dev/null 2>&1 &

