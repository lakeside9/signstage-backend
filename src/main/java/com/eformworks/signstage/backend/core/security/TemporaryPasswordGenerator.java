package com.eformworks.signstage.backend.core.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 관리자가 회원 계정을 대신 만들 때 쓸 임시 비밀번호를 생성한다. 평문은 그 자리에서
 * 딱 한 번 응답으로 반환될 뿐 어디에도 저장하지 않는다 — 저장은 항상 BCrypt 해시로만
 * 이뤄지고, 발급받은 사람은 {@code is_password_reset_required=TRUE}에 따라 첫 로그인 시
 * 반드시 직접 비밀번호를 바꾼다(signstage-docs business/user-organization-design.md 5.3절).
 */
@Component
public class TemporaryPasswordGenerator {

    // 사람이 눈으로 옮겨 적어도 헷갈리지 않도록 혼동되는 문자(I/O/l/0/1)는 뺀다.
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    /** 대문자/소문자/숫자/기호를 최소 1개씩 포함하는 {@value #LENGTH}자리 임시 비밀번호를 만든다. */
    public String generate() {
        char[] chars = new char[LENGTH];
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SYMBOLS);
        for (int i = 4; i < LENGTH; i++) {
            chars[i] = pick(ALL);
        }
        shuffle(chars);
        return new String(chars);
    }

    private char pick(String pool) {
        return pool.charAt(random.nextInt(pool.length()));
    }

    private void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
    }
}
