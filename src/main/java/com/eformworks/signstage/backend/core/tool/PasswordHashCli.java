package com.eformworks.signstage.backend.core.tool;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * DB에 계정을 직접 시딩할 때(예: 최초 플랫폼 관리자 계정) 쓸 BCrypt 해시를 생성하는
 * 1회성 CLI 도구다. 애플리케이션을 기동하지 않고 이 클래스만 단독으로 실행한다.
 *
 * <p>사용법:
 * <pre>
 *   ./gradlew hashPassword --console=plain          # 표준 입력으로 비밀번호 한 줄 입력
 *   ./gradlew hashPassword --console=plain --args="평문비밀번호"   # 인자로 바로 전달
 * </pre>
 *
 * <p>입력한 비밀번호는 화면에 그대로 표시된다(마스킹 없음). 다른 사람이 볼 수 없는
 * 터미널에서 실행하고, 출력된 해시만 시딩 스크립트({@code scripts/seed-platform-admin.sql})에
 * 붙여넣는다. 평문 비밀번호는 어디에도 저장하지 않는다.
 */
public final class PasswordHashCli {

    private PasswordHashCli() {
    }

    public static void main(String[] args) throws IOException {
        String password = args.length > 0 ? args[0] : readPasswordFromStdin();

        if (password == null || password.isBlank()) {
            System.err.println("비밀번호가 비어 있습니다.");
            System.exit(1);
            return;
        }

        String hash = new BCryptPasswordEncoder().encode(password);
        System.out.println();
        System.out.println("BCrypt Hash:");
        System.out.println(hash);
    }

    private static String readPasswordFromStdin() throws IOException {
        System.out.print("비밀번호 입력: ");
        System.out.flush();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            return reader.readLine();
        }
    }
}
