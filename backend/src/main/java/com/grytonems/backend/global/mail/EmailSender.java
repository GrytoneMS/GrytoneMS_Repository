package com.grytonems.backend.global.mail;

/** 메일 발송. 실패하면 BusinessException(EMAIL_SEND_FAILED)을 던집니다. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
