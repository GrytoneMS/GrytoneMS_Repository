package com.grytonems.backend.global.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 메일 서버 설정(SPRING_MAIL_HOST 등)이 있으면 실제로 보내고,
 * 없으면 서버 로그에 내용을 찍습니다 (로컬 개발·시연용).
 */
@Slf4j
@Component
public class SmtpOrLogEmailSender implements EmailSender {

    private final JavaMailSender mailSender; // 메일 설정이 없으면 null
    private final String from;

    public SmtpOrLogEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.from = StringUtils.hasText(from) ? from : username;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (mailSender == null) {
            log.warn("[메일 서버 미설정: 로그로 대신 출력] to={} subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(from)) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("메일 발송 실패 to={}", to, e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
