# SahlBit Launcher — 0.1.2

پروژه آزمایشی لانچر اندرویدی سهل‌بیت. این نسخه علاوه بر صفحه اصلی نمونه، صفحه تنظیمات مدیریت لانچر پیش‌فرض را اضافه می‌کند.

## مشخصات
- نام: سهل‌بیت (SahlBit)
- شناسه برنامه: `ir.sahlbit.launcher`
- حداقل اندروید: Android 10 / API 29
- زبان: Kotlin
- رابط: Jetpack Compose
- ساخت APK: GitHub Actions (بدون نیاز به کامپیوتر قدرتمند)

## قابلیت جدید: انتخاب و بازیابی لانچر
- پس از هفت لمس روی آیکون تنظیمات صفحه اصلی، صفحه تنظیمات باز می‌شود.\n- پس از ثبت یک خطای بحرانیِ مدیریت‌نشده، در اجرای بعدی صفحه بازیابی پیشنهاد می‌شود تا کاربر بتواند تنظیمات انتخاب لانچر اندروید را باز کند.\n- وابستگی آیکون‌ها از بسته کامل `material-icons-extended` به `material-icons-core` کاهش یافته تا حجم خروجی کمتر شود.
- «انتخاب سهل‌بیت به‌عنوان لانچر اصلی» از Android RoleManager برای درخواست نقش HOME استفاده می‌کند.
- «انتخاب لانچر سامسونگ (One UI Home)» تنظیمات استاندارد انتخاب Home app اندروید را باز می‌کند؛ کاربر باید One UI Home را خودش انتخاب کند.
- راهنمای بازیابی در صورت خطا در صفحه تنظیمات نمایش داده می‌شود.

### محدودیت مهم اندروید
یک برنامه عادی نمی‌تواند بدون رضایت کاربر، نقش لانچر پیش‌فرض را به One UI Home تغییر دهد. همچنین برنامه نمی‌تواند تضمین کند پس از هر نوع crash خودکار لانچر دیگری فعال شود. برای همین، این نسخه هیچ محدودیت Device Owner، Lock Task یا قفل خروجی اعمال نمی‌کند و مسیر استاندارد تنظیم لانچر اندروید را در دسترس نگه می‌دارد. One UI Home حذف یا غیرفعال نمی‌شود.

اگر سهل‌بیت crash کند، کاربر می‌تواند با بازکردن تنظیمات سیستم و انتخاب One UI Home بازیابی کند. مسیر معمول سامسونگ: Settings → Apps → Choose default apps → Home app → One UI Home (نام منو ممکن است با نسخه One UI فرق کند).

## ساخت APK در GitHub
1. یک مخزن GitHub با نام `SahlBitLauncher` بسازید.
2. تمام محتویات پروژه را در شاخه `main` قرار دهید.
3. به بخش **Actions** بروید.
4. گردش‌کار **Build SahlBit APK** را اجرا کنید.
5. پس از موفقیت، Artifact با نام `sahlbit-debug-apk` را دریافت کنید.

## ساخت محلی
با JDK 17 و Gradle 8.11.1:

```bash
gradle assembleDebug
```

خروجی: `app/build/outputs/apk/debug/app-debug.apk`

## وضعیت نسخه
- لانچر قابل انتخاب در Android Home Apps
- ساعت آنالوگ و درصد باتری
- صفحات نمونه با جابه‌جایی افقی
- تنظیمات انتخاب سهل‌بیت یا رفتن به تنظیمات انتخاب One UI Home

## مواردی که هنوز پیاده‌سازی نشده‌اند
- PIN واقعی پنل مدیریت
- مدیریت و ذخیره مخاطبان واقعی
- تماس ورودی/خروجی اختصاصی
- Device Owner و محدودیت برنامه‌ها
- پشتیبان‌گیری و تنظیمات پیشرفته
- بازیابی خودکارِ تغییر لانچر پیش‌فرض (اندروید این تغییر را بدون تأیید کاربر مجاز نمی‌کند)


## Version 0.2.1 — contacts and app selection foundation

- Adds the Android phone-contact picker (no broad READ_CONTACTS permission).
- Stores selected contact name/number and copies an available contact photo into app-private storage.
- Adds an installed-launcher-app picker using a narrow package visibility query.
- Adds local launcher entries and page assignment.
- Home cards now render selected contacts/apps; contact taps show a confirmation dialog and open the system dialer with ACTION_DIAL (no direct-call permission).
- Adds configurable grid column count and horizontal alignment.

Limitations: this milestone does not implement InCallService/custom call UI, true per-card drag-and-drop placement, full page CRUD, Device Owner/kiosk restrictions, or reliable app blocking.
