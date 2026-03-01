package com.attendance.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class QRCodeGenerator {

    public static String generateQRBase64(String data, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (WriterException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String generateQRHTML(String serverURL, String token) {
        int refreshMs = ConfigLoader.getInt("qr.refresh.interval");
        int refreshSec = Math.max(1, refreshMs / 1000);

        String qrData = serverURL + "/mark?token=" + token;
        String base64Image = generateQRBase64(qrData, 500, 500);

        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta http-equiv='refresh' content='" + refreshSec + "'>"
                + "<title>QR Attendance</title>"
                + "<style>"
                + "body{display:flex;justify-content:center;align-items:center;height:100vh;background:linear-gradient(135deg,#0f172a,#1e3a8a);margin:0;flex-direction:column;font-family:Arial;color:#fff;}"
                + "img{border:16px solid #fff;border-radius:20px;box-shadow:0 20px 60px rgba(0,0,0,0.35);background:#fff;}"
                + "h1{font-size:40px;margin-bottom:24px;}"
                + ".timer{font-size:20px;margin-top:18px;background:rgba(0,0,0,0.35);padding:12px 20px;border-radius:10px;}"
                + "</style>"
                + "<script>"
                + "let countdown=" + refreshSec + ";"
                + "setInterval(function(){"
                + "document.getElementById('timer').innerText='Refreshing in '+countdown+'s';"
                + "countdown--;if(countdown<0)countdown=" + refreshSec + ";"
                + "},1000);"
                + "</script>"
                + "</head><body>"
                + "<h1>Scan To Mark Attendance</h1>"
                + "<img alt='Attendance QR' src='data:image/png;base64," + base64Image + "'/>"
                + "<div class='timer' id='timer'>Refreshing in " + refreshSec + "s</div>"
                + "</body></html>";
    }
    
    // New method with Start/Stop controls
    public static String generateQRHTMLWithControls(String serverURL, String token, boolean isEnabled) {
        int refreshMs = ConfigLoader.getInt("qr.refresh.interval");
        int refreshSec = Math.max(1, refreshMs / 1000);

        String qrData = serverURL + "/mark?token=" + token;
        String base64Image = generateQRBase64(qrData, 500, 500);

        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta http-equiv='refresh' content='" + refreshSec + "'>"
                + "<title>QR Attendance</title>"
                + "<style>"
                + "body{display:flex;justify-content:center;align-items:center;height:100vh;background:linear-gradient(135deg,#0f172a,#1e3a8a);margin:0;flex-direction:column;font-family:Arial;color:#fff;}"
                + ".container{display:flex;flex-direction:column;align-items:center;}"
                + "img{border:16px solid #fff;border-radius:20px;box-shadow:0 20px 60px rgba(0,0,0,0.35);background:#fff;}"
                + "h1{font-size:36px;margin-bottom:16px;text-align:center;}"
                + ".status{font-size:14px;color:#22c55e;margin-bottom:16px;background:rgba(34,197,94,0.15);padding:8px 16px;border-radius:20px;border:1px solid #22c55e;}"
                + ".timer{font-size:18px;margin-top:12px;background:rgba(0,0,0,0.35);padding:10px 18px;border-radius:10px;}"
                + ".stop-btn{margin-top:20px;padding:14px 35px;background:#dc2626;color:#fff;border:none;border-radius:10px;font-size:18px;font-weight:bold;cursor:pointer;box-shadow:0 8px 25px rgba(220,38,38,0.4);transition:all 0.3s;}"
                + ".stop-btn:hover{background:#b91c1c;transform:translateY(-2px);}"
                + ".note{font-size:14px;margin-top:15px;color:#94a3b8;text-align:center;max-width:500px;}"
                + "</style>"
                + "<script>"
                + "let countdown=" + refreshSec + ";"
                + "setInterval(function(){"
                + "document.getElementById('timer').innerText='Auto-refresh in '+countdown+'s';"
                + "countdown--;if(countdown<0)countdown=" + refreshSec + ";"
                + "},1000);"
                + "function stopQR(){"
                + "  fetch('/qr/stop').then(()=>{window.location.reload();});"
                + "}"
                + "</script>"
                + "</head><body>"
                + "<div class='container'>"
                + "<h1>Scan To Mark Attendance</h1>"
                + "<div class='status'>QR ACTIVE - Waiting for scan...</div>"
                + "<img alt='Attendance QR' src='data:image/png;base64," + base64Image + "'/>"
                + "<div class='timer' id='timer'>Auto-refresh in " + refreshSec + "s</div>"
                + "<button class='stop-btn' onclick='stopQR()'>STOP QR SCANNER</button>"
                + "<div class='note>QR becomes INVALID after successful attendance OR after " + refreshSec + " seconds</div>"
                + "</div>"
                + "</body></html>";
    }
}
