# Hướng dẫn một lần: Alpha Mini Auto Wi‑Fi Portal

## Mục tiêu

Sau khi cài một lần, Alpha Mini sẽ tự phát Wi‑Fi cấu hình sau mỗi lần khởi động.
Điện thoại hoặc laptop có thể kết nối trực tiếp vào robot để cấu hình Wi‑Fi mới,
không cần mở ứng dụng UBTECH và không cần USB trong các lần sử dụng sau.

## Thông tin kết nối P2P

| Mục | Giá trị |
|---|---|
| Wi‑Fi setup | `DIRECT-7Z-Android_e0f1` |
| Passphrase | `dMJ6U50p` |
| Portal trực tiếp | `http://192.168.49.1:8787/` |
| Portal qua USB/ADB | `http://127.0.0.1:18787/` |

Passphrase phân biệt chữ hoa/chữ thường. Đã kiểm tra 6 lần reboot: SSID và
passphrase giữ nguyên.

## Cài đặt lần đầu bằng USB

Mở PowerShell trên máy tính, cắm Alpha Mini qua USB và chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File E:\AlphaCode\alpha-mini-wifi-probe\tools\start-alpha-wifi-setup.ps1 -InstallOnly
```

Lệnh này sẽ:

1. Cài APK Auto Portal độc lập.
2. Cấp quyền Wi‑Fi cho probe.
3. Mở probe một lần để Android ghi nhận app.
4. Không thay đổi APK Alpha Code gốc hoặc firmware.

Sau đó khởi động lại robot:

```powershell
adb -s 010058YUD18082406465 reboot
```

Chờ khoảng 30–120 giây để robot khởi động và phát Wi‑Fi setup.

## Cấu hình bằng điện thoại hoặc laptop, không dùng USB

1. Mở danh sách Wi‑Fi.
2. Chọn `DIRECT-7Z-Android_e0f1`.
3. Nhập passphrase `dMJ6U50p`.
4. Bật tùy chọn **Tự động kết nối** hoặc **Remember network**.
5. Nếu thiết bị báo Wi‑Fi không có Internet, vẫn giữ kết nối; đây là mạng nội bộ
   của robot.
6. Mở trình duyệt:

```text
http://192.168.49.1:8787/
```

Không chọn Wi‑Fi station hiện tại của robot, ví dụ `BETEA`; đó không phải mạng
setup.

## Đổi sang Wi‑Fi khác ở địa điểm mới

1. Kết nối điện thoại/laptop vào `DIRECT-7Z-Android_e0f1`.
2. Mở `http://192.168.49.1:8787/`.
3. Bấm **Quét Wi‑Fi** hoặc nhập SSID thủ công.
4. Nhập mật khẩu Wi‑Fi đích.
5. Bấm **Kết nối Wi‑Fi**.
6. Chờ robot nhận IP mới. P2P có thể mất trong chốc lát.
7. Nếu điện thoại chưa tự kết nối lại, chọn lại `DIRECT-7Z-Android_e0f1`.
8. Mở lại portal để cấu hình mạng tiếp theo nếu cần.

Sau khi robot nhận IP hoặc kết nối timeout, Auto Portal tự tạo lại P2P. Vì vậy
khi chuyển sang địa điểm khác, không cần cài lại APK hoặc dùng USB.

## Khi không mở được portal

### Điện thoại không thấy `DIRECT-7Z-Android_e0f1`

- Đứng gần robot.
- Tắt/bật Wi‑Fi trên điện thoại.
- Khởi động lại Alpha Mini và chờ tối đa 120 giây.
- Kiểm tra điện thoại không đang tự chuyển về Wi‑Fi có Internet khác.

### Đã kết nối DIRECT nhưng URL không mở

- Kiểm tra điện thoại đang kết nối đúng `DIRECT-7Z-Android_e0f1`, không phải
  `BETEA` hoặc Wi‑Fi nhà.
- Tắt VPN/proxy trên điện thoại hoặc laptop.
- Mở đúng `http://192.168.49.1:8787/`.
- Chờ 5–10 giây sau khi P2P vừa xuất hiện rồi thử lại.

### Portal USB trên máy tính

Chỉ dùng khi cần chẩn đoán hoặc khi điện thoại không kết nối được:

```powershell
adb -s 010058YUD18082406465 forward tcp:18787 tcp:8787
```

Sau đó mở:

```text
http://127.0.0.1:18787/
```

Mỗi lần reboot, ADB port-forward bị xóa; cần chạy lại lệnh forward nếu dùng
portal USB.

## Lưu ý

- Không bấm **Dừng / khôi phục** nếu muốn giữ Wi‑Fi cuối cùng.
- P2P sẽ tạm mất trong lúc robot chuyển sang Wi‑Fi station; đây là hành vi bình
  thường của radio Wi‑Fi.
- APK probe độc lập, không thay thế `com.ubtrobot.mini.sdkdemo`.
- Nếu đổi passphrase sau khi firmware reset P2P, cần đọc lại passphrase từ portal
  qua USB một lần.
