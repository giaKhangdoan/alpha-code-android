# Alpha Mini Wi-Fi Probe

Hướng dẫn sử dụng một lần, gồm thông tin P2P và cách cấu hình không USB:
[ALPHA_MINI_AUTO_WIFI_PORTAL_GUIDE.md](../ALPHA_MINI_AUTO_WIFI_PORTAL_GUIDE.md)

APK chẩn đoán độc lập cho Alpha Mini Android 7/API 24. Project này không sửa
`alpha-code-android`, không đăng ký `BOOT_COMPLETED`, không yêu cầu firmware/system
image và không cài vào `/system/priv-app`.

## Build

```powershell
E:\AlphaCode\alpha-code-android\gradlew.bat -p E:\AlphaCode\alpha-mini-wifi-probe assembleDebug
```

APK đầu ra:

```text
E:\AlphaCode\alpha-mini-wifi-probe\app\build\outputs\apk\debug\app-debug.apk
```

## Auto Portal sau khi robot khởi động

Bản APK này đăng ký `BOOT_COMPLETED`. Cài một lần bằng USB:

```powershell
E:\AlphaCode\alpha-mini-wifi-probe\tools\start-alpha-wifi-setup.ps1 -InstallOnly
```

Script cài APK riêng, cấp quyền Wi-Fi cho package probe và không thay đổi APK
Alpha Code. Sau lần reboot tiếp theo, probe tự mở và tự tạo P2P/portal. Trên
Alpha Mini này, SSID và passphrase P2P đã được kiểm tra giữ nguyên qua reboot;
chỉ cần lưu mạng trên điện thoại/laptop với tùy chọn tự kết nối. APK thường vẫn
không thể tự đặt passphrase P2P hoặc ép SoftAP mở có SSID/mật khẩu tùy ý.

## Mở portal bằng một lệnh

Khi đã cắm Alpha Mini qua USB và bật ADB, chạy:

```powershell
E:\AlphaCode\alpha-mini-wifi-probe\tools\start-alpha-wifi-setup.ps1
```

Script tự tìm đúng thiết bị có model `Alpha Mini`, cài/cập nhật probe, mở Activity,
tạo port-forward và mở trình duyệt. Lần khởi động nguội có thể cần tối đa 120 giây
để ROM hoàn tất quét Wi-Fi/P2P. Script cũng tự thử khởi động lại ADB một lần nếu
ADB daemon trên Windows bị kẹt. Muốn build APK mới trước khi chạy:

```powershell
E:\AlphaCode\alpha-mini-wifi-probe\tools\start-alpha-wifi-setup.ps1 -Build
```

Nếu có nhiều Alpha Mini đang cắm, chỉ rõ serial:

```powershell
E:\AlphaCode\alpha-mini-wifi-probe\tools\start-alpha-wifi-setup.ps1 -Serial 010058YUD18082406465
```

## Cài và chạy trên Alpha Mini không có màn hình

```powershell
adb install -r E:\AlphaCode\alpha-mini-wifi-probe\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.alphacode.wifiprobe/.MainActivity
adb logcat -s AlphaMiniWifiProbe
```

APK tự tạo P2P sau khi được mở. Vì Alpha Mini không có màn hình, lần đầu lấy thông tin
setup qua ADB port-forward:

```powershell
adb -s 010058YUD18082406465 shell am start -n com.alphacode.wifiprobe/.MainActivity
adb -s 010058YUD18082406465 forward tcp:18787 tcp:8787
Start-Process http://127.0.0.1:18787/
```

Portal sẽ hiển thị SSID/passphrase của P2P. Sau đó điện thoại/laptop có thể kết nối
vào P2P và mở `http://192.168.49.1:8787/` trực tiếp. Các bước tiếp theo:

1. Chỉ mở APK này khi cần cấu hình ở địa điểm mới.
2. Cấp quyền vị trí khi ROM yêu cầu.
3. Dùng portal để bấm `Quét Wi-Fi` và chờ tối đa 30 giây. ROM Alpha Mini có thể
   chỉ trả các profile đã lưu, được đánh dấu `SAVED`; mạng mới vẫn nhập được bằng
   ô `SSID ẩn / nhập thủ công`.
4. Nhập mật khẩu và bấm `Kết nối Wi-Fi`; portal sẽ trả `202`, robot rời P2P
   để chuyển sang station nhưng HTTP server vẫn giữ qua ADB port-forward, nên có
   thể nhập SSID/mật khẩu tiếp theo mà không chạy lại script. Sau khi station
   nhận IP hoặc timeout, probe tự tạo lại P2P để điện thoại kết nối lại ở địa điểm
   khác; lúc P2P chuyển trạng thái, điện thoại có thể cần chọn lại mạng setup.
5. Nhập serial/model ID nếu cần và bấm `Kiểm tra backend`.
6. Bấm `Dừng / khôi phục` nếu cần hủy trước khi chuyển mạng.

Để kiểm tra endpoint thô, mở `http://192.168.49.1:8787/health`; kết quả đúng là HTTP 200 JSON.

Khi test hoàn tất, gỡ port-forward:

```powershell
adb -s 010058YUD18082406465 forward --remove tcp:18787
```

Mật khẩu Wi-Fi nhà không được ghi vào logcat, URL, QR hoặc file log. Chỉ bấm `Kết nối Wi-Fi`
khi đã có đường khôi phục.
Mạng test do người vận hành nhập, không hard-code.

## An toàn khi thử

- Kiểm tra đúng thiết bị trước khi cài:

  ```powershell
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.sdk
  ```

  Kết quả mục tiêu là `Alpha Mini` và `24`.
- Không chạy đồng thời với thao tác cần station Wi-Fi ổn định; P2P có thể làm một
  radio tạm rời mạng hiện tại.
- Không dùng probe này để flash firmware hoặc thay APK `com.ubtrobot.mini.sdkdemo`.
- Nếu ROM từ chối `createGroup()` hoặc `WifiManager` legacy, giữ nguyên APK gốc và
  dùng lại UBTECH/system route cho đến khi có quyết định mới.
