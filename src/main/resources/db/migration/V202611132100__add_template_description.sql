-- 문서 양식 추가/수정 화면을 기존 eform 화면과 같게 맞추며 설명 칸을 더한다(2026-11-13). 선택 입력이다.
ALTER TABLE templates ADD COLUMN description VARCHAR(1000) NULL AFTER title;
