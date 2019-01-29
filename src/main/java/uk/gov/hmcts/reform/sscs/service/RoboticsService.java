package uk.gov.hmcts.reform.sscs.service;

import static uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment;
import uk.gov.hmcts.reform.sscs.domain.email.RoboticsEmailTemplate;
import uk.gov.hmcts.reform.sscs.domain.robotics.RoboticsWrapper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.json.RoboticsJsonMapper;
import uk.gov.hmcts.reform.sscs.json.RoboticsJsonValidator;

@Service
@Slf4j
public class RoboticsService {

    private final AirLookupService airLookupService;
    private final EmailService emailService;
    private final RoboticsJsonMapper roboticsJsonMapper;
    private final RoboticsJsonValidator roboticsJsonValidator;
    private final RoboticsEmailTemplate roboticsEmailTemplate;
    private final RoboticsJsonUploadService roboticsJsonUploadService;

    @Autowired
    public RoboticsService(
            AirLookupService airLookupService,
            EmailService emailService,
            RoboticsJsonMapper roboticsJsonMapper,
            RoboticsJsonValidator roboticsJsonValidator,
            RoboticsEmailTemplate roboticsEmailTemplate,
            RoboticsJsonUploadService roboticsJsonUploadService
    ) {
        this.airLookupService = airLookupService;
        this.emailService = emailService;
        this.roboticsJsonMapper = roboticsJsonMapper;
        this.roboticsJsonValidator = roboticsJsonValidator;
        this.roboticsEmailTemplate = roboticsEmailTemplate;
        this.roboticsJsonUploadService = roboticsJsonUploadService;
    }

    public JSONObject sendCaseToRobotics(SscsCaseData caseData, Long caseId, String postcode, byte[] pdf) {
        return sendCaseToRobotics(caseData, caseId, postcode, pdf, Collections.emptyMap());
    }

    public JSONObject sendCaseToRobotics(SscsCaseData caseData, Long caseId, String postcode, byte[] pdf, Map<String, byte[]> additionalEvidence) {
        String venue = airLookupService.lookupAirVenueNameByPostCode(postcode);

        JSONObject roboticsJson = createRobotics(RoboticsWrapper.builder().sscsCaseData(caseData)
                .ccdCaseId(caseId).venueName(venue).evidencePresent(caseData.getEvidencePresent()).build());

        sendJsonByEmail(caseData.getAppeal().getAppellant(), roboticsJson, pdf, additionalEvidence);
        log.info("Robotics email sent successfully for caseId {} and Nino {} and benefit type {}", caseId, caseData.getAppeal().getAppellant().getIdentity().getNino(),
                caseData.getAppeal().getBenefitType().getCode());

        return roboticsJson;
    }

    public JSONObject createRobotics(RoboticsWrapper appeal) {

        JSONObject roboticsAppeal = roboticsJsonMapper.map(appeal);

        roboticsJsonValidator.validate(roboticsAppeal);

        return roboticsAppeal;
    }

    public void attachRoboticsJsonToCaseInCcd(JSONObject roboticsJson, SscsCaseData caseData,
                                              IdamTokens idamTokens, SscsCaseDetails caseDetails) {

        log.info("Sending case {} to Robotics", caseDetails.getId());

        if (caseDetails.getId() == null) {
            log.info("CCD caseId is empty - skipping step to update CCD with Robotics JSON");
        } else {
            log.info("CCD caseId is {}, proceeding to update case with Robotics JSON", caseDetails.getId());
            caseData.setCcdCaseId(caseDetails.getId().toString());
            roboticsJsonUploadService
                    .updateCaseWithRoboticsJson(roboticsJson, caseData, caseDetails, idamTokens);
        }
    }

    private void sendJsonByEmail(Appellant appellant, JSONObject json, byte[] pdf, Map<String, byte[]> additionalEvidence) {
        String appellantUniqueId = emailService.generateUniqueEmailId(appellant);
        List<EmailAttachment> attachments = addDefaultAttachment(json, pdf, appellantUniqueId);
        addAdditionalEvidenceAttachments(additionalEvidence, attachments);
        emailService.sendEmail(
                roboticsEmailTemplate.generateEmail(
                        appellantUniqueId,
                        attachments
                )
        );
    }

    private void addAdditionalEvidenceAttachments(Map<String, byte[]> additionalEvidence, List<EmailAttachment> attachments) {
        for (String filename : additionalEvidence.keySet()) {
            if (filename != null) {
                byte[] content = additionalEvidence.get(filename);
                if (content != null) {
                    attachments.add(file(content, filename));
                }
            }
        }
    }

    private List<EmailAttachment> addDefaultAttachment(JSONObject json, byte[] pdf, String appellantUniqueId) {
        List<EmailAttachment> emailAttachments = new ArrayList<>();

        emailAttachments.add(json(json.toString().getBytes(), appellantUniqueId + ".txt"));

        if (pdf != null) {
            emailAttachments.add(pdf(pdf, appellantUniqueId + ".pdf"));
        }

        return emailAttachments;
    }

}
