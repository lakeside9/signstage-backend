package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 서명자(Signer). {@code Ceremony} 직속이라 같은 행사의 TEST/MAIN 하위 행사가 명단을
 * 공유한다(signstage-docs business/ceremony-feature-migration-review.md 4.3절).
 * 조직/행사 접근 검사와 유효 한도 계산은 {@link CeremonyService}의 package-private
 * 헬퍼를 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignerService {

    private final SignerRepository signerRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final StrokeDataRepository strokeDataRepository;
    private final CeremonyEventLogRepository ceremonyEventLogRepository;
    private final CeremonyTemplateRepository ceremonyTemplateRepository;
    private final CeremonyService ceremonyService;

    @Transactional
    public SignerDto.Response.SignerSummary createSigner(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            SignerDto.Request.CreateSigner request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);
        ceremonyService.checkCeremonyPlanConfirmed(ceremony);

        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.SIGNERS);
        long currentCount = signerRepository.countByCeremonyId(ceremonyId);
        if (currentCount >= effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_SIGNER_LIMIT_EXCEEDED);
        }

        Signer signer = Signer.builder()
                .ceremony(ceremony)
                .name(request.getName())
                .position(request.getPosition())
                .affiliation(request.getAffiliation())
                .roleCode(request.getRoleCode())
                .accessKey(generateUniqueAccessKey())
                .build();
        signerRepository.save(signer);

        return toSummary(signer);
    }

    /**
     * 서명자 일괄 업로드용 빈 엑셀 양식(.xlsx)을 만든다. 헤더만 있는 파일이라 행사별로 내용이
     * 달라지지 않지만, {@link #findSigners}와 같은 읽기 접근 검사는 그대로 적용한다.
     */
    public byte[] generateExcelTemplate(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("서명자");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            String[] columns = {"이름", "소속", "직위"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApplicationException(CommonErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * 엑셀(.xlsx)로 서명자를 한 번에 등록한다. 1행은 헤더로 보고 건너뛰며, 열 순서는
     * {@link #generateExcelTemplate}과 같이 이름/소속/직위다. 이름이 빈 행은 등록하지 않고
     * {@code skippedRows}로 알려준다 — 행 하나가 잘못됐다고 나머지 유효한 행까지 막지 않는다.
     * 다만 유효한 행 수가 플랜의 서명자 한도를 넘으면(단일 등록과 같은 4.5절 하드 블록 원칙)
     * 아무것도 등록하지 않는다 — 몇 명만 등록되고 나머지가 조용히 빠지는 상황을 막기 위해서다.
     */
    @Transactional
    public SignerDto.Response.ExcelUploadResult uploadSignersExcel(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            MultipartFile file
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);
        ceremonyService.checkCeremonyPlanConfirmed(ceremony);
        checkExcelExtension(file.getOriginalFilename());

        List<Signer> toCreate = new ArrayList<>();
        List<SignerDto.Response.SkippedRow> skippedRows = new ArrayList<>();
        for (ExcelSignerRow row : parseExcelRows(file)) {
            if (row.name().isBlank() && row.affiliation().isBlank() && row.position().isBlank()) {
                continue; // 완전히 빈 행(엑셀 끝의 여백 등)은 조용히 건너뛴다.
            }
            if (row.name().isBlank()) {
                skippedRows.add(new SignerDto.Response.SkippedRow(row.rowNumber(), "이름이 비어있습니다."));
                continue;
            }
            toCreate.add(Signer.builder()
                    .ceremony(ceremony)
                    .name(row.name())
                    .position(row.position().isBlank() ? null : row.position())
                    .affiliation(row.affiliation().isBlank() ? null : row.affiliation())
                    .accessKey(generateUniqueAccessKey())
                    .build());
        }

        if (toCreate.isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_EXCEL_NO_VALID_ROWS);
        }

        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.SIGNERS);
        long currentCount = signerRepository.countByCeremonyId(ceremonyId);
        if (currentCount + toCreate.size() > effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_SIGNER_LIMIT_EXCEEDED);
        }

        signerRepository.saveAll(toCreate);

        List<SignerDto.Response.SignerSummary> createdSigners = toCreate.stream().map(this::toSummary).toList();
        return new SignerDto.Response.ExcelUploadResult(createdSigners, skippedRows);
    }

    private void checkExcelExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_EXCEL_INVALID_FORMAT);
        }
    }

    /** 엑셀 한 행(이름/소속/직위)과 그 실제 엑셀 행 번호(헤더=1행, 데이터는 2행부터). */
    private record ExcelSignerRow(int rowNumber, String name, String affiliation, String position) {
    }

    /**
     * 시트 첫 장의 2행부터 끝까지 읽는다. 손상됐거나 형식이 다른 파일은 POI가 다양한 예외를
     * 던지므로 전부 {@code SIGNER_EXCEL_PARSE_FAILED}로 묶는다 — 업로드하는 사람 입장에서는
     * 어떤 예외든 "이 파일을 못 읽었다"는 뜻이 같기 때문이다.
     */
    private List<ExcelSignerRow> parseExcelRows(MultipartFile file) {
        DataFormatter formatter = new DataFormatter();
        List<ExcelSignerRow> rows = new ArrayList<>();
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                rows.add(new ExcelSignerRow(
                        r + 1,
                        readCell(formatter, row, 0),
                        readCell(formatter, row, 1),
                        readCell(formatter, row, 2)
                ));
            }
        } catch (Exception e) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_EXCEL_PARSE_FAILED, e);
        }
        return rows;
    }

    private String readCell(DataFormatter formatter, Row row, int cellIndex) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(cellIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    public List<SignerDto.Response.SignerSummary> findSigners(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return signerRepository.findAllByCeremonyId(ceremonyId).stream().map(this::toSummary).toList();
    }

    public SignerDto.Response.SignerSummary retrieveSigner(
            Long organizationId,
            Long ceremonyId,
            Long signerId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return toSummary(findSignerInCeremonyOrThrow(ceremonyId, signerId));
    }

    /** 이름/직책/소속/역할코드만 바꾼다. accessKey는 여기서 바꾸지 않는다. */
    @Transactional
    public SignerDto.Response.SignerSummary updateSigner(
            Long organizationId,
            Long ceremonyId,
            Long signerId,
            Long currentUserId,
            SignerDto.Request.UpdateSigner request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Signer signer = findSignerInCeremonyOrThrow(ceremonyId, signerId);
        if (isSignerLockedByStartedEvent(signerId)) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_LOCKED_BY_EVENT);
        }
        signer.updateInfo(request.getName(), request.getPosition(), request.getAffiliation(), request.getRoleCode());
        return toSummary(signer);
    }

    /**
     * 이 서명자가 배정된 서명란이 속한 문서 양식이, 시작(STARTED)됐거나 종료(FINISHED)된
     * 하위 행사에 매핑돼 있으면 잠긴 것으로 본다 — 현장에서 이미 쓰이고 있는 서명자 정보를
     * 바꾸면 결과물(계약서/감사 기록)과 화면에 보이던 이름이 어긋나기 때문이다.
     */
    private boolean isSignerLockedByStartedEvent(Long signerId) {
        List<TemplateField> fields = templateFieldRepository.findAllBySignerId(signerId);
        if (fields.isEmpty()) {
            return false;
        }
        return fields.stream()
                .map(field -> field.getTemplate().getId())
                .distinct()
                .flatMap(templateId -> ceremonyTemplateRepository.findAllByTemplateId(templateId).stream())
                .map(ceremonyTemplate -> ceremonyTemplate.getCeremonyEvent().getStatus())
                .anyMatch(status -> status == CeremonyEventStatus.STARTED || status == CeremonyEventStatus.FINISHED);
    }

    /**
     * 서명란에 배정돼 있거나(template_fields), 실제로 서명한 기록(stroke_data)/감사 로그
     * (ceremony_event_logs)가 있는 서명자는 삭제할 수 없다 — 셋 중 하나라도 있으면
     * {@code SIGNER_IN_USE}. 아무 흔적도 없어야 지울 수 있다.
     */
    @Transactional
    public void deleteSigner(Long organizationId, Long ceremonyId, Long signerId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Signer signer = findSignerInCeremonyOrThrow(ceremonyId, signerId);
        if (isSignerInUse(signerId)) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_IN_USE);
        }

        signerRepository.delete(signer);
    }

    /**
     * 서명란에 배정돼 있거나(template_fields), 실제로 서명한 기록(stroke_data)/감사 로그
     * (ceremony_event_logs)가 있는지 — {@link #deleteSigner}의 차단 조건이자 {@link #toSummary}가
     * 목록 화면의 삭제 버튼 노출 여부(deletable)를 계산하는 데도 재사용한다.
     */
    private boolean isSignerInUse(Long signerId) {
        return templateFieldRepository.existsBySignerId(signerId)
                || strokeDataRepository.existsBySignerId(signerId)
                || ceremonyEventLogRepository.existsByTargetSignerId(signerId);
    }

    /** {@link com.eformworks.signstage.backend.feature.ceremony.service.TemplateFieldService}가 signerId 검증에 재사용한다. */
    Signer findSignerInCeremonyOrThrow(Long ceremonyId, Long signerId) {
        Signer signer = signerRepository.findById(signerId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND));
        if (!signer.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND);
        }
        return signer;
    }

    private String generateUniqueAccessKey() {
        String accessKey;
        do {
            accessKey = UUID.randomUUID().toString().replace("-", "");
        } while (signerRepository.existsByAccessKey(accessKey));
        return accessKey;
    }

    private SignerDto.Response.SignerSummary toSummary(Signer signer) {
        return new SignerDto.Response.SignerSummary(
                signer.getId(),
                signer.getCeremony().getId(),
                signer.getName(),
                signer.getPosition(),
                signer.getAffiliation(),
                signer.getRoleCode(),
                signer.getAccessKey(),
                isSignerLockedByStartedEvent(signer.getId()),
                !isSignerInUse(signer.getId()),
                signer.getCreatedAt()
        );
    }
}
