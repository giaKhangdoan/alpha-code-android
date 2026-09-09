# 📲 HƯỚNG DẪN NẠP FILE APK CÓ SẴN VÀO ROBOT ALPHA MINI

Tài liệu này dành cho trường hợp **bạn đã có sẵn file APK** (được gửi từ người khác, tải từ WebApp `/admin/apks`, hoặc tải từ bản release) và muốn cài đặt trực tiếp vào robot **Alpha Mini** mà **không cần cài đặt Android Studio hay biên dịch mã nguồn**.

---

## 📋 MỤC LỤC
1. [Yêu cầu & Công cụ cần chuẩn bị](#1-yêu-cầu--công-cụ-cần-chuẩn-bị)
2. [Bước 1: Kết nối máy tính với Robot Alpha Mini](#bước-1-kết-nối-máy-tính-với-robot-alpha-mini)
3. [Bước 2: Nạp (Cài đặt) file APK](#bước-2-nạp-cài-đặt-file-apk)
4. [Bước 3: Khởi chạy ứng dụng trên Robot](#bước-3-khởi-chạy-ứng-dụng-trên-robot)
5. [Bước 4: Kiểm tra Log hoạt động & Kết nối Backend](#bước-4-kiểm-tra-log-hoạt-động--kết-nối-backend)
6. [Xử lý các lỗi thường gặp (Troubleshooting)](#xử-lý-các-lỗi-thường-gặp-troubleshooting)

---

## 1. Yêu cầu & Công cụ cần chuẩn bị

Bạn chỉ cần **duy nhất công cụ ADB (Android Debug Bridge)** của Google trên máy tính.

### Cài đặt nhanh ADB (Nếu máy chưa có):
- **Link tải trực tiếp từ Google:** [platform-tools-latest-windows.zip](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)
- **Hoặc chạy 1 dòng lệnh PowerShell để tự động tải & giải nén vào `C:\platform-tools`:**
  ```powershell
  $url = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"; $zip = "$env:TEMP\platform-tools.zip"; Invoke-WebRequest -Uri $url -OutFile $zip; Expand-Archive -Path $zip -DestinationPath "C:\" -Force; [Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\platform-tools", "User")
  ```
- **Kiểm tra công cụ:** Mở Terminal (PowerShell / CMD) và gõ:
  ```powershell
  adb version
  ```
  *(Nếu hiện ra thông tin phiên bản `Android Debug Bridge version 1.0.xx` là đã sẵn sàng).*

---

## 2. Bước 1: Kết nối máy tính với Robot Alpha Mini

Alpha Mini hỗ trợ nạp phần mềm qua mạng **Wi-Fi** (rất tiện lợi, không cần tháo lắp hay cắm dây cáp).

1. **Bật nguồn robot** và đảm bảo robot đã được kết nối vào **cùng mạng Wi-Fi** với máy tính của bạn.
2. **Lấy địa chỉ IP của Robot:**
   - Mở ứng dụng Alpha Mini chính thức trên điện thoại -> vào phần *Cài đặt Robot* -> *Thông tin mạng/Wi-Fi* -> ghi lại địa chỉ IP.
   - *(Hoặc đăng nhập vào trang quản trị Modem/Router Wi-Fi để xem IP của thiết bị UBTECH / Android).*
   - *Ví dụ IP lấy được là:* `192.168.1.150`.
3. **Mở Terminal (PowerShell hoặc Command Prompt) trên máy tính và chạy:**
   ```powershell
   adb connect 192.168.1.150:5555
   ```
   *(Thay `192.168.1.150` bằng IP thực tế của robot).*
4. **Kiểm tra kết nối:**
   ```powershell
   adb devices
   ```
   👉 Khi màn hình xuất hiện:
   ```text
   List of devices attached
   192.168.1.150:5555    device
   ```
   nghĩa là máy tính đã kết nối thành công với robot.

---

## 3. Bước 2: Nạp (Cài đặt) file APK

Giả sử file APK của bạn có tên là `alpha-code.apk` (hoặc `app-debug.apk`) nằm tại một thư mục trên máy tính.

Chạy lệnh sau để cài đặt:

```powershell
adb install -r -d "C:\duong-dan-den-file\app-debug.apk"
```

> 💡 **Giải thích các tham số:**
> - `-r` (*Reinstall*): Cài đè lên ứng dụng cũ mà vẫn giữ nguyên dữ liệu cấu hình.
> - `-d` (*Downgrade*): Cho phép cài đặt ngay cả khi phiên bản của file APK thấp hơn bản đang có trên robot.
> - `-g` (*Grant permissions - tùy chọn*): Tự động cấp tất cả quyền Runtime (Camera, Micro, Bộ nhớ) cho ứng dụng:
>   ```powershell
>   adb install -r -d -g "C:\duong-dan-den-file\app-debug.apk"
>   ```

⏳ Quá trình truyền file và cài đặt mất khoảng **10 – 30 giây** tùy dung lượng APK. Khi thấy dòng chữ:
```text
Success
```
nghĩa là file APK đã được nạp thành công vào robot!

---

## 4. Bước 3: Khởi chạy ứng dụng trên Robot

Sau khi cài đặt xong, bạn có thể kích hoạt ứng dụng chạy ngay lập tức từ máy tính bằng lệnh:

```powershell
adb shell am start -n com.ubtrobot.mini.sdkdemo/.MainActivity
```

Robot sẽ phát âm thanh khởi động ứng dụng hoặc màn hình mắt robot sẽ hiển thị giao diện của ứng dụng AlphaCode.

---

## 5. Bước 4: Kiểm tra Log hoạt động & Kết nối Backend

Để đảm bảo ứng dụng sau khi nạp đã kết nối thành công với Backend (WebSocket & API):

Chạy lệnh xem log theo thời gian thực:
```powershell
adb logcat -s DemoApp MiniSdk WebSocket OkHttp
```

- **Khi kết nối thành công:** Bạn sẽ thấy các dòng log báo kết nối WebSocket mở (`WebSocket onOpen`) và robot gửi bản tin định danh Serial Number lên server `wss://ai.alpa.vn/websocket/ws`.
- **Nhấn `Ctrl + C`** để dừng xem log.

---

## 6. Xử lý các lỗi thường gặp (Troubleshooting)

### 🔴 Lỗi 1: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` hoặc `signatures do not match`
- **Nguyên nhân:** File APK mới được ký bằng một Key/Chứng chỉ khác với phiên bản ứng dụng cũ đang cài trên robot.
- **Cách xử lý:** Gỡ cài đặt bản cũ trước, sau đó chạy lại lệnh cài đặt:
  ```powershell
  # Bước 1: Gỡ phiên bản cũ
  adb uninstall com.ubtrobot.mini.sdkdemo

  # Bước 2: Cài đặt lại file APK mới
  adb install "C:\duong-dan-den-file\app-debug.apk"
  ```

---

### 🔴 Lỗi 2: `cannot connect to 192.168.x.x:5555: No connection could be made...`
- **Nguyên nhân:** Robot bị đổi IP, robot chưa bật Wi-Fi, hoặc máy tính và robot đang không ở chung một mạng Wi-Fi.
- **Cách xử lý:**
  1. Kiểm tra lại IP của robot xem có bị thay đổi không.
  2. Ping thử tới robot từ máy tính: `ping <IP_ROBOT>`.
  3. Khởi động lại dịch vụ ADB trên máy tính:
     ```powershell
     adb kill-server
     adb start-server
     adb connect <IP_ROBOT>:5555
     ```

---

### 🔴 Lỗi 3: Thiết bị báo trạng thái `offline` hoặc `unauthorized`
- Chạy lệnh ngắt kết nối rồi nối lại:
  ```powershell
  adb disconnect <IP_ROBOT>:5555
  adb connect <IP_ROBOT>:5555
  ```

---

### 🔴 Lỗi 4: Kiểm tra robot có truy cập được Internet/Domain Backend không
Bạn có thể ra lệnh cho robot tự ping kiểm tra domain hệ thống:
```powershell
adb shell ping -c 4 alpa.vn
```
*Nếu nhận được các gói tin phản hồi (`bytes from ...`), robot đã sẵn sàng giao tiếp với Cloud Backend.*
