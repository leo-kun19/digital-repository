package com.example.thesisrepo.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${app.email.from:melindalouisagunawan@gmail.com}")
  private String fromAddress;

  @Value("${app.email.app-name:FYP Digital Repository}")
  private String appName;

  @Async
  public void sendOtpEmail(String toEmail, String otpCode, int expiryMinutes) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromAddress);
      helper.setTo(toEmail);
      helper.setSubject(appName + " - Email Verification Code");
      helper.setText(buildOtpHtml(otpCode, expiryMinutes, toEmail), true);

      mailSender.send(message);
      log.info("OTP email sent successfully to {}", toEmail);
    } catch (MessagingException e) {
      log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
    }
  }

  private String buildOtpHtml(String otpCode, int expiryMinutes, String email) {
    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
      </head>
      <body style="margin:0;padding:0;font-family:'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;background-color:#f4f6f9;">
        <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
          <tr>
            <td align="center">
              <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,0.08);overflow:hidden;">
                <!-- Header -->
                <tr>
                  <td style="background:linear-gradient(135deg,#1a3a5c 0%%,#2d5f8a 100%%);padding:32px 40px;text-align:center;">
                    <div style="width:48px;height:48px;background:rgba(255,255,255,0.2);border-radius:50%%;display:inline-flex;align-items:center;justify-content:center;margin-bottom:12px;">
                      <span style="color:#ffffff;font-weight:700;font-size:16px;">SU</span>
                    </div>
                    <h1 style="margin:0;color:#ffffff;font-size:20px;font-weight:600;">%s</h1>
                    <p style="margin:6px 0 0;color:rgba(255,255,255,0.8);font-size:13px;">Email Verification</p>
                  </td>
                </tr>
                <!-- Body -->
                <tr>
                  <td style="padding:36px 40px;">
                    <p style="margin:0 0 8px;color:#333;font-size:15px;">Hello,</p>
                    <p style="margin:0 0 24px;color:#555;font-size:14px;line-height:1.6;">
                      Please use the verification code below to confirm your email address
                      <strong style="color:#1a3a5c;">%s</strong>.
                    </p>

                    <!-- OTP Box -->
                    <div style="text-align:center;margin:0 0 24px;">
                      <div style="display:inline-block;background:#f0f5fa;border:2px dashed #2d5f8a;border-radius:10px;padding:18px 40px;">
                        <span style="font-size:36px;font-weight:700;letter-spacing:8px;color:#1a3a5c;">%s</span>
                      </div>
                    </div>

                    <p style="margin:0 0 6px;color:#888;font-size:13px;text-align:center;">
                      ⏱ This code expires in <strong>%d minutes</strong>.
                    </p>
                    <p style="margin:0;color:#999;font-size:12px;text-align:center;">
                      If you did not request this code, you can safely ignore this email.
                    </p>
                  </td>
                </tr>
                <!-- Footer -->
                <tr>
                  <td style="background:#f8f9fb;padding:20px 40px;text-align:center;border-top:1px solid #eee;">
                    <p style="margin:0;color:#aaa;font-size:11px;">
                      &copy; Sampoerna University &mdash; %s
                    </p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
        </table>
      </body>
      </html>
      """.formatted(appName, email, otpCode, expiryMinutes, appName);
  }
}
